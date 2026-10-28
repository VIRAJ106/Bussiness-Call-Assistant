package com.dayclose.inventory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enable scheduled tasks: expired reservation sweeper and outbox relay.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
