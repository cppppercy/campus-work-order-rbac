-- RBAC1: a senior role inherits all permissions of each direct or indirect junior role.
-- No default relationship is seeded, so upgrading does not broaden any existing account.
CREATE TABLE IF NOT EXISTS role_hierarchy (
    senior_role_id INTEGER NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    junior_role_id INTEGER NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    PRIMARY KEY (senior_role_id, junior_role_id),
    CHECK (senior_role_id <> junior_role_id)
);
CREATE INDEX IF NOT EXISTS idx_role_hierarchy_junior ON role_hierarchy(junior_role_id);
INSERT INTO schema_version(version,applied_at)
SELECT 2, strftime('%Y-%m-%dT%H:%M:%fZ','now')
WHERE NOT EXISTS (SELECT 1 FROM schema_version WHERE version=2);
