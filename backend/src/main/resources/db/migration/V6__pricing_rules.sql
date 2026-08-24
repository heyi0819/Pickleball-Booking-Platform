-- Slice 3 / S3.3: canonical server-side pricing rules.
-- Forward-only. No default prices are seeded: production pricing must be configured explicitly.

CREATE TABLE pricing_rules (
    id uuid PRIMARY KEY,
    organization_id uuid NOT NULL,
    name varchar(120) NOT NULL,
    priority integer NOT NULL,
    coach_profile_id uuid,
    course_type varchar(20),
    skill_level varchar(30),
    min_participants smallint,
    max_participants smallint,
    base_amount numeric(12,2) NOT NULL,
    pricing_unit varchar(20) NOT NULL,
    condition_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    calculation_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    active_from timestamptz NOT NULL,
    active_to timestamptz,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    deleted_at timestamptz,
    CONSTRAINT fk_pricing_rules_organization_id
        FOREIGN KEY (organization_id) REFERENCES organizations(id) ON DELETE RESTRICT,
    CONSTRAINT fk_pricing_rules_coach_profile_id
        FOREIGN KEY (coach_profile_id) REFERENCES coach_profiles(id) ON DELETE RESTRICT,
    CONSTRAINT ck_pricing_rules_priority CHECK (priority >= 0),
    CONSTRAINT ck_pricing_rules_course_type CHECK (course_type IS NULL OR course_type IN ('PRIVATE', 'GROUP')),
    CONSTRAINT ck_pricing_rules_participant_range CHECK (
        (min_participants IS NULL OR min_participants > 0)
        AND (max_participants IS NULL OR max_participants > 0)
        AND (min_participants IS NULL OR max_participants IS NULL OR min_participants <= max_participants)
    ),
    CONSTRAINT ck_pricing_rules_base_amount CHECK (base_amount >= 0),
    CONSTRAINT ck_pricing_rules_pricing_unit CHECK (pricing_unit IN ('PER_SESSION', 'PER_PERSON', 'FLAT')),
    CONSTRAINT ck_pricing_rules_active_range CHECK (active_to IS NULL OR active_from < active_to),
    CONSTRAINT ck_pricing_rules_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE'))
);

CREATE INDEX idx_pricing_rules_org_status_priority
    ON pricing_rules(organization_id, status, priority);
CREATE INDEX idx_pricing_rules_org_coach_status
    ON pricing_rules(organization_id, coach_profile_id, status);
CREATE INDEX idx_pricing_rules_active_window
    ON pricing_rules(organization_id, active_from, active_to)
    WHERE status = 'ACTIVE' AND deleted_at IS NULL;
