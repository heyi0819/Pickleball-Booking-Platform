package com.pickleball.booking.notification.infrastructure;

import com.pickleball.booking.notification.application.NotificationDeliveryException;
import com.pickleball.booking.notification.application.NotificationDeliveryPort.NotificationMessage;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LineRecipientResolver {
    private final JdbcTemplate jdbc;

    public LineRecipientResolver(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String resolve(NotificationMessage message) {
        boolean target = message.notificationTargetId() != null;
        boolean user = message.recipientUserId() != null;
        if (target == user) {
            throw NotificationDeliveryException.permanent(
                    "LINE_RECIPIENT_INVALID",
                    "LINE notification requires exactly one target or user recipient");
        }
        return target ? resolveTarget(message) : resolveUser(message);
    }

    private String resolveTarget(NotificationMessage message) {
        List<String> recipients = jdbc.queryForList("""
                select external_target_id
                from notification_targets
                where id=? and organization_id=? and channel='LINE' and status='ACTIVE'
                """, String.class, message.notificationTargetId(), message.organizationId());
        if (recipients.size() != 1) {
            throw NotificationDeliveryException.permanent(
                    "LINE_TARGET_UNAVAILABLE",
                    "LINE notification target is unavailable or inactive");
        }
        return recipients.getFirst();
    }

    private String resolveUser(NotificationMessage message) {
        List<String> recipients = jdbc.queryForList("""
                select i.provider_subject
                from user_external_identities i
                join users u on u.id=i.user_id
                where i.user_id=? and i.provider='LINE' and i.revoked_at is null
                  and u.status='ACTIVE' and u.deleted_at is null
                order by i.linked_at desc
                limit 1
                """, String.class, message.recipientUserId());
        if (recipients.isEmpty()) {
            throw NotificationDeliveryException.permanent(
                    "LINE_USER_IDENTITY_UNAVAILABLE",
                    "Recipient does not have an active LINE identity");
        }
        return recipients.getFirst();
    }
}
