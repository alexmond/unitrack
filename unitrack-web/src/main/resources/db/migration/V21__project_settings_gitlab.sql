-- Per-project GitLab integration toggle (parity with gh_enabled). Null inherits the global
-- unitrack.gitlab.enabled default.
ALTER TABLE project_settings ADD COLUMN gl_enabled BOOLEAN;
