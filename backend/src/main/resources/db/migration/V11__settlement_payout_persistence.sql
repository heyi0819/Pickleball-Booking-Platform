-- Slice 7 / S7.1: Settlement / Payout persistence foundation.
-- Forward-only. V1-V10 history is immutable.
-- Cross-row financial invariants (coach allocation sums, payout outstanding totals,
-- and active-batch duplication) are enforced by application transactions with row locks;
-- this migration provides the approved relational guardrails.

CREATE TABLE session_settlements (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    course_session_id uuid NOT NULL REFERENCES course_sessions(id) ON DELETE RESTRICT,
    price_snapshot_id uuid NOT NULL REFERENCES session_price_snapshots(id) ON DELETE RESTRICT,
    gross_receivable numeric(12,2) NOT NULL,
    venue_cost numeric(12,2) NOT NULL,
    other_adjustment numeric(12,2) NOT NULL DEFAULT 0,
    distributable_amount numeric(12,2) NOT NULL,
    status varchar(30) NOT NULL DEFAULT 'PENDING_CALCULATION',
    confirmed_by uuid REFERENCES users(id) ON DELETE RESTRICT,
    confirmed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_session_settlements_course_session UNIQUE (course_session_id),
    CONSTRAINT ck_session_settlements_amounts CHECK (
        gross_receivable >= 0
        AND venue_cost >= 0
        AND distributable_amount >= 0
        AND distributable_amount = gross_receivable - venue_cost + other_adjustment
    ),
    CONSTRAINT ck_session_settlements_status CHECK (
        status IN ('PENDING_CALCULATION','CALCULATED','PENDING_APPROVAL','CONFIRMED','VOIDED')
    ),
    CONSTRAINT ck_session_settlements_confirmation CHECK (
        status <> 'CONFIRMED' OR (confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)
    )
);
CREATE INDEX idx_session_settlements_org_status_created
    ON session_settlements(organization_id, status, created_at DESC);
CREATE INDEX idx_session_settlements_price_snapshot
    ON session_settlements(price_snapshot_id);

CREATE TABLE settlement_adjustments (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    session_settlement_id uuid NOT NULL REFERENCES session_settlements(id) ON DELETE RESTRICT,
    adjustment_type varchar(30) NOT NULL,
    amount numeric(12,2) NOT NULL,
    direction varchar(10) NOT NULL,
    before_amount numeric(12,2) NOT NULL,
    after_amount numeric(12,2) NOT NULL,
    handling_method varchar(30),
    reason text NOT NULL,
    approved_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_settlement_adjustments_type CHECK (
        adjustment_type IN ('RECEIVABLE','VENUE_COST','COACH_ALLOCATION','OTHER')
    ),
    CONSTRAINT ck_settlement_adjustments_amount CHECK (amount > 0),
    CONSTRAINT ck_settlement_adjustments_direction CHECK (direction IN ('INCREASE','DECREASE')),
    CONSTRAINT ck_settlement_adjustments_arithmetic CHECK (
        (direction = 'INCREASE' AND after_amount = before_amount + amount)
        OR (direction = 'DECREASE' AND after_amount = before_amount - amount)
    ),
    CONSTRAINT ck_settlement_adjustments_handling CHECK (
        handling_method IS NULL OR handling_method IN ('RETURN','NEXT_PAYOUT_OFFSET','MANUAL')
    ),
    CONSTRAINT ck_settlement_adjustments_reason CHECK (length(btrim(reason)) > 0)
);
CREATE INDEX idx_settlement_adjustments_settlement_created
    ON settlement_adjustments(session_settlement_id, created_at DESC);
CREATE INDEX idx_settlement_adjustments_org_created
    ON settlement_adjustments(organization_id, created_at DESC);
CREATE INDEX idx_settlement_adjustments_approved_by
    ON settlement_adjustments(approved_by, created_at DESC);

CREATE TABLE coach_settlements (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    session_settlement_id uuid NOT NULL REFERENCES session_settlements(id) ON DELETE RESTRICT,
    coach_assignment_id uuid NOT NULL REFERENCES session_coach_assignments(id) ON DELETE RESTRICT,
    coach_profile_id uuid NOT NULL REFERENCES coach_profiles(id) ON DELETE RESTRICT,
    allocation_type varchar(20) NOT NULL,
    allocation_value numeric(12,4),
    payable_amount numeric(12,2) NOT NULL,
    paid_amount numeric(12,2) NOT NULL DEFAULT 0,
    payout_status varchar(30) NOT NULL DEFAULT 'WAITING_RECEIPT',
    ready_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_coach_settlements_settlement_assignment UNIQUE (session_settlement_id, coach_assignment_id),
    CONSTRAINT ck_coach_settlements_allocation_type CHECK (
        allocation_type IN ('EQUAL','PERCENTAGE','FIXED')
    ),
    CONSTRAINT ck_coach_settlements_allocation_value CHECK (
        (allocation_type = 'EQUAL' AND allocation_value IS NULL)
        OR (allocation_type = 'PERCENTAGE' AND allocation_value > 0 AND allocation_value <= 100)
        OR (allocation_type = 'FIXED' AND allocation_value >= 0)
    ),
    CONSTRAINT ck_coach_settlements_amounts CHECK (
        payable_amount >= 0 AND paid_amount >= 0 AND paid_amount <= payable_amount
    ),
    CONSTRAINT ck_coach_settlements_payout_status CHECK (
        payout_status IN ('WAITING_RECEIPT','READY','IN_BATCH','PARTIALLY_PAID','PAID','ON_HOLD','CANCELLED')
    )
);
CREATE INDEX idx_coach_settlements_org_coach_status
    ON coach_settlements(organization_id, coach_profile_id, payout_status, created_at DESC);
CREATE INDEX idx_coach_settlements_session_status
    ON coach_settlements(session_settlement_id, payout_status);
CREATE INDEX idx_coach_settlements_ready
    ON coach_settlements(organization_id, ready_at)
    WHERE payout_status = 'READY' AND ready_at IS NOT NULL;

CREATE TABLE payout_batches (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    batch_no varchar(30) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    payout_date date,
    method varchar(20),
    currency char(3) NOT NULL DEFAULT 'TWD',
    total_amount numeric(12,2) NOT NULL DEFAULT 0,
    item_count integer NOT NULL DEFAULT 0,
    created_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    approved_by uuid REFERENCES users(id) ON DELETE RESTRICT,
    approved_at timestamptz,
    completed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_payout_batches_org_no UNIQUE (organization_id, batch_no),
    CONSTRAINT ck_payout_batches_status CHECK (
        status IN ('DRAFT','APPROVED','PROCESSING','COMPLETED','CANCELLED')
    ),
    CONSTRAINT ck_payout_batches_method CHECK (
        method IS NULL OR method IN ('CASH','BANK_TRANSFER','OTHER')
    ),
    CONSTRAINT ck_payout_batches_amount CHECK (total_amount >= 0),
    CONSTRAINT ck_payout_batches_item_count CHECK (item_count >= 0),
    CONSTRAINT ck_payout_batches_approval CHECK (
        status NOT IN ('APPROVED','PROCESSING','COMPLETED')
        OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)
    ),
    CONSTRAINT ck_payout_batches_completion CHECK (
        status <> 'COMPLETED' OR completed_at IS NOT NULL
    )
);
CREATE INDEX idx_payout_batches_org_status_date
    ON payout_batches(organization_id, status, payout_date);
CREATE INDEX idx_payout_batches_created_by
    ON payout_batches(created_by, created_at DESC);

CREATE TABLE payout_batch_items (
    id uuid PRIMARY KEY,
    payout_batch_id uuid NOT NULL REFERENCES payout_batches(id) ON DELETE RESTRICT,
    coach_settlement_id uuid NOT NULL REFERENCES coach_settlements(id) ON DELETE RESTRICT,
    coach_profile_id uuid NOT NULL REFERENCES coach_profiles(id) ON DELETE RESTRICT,
    amount numeric(12,2) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PLANNED',
    paid_at timestamptz,
    processed_by uuid REFERENCES users(id) ON DELETE RESTRICT,
    reference_no varchar(100),
    failure_reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_payout_batch_items_batch_settlement UNIQUE (payout_batch_id, coach_settlement_id),
    CONSTRAINT ck_payout_batch_items_amount CHECK (amount > 0),
    CONSTRAINT ck_payout_batch_items_status CHECK (
        status IN ('PLANNED','PAID','FAILED','CANCELLED')
    ),
    CONSTRAINT ck_payout_batch_items_paid_metadata CHECK (
        status <> 'PAID' OR (paid_at IS NOT NULL AND processed_by IS NOT NULL)
    )
);
CREATE INDEX idx_payout_batch_items_batch_status
    ON payout_batch_items(payout_batch_id, status);
CREATE INDEX idx_payout_batch_items_coach_settlement
    ON payout_batch_items(coach_settlement_id, status);
CREATE INDEX idx_payout_batch_items_coach_profile_paid
    ON payout_batch_items(coach_profile_id, paid_at DESC)
    WHERE status = 'PAID';
