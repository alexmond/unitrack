-- #393: an explicit build module carried by the uploader.
--
-- Report formats don't encode a module: Surefire/JUnit XML and JaCoCo/Cobertura/LCOV
-- report packages and classes/files, never the Maven/Gradle module they belong to. So
-- "module" today is *derived* heuristically from the package tree, which is Java-centric
-- and wrong for Go/.NET/Node. The uploader (which knows it runs in e.g. unitrack-web/) is
-- the only place that knows the real module, so it can now send one per upload and we store
-- it on the rows it produced.
--
-- Nullable on purpose: existing rows and module-unaware uploaders fall back to the
-- package-derived grouping, so nothing breaks.
ALTER TABLE test_suite_result
    ADD COLUMN module VARCHAR(255);

ALTER TABLE test_case_result
    ADD COLUMN module VARCHAR(255);

ALTER TABLE coverage_file_entry
    ADD COLUMN module VARCHAR(255);
