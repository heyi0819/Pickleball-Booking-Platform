CREATE TABLE coach_profiles (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id),
    user_id uuid NOT NULL REFERENCES users(id),
    approval_status varchar(20) NOT NULL CHECK (approval_status IN ('PENDING','APPROVED','REJECTED','SUSPENDED')),
    skill_level varchar(30), bio text, approved_by uuid REFERENCES users(id), approved_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0, deleted_at timestamptz,
    CONSTRAINT uk_coach_profiles_organization_user UNIQUE (organization_id, user_id)
);
CREATE INDEX idx_coach_profiles_org_approval ON coach_profiles(organization_id, approval_status);

CREATE TABLE venues (
    id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    name varchar(150) NOT NULL, address varchar(300), contact_info varchar(300), default_cost_amount numeric(12,2),
    status varchar(20) NOT NULL CHECK (status IN ('ACTIVE','INACTIVE')), note text,
    created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), deleted_at timestamptz,
    CONSTRAINT ck_venues_default_cost CHECK (default_cost_amount IS NULL OR default_cost_amount >= 0)
);
CREATE INDEX idx_venues_org_status ON venues(organization_id,status);
CREATE INDEX idx_venues_org_name ON venues(organization_id,name);

CREATE TABLE coach_applications (
    id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id),
    coach_profile_id uuid NOT NULL REFERENCES coach_profiles(id),
    status varchar(20) NOT NULL CHECK (status IN ('SUBMITTED','APPROVED','REJECTED','WITHDRAWN')),
    application_note text, submitted_at timestamptz NOT NULL, reviewed_by uuid REFERENCES users(id),
    reviewed_at timestamptz, review_note text, created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_coach_applications_review CHECK ((status NOT IN ('APPROVED','REJECTED')) OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_note IS NOT NULL))
);
CREATE INDEX idx_coach_applications_org_status_submitted ON coach_applications(organization_id,status,submitted_at);
CREATE INDEX idx_coach_applications_profile_submitted ON coach_applications(coach_profile_id,submitted_at DESC);

CREATE TABLE coach_availability_proposals (
    id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id),
    coach_profile_id uuid NOT NULL REFERENCES coach_profiles(id), start_at timestamptz NOT NULL, end_at timestamptz NOT NULL,
    preferred_venue_id uuid REFERENCES venues(id) ON DELETE RESTRICT, status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','MATCHED','CLOSED')),
    submitted_at timestamptz, reviewed_by uuid REFERENCES users(id), reviewed_at timestamptz, review_note text, matched_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_coach_availability_time CHECK (start_at < end_at),
    CONSTRAINT ck_coach_availability_review CHECK ((status NOT IN ('APPROVED','REJECTED')) OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_note IS NOT NULL))
);
CREATE INDEX idx_coach_availability_coach_time ON coach_availability_proposals(coach_profile_id,start_at,end_at);
CREATE INDEX idx_coach_availability_status_time ON coach_availability_proposals(organization_id,status,start_at);

CREATE TABLE lesson_requests (
    id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id), requester_user_id uuid NOT NULL REFERENCES users(id),
    preferred_coach_profile_id uuid REFERENCES coach_profiles(id), selected_availability_proposal_id uuid REFERENCES coach_availability_proposals(id) ON DELETE RESTRICT, lesson_type varchar(20) NOT NULL CHECK (lesson_type IN ('PRIVATE','GROUP')),
    schedule_type varchar(20) NOT NULL CHECK (schedule_type IN ('SINGLE','RECURRING')),
    billing_mode varchar(20) NOT NULL CHECK (billing_mode IN ('FULL_COURSE','PER_SESSION')), skill_level varchar(30),
    participant_count smallint NOT NULL, guest_participant_count smallint NOT NULL DEFAULT 0, minimum_participants smallint, maximum_participants smallint,
    requested_session_count smallint NOT NULL, status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SUBMITTED','APPROVED','REJECTED','MATCHED','CANCELLED')),
    notes text, submitted_at timestamptz, reviewed_by uuid REFERENCES users(id), reviewed_at timestamptz, review_note text,
    created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_lesson_requests_participants CHECK (participant_count > 0), CONSTRAINT ck_lesson_requests_session_count CHECK (requested_session_count > 0),
    CONSTRAINT ck_lesson_requests_private_size CHECK (lesson_type <> 'PRIVATE' OR maximum_participants IS NULL OR maximum_participants <= 4),
    CONSTRAINT ck_lesson_requests_group_range CHECK (lesson_type <> 'GROUP' OR (minimum_participants IS NOT NULL AND maximum_participants IS NOT NULL AND minimum_participants <= maximum_participants)),
    CONSTRAINT ck_lesson_requests_single_count CHECK (schedule_type <> 'SINGLE' OR requested_session_count = 1),
    CONSTRAINT ck_lesson_requests_review CHECK ((status NOT IN ('APPROVED','REJECTED')) OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_note IS NOT NULL))
);
CREATE INDEX idx_lesson_requests_org_status_created ON lesson_requests(organization_id,status,created_at);
CREATE INDEX idx_lesson_requests_requester_status_created ON lesson_requests(requester_user_id,status,created_at DESC);
CREATE INDEX idx_lesson_requests_selected_availability_proposal ON lesson_requests(selected_availability_proposal_id) WHERE selected_availability_proposal_id IS NOT NULL;

CREATE TABLE lesson_request_session_preferences (
    id uuid PRIMARY KEY, lesson_request_id uuid NOT NULL REFERENCES lesson_requests(id), sequence_no smallint NOT NULL,
    preferred_start_at timestamptz NOT NULL, preferred_end_at timestamptz NOT NULL, preferred_venue_id uuid REFERENCES venues(id) ON DELETE RESTRICT, note varchar(500), created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_lesson_request_preferences_sequence UNIQUE (lesson_request_id,sequence_no), CONSTRAINT ck_lesson_request_preferences_time CHECK (preferred_start_at < preferred_end_at)
);
CREATE INDEX idx_lesson_request_preferences_time ON lesson_request_session_preferences(preferred_start_at,preferred_end_at);

CREATE TABLE coach_availability_claims (
    id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id), coach_availability_proposal_id uuid NOT NULL REFERENCES coach_availability_proposals(id),
    lesson_request_id uuid NOT NULL REFERENCES lesson_requests(id), status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','RELEASED','CONVERTED','CLOSED')),
    claimed_at timestamptz NOT NULL DEFAULT now(), released_at timestamptz, released_by uuid REFERENCES users(id), release_reason text,
    converted_course_match_id uuid, created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(), version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_availability_claim_request_proposal UNIQUE (lesson_request_id,coach_availability_proposal_id)
);
CREATE UNIQUE INDEX uk_coach_availability_claims_active_proposal ON coach_availability_claims(coach_availability_proposal_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_coach_availability_claims_org_status_claimed ON coach_availability_claims(organization_id,status,claimed_at);

CREATE TABLE api_idempotency_keys (
    id uuid PRIMARY KEY, organization_id uuid REFERENCES organizations(id) ON DELETE RESTRICT, actor_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    operation varchar(100) NOT NULL, idempotency_key varchar(100) NOT NULL, request_hash varchar(64) NOT NULL,
    result_resource_type varchar(80), result_resource_id uuid, response_status integer, expires_at timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_api_idempotency_keys_actor_operation_key UNIQUE (actor_user_id,operation,idempotency_key)
);
CREATE INDEX idx_api_idempotency_keys_expires_at ON api_idempotency_keys(expires_at);
CREATE INDEX idx_api_idempotency_keys_actor_created_at ON api_idempotency_keys(actor_user_id,created_at DESC);

CREATE TABLE outbox_events (
    id uuid PRIMARY KEY, organization_id uuid NOT NULL REFERENCES organizations(id), aggregate_type varchar(50) NOT NULL, aggregate_id uuid NOT NULL,
    event_type varchar(80) NOT NULL, payload jsonb NOT NULL, dedupe_key varchar(150), status varchar(20) NOT NULL CHECK (status IN ('PENDING','PROCESSING','PROCESSED','FAILED','DEAD')),
    attempt_count integer NOT NULL DEFAULT 0, available_at timestamptz NOT NULL, processed_at timestamptz, last_error text, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_outbox_events_status_available_created ON outbox_events(status,available_at,created_at);
CREATE INDEX idx_outbox_events_aggregate_created ON outbox_events(aggregate_type,aggregate_id,created_at);
CREATE UNIQUE INDEX uk_outbox_events_dedupe_key ON outbox_events(dedupe_key) WHERE dedupe_key IS NOT NULL;

CREATE TABLE audit_logs (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY, organization_id uuid REFERENCES organizations(id), actor_user_id uuid REFERENCES users(id),
    actor_type varchar(20) NOT NULL CHECK (actor_type IN ('USER','SYSTEM','WORKER')), action varchar(80) NOT NULL, entity_type varchar(50) NOT NULL,
    entity_id uuid NOT NULL, before_data jsonb, after_data jsonb, reason text, request_id varchar(100), ip_address inet, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_entity_created ON audit_logs(entity_type,entity_id,created_at DESC);
CREATE INDEX idx_audit_logs_actor_created ON audit_logs(actor_user_id,created_at DESC);
CREATE INDEX idx_audit_logs_org_created ON audit_logs(organization_id,created_at DESC);
