ALTER TABLE companies ADD COLUMN email TEXT;
ALTER TABLE companies ADD COLUMN address TEXT;
ALTER TABLE companies ADD COLUMN logo_key TEXT;
-- Settings JSON: otRule, breakRule, requireGps, punchDistanceFt, photoOnPunch (SPEC FL-Company).
ALTER TABLE companies ADD COLUMN settings TEXT NOT NULL DEFAULT '{}';
ALTER TABLE memberships ADD COLUMN rate_cents INTEGER;
ALTER TABLE memberships ADD COLUMN fcm_token TEXT;

-- One live invite code per company; rotating replaces it.
CREATE TABLE company_invites (
  company_id TEXT PRIMARY KEY REFERENCES companies(id),
  code TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL
);
