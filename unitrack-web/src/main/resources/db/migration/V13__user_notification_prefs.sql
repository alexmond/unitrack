-- Per-user notification opt-in/out. Default on, matching prior behaviour.
ALTER TABLE app_user ADD COLUMN notify_gate_failure BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE app_user ADD COLUMN notify_token_expiry BOOLEAN NOT NULL DEFAULT TRUE;
