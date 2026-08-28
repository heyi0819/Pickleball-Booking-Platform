package com.pickleball.booking.notification.application;

public final class NotificationDeliveryException extends RuntimeException {
    private final String errorCode;
    private final boolean retryable;

    private NotificationDeliveryException(String errorCode, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.retryable = retryable;
    }

    public static NotificationDeliveryException retryable(String errorCode, String message, Throwable cause) {
        return new NotificationDeliveryException(errorCode, message, true, cause);
    }

    public static NotificationDeliveryException permanent(String errorCode, String message) {
        return new NotificationDeliveryException(errorCode, message, false, null);
    }

    public String errorCode() {
        return errorCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
