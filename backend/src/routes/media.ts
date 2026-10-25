import { Hono } from "hono";
import { z } from "zod";
import type { AppEnv } from "../env";
import { membershipFor, notFound, requireSession } from "../tenancy";
import { presignUrl, type R2Credentials } from "../media/presign";

export const media = new Hono<AppEnv>();
media.use("/media/*", requireSession);

export const PUT_SECONDS = 10 * 60;
export const GET_SECONDS = 7 * 24 * 60 * 60;
export const ORPHAN_DAYS = 30;

const segment = z.string().min(1).max(100).regex(/^[A-Za-z0-9._-]+$/).refine((s) => s !== "." && s !== "..");
const PresignBody = z.object({
  tenantKind: z.enum(["company", "home"]),
  tenantId: segment,
  entity: segment,
  entityId: segment,
  filename: segment,
});

export function r2Credentials(env: AppEnv["Bindings"]): R2Credentials | null {
  if (!env.R2_ACCOUNT_ID || !env.R2_ACCESS_KEY_ID || !env.R2_SECRET_ACCESS_KEY) return null;
  return {
    accountId: env.R2_ACCOUNT_ID,
    accessKeyId: env.R2_ACCESS_KEY_ID,
    secretAccessKey: env.R2_SECRET_ACCESS_KEY,
    bucket: env.R2_BUCKET ?? "homerecord-media",
  };
}

media.post("/media/presign", async (c) => {
  const parsed = PresignBody.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "bad_request" }, 400);
  const b = parsed.data;
  if (!membershipFor(c.get("session"), b.tenantKind, b.tenantId)) return notFound(c);
  const creds = r2Credentials(c.env);
  if (!creds) return c.json({ error: "unavailable" }, 503);

  const key = `${b.tenantId}/${b.entity}/${b.entityId}/${b.filename}`;
  await c.env.DB.prepare(
    "INSERT OR IGNORE INTO media_objects (object_key, tenant_kind, tenant_id, created_at) VALUES (?, ?, ?, ?)",
  )
    .bind(key, b.tenantKind, b.tenantId, Date.now())
    .run();
  const now = new Date();
  return c.json({
    key,
    putUrl: await presignUrl(creds, "PUT", key, PUT_SECONDS, now),
    putExpiresAt: now.getTime() + PUT_SECONDS * 1000,
    getUrl: await presignUrl(creds, "GET", key, GET_SECONDS, now),
    getExpiresAt: now.getTime() + GET_SECONDS * 1000,
  });
});

/** Cron: removes objects presigned over 30 days ago that no synced row references. Returns the count removed. */
export async function removeOrphanedMedia(env: AppEnv["Bindings"], now = Date.now()): Promise<number> {
  const cutoff = now - ORPHAN_DAYS * 86_400_000;
  const { results } = await env.DB.prepare("SELECT object_key FROM media_objects WHERE created_at < ? LIMIT 500")
    .bind(cutoff)
    .all<{ object_key: string }>();
  let removed = 0;
  for (const { object_key } of results) {
    const used = await env.DB.prepare("SELECT 1 FROM sync_rows WHERE instr(row_json, ?) > 0 LIMIT 1").bind(object_key).first();
    if (used) {
      // Still referenced: look again in another 30 days.
      await env.DB.prepare("UPDATE media_objects SET created_at = ? WHERE object_key = ?").bind(now, object_key).run();
      continue;
    }
    await env.MEDIA.delete(object_key);
    await env.DB.prepare("DELETE FROM media_objects WHERE object_key = ?").bind(object_key).run();
    removed++;
  }
  return removed;
}
