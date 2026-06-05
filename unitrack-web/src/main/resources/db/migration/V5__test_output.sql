ALTER TABLE test_case_result
    ADD COLUMN system_out TEXT;

ALTER TABLE test_case_result
    ADD COLUMN system_err TEXT;

ALTER TABLE test_case_result
    ADD COLUMN attachments VARCHAR(8000);
