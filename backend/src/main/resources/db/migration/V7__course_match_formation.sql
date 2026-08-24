-- Slice 3 / S3.4: ConfirmCourseMatch formal formation persistence.
-- Forward-only. Open Enrollment dual-source reservation support remains Slice 4.

CREATE TABLE course_contact_assignments (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    course_id uuid NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    effective_from timestamptz NOT NULL,
    effective_to timestamptz,
    assigned_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_course_contact_assignment_period CHECK (effective_to IS NULL OR effective_from < effective_to)
);
CREATE UNIQUE INDEX uk_course_contact_assignments_current
    ON course_contact_assignments(course_id) WHERE effective_to IS NULL;
CREATE INDEX idx_course_contact_assignments_user_current
    ON course_contact_assignments(user_id, course_id) WHERE effective_to IS NULL;

CREATE TABLE course_memberships (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    course_id uuid NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    joined_at timestamptz NOT NULL DEFAULT now(),
    withdrawn_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_course_memberships_course_user UNIQUE (course_id, user_id),
    CONSTRAINT ck_course_memberships_status CHECK (status IN ('ACTIVE','WITHDRAWN')),
    CONSTRAINT ck_course_memberships_withdrawn CHECK (status <> 'WITHDRAWN' OR withdrawn_at IS NOT NULL)
);
CREATE INDEX idx_course_memberships_user_status ON course_memberships(user_id, status);
CREATE INDEX idx_course_memberships_course_status ON course_memberships(course_id, status);

CREATE TABLE enrollments (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    course_membership_id uuid NOT NULL REFERENCES course_memberships(id) ON DELETE RESTRICT,
    course_session_id uuid NOT NULL REFERENCES course_sessions(id) ON DELETE RESTRICT,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    status varchar(20) NOT NULL DEFAULT 'SCHEDULED',
    enrolled_at timestamptz NOT NULL DEFAULT now(),
    cancelled_at timestamptz,
    attendance_marked_at timestamptz,
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_enrollments_session_user UNIQUE (course_session_id, user_id),
    CONSTRAINT ck_enrollments_status CHECK (status IN ('SCHEDULED','CANCELLED','ATTENDED','ABSENT')),
    CONSTRAINT ck_enrollments_cancelled CHECK (status <> 'CANCELLED' OR cancelled_at IS NOT NULL),
    CONSTRAINT ck_enrollments_attendance CHECK (status NOT IN ('ATTENDED','ABSENT') OR attendance_marked_at IS NOT NULL)
);
CREATE INDEX idx_enrollments_user_status_session ON enrollments(user_id, status, course_session_id);
CREATE INDEX idx_enrollments_session_status ON enrollments(course_session_id, status);

CREATE TABLE session_coach_assignments (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    course_session_id uuid NOT NULL REFERENCES course_sessions(id) ON DELETE RESTRICT,
    coach_profile_id uuid NOT NULL REFERENCES coach_profiles(id) ON DELETE RESTRICT,
    source_type varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    is_primary boolean NOT NULL DEFAULT false,
    invited_at timestamptz,
    responded_at timestamptz,
    response_reason text,
    assigned_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_session_coach_assignments_source CHECK (source_type IN ('SPECIFIED','MATCHED','REPLACEMENT','DIRECT')),
    CONSTRAINT ck_session_coach_assignments_status CHECK (status IN ('INVITED','ACCEPTED','REJECTED','CANCEL_PENDING','CANCELLED','REPLACED')),
    CONSTRAINT ck_session_coach_assignments_response CHECK (status NOT IN ('ACCEPTED','REJECTED') OR responded_at IS NOT NULL)
);
CREATE UNIQUE INDEX uk_session_coach_assignments_active_coach
    ON session_coach_assignments(course_session_id, coach_profile_id)
    WHERE status IN ('INVITED','ACCEPTED','CANCEL_PENDING');
CREATE UNIQUE INDEX uk_session_coach_assignments_active_primary
    ON session_coach_assignments(course_session_id)
    WHERE is_primary AND status IN ('INVITED','ACCEPTED','CANCEL_PENDING');
CREATE INDEX idx_session_coach_assignments_coach_status
    ON session_coach_assignments(coach_profile_id, status, course_session_id);

CREATE TABLE course_approvals (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    course_id uuid NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    course_version bigint NOT NULL,
    decision varchar(30) NOT NULL,
    reason text NOT NULL,
    decided_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    decided_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_course_approvals_decision CHECK (decision IN ('APPROVED','REJECTED','CHANGES_REQUESTED'))
);
CREATE INDEX idx_course_approvals_course_version_decided
    ON course_approvals(course_id, course_version, decided_at DESC);

-- Slice 3 reservations are formal CourseSession-only. Slice 4 forward migration will
-- add CourseOfferingSession as the second source and convert the source constraint to XOR.
CREATE TABLE schedule_reservations (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    course_session_id uuid NOT NULL REFERENCES course_sessions(id) ON DELETE RESTRICT,
    reservation_role varchar(20) NOT NULL,
    reserved_period tstzrange NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'CONFIRMED',
    expires_at timestamptz,
    released_at timestamptz,
    release_reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_schedule_reservations_session_user UNIQUE (course_session_id, user_id),
    CONSTRAINT ck_schedule_reservations_role CHECK (reservation_role IN ('COACH','PARTICIPANT')),
    CONSTRAINT ck_schedule_reservations_status CHECK (status IN ('HELD','CONFIRMED','RELEASED','EXPIRED')),
    CONSTRAINT ck_schedule_reservations_period CHECK (NOT isempty(reserved_period) AND lower(reserved_period) < upper(reserved_period)),
    CONSTRAINT ck_schedule_reservations_release CHECK (status <> 'RELEASED' OR released_at IS NOT NULL),
    CONSTRAINT ex_schedule_reservations_no_overlap EXCLUDE USING gist (
        organization_id WITH =,
        user_id WITH =,
        reserved_period WITH &&
    ) WHERE (status IN ('HELD','CONFIRMED'))
);
CREATE INDEX idx_schedule_reservations_user_status ON schedule_reservations(user_id, status);
CREATE INDEX idx_schedule_reservations_session ON schedule_reservations(course_session_id);

CREATE TABLE session_venue_arrangements (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    course_session_id uuid NOT NULL REFERENCES course_sessions(id) ON DELETE RESTRICT,
    source_type varchar(20) NOT NULL,
    venue_id uuid REFERENCES venues(id) ON DELETE RESTRICT,
    venue_name_snapshot varchar(150) NOT NULL,
    address_snapshot varchar(300),
    cost_amount numeric(12,2) NOT NULL DEFAULT 0,
    cost_payer_type varchar(20) NOT NULL DEFAULT 'NONE',
    cost_payer_user_id uuid REFERENCES users(id) ON DELETE RESTRICT,
    status varchar(20) NOT NULL,
    confirmed_by uuid REFERENCES users(id) ON DELETE RESTRICT,
    confirmed_at timestamptz,
    note text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_session_venue_arrangements_source CHECK (source_type IN ('STUDENT','COACH','COMMITTEE')),
    CONSTRAINT ck_session_venue_arrangements_cost CHECK (cost_amount >= 0),
    CONSTRAINT ck_session_venue_arrangements_payer CHECK (cost_payer_type IN ('STUDENT','COACH','COMMITTEE','NONE')),
    CONSTRAINT ck_session_venue_arrangements_payer_user CHECK (
        (cost_payer_type = 'NONE' AND cost_payer_user_id IS NULL)
        OR cost_payer_type <> 'NONE'
    ),
    CONSTRAINT ck_session_venue_arrangements_status CHECK (status IN ('PROPOSED','CONFIRMED','REJECTED','REPLACED')),
    CONSTRAINT ck_session_venue_arrangements_confirmed CHECK (status <> 'CONFIRMED' OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL))
);
CREATE UNIQUE INDEX uk_session_venue_arrangements_confirmed
    ON session_venue_arrangements(course_session_id) WHERE status = 'CONFIRMED';
CREATE INDEX idx_session_venue_arrangements_venue_status ON session_venue_arrangements(venue_id, status) WHERE venue_id IS NOT NULL;

CREATE TABLE receivables (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    receivable_no varchar(30) NOT NULL,
    course_id uuid NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
    payer_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    billing_mode varchar(20) NOT NULL,
    currency char(3) NOT NULL DEFAULT 'TWD',
    total_amount numeric(12,2) NOT NULL,
    adjusted_amount numeric(12,2) NOT NULL DEFAULT 0,
    paid_amount numeric(12,2) NOT NULL DEFAULT 0,
    refunded_amount numeric(12,2) NOT NULL DEFAULT 0,
    balance_amount numeric(12,2) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'OPEN',
    due_at timestamptz,
    closed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_receivables_org_no UNIQUE (organization_id, receivable_no),
    CONSTRAINT ck_receivables_billing_mode CHECK (billing_mode IN ('FULL_COURSE','PER_SESSION')),
    CONSTRAINT ck_receivables_amounts CHECK (
        total_amount >= 0 AND paid_amount >= 0 AND refunded_amount >= 0
        AND balance_amount = total_amount + adjusted_amount - paid_amount + refunded_amount
    ),
    CONSTRAINT ck_receivables_status CHECK (status IN ('OPEN','PARTIALLY_PAID','PAID','OVERDUE','CANCELLED','REFUNDED'))
);
CREATE INDEX idx_receivables_org_payer_status ON receivables(organization_id, payer_user_id, status);
CREATE INDEX idx_receivables_course_status ON receivables(course_id, status);

CREATE TABLE receivable_items (
    id uuid PRIMARY KEY,
    receivable_id uuid NOT NULL REFERENCES receivables(id) ON DELETE RESTRICT,
    course_session_id uuid NOT NULL REFERENCES course_sessions(id) ON DELETE RESTRICT,
    enrollment_id uuid REFERENCES enrollments(id) ON DELETE RESTRICT,
    price_snapshot_id uuid NOT NULL REFERENCES session_price_snapshots(id) ON DELETE RESTRICT,
    amount numeric(12,2) NOT NULL,
    paid_amount numeric(12,2) NOT NULL DEFAULT 0,
    refunded_amount numeric(12,2) NOT NULL DEFAULT 0,
    status varchar(20) NOT NULL DEFAULT 'OPEN',
    sort_order smallint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT ck_receivable_items_amounts CHECK (amount >= 0 AND paid_amount >= 0 AND refunded_amount >= 0),
    CONSTRAINT ck_receivable_items_status CHECK (status IN ('OPEN','PARTIALLY_PAID','PAID','REFUNDED','CANCELLED'))
);
CREATE UNIQUE INDEX uk_receivable_items_session_enrollment
    ON receivable_items(receivable_id, course_session_id, enrollment_id) WHERE enrollment_id IS NOT NULL;
CREATE UNIQUE INDEX uk_receivable_items_session_without_enrollment
    ON receivable_items(receivable_id, course_session_id) WHERE enrollment_id IS NULL;
CREATE INDEX idx_receivable_items_snapshot ON receivable_items(price_snapshot_id);
