-- Pull/merge-request context for a run (#179): the target branch the change merges into
-- and the PR/MR number. Both nullable — ordinary branch builds leave them empty.
ALTER TABLE test_run ADD COLUMN base_branch VARCHAR(255);
ALTER TABLE test_run ADD COLUMN pr_number INTEGER;
