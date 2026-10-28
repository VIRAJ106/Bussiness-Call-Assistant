package com.dayclose.inventory.config;

import org.springframework.context.annotation.Configuration;

/**
 * Configuration for event publishing.
 * EventSerializer is used statically - no bean needed.
 */
@Configuration
public class EventConfig {
    // EventSerializer uses static methods - no bean configuration needed
}

