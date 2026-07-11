-- #443 GitHub Checks API: store the line numbers with no coverage per file so a PR's
-- changed lines can be annotated inline ("this line isn't covered"). Comma-separated
-- line numbers, populated from JaCoCo's per-line data; null when a coverage format
-- carries no line detail (Cobertura/LCOV/OpenCover today).
ALTER TABLE coverage_file_entry ADD COLUMN uncovered_lines TEXT;
