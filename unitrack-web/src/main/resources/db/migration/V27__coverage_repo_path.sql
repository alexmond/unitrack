-- #454: store the repo-relative source path for each coverage file, resolved at ingest
-- from the uploader's source manifest (git ls-files). Drives working GitHub source links;
-- null when no manifest was sent (back-compat) or nothing matched.
ALTER TABLE coverage_file_entry ADD COLUMN repo_path TEXT;
