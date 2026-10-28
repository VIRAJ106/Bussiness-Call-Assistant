package com.dayclose.notification.domain;

public enum NotificationStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED,
    CANCELLED;

    public String toDbValue() {
        return name().toLowerCase();
    }

    public static NotificationStatus fromString(String value) {
        return valueOf(value.toUpperCase());
    }
}
