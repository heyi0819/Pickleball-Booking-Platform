-- Slice 3 / S3.1: Matching -> Confirmed Course database foundation.
-- This migration is forward-only and intentionally leaves Open Enrollment tables to Slice 4.
-- source_offering_price_snapshot_id is reserved now so the source XOR can be enforced;
-- its FK is added by the Slice 4 migration after course_offering_price_snapshots exists.

CREATE TABLE course_matches (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    lesson_request_id uuid,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    participant_count_snapshot smallint NOT NULL,
    minimum_participants_snapshot smallint,
    maximum_participants_snapshot smallint,
    decision_note text,
    confirmed_by uuid,
    confirmed_at timestamptz,
    cancelled_by uuid,
    cancelled_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_course_matches_organization_id FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_matches_lesson_request_id FOREIGN KEY (lesson_request_id) REFERENCES lesson_requests(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_matches_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_matches_cancelled_by FOREIGN KEY (cancelled_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_course_matches_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_course_matches_participant_count CHECK (participant_count_snapshot > 0),
    CONSTRAINT ck_course_matches_participant_range CHECK (
        minimum_participants_snapshot IS NULL
        OR maximum_participants_snapshot IS NULL
        OR minimum_participants_snapshot <= maximum_participants_snapshot
    ),
    CONSTRAINT ck_course_matches_confirmed_actor CHECK (
        status <> 'CONFIRMED' OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)
    ),
    CONSTRAINT ck_course_matches_cancelled_actor CHECK (
        status <> 'CANCELLED' OR (cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL)
    )
);
CREATE INDEX idx_course_matches_org_status_created ON course_matches(organization_id, status, created_at DESC);
CREATE INDEX idx_course_matches_lesson_request_status ON course_matches(lesson_request_id, status) WHERE lesson_request_id IS NOT NULL;

CREATE TABLE course_match_sessions (
    id uuid PRIMARY KEY,
    course_match_id uuid NOT NULL,
    sequence_no smallint NOT NULL,
    start_at timestamptz NOT NULL,
    end_at timestamptz NOT NULL,
    venue_id uuid,
    venue_name_snapshot varchar(150) NOT NULL,
    venue_address_snapshot varchar(300),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_course_match_sessions_course_match_id FOREIGN KEY (course_match_id) REFERENCES course_matches(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_match_sessions_venue_id FOREIGN KEY (venue_id) REFERENCES venues(id) ON DELETE RESTRICT,
    CONSTRAINT uk_course_match_sessions_match_sequence UNIQUE (course_match_id, sequence_no),
    CONSTRAINT ck_course_match_sessions_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_course_match_sessions_time CHECK (start_at < end_at)
);
CREATE INDEX idx_course_match_sessions_time ON course_match_sessions(start_at, end_at);
CREATE INDEX idx_course_match_sessions_match ON course_match_sessions(course_match_id);

CREATE TABLE course_match_session_coaches (
    id uuid PRIMARY KEY,
    course_match_session_id uuid NOT NULL,
    coach_profile_id uuid NOT NULL,
    availability_proposal_id uuid,
    role_type varchar(20) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'INVITED',
    invited_at timestamptz NOT NULL DEFAULT now(),
    responded_at timestamptz,
    response_reason text,
    invited_by uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_course_match_session_coaches_session_id FOREIGN KEY (course_match_session_id) REFERENCES course_match_sessions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_match_session_coaches_coach_profile_id FOREIGN KEY (coach_profile_id) REFERENCES coach_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_match_session_coaches_availability_proposal_id FOREIGN KEY (availability_proposal_id) REFERENCES coach_availability_proposals(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_match_session_coaches_invited_by FOREIGN KEY (invited_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_course_match_session_coaches_session_coach UNIQUE (course_match_session_id, coach_profile_id),
    CONSTRAINT ck_course_match_session_coaches_role CHECK (role_type IN ('PRIMARY', 'ASSISTANT')),
    CONSTRAINT ck_course_match_session_coaches_status CHECK (status IN ('INVITED', 'ACCEPTED', 'REJECTED', 'REPLACED')),
    CONSTRAINT ck_course_match_session_coaches_response CHECK (
        status = 'INVITED' OR responded_at IS NOT NULL
    )
);
CREATE UNIQUE INDEX uk_course_match_session_coaches_active_primary
    ON course_match_session_coaches(course_match_session_id)
    WHERE role_type = 'PRIMARY' AND status IN ('INVITED', 'ACCEPTED');
CREATE INDEX idx_course_match_session_coaches_coach_status ON course_match_session_coaches(coach_profile_id, status);
CREATE INDEX idx_course_match_session_coaches_availability ON course_match_session_coaches(availability_proposal_id) WHERE availability_proposal_id IS NOT NULL;

CREATE TABLE course_match_price_snapshots (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    course_match_id uuid NOT NULL,
    version_no integer NOT NULL,
    status varchar(20) NOT NULL,
    billing_mode varchar(20) NOT NULL,
    currency char(3) NOT NULL DEFAULT 'TWD',
    total_amount numeric(12,2) NOT NULL,
    pricing_fingerprint varchar(64) NOT NULL,
    rule_trace jsonb NOT NULL DEFAULT '{}'::jsonb,
    confirmation_note text,
    confirmed_by uuid,
    confirmed_at timestamptz,
    created_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_course_match_price_snapshots_organization_id FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_match_price_snapshots_course_match_id FOREIGN KEY (course_match_id) REFERENCES course_matches(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_match_price_snapshots_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_match_price_snapshots_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_course_match_price_snapshots_match_version UNIQUE (course_match_id, version_no),
    CONSTRAINT ck_course_match_price_snapshots_version CHECK (version_no >= 1),
    CONSTRAINT ck_course_match_price_snapshots_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'SUPERSEDED')),
    CONSTRAINT ck_course_match_price_snapshots_billing_mode CHECK (billing_mode IN ('FULL_COURSE', 'PER_SESSION')),
    CONSTRAINT ck_course_match_price_snapshots_total CHECK (total_amount >= 0),
    CONSTRAINT ck_course_match_price_snapshots_confirmed_actor CHECK (
        status <> 'CONFIRMED' OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)
    )
);
CREATE UNIQUE INDEX uk_course_match_price_snapshots_confirmed
    ON course_match_price_snapshots(course_match_id)
    WHERE status = 'CONFIRMED';
CREATE INDEX idx_course_match_price_snapshots_match_status ON course_match_price_snapshots(course_match_id, status);
CREATE INDEX idx_course_match_price_snapshots_org_created ON course_match_price_snapshots(organization_id, created_at DESC);

CREATE TABLE course_match_price_snapshot_items (
    id uuid PRIMARY KEY,
    course_match_price_snapshot_id uuid NOT NULL,
    course_match_session_id uuid,
    item_type varchar(30) NOT NULL,
    description text,
    quantity numeric(12,4),
    unit_amount numeric(12,2),
    line_amount numeric(12,2) NOT NULL,
    source_reference_type varchar(50),
    source_reference_id uuid,
    sort_order integer NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_match_price_snapshot_items_snapshot_id FOREIGN KEY (course_match_price_snapshot_id) REFERENCES course_match_price_snapshots(id) ON DELETE RESTRICT,
    CONSTRAINT fk_match_price_snapshot_items_session_id FOREIGN KEY (course_match_session_id) REFERENCES course_match_sessions(id) ON DELETE RESTRICT,
    CONSTRAINT ck_match_price_snapshot_items_type CHECK (item_type IN ('TUITION', 'VENUE', 'ADJUSTMENT')),
    CONSTRAINT ck_match_price_snapshot_items_quantity CHECK (quantity IS NULL OR quantity > 0)
);
CREATE INDEX idx_match_price_snapshot_items_snapshot ON course_match_price_snapshot_items(course_match_price_snapshot_id, sort_order);
CREATE INDEX idx_match_price_snapshot_items_session ON course_match_price_snapshot_items(course_match_session_id) WHERE course_match_session_id IS NOT NULL;

-- Minimal formal course backbone required by the Slice 3 confirmation and pricing-lineage merge gate.
CREATE TABLE courses (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    course_no varchar(30) NOT NULL,
    source_match_id uuid,
    created_by_user_id uuid NOT NULL,
    course_type varchar(20) NOT NULL,
    schedule_type varchar(20) NOT NULL,
    billing_mode varchar(20) NOT NULL,
    skill_level varchar(30),
    expected_participant_count smallint NOT NULL,
    guest_participant_count smallint NOT NULL DEFAULT 0,
    minimum_participants smallint,
    maximum_participants smallint,
    total_session_count smallint NOT NULL,
    status varchar(30) NOT NULL DEFAULT 'DRAFT',
    activated_at timestamptz,
    completed_at timestamptz,
    cancelled_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_courses_organization_id FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_courses_source_match_id FOREIGN KEY (source_match_id) REFERENCES course_matches(id) ON DELETE RESTRICT,
    CONSTRAINT fk_courses_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_courses_org_course_no UNIQUE (organization_id, course_no),
    CONSTRAINT ck_courses_type CHECK (course_type IN ('PRIVATE', 'GROUP')),
    CONSTRAINT ck_courses_schedule_type CHECK (schedule_type IN ('SINGLE', 'RECURRING')),
    CONSTRAINT ck_courses_billing_mode CHECK (billing_mode IN ('FULL_COURSE', 'PER_SESSION')),
    CONSTRAINT ck_courses_status CHECK (status IN ('DRAFT', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    CONSTRAINT ck_courses_participant_count CHECK (expected_participant_count > 0 AND guest_participant_count >= 0),
    CONSTRAINT ck_courses_total_session_count CHECK (total_session_count > 0),
    CONSTRAINT ck_courses_single_session CHECK (schedule_type <> 'SINGLE' OR total_session_count = 1),
    CONSTRAINT ck_courses_participant_range CHECK (
        minimum_participants IS NULL
        OR maximum_participants IS NULL
        OR minimum_participants <= maximum_participants
    ),
    CONSTRAINT ck_courses_private_max CHECK (course_type <> 'PRIVATE' OR maximum_participants IS NULL OR maximum_participants <= 4)
);
CREATE UNIQUE INDEX uk_courses_source_match ON courses(source_match_id) WHERE source_match_id IS NOT NULL;
CREATE INDEX idx_courses_org_status_created ON courses(organization_id, status, created_at DESC);

CREATE TABLE course_sessions (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    course_id uuid NOT NULL,
    sequence_no smallint NOT NULL,
    scheduled_start_at timestamptz NOT NULL,
    scheduled_end_at timestamptz NOT NULL,
    expected_participant_count smallint NOT NULL,
    guest_participant_count smallint NOT NULL DEFAULT 0,
    actual_participant_count smallint,
    status varchar(30) NOT NULL DEFAULT 'SCHEDULED',
    cancellation_source varchar(20),
    cancellation_note text,
    completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_course_sessions_organization_id FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_sessions_course_id FOREIGN KEY (course_id) REFERENCES courses(id) ON DELETE RESTRICT,
    CONSTRAINT uk_course_sessions_course_sequence UNIQUE (course_id, sequence_no),
    CONSTRAINT ck_course_sessions_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_course_sessions_time CHECK (scheduled_start_at < scheduled_end_at),
    CONSTRAINT ck_course_sessions_participant_count CHECK (
        expected_participant_count > 0
        AND guest_participant_count >= 0
        AND (actual_participant_count IS NULL OR actual_participant_count >= 0)
    ),
    CONSTRAINT ck_course_sessions_status CHECK (status IN ('SCHEDULED', 'CANCEL_PENDING', 'CANCELLED', 'COMPLETED', 'POSTPONED')),
    CONSTRAINT ck_course_sessions_cancellation_source CHECK (
        cancellation_source IS NULL OR cancellation_source IN ('STUDENT', 'COACH', 'COMMITTEE', 'SYSTEM')
    )
);
CREATE INDEX idx_course_sessions_org_status_start ON course_sessions(organization_id, status, scheduled_start_at);
CREATE INDEX idx_course_sessions_course_sequence ON course_sessions(course_id, sequence_no);

CREATE TABLE session_price_snapshots (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    course_session_id uuid NOT NULL,
    version_no integer NOT NULL,
    status varchar(20) NOT NULL,
    currency char(3) NOT NULL DEFAULT 'TWD',
    tuition_amount numeric(12,2) NOT NULL,
    venue_fee numeric(12,2) NOT NULL,
    other_adjustment numeric(12,2) NOT NULL DEFAULT 0,
    total_receivable numeric(12,2) NOT NULL,
    rule_trace jsonb NOT NULL DEFAULT '{}'::jsonb,
    source_match_price_snapshot_id uuid,
    source_offering_price_snapshot_id uuid,
    confirmed_by uuid,
    confirmed_at timestamptz,
    created_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_session_price_snapshots_organization_id FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_session_price_snapshots_course_session_id FOREIGN KEY (course_session_id) REFERENCES course_sessions(id) ON DELETE RESTRICT,
    CONSTRAINT fk_session_price_snapshots_source_match_price_snapshot_id FOREIGN KEY (source_match_price_snapshot_id) REFERENCES course_match_price_snapshots(id) ON DELETE RESTRICT,
    CONSTRAINT fk_session_price_snapshots_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_session_price_snapshots_created_by FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_session_price_snapshots_session_version UNIQUE (course_session_id, version_no),
    CONSTRAINT ck_session_price_snapshots_version CHECK (version_no >= 1),
    CONSTRAINT ck_session_price_snapshots_status CHECK (status IN ('DRAFT', 'CONFIRMED', 'SUPERSEDED')),
    CONSTRAINT ck_session_price_snapshots_amounts CHECK (
        tuition_amount >= 0
        AND venue_fee >= 0
        AND total_receivable >= 0
        AND total_receivable = tuition_amount + venue_fee + other_adjustment
    ),
    CONSTRAINT ck_session_price_snapshots_confirmed_actor CHECK (
        status <> 'CONFIRMED' OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)
    ),
    CONSTRAINT ck_session_price_snapshots_source_xor CHECK (
        num_nonnulls(source_match_price_snapshot_id, source_offering_price_snapshot_id) <= 1
    )
);
CREATE UNIQUE INDEX uk_session_price_snapshots_confirmed
    ON session_price_snapshots(course_session_id)
    WHERE status = 'CONFIRMED';
CREATE INDEX idx_session_price_snapshots_source_match ON session_price_snapshots(source_match_price_snapshot_id) WHERE source_match_price_snapshot_id IS NOT NULL;
CREATE INDEX idx_session_price_snapshots_source_offering ON session_price_snapshots(source_offering_price_snapshot_id) WHERE source_offering_price_snapshot_id IS NOT NULL;

CREATE TABLE session_price_snapshot_items (
    id uuid PRIMARY KEY,
    price_snapshot_id uuid NOT NULL,
    item_type varchar(30) NOT NULL,
    description varchar(300) NOT NULL,
    quantity numeric(10,2) NOT NULL,
    unit_amount numeric(12,2) NOT NULL,
    line_amount numeric(12,2) NOT NULL,
    source_reference_type varchar(30),
    source_reference_id uuid,
    sort_order smallint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_session_price_snapshot_items_snapshot_id FOREIGN KEY (price_snapshot_id) REFERENCES session_price_snapshots(id) ON DELETE RESTRICT,
    CONSTRAINT ck_session_price_snapshot_items_type CHECK (item_type IN ('TUITION', 'VENUE_FEE', 'OTHER', 'DISCOUNT', 'MANUAL_ADJUSTMENT')),
    CONSTRAINT ck_session_price_snapshot_items_quantity CHECK (quantity > 0),
    CONSTRAINT ck_session_price_snapshot_items_line_amount CHECK (line_amount = quantity * unit_amount),
    CONSTRAINT ck_session_price_snapshot_items_negative CHECK (
        unit_amount >= 0 OR item_type IN ('DISCOUNT', 'MANUAL_ADJUSTMENT')
    )
);
CREATE INDEX idx_session_price_snapshot_items_snapshot ON session_price_snapshot_items(price_snapshot_id, sort_order);

ALTER TABLE coach_availability_claims
    ADD CONSTRAINT fk_coach_availability_claims_converted_course_match_id
    FOREIGN KEY (converted_course_match_id) REFERENCES course_matches(id) ON DELETE RESTRICT;
CREATE INDEX idx_coach_availability_claims_converted_match
    ON coach_availability_claims(converted_course_match_id)
    WHERE converted_course_match_id IS NOT NULL;
