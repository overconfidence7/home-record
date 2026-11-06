-- T076a (AC-H16): sharing a home. A home now has three roles: 'owner' (made it; billing, invites, delete cloud copy),
-- 'editor' (family: edits the whole home) and 'viewer' (a buyer or house-sitter: reads only, the server refuses
-- their pushes and uploads). SQLite cannot change a CHECK constraint in place, so memberships is rebuilt with the
-- two new roles; nothing references memberships, and every column and the index come across unchanged.
CREATE TABLE memberships_new (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  tenant_kind TEXT NOT NULL CHECK (tenant_kind IN ('company', 'home')),
  tenant_id TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('owner', 'office', 'lead', 'crew', 'sub', 'member', 'editor', 'viewer')),
  created_at INTEGER NOT NULL,
  rate_cents INTEGER,
  fcm_token TEXT,
  UNIQUE (user_id, tenant_kind, tenant_id)
);
INSERT INTO memberships_new (id, user_id, tenant_kind, tenant_id, role, created_at, rate_cents, fcm_token)
  SELECT id, user_id, tenant_kind, tenant_id, role, created_at, rate_cents, fcm_token FROM memberships;
DROP TABLE memberships;
ALTER TABLE memberships_new RENAME TO memberships;
CREATE INDEX memberships_tenant ON memberships (tenant_kind, tenant_id);

-- One invite link per person: a random token (only its SHA-256 is stored), the role it grants, single use, expires
-- after 14 days, revocable by the owner. `accepted_by` is the account that used it.
CREATE TABLE home_invites (
  id TEXT PRIMARY KEY,
  token_hash TEXT NOT NULL UNIQUE,
  home_id TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('editor', 'viewer')),
  created_by TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  expires_at INTEGER NOT NULL,
  accepted_by TEXT,
  accepted_at INTEGER
);
CREATE INDEX home_invites_home ON home_invites (home_id);
CREATE INDEX home_invites_expires ON home_invites (expires_at);
