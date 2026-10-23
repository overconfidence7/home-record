-- Cost log for paid lookups (AC-B10). One row per upstream call; cache hits are not logged.
CREATE TABLE lookup_log (
  id TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  kind TEXT NOT NULL,
  cache_key TEXT NOT NULL,
  input_tokens INTEGER NOT NULL DEFAULT 0,
  output_tokens INTEGER NOT NULL DEFAULT 0,
  searches INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);
CREATE INDEX lookup_log_user ON lookup_log (user_id, created_at);
