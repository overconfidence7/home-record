-- Who presigned the first PUT for an object (cr:presign-overwrite): only that member, or a company's
-- Owner or Office, may sign another PUT for the same key. Rows from before this migration have no owner.
ALTER TABLE media_objects ADD COLUMN owner_user_id TEXT;
