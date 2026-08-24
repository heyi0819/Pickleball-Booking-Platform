-- Slice 4 / S4.1: Open Enrollment persistence foundation.
-- Forward-only. V1-V7 remain immutable.
--
-- Open Enrollment is a first-class source of a formal Course. It does not create
-- synthetic LessonRequest/CourseMatch rows. A confirmed offering may later be
-- converted to exactly one Course by application-layer confirmation logic.

CREATE TABLE course_offerings (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    coach_profile_id uuid NOT NULL,
    title varchar(200) NOT NULL,
    description text,
    lesson_type varchar(20) NOT NULL,
    schedule_type varchar(20) NOT NULL,
    billing_mode varchar(20) NOT NULL,
    skill_level varchar(30),
    minimum_participants smallint NOT NULL,
    maximum_participants smallint NOT NULL,
    registration_open_at timestamptz NOT NULL,
    registration_close_at timestamptz NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    published_by uuid,
    published_at timestamptz,
    closed_by uuid,
    closed_at timestamptz,
    confirmed_by uuid,
    confirmed_at timestamptz,
    cancelled_by uuid,
    cancelled_at timestamptz,
    cancel_reason text,
    created_by uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_course_offerings_organization_id
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_offerings_coach_profile_id
        FOREIGN KEY (coach_profile_id) REFERENCES coach_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_offerings_published_by
        FOREIGN KEY (published_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_offerings_closed_by
        FOREIGN KEY (closed_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_offerings_confirmed_by
        FOREIGN KEY (confirmed_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_offerings_cancelled_by
        FOREIGN KEY (cancelled_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_offerings_created_by
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT ck_course_offerings_lesson_type
        CHECK (lesson_type = 'GROUP'),
    CONSTRAINT ck_course_offerings_schedule_type
        CHECK (schedule_type IN ('SINGLE', 'RECURRING')),
    CONSTRAINT ck_course_offerings_billing_mode
        CHECK (billing_mode IN ('FULL_COURSE', 'PER_SESSION')),
    CONSTRAINT ck_course_offerings_participant_range
        CHECK (minimum_participants > 0 AND maximum_participants >= minimum_participants),
    CONSTRAINT ck_course_offerings_registration_window
        CHECK (registration_close_at > registration_open_at),
    CONSTRAINT ck_course_offerings_status
        CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'CONFIRMED', 'CANCELLED')),
    CONSTRAINT ck_course_offerings_published_actor
        CHECK (status NOT IN ('OPEN', 'CLOSED', 'CONFIRMED') OR (published_by IS NOT NULL AND published_at IS NOT NULL)),
    CONSTRAINT ck_course_offerings_closed_actor
        CHECK (status NOT IN ('CLOSED', 'CONFIRMED') OR (closed_by IS NOT NULL AND closed_at IS NOT NULL)),
    CONSTRAINT ck_course_offerings_confirmed_actor
        CHECK (status <> 'CONFIRMED' OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)),
    CONSTRAINT ck_course_offerings_cancelled_actor
        CHECK (status <> 'CANCELLED' OR (cancelled_by IS NOT NULL AND cancelled_at IS NOT NULL))
);

CREATE INDEX idx_course_offerings_org_status_reg_close
    ON course_offerings(organization_id, status, registration_close_at);
CREATE INDEX idx_course_offerings_open_window
    ON course_offerings(organization_id, registration_open_at, registration_close_at)
    WHERE status = 'OPEN';
CREATE INDEX idx_course_offerings_coach_status
    ON course_offerings(coach_profile_id, status, registration_close_at);

CREATE TABLE course_offering_sessions (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    course_offering_id uuid NOT NULL,
    sequence_no smallint NOT NULL,
    start_at timestamptz NOT NULL,
    end_at timestamptz NOT NULL,
    venue_id uuid,
    venue_name_snapshot varchar(150) NOT NULL,
    venue_address_snapshot varchar(300),
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_course_offering_sessions_organization_id
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_offering_sessions_offering_id
        FOREIGN KEY (course_offering_id) REFERENCES course_offerings(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_offering_sessions_venue_id
        FOREIGN KEY (venue_id) REFERENCES venues(id) ON DELETE RESTRICT,
    CONSTRAINT uk_course_offering_sessions_offering_sequence
        UNIQUE (course_offering_id, sequence_no),
    CONSTRAINT ck_course_offering_sessions_sequence
        CHECK (sequence_no > 0),
    CONSTRAINT ck_course_offering_sessions_time
        CHECK (start_at < end_at)
);

CREATE INDEX idx_course_offering_sessions_org_time
    ON course_offering_sessions(organization_id, start_at, end_at);
CREATE INDEX idx_course_offering_sessions_venue
    ON course_offering_sessions(venue_id) WHERE venue_id IS NOT NULL;

CREATE TABLE course_offering_price_snapshots (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    course_offering_id uuid NOT NULL,
    version_no integer NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    currency char(3) NOT NULL DEFAULT 'TWD',
    price_per_participant numeric(12,2) NOT NULL,
    rule_trace jsonb NOT NULL DEFAULT '{}'::jsonb,
    confirmed_by uuid,
    confirmed_at timestamptz,
    created_by uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT fk_course_offering_price_snapshots_organization_id
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_offering_price_snapshots_offering_id
        FOREIGN KEY (course_offering_id) REFERENCES course_offerings(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_offering_price_snapshots_confirmed_by
        FOREIGN KEY (confirmed_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_offering_price_snapshots_created_by
        FOREIGN KEY (created_by) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT uk_course_offering_price_snapshots_offering_version
        UNIQUE (course_offering_id, version_no),
    CONSTRAINT ck_course_offering_price_snapshots_version
        CHECK (version_no >= 1),
    CONSTRAINT ck_course_offering_price_snapshots_status
        CHECK (status IN ('DRAFT', 'CONFIRMED', 'SUPERSEDED')),
    CONSTRAINT ck_course_offering_price_snapshots_price
        CHECK (price_per_participant >= 0),
    CONSTRAINT ck_course_offering_price_snapshots_confirmed_actor
        CHECK (status <> 'CONFIRMED' OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL))
);

CREATE UNIQUE INDEX uk_course_offering_price_snapshots_confirmed
    ON course_offering_price_snapshots(course_offering_id)
    WHERE status = 'CONFIRMED';
CREATE INDEX idx_course_offering_price_snapshots_org_created
    ON course_offering_price_snapshots(organization_id, created_at DESC);

CREATE TABLE course_offering_registrations (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    course_offering_id uuid NOT NULL,
    user_id uuid NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    registered_at timestamptz NOT NULL DEFAULT now(),
    cancelled_at timestamptz,
    cancel_reason text,
    converted_course_membership_id uuid,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT fk_course_offering_registrations_organization_id
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_offering_registrations_offering_id
        FOREIGN KEY (course_offering_id) REFERENCES course_offerings(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_offering_registrations_user_id
        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE RESTRICT,
    CONSTRAINT fk_course_offering_registrations_membership_id
        FOREIGN KEY (converted_course_membership_id) REFERENCES course_memberships(id) ON DELETE RESTRICT,
    CONSTRAINT ck_course_offering_registrations_status
        CHECK (status IN ('ACTIVE', 'CANCELLED', 'CONVERTED')),
    CONSTRAINT ck_course_offering_registrations_cancelled
        CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL),
    CONSTRAINT ck_course_offering_registrations_converted
        CHECK (status <> 'CONVERTED' OR converted_course_membership_id IS NOT NULL)
);

CREATE UNIQUE INDEX uk_course_offering_registrations_active_user
    ON course_offering_registrations(course_offering_id, user_id)
    WHERE status = 'ACTIVE';
CREATE INDEX idx_course_offering_registrations_offering_status_registered
    ON course_offering_registrations(course_offering_id, status, registered_at);
CREATE INDEX idx_course_offering_registrations_user_status_registered
    ON course_offering_registrations(user_id, status, registered_at DESC);
CREATE INDEX idx_course_offering_registrations_membership
    ON course_offering_registrations(converted_course_membership_id)
    WHERE converted_course_membership_id IS NOT NULL;

-- A formal Course can come from either a CourseMatch or an Open Enrollment offering,
-- but never both. Both NULL remains valid for Committee Direct Course creation.
ALTER TABLE courses
    ADD COLUMN source_offering_id uuid,
    ADD CONSTRAINT fk_courses_source_offering_id
        FOREIGN KEY (source_offering_id) REFERENCES course_offerings(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_courses_source_xor
        CHECK (num_nonnulls(source_match_id, source_offering_id) <= 1);

CREATE UNIQUE INDEX uk_courses_source_offering
    ON courses(source_offering_id) WHERE source_offering_id IS NOT NULL;

-- Schedule reservations become dual-source. Existing formal CourseSession rows remain
-- valid after course_session_id becomes nullable because the new XOR requires exactly
-- one source. The GiST overlap exclusion continues to protect both source types.
ALTER TABLE schedule_reservations
    DROP CONSTRAINT uk_schedule_reservations_session_user,
    ALTER COLUMN course_session_id DROP NOT NULL,
    ADD COLUMN course_offering_session_id uuid,
    ADD CONSTRAINT fk_schedule_reservations_offering_session_id
        FOREIGN KEY (course_offering_session_id) REFERENCES course_offering_sessions(id) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_schedule_reservations_source_xor
        CHECK (num_nonnulls(course_session_id, course_offering_session_id) = 1);

CREATE UNIQUE INDEX uk_schedule_reservations_course_session_user
    ON schedule_reservations(course_session_id, user_id)
    WHERE course_session_id IS NOT NULL;
CREATE UNIQUE INDEX uk_schedule_reservations_offering_session_user
    ON schedule_reservations(course_offering_session_id, user_id)
    WHERE course_offering_session_id IS NOT NULL;
CREATE INDEX idx_schedule_reservations_offering_session
    ON schedule_reservations(course_offering_session_id)
    WHERE course_offering_session_id IS NOT NULL;

-- V4 reserved source_offering_price_snapshot_id and its XOR check. Now that the
-- Open Enrollment snapshot table exists, complete the deferred foreign key.
ALTER TABLE session_price_snapshots
    ADD CONSTRAINT fk_session_price_snapshots_source_offering_price_snapshot_id
        FOREIGN KEY (source_offering_price_snapshot_id)
        REFERENCES course_offering_price_snapshots(id) ON DELETE RESTRICT;
