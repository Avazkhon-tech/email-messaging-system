CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    fullname    VARCHAR(255) NOT NULL,
    email       VARCHAR(320) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE email_accounts (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider         VARCHAR(32)  NOT NULL,
    email_address    VARCHAR(320) NOT NULL,
    credentials      TEXT         NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_synced_at   TIMESTAMPTZ,
    last_sync_status VARCHAR(512),
    CONSTRAINT uq_account_user_email UNIQUE (user_id, email_address)
);

CREATE INDEX idx_accounts_user ON email_accounts (user_id);
CREATE INDEX idx_accounts_status ON email_accounts (status);

CREATE TABLE email_messages (
    id                  BIGSERIAL PRIMARY KEY,
    external_message_id VARCHAR(512) NOT NULL,
    account_id          BIGINT       NOT NULL REFERENCES email_accounts (id) ON DELETE CASCADE,
    sender              VARCHAR(320),
    recipients          TEXT,
    subject             VARCHAR(1024),
    body                TEXT,
    body_html           TEXT,
    preview             VARCHAR(512),
    received_at         TIMESTAMPTZ,
    read_status         BOOLEAN      NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_message_account_external UNIQUE (account_id, external_message_id)
);

CREATE INDEX idx_messages_account_received ON email_messages (account_id, received_at DESC);
CREATE INDEX idx_messages_subject ON email_messages (lower(subject));
CREATE INDEX idx_messages_sender ON email_messages (lower(sender));
