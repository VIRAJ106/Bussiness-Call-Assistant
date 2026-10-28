package com.dayclose.inventory.service;

import com.dayclose.events.EventTypes;
import com.dayclose.inventory.infra.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Outbox poller relay publishing domain events to RabbitMQ topic exchange (ADR-0006).
 */
@Service
@ConditionalOnProperty(name = "dayclose.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outboxRepository;
    private final RabbitTemplate rabbitTemplate;

    public OutboxRelay(OutboxRepository outboxRepository, RabbitTemplate rabbitTemplate) {
        this.outboxRepository = outboxRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Scheduled(fixedDelayString = "${dayclose.outbox.poll-interval-ms:500}")
    public void pollAndPublish() {
        List<OutboxRepository.OutboxRecord> records = outboxRepository.fetchUnpublished(50);
        for (var rec : records) {
            try {
                rabbitTemplate.convertAndSend(EventTypes.EXCHANGE, rec.eventType(), rec.payloadJson());
                outboxRepository.markPublished(rec.id());
                log.debug("Published outbox event {} of type {}", rec.eventId(), rec.eventType());
            } catch (Exception e) {
                log.warn("Failed to publish outbox event {}: {}", rec.eventId(), e.getMessage());
                outboxRepository.incrementRetry(rec.id());
                break; // Stop batch on connection failure, retry next cycle
            }
        }
    }
}
