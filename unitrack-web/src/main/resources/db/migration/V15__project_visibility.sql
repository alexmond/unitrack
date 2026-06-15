-- Project visibility: PUBLIC (anyone can read) or PRIVATE (members + admins only).
-- Existing projects become PRIVATE; the admin(s) are seeded as OWNER so the projects
-- remain manageable (global ADMINs also bypass access checks).
ALTER TABLE project
    ADD COLUMN visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE';

INSERT INTO project_membership (project_id, user_id, role, created_at)
SELECT p.id, u.id, 'OWNER', CURRENT_TIMESTAMP
FROM project p
         JOIN app_user u ON u.role = 'ADMIN'
WHERE NOT EXISTS (SELECT 1
                  FROM project_membership m
                  WHERE m.project_id = p.id
                    AND m.user_id = u.id);
