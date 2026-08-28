-- Slice 8 / S8.1: Notification persistence foundation.
-- Forward-only. V1-V11 history is immutable.
-- Outbox remains the domain-event delivery ledger; notifications are a separate
-- channel-delivery projection and must not be merged with outbox_events.

CREATE TABLE notification_targets (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    channel varchar(20) NOT NULL,
    target_type varchar(20) NOT NULL,
    target_code varchar(50) NOT NULL,
    external_target_id varchar(255) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_notification_targets_org_channel_code
        UNIQUE (organization_id, channel, target_code),
    CONSTRAINT ck_notification_targets_channel
        CHECK (channel IN ('LINE')),
    CONSTRAINT ck_notification_targets_type
        CHECK (target_type IN ('GROUP','USER')),
    CONSTRAINT ck_notification_targets_status
        CHECK (status IN ('ACTIVE','INACTIVE')),
    CONSTRAINT ck_notification_targets_code_not_blank
        CHECK (length(btrim(target_code)) > 0),
    CONSTRAINT ck_notification_targets_external_id_not_blank
        CHECK (length(btrim(external_target_id)) > 0)
);

CREATE INDEX idx_notification_targets_org_status
    ON notification_targets(organization_id, status, target_code);

CREATE TABLE notifications (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    notification_target_id uuid REFERENCES notification_targets(id) ON DELETE RESTRICT,
    recipient_user_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    channel varchar(20) NOT NULL,
    template_code varchar(60) NOT NULL,
    business_type varchar(40) NOT NULL,
    business_id uuid NOT NULL,
    payload jsonb NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0,
    next_attempt_at timestamptz,
    sent_at timestamptz,
    last_error_code varchar(100),
    last_error_message text,
    dedupe_key varchar(150) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_notifications_org_dedupe UNIQUE (organization_id, dedupe_key),
    CONSTRAINT ck_notifications_channel CHECK (channel IN ('LINE')),
    CONSTRAINT ck_notifications_status
        CHECK (status IN ('PENDING','SENDING','SENT','FAILED','DEAD')),
    CONSTRAINT ck_notifications_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_notifications_template_code_not_blank
        CHECK (length(btrim(template_code)) > 0),
    CONSTRAINT ck_notifications_business_type_not_blank
        CHECK (length(btrim(business_type)) > 0),
    CONSTRAINT ck_notifications_dedupe_key_not_blank
        CHECK (length(btrim(dedupe_key)) > 0),
    CONSTRAINT ck_notifications_sent_metadata
        CHECK (status <> 'SENT' OR sent_at IS NOT NULL)
);

-- Claim path for concurrent workers. The application worker will combine this
-- index with SELECT ... FOR UPDATE SKIP LOCKED.
CREATE INDEX idx_notifications_worker
    ON notifications(status, next_attempt_at, created_at)
    WHERE status IN ('PENDING','FAILED');

CREATE INDEX idx_notifications_business
    ON notifications(organization_id, business_type, business_id, created_at DESC);

CREATE INDEX idx_notifications_recipient
    ON notifications(organization_id, recipient_user_id, created_at DESC)
    WHERE recipient_user_id IS NOT NULL;

CREATE INDEX idx_notifications_target
    ON notifications(notification_target_id, created_at DESC)
    WHERE notification_target_id IS NOT NULL;
