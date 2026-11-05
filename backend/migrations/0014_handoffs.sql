-- T063c (AC-B40): a Field Log handoff. The `.homerecord` bundle sits in R2 under `object_key`; the homeowner gets an
-- emailed claim link carrying a random token (only its SHA-256 is stored). Claiming binds the handoff to one account,
-- which may download the bundle until the link expires 30 days after it was sent; the daily cron removes expired ones.
CREATE TABLE handoffs (
  id TEXT PRIMARY KEY,
  token_hash TEXT NOT NULL UNIQUE,
  company_id TEXT NOT NULL,
  job_id TEXT,
  home_name TEXT NOT NULL,
  email TEXT NOT NULL,
  object_key TEXT NOT NULL,
  size INTEGER NOT NULL,
  created_by TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  claimed_by TEXT,
  claimed_at INTEGER
);
CREATE INDEX handoffs_expires ON handoffs (expires_at);
