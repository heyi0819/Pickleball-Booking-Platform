package com.pickleball.booking.receivable.domain;

public final class ReceivablePaymentRuleViolation extends RuntimeException {
    private final String code;

    public ReceivablePaymentRuleViolation(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
