-- Fixed-window counters for sign-in rate limits (AC-B1): code requests per email and per IP,
-- wrong codes per email (across codes, so a new request does not reset them) and per IP.
CREATE TABLE auth_limits (
  key TEXT PRIMARY KEY,
  count INTEGER NOT NULL,
  window_start INTEGER NOT NULL
);
