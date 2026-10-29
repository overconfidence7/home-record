-- Sync bookkeeping is pruned daily (cr:sync-limits): idempotency records and the conflict log older than 90 days.
-- Rows already stored count as applied now, so the first prune keeps them for the full window.
ALTER TABLE sync_applied ADD COLUMN applied_at INTEGER NOT NULL DEFAULT 0;
UPDATE sync_applied SET applied_at = CAST(strftime('%s', 'now') AS INTEGER) * 1000;
CREATE INDEX sync_applied_at ON sync_applied (applied_at);
CREATE INDEX sync_conflicts_at ON sync_conflicts (at);
