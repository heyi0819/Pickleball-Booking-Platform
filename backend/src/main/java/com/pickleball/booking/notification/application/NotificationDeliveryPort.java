package com.pickleball.booking.notification.application;

import java.util.Map;
import java.util.UUID;

public interface NotificationDeliveryPort {
    void deliver(NotificationMessage message);

    record NotificationMessage(
            UUID id,
            UUID organizationId,
            UUID notificationTargetId,
            UUID recipientUserId,
            String channel,
            String templateCode,
            String businessType,
            UUID businessId,
            Map<String, Object> payload,
            int attemptCount) {}
}
