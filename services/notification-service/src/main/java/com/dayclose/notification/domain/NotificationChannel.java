package com.dayclose.notification.domain;

public enum NotificationChannel {
    SMS,
    EMAIL,
    WHATSAPP,
    PUSH;

    public String toDbValue() {
        return name().toLowerCase();
    }

    public static NotificationChannel fromString(String value) {
        return valueOf(value.toUpperCase());
    }
}
