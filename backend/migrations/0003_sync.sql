-- Per-tenant sync (AC-B20). One row per synced entity; serverSeq orders every change.
CREATE TABLE sync_counters (
  tenant_kind TEXT NOT NULL,
  tenant_id TEXT NOT NULL,
  seq INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (tenant_kind, tenant_id)
);

CREATE TABLE sync_rows (
  tenant_kind TEXT NOT NULL,
  tenant_id TEXT NOT NULL,
  entity TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  row_json TEXT NOT NULL,
  times_json TEXT NOT NULL,
  authoritative INTEGER NOT NULL DEFAULT 0,
  server_seq INTEGER NOT NULL,
  PRIMARY KEY (tenant_kind, tenant_id, entity, entity_id)
);
CREATE INDEX sync_rows_seq ON sync_rows (tenant_kind, tenant_id, server_seq);

-- Idempotency: a retried push of the same (device, outbox row) is acknowledged, never re-applied.
CREATE TABLE sync_applied (
  tenant_kind TEXT NOT NULL,
  tenant_id TEXT NOT NULL,
  device_id TEXT NOT NULL,
  outbox_id INTEGER NOT NULL,
  server_seq INTEGER NOT NULL,
  PRIMARY KEY (tenant_kind, tenant_id, device_id, outbox_id)
);

CREATE TABLE sync_conflicts (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  tenant_kind TEXT NOT NULL,
  tenant_id TEXT NOT NULL,
  entity TEXT NOT NULL,
  entity_id TEXT NOT NULL,
  field TEXT NOT NULL,
  kept_json TEXT,
  dropped_json TEXT,
  reason TEXT NOT NULL,
  device_id TEXT NOT NULL,
  at INTEGER NOT NULL
);
