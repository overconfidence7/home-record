CREATE TABLE users (
  id TEXT PRIMARY KEY,
  email TEXT NOT NULL UNIQUE,
  name TEXT,
  google_sub TEXT UNIQUE,
  created_at INTEGER NOT NULL
);

CREATE TABLE companies (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  trade TEXT,
  phone TEXT,
  trial_ends_at INTEGER,
  entitlement TEXT,
  created_at INTEGER NOT NULL
);

CREATE TABLE homes (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  entitlement TEXT,
  created_at INTEGER NOT NULL
);

-- One row per (user, tenant). tenant_kind is 'company' (Field Log) or 'home' (House Manual).
CREATE TABLE memberships (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL REFERENCES users(id),
  tenant_kind TEXT NOT NULL CHECK (tenant_kind IN ('company', 'home')),
  tenant_id TEXT NOT NULL,
  role TEXT NOT NULL CHECK (role IN ('owner', 'office', 'lead', 'crew', 'sub', 'member')),
  created_at INTEGER NOT NULL,
  UNIQUE (user_id, tenant_kind, tenant_id)
);
CREATE INDEX memberships_tenant ON memberships (tenant_kind, tenant_id);

CREATE TABLE email_codes (
  email TEXT PRIMARY KEY,
  code_hash TEXT NOT NULL,
  expires_at INTEGER NOT NULL,
  attempts INTEGER NOT NULL DEFAULT 0
);
