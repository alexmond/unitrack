-- Track when an API token's pre-expiry reminder was sent, so it fires at most once.
ALTER TABLE api_token ADD COLUMN expiry_reminded_at TIMESTAMP;
