-- auth-service schema (Flyway migration V1)
-- Implements ADR-0002: JWT Authentication, Multi-tenant users, and Service credentials

CREATE TABLE tenants (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name       text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE users (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id     uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    email         text NOT NULL,
    password_hash text NOT NULL,
    role          text NOT NULL CHECK (role IN ('OWNER', 'STAFF', 'SERVICE')),
    created_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, email)
);

CREATE TABLE refresh_tokens (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash text NOT NULL,
    family_id  uuid NOT NULL,
    revoked    boolean NOT NULL DEFAULT false,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);

CREATE TABLE service_credentials (
    client_id     text PRIMARY KEY,
    tenant_id     uuid NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    secret_hash   text NOT NULL,
    scopes        text NOT NULL,
    created_at    timestamptz NOT NULL DEFAULT now()
);
