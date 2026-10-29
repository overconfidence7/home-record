-- Lookup quotas move from KV to one D1 row per account and UTC day (cr:quota-race): the increment is a single
-- conditional upsert, so concurrent requests cannot both read the same count and pass the limit.
CREATE TABLE lookup_quota (
  user_id TEXT NOT NULL,
  day TEXT NOT NULL,
  used INTEGER NOT NULL,
  PRIMARY KEY (user_id, day)
);
