package com.dayclose.notification.service;

import com.dayclose.notification.domain.Notification;
import com.dayclose.notification.domain.NotificationChannel;
import com.dayclose.notification.domain.NotificationStatus;
import com.dayclose.notification.infra.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Background worker that sends pending notifications.
 */
@Service
@ConditionalOnProperty(name = "dayclose.notification.worker.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationWorker {

    private static final Logger log = LoggerFactory.getLogger(NotificationWorker.class);

    private final NotificationRepository notificationRepository;
    private final SmsProvider smsProvider;

    public NotificationWorker(NotificationRepository notificationRepository,
                             SmsProvider smsProvider) {
        this.notificationRepository = notificationRepository;
        this.smsProvider = smsProvider;
    }

    @Scheduled(fixedDelayString = "${dayclose.notification.poll-interval-ms:1000}")
    public void processNotifications() {
        List<Notification> pending = notificationRepository.fetchPending(10);

        for (Notification notification : pending) {
            try {
                log.info("Sending notification {} via {} to {}",
                        notification.id(), notification.channel(), notification.recipient());

                // Update status to sending
                notificationRepository.updateStatus(notification.id(), NotificationStatus.SENDING, null);

                // Send based on channel
                boolean success = switch (notification.channel()) {
                    case SMS -> smsProvider.send(notification.recipient(), notification.messageBody());
                    case WHATSAPP -> smsProvider.sendWhatsApp(notification.recipient(), notification.messageBody());
                    case EMAIL, PUSH -> {
                        log.warn("Channel {} not implemented yet", notification.channel());
                        yield false;
                    }
                };

                if (success) {
                    notificationRepository.updateStatus(notification.id(), NotificationStatus.SENT, null);
                    log.info("Notification {} sent successfully", notification.id());
                } else {
                    throw new Exception("Provider returned failure");
                }

            } catch (Exception e) {
                log.error("Failed to send notification {}: {}", notification.id(), e.getMessage());

                // Retry logic
                if (notification.retryCount() < notification.maxRetries() - 1) {
                    notificationRepository.incrementRetry(notification.id());
                    log.info("Notification {} will be retried (attempt {}/{})",
                            notification.id(), notification.retryCount() + 1, notification.maxRetries());
                } else {
                    notificationRepository.updateStatus(
                            notification.id(),
                            NotificationStatus.FAILED,
                            e.getMessage()
                    );
                    log.error("Notification {} failed after {} attempts", notification.id(), notification.maxRetries());
                }
            }
        }
    }
}
