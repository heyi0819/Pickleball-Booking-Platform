package com.pickleball.booking.receivable.domain;

public final class RefundRuleViolation extends RuntimeException {
    private final String code;

    public RefundRuleViolation(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
