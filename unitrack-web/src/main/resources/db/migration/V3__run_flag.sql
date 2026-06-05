ALTER TABLE test_run
    ADD COLUMN flag VARCHAR(255) NOT NULL DEFAULT 'default';

CREATE INDEX idx_test_run_project_flag_created ON test_run (project_id, flag, created_at);
