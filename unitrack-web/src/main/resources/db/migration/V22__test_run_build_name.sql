-- Friendly build identifier from the CI run (e.g. GitHub Actions run number), shown next to
-- the build-URL deep link on the run page. Nullable: standalone uploads have no CI build.
ALTER TABLE test_run ADD COLUMN build_name VARCHAR(255);
