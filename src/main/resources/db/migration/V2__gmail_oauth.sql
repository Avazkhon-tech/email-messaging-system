ALTER TABLE email_accounts
    ADD COLUMN auth_type        VARCHAR(16) NOT NULL DEFAULT 'PASSWORD',
    ADD COLUMN history_id       VARCHAR(64),
    ADD COLUMN watch_expiration TIMESTAMPTZ;

CREATE INDEX idx_accounts_provider_email ON email_accounts (provider, lower(email_address));
