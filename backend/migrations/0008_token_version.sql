-- Session revocation (cr:session-revoke): every token carries the user's token_version as `ver`;
-- requireSession refuses a token whose ver is not the current one, so bumping it signs out every phone.
ALTER TABLE users ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
