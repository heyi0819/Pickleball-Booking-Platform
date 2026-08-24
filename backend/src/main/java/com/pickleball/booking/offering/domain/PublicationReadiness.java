package com.pickleball.booking.offering.domain;

import java.util.UUID;

public record PublicationReadiness(
        boolean coachApproved,
        UUID confirmedPriceSnapshotId,
        boolean coachReservationsHeld) {

    public boolean isReady() {
        return coachApproved && confirmedPriceSnapshotId != null && coachReservationsHeld;
    }
}
