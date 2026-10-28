package com.dayclose.inventory.service;

import com.dayclose.inventory.infra.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Background sweeper releasing expired reservations across all items and locations (ADR-0005).
 */
@Service
@ConditionalOnProperty(name = "dayclose.sweeper.enabled", havingValue = "true", matchIfMissing = true)
public class ExpiredReservationSweeper {

    private static final Logger log = LoggerFactory.getLogger(ExpiredReservationSweeper.class);

    private final ReservationRepository reservationRepository;

    public ExpiredReservationSweeper(ReservationRepository reservationRepository) {
        this.reservationRepository = reservationRepository;
    }

    @Scheduled(fixedDelayString = "${dayclose.sweeper.interval-ms:60000}")
    public void sweepExpiredReservations() {
        try {
            int released = reservationRepository.releaseExpiredAll(100);
            if (released > 0) {
                log.info("Sweeper released {} expired stock reservations", released);
            }
        } catch (Exception e) {
            log.error("Error running expired reservation sweeper: {}", e.getMessage());
        }
    }
}
