-- Presigned media objects (AC-B21). The orphan cron deletes rows older than 30 days that no synced row mentions.
CREATE TABLE media_objects (
  object_key TEXT PRIMARY KEY,
  tenant_kind TEXT NOT NULL,
  tenant_id TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX media_objects_created ON media_objects (created_at);
