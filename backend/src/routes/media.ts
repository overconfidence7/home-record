import { Hono, type Context } from "hono";
import { z } from "zod";
import type { AppEnv, Membership } from "../env";
import { membershipFor, notFound, requireSession } from "../tenancy";
import { MEDIA_BUCKET, presignUrl, type R2Credentials } from "../media/presign";
import { ADMIN_ROLES, accessFor, canUpload, visibleRow } from "../sync/policy";
import type { Row } from "../sync/merge";

export const media = new Hono<AppEnv>();
media.use("/media/*", requireSession);

export const PUT_SECONDS = 10 * 60;
export const GET_SECONDS = 7 * 24 * 60 * 60;
export const ORPHAN_DAYS = 30;

const MB = 1024 * 1024;
/** Entities that carry a file, with the largest upload each may sign (cr:presign-size). */
export const MEDIA_MAX_BYTES: Record<string, number> = {
  photo: 25 * MB,
  wall_photo: 25 * MB,
  note: 25 * MB,
  document: 100 * MB,
  job_file: 100 * MB,
};

const segment = z.string().min(1).max(100).regex(/^[A-Za-z0-9._-]+$/).refine((s) => s !== "." && s !== "..");
const token = "[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]*";
const Tenant = { tenantKind: z.enum(["company", "home"]), tenantId: segment };
const PresignPutBody = z.object({
  ...Tenant,
  entity: segment,
  entityId: segment,
  filename: segment,
  size: z.number().int().positive(),
  contentType: z.string().max(100).regex(new RegExp(`^${token}/${token}$`)),
});
const PresignGetBody = z.object({ ...Tenant, key: z.string().min(1).max(420) });

export function r2Credentials(env: AppEnv["Bindings"]): R2Credentials | null {
  if (!env.R2_ACCOUNT_ID || !env.R2_ACCESS_KEY_ID || !env.R2_SECRET_ACCESS_KEY) return null;
  return {
    accountId: env.R2_ACCOUNT_ID,
    accessKeyId: env.R2_ACCESS_KEY_ID,
    secretAccessKey: env.R2_SECRET_ACCESS_KEY,
    bucket: MEDIA_BUCKET,
  };
}

/** The synced row an object belongs to; `deleted` when it is soft-deleted. */
async function ownerRow(db: D1Database, m: Membership, entity: string, entityId: string): Promise<{ row: Row; deleted: boolean } | null> {
  const found = await db
    .prepare("SELECT row_json FROM sync_rows WHERE tenant_kind = ? AND tenant_id = ? AND entity = ? AND entity_id = ?")
    .bind(m.tenantKind, m.tenantId, entity, entityId)
    .first<{ row_json: string }>();
  if (!found) return null;
  const row = JSON.parse(found.row_json) as Row;
  return { row, deleted: row.deletedAt !== null && row.deletedAt !== undefined };
}

const forbidden = (c: Context) => c.json({ error: "forbidden" }, 403);

/**
 * Signs one PUT (AC-B21) for the file of a row the caller may write: only entities that carry files, at most their
 * size cap, for exactly the declared Content-Length and Content-Type. The first caller to sign a key owns it; after
 * that only they, or a company's Owner or Office, may sign another PUT for it, so a member cannot overwrite someone
 * else's photo or plan (cr:presign-overwrite). Home members share the whole home, so any of them may.
 */
media.post("/media/presign", async (c) => {
  const parsed = PresignPutBody.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "bad_request" }, 400);
  const b = parsed.data;
  const session = c.get("session");
  const m = membershipFor(session, b.tenantKind, b.tenantId);
  if (!m) return notFound(c);
  const maxBytes = MEDIA_MAX_BYTES[b.entity];
  if (maxBytes === undefined) return c.json({ error: "bad_request" }, 400);
  if (b.size > maxBytes) return c.json({ error: "too_large", maxBytes }, 413);
  const creds = r2Credentials(c.env);
  if (!creds) return c.json({ error: "unavailable" }, 503);

  const owner = await ownerRow(c.env.DB, m, b.entity, b.entityId);
  if (owner?.deleted) return notFound(c);
  const admin = m.tenantKind === "home" || ADMIN_ROLES.has(m.role);
  if (m.tenantKind === "company") {
    const access = await accessFor(c.env.DB, m.tenantId, session.userId, m.role);
    if (!canUpload(access, b.entity, owner?.row ?? null)) return forbidden(c);
  }

  const key = `${b.tenantId}/${b.entity}/${b.entityId}/${b.filename}`;
  // Claims the key for this caller if nobody has; an older object with no owner goes to the next uploader.
  await c.env.DB.prepare(
    `INSERT INTO media_objects (object_key, tenant_kind, tenant_id, created_at, owner_user_id) VALUES (?, ?, ?, ?, ?)
     ON CONFLICT (object_key) DO UPDATE SET owner_user_id = excluded.owner_user_id WHERE owner_user_id IS NULL`,
  )
    .bind(key, b.tenantKind, b.tenantId, Date.now(), session.userId)
    .run();
  const claimed = await c.env.DB.prepare("SELECT owner_user_id FROM media_objects WHERE object_key = ?")
    .bind(key)
    .first<{ owner_user_id: string | null }>();
  if (claimed?.owner_user_id !== session.userId && !admin) return forbidden(c);

  const now = new Date();
  return c.json({
    key,
    putUrl: await presignUrl(creds, "PUT", key, PUT_SECONDS, now, {
      "content-length": String(b.size),
      "content-type": b.contentType,
    }),
    putExpiresAt: now.getTime() + PUT_SECONDS * 1000,
  });
});

/**
 * Signs one GET for an object of a row the caller may see (AC-B21): the key must name this tenant and a file-carrying
 * entity, and the row must be live and visible to the caller under the pull rules. Never signs a PUT.
 */
media.post("/media/presign-get", async (c) => {
  const parsed = PresignGetBody.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "bad_request" }, 400);
  const b = parsed.data;
  const session = c.get("session");
  const m = membershipFor(session, b.tenantKind, b.tenantId);
  if (!m) return notFound(c);
  const parts = parseMediaKey(b.key);
  if (!parts || !b.key.split("/").every((p) => segment.safeParse(p).success)) return c.json({ error: "bad_request" }, 400);
  if (parts.tenantId !== b.tenantId || MEDIA_MAX_BYTES[parts.entity] === undefined) return notFound(c);
  const creds = r2Credentials(c.env);
  if (!creds) return c.json({ error: "unavailable" }, 503);

  const owner = await ownerRow(c.env.DB, m, parts.entity, parts.entityId);
  if (!owner || owner.deleted) return notFound(c);
  if (m.tenantKind === "company") {
    const access = await accessFor(c.env.DB, m.tenantId, session.userId, m.role);
    if (visibleRow(access, parts.entity, owner.row) === null) return notFound(c);
  }
  const now = new Date();
  return c.json({
    getUrl: await presignUrl(creds, "GET", b.key, GET_SECONDS, now),
    getExpiresAt: now.getTime() + GET_SECONDS * 1000,
  });
});

/** Splits a `tenant/entity/id/filename` key; null when it does not have that shape. */
export function parseMediaKey(key: string): { tenantId: string; entity: string; entityId: string } | null {
  const parts = key.split("/");
  if (parts.length !== 4 || parts.some((p) => p.length === 0)) return null;
  return { tenantId: parts[0], entity: parts[1], entityId: parts[2] };
}

/**
 * Cron: removes objects presigned over 30 days ago whose owning row (the tenant, entity and id in the key)
 * is gone or soft-deleted. Rows reference their media by id, not by key (a photo row stores no key until
 * its upload finishes, and only on this phone), so the key itself names the row that keeps it.
 * Returns the count removed.
 */
export async function removeOrphanedMedia(env: AppEnv["Bindings"], now = Date.now()): Promise<number> {
  const cutoff = now - ORPHAN_DAYS * 86_400_000;
  const { results } = await env.DB.prepare(
    "SELECT object_key, tenant_kind, tenant_id FROM media_objects WHERE created_at < ? LIMIT 500",
  )
    .bind(cutoff)
    .all<{ object_key: string; tenant_kind: string; tenant_id: string }>();
  let removed = 0;
  for (const { object_key, tenant_kind, tenant_id } of results) {
    const parsed = parseMediaKey(object_key);
    const used =
      parsed === null ||
      parsed.tenantId !== tenant_id ||
      (await env.DB.prepare(
        "SELECT 1 FROM sync_rows WHERE tenant_kind = ? AND tenant_id = ? AND entity = ? AND entity_id = ? " +
          "AND json_extract(row_json, '$.deletedAt') IS NULL LIMIT 1",
      )
        .bind(tenant_kind, tenant_id, parsed.entity, parsed.entityId)
        .first()) !== null;
    if (used) {
      // Still owned (or a key we cannot attribute): look again in another 30 days.
      await env.DB.prepare("UPDATE media_objects SET created_at = ? WHERE object_key = ?").bind(now, object_key).run();
      continue;
    }
    await env.MEDIA.delete(object_key);
    await env.DB.prepare("DELETE FROM media_objects WHERE object_key = ?").bind(object_key).run();
    removed++;
  }
  return removed;
}
