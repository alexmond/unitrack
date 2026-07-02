-- Fold per-module flag-runs into their build's single rollup run (modules-within-one-run).
--
-- Background: some CIs upload each module as its own run with flag=<module> alongside a
-- flag='default' "rollup" run that already holds the WHOLE build (the module runs are the
-- same tests, partitioned). That makes a build look like N+1 runs and encodes the module in
-- the flag axis. The intended model is one run per commit/build carrying its modules as the
-- per-row `module` tag (#393). This migration reconciles the existing data to that model.
--
-- Per (project, branch, commit) group that has a 'default' rollup run AND >=1 non-default
-- (module) run:
--   1. tag the rollup's cases  with module = the module run's flag (matched by class + name)
--   2. tag the rollup's coverage files with the same (matched by package + file)
--   3. delete the now-redundant module runs and their children
-- The rollup is only ever TAGGED IN PLACE (never has rows removed), so no test/coverage row
-- can be lost even if a rollup lacks a match — the module runs' rows are duplicates of it.
-- Groups WITHOUT a default sibling (a real coverage component like `frontend`, or a
-- single-flag project) are left untouched.

-- Temporary match indexes: the tagging steps match rollup rows to module rows by
-- (class_name, name) / (package_name, file_name), which are otherwise unindexed — on a large
-- production dataset the correlated lookups would be far too slow at startup without these.
CREATE INDEX idx_v25_case_match ON test_case_result (class_name, name);
CREATE INDEX idx_v25_cov_match ON coverage_file_entry (package_name, file_name);

-- 1. Tag the rollup's test cases with the owning module (the sibling module run's flag).
UPDATE test_case_result rc
SET module = (
    SELECT mr.flag
    FROM test_run canon
    JOIN test_run mr
      ON mr.project_id = canon.project_id
     AND mr.commit_sha = canon.commit_sha
     AND COALESCE(mr.branch, '') = COALESCE(canon.branch, '')
     AND mr.flag <> 'default'
    JOIN test_case_result mc
      ON mc.run_id = mr.id
     AND COALESCE(mc.class_name, '') = COALESCE(rc.class_name, '')
     AND mc.name = rc.name
    WHERE canon.id = rc.run_id
    LIMIT 1
)
WHERE rc.module IS NULL
  AND EXISTS (
    SELECT 1 FROM test_run canon
    WHERE canon.id = rc.run_id
      AND canon.flag = 'default'
      AND canon.commit_sha IS NOT NULL
      AND EXISTS (
        SELECT 1 FROM test_run mr2
        WHERE mr2.project_id = canon.project_id
          AND mr2.commit_sha = canon.commit_sha
          AND COALESCE(mr2.branch, '') = COALESCE(canon.branch, '')
          AND mr2.flag <> 'default'
      )
  );

-- 2. Tag the rollup's coverage files with the owning module (matched by package + file).
UPDATE coverage_file_entry cfe
SET module = (
    SELECT mr.flag
    FROM coverage_report canonrep
    JOIN test_run canon ON canon.id = canonrep.run_id
    JOIN test_run mr
      ON mr.project_id = canon.project_id
     AND mr.commit_sha = canon.commit_sha
     AND COALESCE(mr.branch, '') = COALESCE(canon.branch, '')
     AND mr.flag <> 'default'
    JOIN coverage_report mrep ON mrep.run_id = mr.id
    JOIN coverage_file_entry mcfe
      ON mcfe.report_id = mrep.id
     AND COALESCE(mcfe.package_name, '') = COALESCE(cfe.package_name, '')
     AND mcfe.file_name = cfe.file_name
    WHERE canonrep.id = cfe.report_id
    LIMIT 1
)
WHERE cfe.module IS NULL
  AND EXISTS (
    SELECT 1
    FROM coverage_report canonrep
    JOIN test_run canon ON canon.id = canonrep.run_id
    WHERE canonrep.id = cfe.report_id
      AND canon.flag = 'default'
      AND canon.commit_sha IS NOT NULL
      AND EXISTS (
        SELECT 1 FROM test_run mr2
        WHERE mr2.project_id = canon.project_id
          AND mr2.commit_sha = canon.commit_sha
          AND COALESCE(mr2.branch, '') = COALESCE(canon.branch, '')
          AND mr2.flag <> 'default'
      )
  );

DROP INDEX idx_v25_case_match;
DROP INDEX idx_v25_cov_match;

-- 3. Delete the redundant module runs and their children. A "foldable" module run is a
--    non-default run at a real commit that has a 'default' rollup sibling in its group.
DELETE FROM coverage_file_entry
WHERE report_id IN (
    SELECT cr.id FROM coverage_report cr
    WHERE cr.run_id IN (
        SELECT mr.id FROM test_run mr
        WHERE mr.flag <> 'default' AND mr.commit_sha IS NOT NULL
          AND EXISTS (
            SELECT 1 FROM test_run d
            WHERE d.project_id = mr.project_id AND d.commit_sha = mr.commit_sha
              AND COALESCE(d.branch, '') = COALESCE(mr.branch, '') AND d.flag = 'default'
          )
    )
);

DELETE FROM coverage_report
WHERE run_id IN (
    SELECT mr.id FROM test_run mr
    WHERE mr.flag <> 'default' AND mr.commit_sha IS NOT NULL
      AND EXISTS (
        SELECT 1 FROM test_run d
        WHERE d.project_id = mr.project_id AND d.commit_sha = mr.commit_sha
          AND COALESCE(d.branch, '') = COALESCE(mr.branch, '') AND d.flag = 'default'
      )
);

DELETE FROM test_case_result
WHERE run_id IN (
    SELECT mr.id FROM test_run mr
    WHERE mr.flag <> 'default' AND mr.commit_sha IS NOT NULL
      AND EXISTS (
        SELECT 1 FROM test_run d
        WHERE d.project_id = mr.project_id AND d.commit_sha = mr.commit_sha
          AND COALESCE(d.branch, '') = COALESCE(mr.branch, '') AND d.flag = 'default'
      )
);

DELETE FROM test_suite_result
WHERE run_id IN (
    SELECT mr.id FROM test_run mr
    WHERE mr.flag <> 'default' AND mr.commit_sha IS NOT NULL
      AND EXISTS (
        SELECT 1 FROM test_run d
        WHERE d.project_id = mr.project_id AND d.commit_sha = mr.commit_sha
          AND COALESCE(d.branch, '') = COALESCE(mr.branch, '') AND d.flag = 'default'
      )
);

DELETE FROM share_link
WHERE run_id IN (
    SELECT mr.id FROM test_run mr
    WHERE mr.flag <> 'default' AND mr.commit_sha IS NOT NULL
      AND EXISTS (
        SELECT 1 FROM test_run d
        WHERE d.project_id = mr.project_id AND d.commit_sha = mr.commit_sha
          AND COALESCE(d.branch, '') = COALESCE(mr.branch, '') AND d.flag = 'default'
      )
);

-- ingest_job.run_id has no FK, so null out references to runs that are about to vanish.
UPDATE ingest_job SET run_id = NULL
WHERE run_id IN (
    SELECT mr.id FROM test_run mr
    WHERE mr.flag <> 'default' AND mr.commit_sha IS NOT NULL
      AND EXISTS (
        SELECT 1 FROM test_run d
        WHERE d.project_id = mr.project_id AND d.commit_sha = mr.commit_sha
          AND COALESCE(d.branch, '') = COALESCE(mr.branch, '') AND d.flag = 'default'
      )
);

DELETE FROM test_run
WHERE flag <> 'default' AND commit_sha IS NOT NULL
  AND EXISTS (
    SELECT 1 FROM test_run d
    WHERE d.project_id = test_run.project_id AND d.commit_sha = test_run.commit_sha
      AND COALESCE(d.branch, '') = COALESCE(test_run.branch, '') AND d.flag = 'default'
  );
