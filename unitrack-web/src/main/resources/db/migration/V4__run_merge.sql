ALTER TABLE test_run
    ADD COLUMN run_key VARCHAR(255);

ALTER TABLE test_run
    ADD COLUMN uploads INT NOT NULL DEFAULT 1;

-- One run per (project, run_key); NULL run_keys remain independent (NULLs are distinct).
CREATE UNIQUE INDEX uq_test_run_key ON test_run (project_id, run_key);
