CREATE TABLE organizations (
    id uuid PRIMARY KEY,
    code varchar(50) NOT NULL UNIQUE,
    name varchar(120) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    timezone varchar(50) NOT NULL DEFAULT 'Asia/Taipei',
    currency char(3) NOT NULL DEFAULT 'TWD',
    settings jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0
);

CREATE TABLE users (
    id uuid PRIMARY KEY,
    display_name varchar(100) NOT NULL,
    phone varchar(30),
    email varchar(254),
    status varchar(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CLOSED')),
    locale varchar(10) NOT NULL DEFAULT 'zh-TW',
    last_login_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    version bigint NOT NULL DEFAULT 0,
    deleted_at timestamptz
);
CREATE INDEX idx_users_phone ON users(phone) WHERE phone IS NOT NULL;
CREATE INDEX idx_users_email_lower ON users(lower(email)) WHERE email IS NOT NULL AND deleted_at IS NULL;
CREATE INDEX idx_users_status ON users(status);

CREATE TABLE user_external_identities (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    provider varchar(30) NOT NULL,
    provider_subject varchar(255) NOT NULL,
    profile_data jsonb NOT NULL,
    linked_at timestamptz NOT NULL,
    last_verified_at timestamptz,
    revoked_at timestamptz,
    CONSTRAINT uq_external_identity_provider_subject UNIQUE (provider, provider_subject),
    CONSTRAINT ck_external_identity_provider CHECK (provider = 'LINE')
);
CREATE INDEX idx_user_external_identities_user_id ON user_external_identities(user_id);

CREATE TABLE user_role_assignments (
    id uuid PRIMARY KEY,
    organization_id uuid REFERENCES organizations(id),
    user_id uuid NOT NULL REFERENCES users(id),
    role_code varchar(30) NOT NULL CHECK (role_code IN ('STUDENT', 'COACH', 'COMMITTEE', 'PLATFORM_ADMIN')),
    status varchar(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    granted_by uuid REFERENCES users(id),
    granted_at timestamptz NOT NULL,
    revoked_at timestamptz,
    CONSTRAINT ck_role_scope CHECK ((role_code = 'PLATFORM_ADMIN') OR organization_id IS NOT NULL),
    CONSTRAINT ck_platform_admin_global CHECK (role_code <> 'PLATFORM_ADMIN' OR organization_id IS NULL)
);
CREATE UNIQUE INDEX uq_role_assignment_scoped ON user_role_assignments(user_id, organization_id, role_code) WHERE organization_id IS NOT NULL;
CREATE UNIQUE INDEX uq_role_assignment_global ON user_role_assignments(user_id, role_code) WHERE organization_id IS NULL;
CREATE INDEX idx_user_role_assignments_org_role ON user_role_assignments(organization_id, role_code, status);
