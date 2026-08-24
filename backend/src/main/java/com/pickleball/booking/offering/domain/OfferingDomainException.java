package com.pickleball.booking.offering.domain;

import java.util.Objects;

public final class OfferingDomainException extends RuntimeException {
    private final OfferingDomainError error;

    public OfferingDomainException(OfferingDomainError error, String message) {
        super(message);
        this.error = Objects.requireNonNull(error, "error");
    }

    public OfferingDomainError error() {
        return error;
    }
}
