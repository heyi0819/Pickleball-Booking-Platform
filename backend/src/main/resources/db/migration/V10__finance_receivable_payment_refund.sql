-- Slice 6 / S6.1: Receivable / Payment / Refund persistence.
-- Forward-only. Existing receivables / receivable_items were introduced by V7 and are preserved.
-- Cross-row financial invariants (allocation sums and cumulative refundable balance) are enforced
-- by the application transaction with row locks; this migration provides the relational guardrails.

-- Operational indexes missing from the Slice 3 receivable foundation.
CREATE INDEX idx_receivables_open_due
    ON receivables(organization_id, due_at)
    WHERE status IN ('OPEN','PARTIALLY_PAID','OVERDUE') AND due_at IS NOT NULL;
CREATE INDEX idx_receivable_items_course_session ON receivable_items(course_session_id);
CREATE INDEX idx_receivable_items_enrollment
    ON receivable_items(enrollment_id) WHERE enrollment_id IS NOT NULL;

CREATE TABLE receivable_adjustments (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    receivable_id uuid NOT NULL REFERENCES receivables(id) ON DELETE RESTRICT,
    receivable_item_id uuid REFERENCES receivable_items(id) ON DELETE RESTRICT,
    adjustment_type varchar(20) NOT NULL,
    amount numeric(12,2) NOT NULL,
    reason text NOT NULL,
    approved_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_receivable_adjustments_type CHECK (adjustment_type IN ('INCREASE','DECREASE')),
    CONSTRAINT ck_receivable_adjustments_amount CHECK (amount > 0),
    CONSTRAINT ck_receivable_adjustments_reason CHECK (length(btrim(reason)) > 0)
);
CREATE INDEX idx_receivable_adjustments_receivable_created
    ON receivable_adjustments(receivable_id, created_at DESC);
CREATE INDEX idx_receivable_adjustments_item
    ON receivable_adjustments(receivable_item_id) WHERE receivable_item_id IS NOT NULL;
CREATE INDEX idx_receivable_adjustments_approved_by
    ON receivable_adjustments(approved_by, created_at DESC);

CREATE TABLE payments (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    payment_no varchar(30) NOT NULL,
    payer_user_id uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    amount numeric(12,2) NOT NULL,
    currency char(3) NOT NULL DEFAULT 'TWD',
    payment_method varchar(20) NOT NULL,
    status varchar(30) NOT NULL DEFAULT 'COMPLETED',
    paid_at timestamptz NOT NULL,
    recorded_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    reference_no varchar(100),
    idempotency_key varchar(100),
    note text,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_payments_org_no UNIQUE (organization_id, payment_no),
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_method CHECK (payment_method IN ('CASH','BANK_TRANSFER','OTHER')),
    CONSTRAINT ck_payments_status CHECK (status IN ('COMPLETED','PARTIALLY_REFUNDED','REFUNDED','VOIDED'))
);
CREATE UNIQUE INDEX uk_payments_org_idempotency_key
    ON payments(organization_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_payments_payer_paid_at ON payments(payer_user_id, paid_at DESC);
CREATE INDEX idx_payments_org_status_paid_at ON payments(organization_id, status, paid_at DESC);
CREATE INDEX idx_payments_recorded_by ON payments(recorded_by, paid_at DESC);

CREATE TABLE payment_allocations (
    id uuid PRIMARY KEY,
    payment_id uuid NOT NULL REFERENCES payments(id) ON DELETE RESTRICT,
    receivable_item_id uuid NOT NULL REFERENCES receivable_items(id) ON DELETE RESTRICT,
    amount numeric(12,2) NOT NULL,
    allocated_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    allocated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uk_payment_allocations_payment_item UNIQUE (payment_id, receivable_item_id),
    CONSTRAINT ck_payment_allocations_amount CHECK (amount > 0)
);
CREATE INDEX idx_payment_allocations_receivable_item
    ON payment_allocations(receivable_item_id, allocated_at DESC);
CREATE INDEX idx_payment_allocations_allocated_by
    ON payment_allocations(allocated_by, allocated_at DESC);

CREATE TABLE refunds (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL REFERENCES organizations(id) ON DELETE RESTRICT,
    refund_no varchar(30) NOT NULL,
    payment_id uuid NOT NULL REFERENCES payments(id) ON DELETE RESTRICT,
    receivable_item_id uuid REFERENCES receivable_items(id) ON DELETE RESTRICT,
    enrollment_id uuid REFERENCES enrollments(id) ON DELETE RESTRICT,
    amount numeric(12,2) NOT NULL,
    refund_method varchar(20),
    status varchar(30) NOT NULL DEFAULT 'PENDING_APPROVAL',
    reason text NOT NULL,
    requested_by uuid NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    requested_at timestamptz NOT NULL DEFAULT now(),
    approved_by uuid REFERENCES users(id) ON DELETE RESTRICT,
    approved_at timestamptz,
    approval_note text,
    processed_by uuid REFERENCES users(id) ON DELETE RESTRICT,
    refunded_at timestamptz,
    reference_no varchar(100),
    failure_reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uk_refunds_org_no UNIQUE (organization_id, refund_no),
    CONSTRAINT ck_refunds_amount CHECK (amount > 0),
    CONSTRAINT ck_refunds_method CHECK (
        refund_method IS NULL OR refund_method IN ('CASH','BANK_TRANSFER','OTHER')
    ),
    CONSTRAINT ck_refunds_status CHECK (
        status IN ('PENDING_APPROVAL','APPROVED','REJECTED','COMPLETED','FAILED','CANCELLED')
    ),
    CONSTRAINT ck_refunds_reason CHECK (length(btrim(reason)) > 0),
    CONSTRAINT ck_refunds_approval_metadata CHECK (
        status NOT IN ('APPROVED','COMPLETED','FAILED')
        OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)
    ),
    CONSTRAINT ck_refunds_completed_metadata CHECK (
        status <> 'COMPLETED'
        OR (
            processed_by IS NOT NULL
            AND refunded_at IS NOT NULL
            AND refund_method IS NOT NULL
        )
    )
);
CREATE INDEX idx_refunds_payment_status ON refunds(payment_id, status);
CREATE INDEX idx_refunds_org_status_requested
    ON refunds(organization_id, status, requested_at DESC);
CREATE INDEX idx_refunds_receivable_item_status
    ON refunds(receivable_item_id, status) WHERE receivable_item_id IS NOT NULL;
CREATE INDEX idx_refunds_enrollment_status
    ON refunds(enrollment_id, status) WHERE enrollment_id IS NOT NULL;
CREATE INDEX idx_refunds_requested_by ON refunds(requested_by, requested_at DESC);
