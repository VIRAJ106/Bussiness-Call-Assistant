package com.dayclose.commitment.saga.client;

/**
 * Thrown when a saga step fails and cannot be retried.
 */
public class SagaException extends RuntimeException {
    public SagaException(String message, Throwable cause) {
        super(message, cause);
    }

    public SagaException(String message) {
        super(message);
    }
}
