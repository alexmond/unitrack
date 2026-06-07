ALTER TABLE project_settings ADD COLUMN gh_enabled BOOLEAN;
ALTER TABLE project_settings ADD COLUMN gh_context VARCHAR(255);
ALTER TABLE project_settings ADD COLUMN gh_pr_comment BOOLEAN;
