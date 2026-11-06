CREATE TABLE IF NOT EXISTS users (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(30)  NOT NULL,
    email         VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX IF NOT EXISTS users_username_lower ON users (lower(username));

CREATE TABLE IF NOT EXISTS sessions (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS deployments (
    id         BIGSERIAL PRIMARY KEY,
    version    VARCHAR(20) NOT NULL,
    status     VARCHAR(30) NOT NULL,
    note       TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
ALTER TABLE deployments ADD COLUMN IF NOT EXISTS user_id BIGINT;

CREATE TABLE IF NOT EXISTS incidents (
    id               BIGSERIAL PRIMARY KEY,
    version          VARCHAR(20) NOT NULL,
    reason           TEXT NOT NULL,
    metrics          TEXT,
    action           TEXT,
    outcome          VARCHAR(40) NOT NULL DEFAULT 'OPEN',
    recovery_seconds INTEGER,
    detected_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at      TIMESTAMPTZ
);
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS user_id BIGINT;

-- Recovery context that must survive a restart of FactoryGuard
CREATE TABLE IF NOT EXISTS guard_state (
    id                    INTEGER PRIMARY KEY,
    owner_id              BIGINT,
    current_version       VARCHAR(20),
    last_known_good       VARCHAR(20),
    deployed_at           TIMESTAMPTZ,
    current_deployment_id BIGINT
);
CREATE TABLE IF NOT EXISTS rollback_events (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT,
    at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Recovery evidence recorded for every incident
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS rollback_target VARCHAR(20);
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS threshold DOUBLE PRECISION;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS detected_events_per_minute DOUBLE PRECISION;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS detected_health_up BOOLEAN;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS detection_seconds INTEGER;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS verified_events_per_minute DOUBLE PRECISION;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS verified_health_up BOOLEAN;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS verification TEXT;
