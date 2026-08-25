-- Slice 5 / S5.1: Course Operations persistence foundation.
-- Forward-only: preserve all V1-V8 history and add only the approved operational
-- cancellation/change history that was intentionally deferred until Course Operations.

CREATE TABLE member_cancellation_records (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    member_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    enrollment_id uuid NOT NULL REFERENCES enrollments(id) ON DELETE RESTRICT,
    course_session_id uuid NOT NULL REFERENCES course_sessions(id) ON DELETE RESTRICT,
    reason text,
    cancelled_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_member_cancellation_records_enrollment UNIQUE (enrollment_id)
);
CREATE INDEX idx_member_cancellation_records_member_cancelled_at
    ON member_cancellation_records(member_id, cancelled_at DESC);
CREATE INDEX idx_member_cancellation_records_course_session
    ON member_cancellation_records(course_session_id);

CREATE TABLE course_cancellation_requests (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    course_session_id uuid NOT NULL REFERENCES course_sessions(id) ON DELETE RESTRICT,
    requested_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    requester_role varchar(20) NOT NULL,
    reason text NOT NULL,
    status varchar(30) NOT NULL,
    reviewed_by uuid REFERENCES users(id) ON DELETE RESTRICT,
    reviewed_at timestamptz,
    review_note text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_course_cancellation_requests_requester_role CHECK (requester_role = 'COACH'),
    CONSTRAINT ck_course_cancellation_requests_status CHECK (
        status IN ('PENDING_REVIEW','APPROVED','REJECTED','WITHDRAWN')
    ),
    CONSTRAINT ck_course_cancellation_requests_review CHECK (
        status NOT IN ('APPROVED','REJECTED')
        OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL AND review_note IS NOT NULL)
    )
);
CREATE UNIQUE INDEX uk_course_cancellation_requests_pending_session
    ON course_cancellation_requests(course_session_id)
    WHERE status = 'PENDING_REVIEW';
CREATE INDEX idx_course_cancellation_requests_org_status_created
    ON course_cancellation_requests(organization_id, status, created_at);

CREATE TABLE session_change_requests (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    course_session_id uuid NOT NULL REFERENCES course_sessions(id) ON DELETE RESTRICT,
    request_type varchar(30) NOT NULL,
    requested_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reason text NOT NULL,
    proposed_start_at timestamptz,
    proposed_end_at timestamptz,
    proposed_coach_profile_id uuid REFERENCES coach_profiles(id) ON DELETE RESTRICT,
    proposed_venue_id uuid REFERENCES venues(id) ON DELETE RESTRICT,
    status varchar(20) NOT NULL,
    decided_by uuid REFERENCES users(id) ON DELETE RESTRICT,
    decided_at timestamptz,
    decision_reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_session_change_requests_type CHECK (
        request_type IN ('RESCHEDULE','CHANGE_COACH','CHANGE_VENUE','COACH_LEAVE')
    ),
    CONSTRAINT ck_session_change_requests_status CHECK (
        status IN ('PENDING','APPROVED','REJECTED','WITHDRAWN')
    ),
    CONSTRAINT ck_session_change_requests_reschedule CHECK (
        request_type <> 'RESCHEDULE'
        OR (
            proposed_start_at IS NOT NULL
            AND proposed_end_at IS NOT NULL
            AND proposed_start_at < proposed_end_at
        )
    ),
    CONSTRAINT ck_session_change_requests_decision CHECK (
        status NOT IN ('APPROVED','REJECTED')
        OR (decided_by IS NOT NULL AND decided_at IS NOT NULL AND decision_reason IS NOT NULL)
    )
);
CREATE INDEX idx_session_change_requests_session_status_created
    ON session_change_requests(course_session_id, status, created_at);
CREATE INDEX idx_session_change_requests_org_status_created
    ON session_change_requests(organization_id, status, created_at);
