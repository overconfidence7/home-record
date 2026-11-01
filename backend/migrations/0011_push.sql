-- Push notifications (AC-B30): FCM device tokens per member and app, and a short log of what was sent.
-- A token belongs to one account at a time; signing in as someone else on that phone moves it.
CREATE TABLE push_tokens (
  token TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  app TEXT NOT NULL CHECK (app IN ('field-log', 'house-manual')),
  updated_at INTEGER NOT NULL
);
CREATE INDEX push_tokens_user ON push_tokens (user_id, app);

-- One row per notification per member (not per phone), kept 30 days; `delivered` counts the phones FCM accepted.
CREATE TABLE push_log (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  user_id TEXT NOT NULL,
  tenant_id TEXT NOT NULL,
  type TEXT NOT NULL,
  data_json TEXT NOT NULL,
  delivered INTEGER NOT NULL,
  at INTEGER NOT NULL
);
CREATE INDEX push_log_at ON push_log (at);
