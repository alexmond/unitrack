-- Least-privilege API tokens: FULL (acts as the user) or INGEST (may only upload).
-- Existing tokens keep their current behavior (FULL).
ALTER TABLE api_token
    ADD COLUMN scope VARCHAR(16) NOT NULL DEFAULT 'FULL';
