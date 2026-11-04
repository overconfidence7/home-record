-- T058 (AC-B35, AC-B36): share pages look a link up by its token across every company. The link itself is the synced
-- `share_link` row (kind, jobId, targetId, token, revoked) Owner or Office writes from the app; this index keeps
-- the lookup off a scan of every synced row.
CREATE INDEX sync_rows_share_token ON sync_rows (json_extract(row_json, '$.token'))
  WHERE tenant_kind = 'company' AND entity = 'share_link';
