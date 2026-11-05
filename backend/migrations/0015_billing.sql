-- T064a (AC-B50, AC-F56): RevenueCat webhook state. `entitlement` (already on companies and homes) holds the active
-- entitlement id (`fieldlog_solo`, `fieldlog_crew`, `pro`); `entitlement_expires_at` is when it lapses (NULL = no end,
-- e.g. a lifetime purchase); `entitlement_event_at` is the RevenueCat event time last applied, so a late, older
-- event never undoes a newer one. `billing_events` keeps each event id once so RevenueCat's retries are no-ops.
ALTER TABLE companies ADD COLUMN entitlement_expires_at INTEGER;
ALTER TABLE companies ADD COLUMN entitlement_event_at INTEGER;
ALTER TABLE homes ADD COLUMN entitlement_expires_at INTEGER;
ALTER TABLE homes ADD COLUMN entitlement_event_at INTEGER;
CREATE TABLE billing_events (
  id TEXT PRIMARY KEY,
  app_user_id TEXT NOT NULL,
  type TEXT NOT NULL,
  received_at INTEGER NOT NULL
);
