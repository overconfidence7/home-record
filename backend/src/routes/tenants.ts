import { Hono } from "hono";
import { z } from "zod";
import type { AppEnv } from "../env";
import { membershipFor, notFound, requireSession, requireTenant } from "../tenancy";
import { DEFAULT_SETTINGS } from "./company";

export const tenants = new Hono<AppEnv>();

// Columns every member may read, and the ones only Owner/Office (company) or the owner (home) see (AC-B3, AC-F3).
// Pay-sensitive and billing fields never go to Crew/Sub/Lead; settings are the clock rules every phone applies.
const COMPANY_MEMBER = "id, name, trade, phone, settings";
const COMPANY_ADMIN = `${COMPANY_MEMBER}, email, address, logo_key, trial_ends_at, entitlement, created_at`;
const HOME_MEMBER = "id, name";
const HOME_OWNER = `${HOME_MEMBER}, entitlement, created_at`;

/** Tenant lookup with an explicit column list per role; also the reference for how every tenant-scoped route is guarded (AC-B2). */
tenants.get("/:kind{companies|homes}/:tenantId", requireSession, requireTenant(), async (c) => {
  const isCompany = c.req.param("kind") === "companies";
  const id = c.req.param("tenantId");
  const role = membershipFor(c.get("session"), isCompany ? "company" : "home", id)?.role;
  const columns = isCompany
    ? (role === "owner" || role === "office" ? COMPANY_ADMIN : COMPANY_MEMBER)
    : (role === "owner" ? HOME_OWNER : HOME_MEMBER);
  const row = await c.env.DB.prepare(`SELECT ${columns} FROM ${isCompany ? "companies" : "homes"} WHERE id = ?`)
    .bind(id)
    .first<Record<string, unknown>>();
  if (!row) return notFound(c);
  const { settings, logo_key, trial_ends_at, created_at, ...rest } = row;
  return c.json({
    ...rest,
    ...(settings !== undefined && { settings: { ...DEFAULT_SETTINGS, ...JSON.parse(String(settings)) } }),
    ...(logo_key !== undefined && { logoKey: logo_key }),
    ...(trial_ends_at !== undefined && { trialEndsAt: trial_ends_at }),
    ...(created_at !== undefined && { createdAt: created_at }),
  });
});

const homeBody = z.object({ name: z.string().trim().min(1).max(120) });
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * AC-H15: signing in uploads the home. House Manual makes its home offline with its own id, so the first sync registers
 * that id here: a new id creates the home with the caller as owner (201); a home the caller already belongs to answers
 * 200; someone else's home answers 404 like any other tenant the caller cannot see (AC-B2).
 */
tenants.put("/homes/:homeId", requireSession, async (c) => {
  const homeId = c.req.param("homeId");
  const parsed = homeBody.safeParse(await c.req.json().catch(() => null));
  if (!uuid.test(homeId) || !parsed.success) return c.json({ error: "invalid" }, 400);
  const session = c.get("session");
  const member = membershipFor(session, "home", homeId);
  if (member) return c.json({ id: homeId, role: member.role }, 200);
  const now = Date.now();
  const created = await c.env.DB.prepare("INSERT INTO homes (id, name, created_at) VALUES (?, ?, ?) ON CONFLICT (id) DO NOTHING")
    .bind(homeId, parsed.data.name, now)
    .run();
  if (created.meta.changes !== 1) return notFound(c);
  await c.env.DB.prepare("INSERT INTO memberships (id, user_id, tenant_kind, tenant_id, role, created_at) VALUES (?, ?, 'home', ?, 'owner', ?)")
    .bind(crypto.randomUUID(), session.userId, homeId, now)
    .run();
  return c.json({ id: homeId, role: "owner" }, 201);
});

/**
 * HM-Account "Delete cloud copy" (AC-H15): the owner removes the home from the server. Every synced row, counter,
 * applied mark and conflict of the home tenant goes, then its R2 media (every object under `<homeId>/`, the key
 * prefix /media/presign signs, plus any `media_objects` row of the tenant), its memberships and the home itself.
 * The phone keeps its own copy and signs out. Other members and other homes are untouched; a non-owner gets 404.
 */
tenants.delete("/:kind{homes}/:tenantId", requireSession, requireTenant(["owner"]), async (c) => {
  const homeId = c.req.param("tenantId");
  const removed = await deleteHomeTenant(c.env, homeId);
  return c.json({ id: homeId, mediaRemoved: removed });
});

/** Removes everything the server holds for home [homeId]; answers how many R2 objects it deleted. */
export async function deleteHomeTenant(env: AppEnv["Bindings"], homeId: string): Promise<number> {
  const db = env.DB;
  // R2 first: if the Worker stops half way, the rows still name the tenant and a retry finds the rest.
  let removed = 0;
  const keys = new Set<string>();
  let cursor: string | undefined;
  do {
    const page = await env.MEDIA.list({ prefix: `${homeId}/`, cursor });
    page.objects.forEach((o) => keys.add(o.key));
    cursor = page.truncated ? page.cursor : undefined;
  } while (cursor);
  const { results } = await db
    .prepare("SELECT object_key FROM media_objects WHERE tenant_kind = 'home' AND tenant_id = ?")
    .bind(homeId)
    .all<{ object_key: string }>();
  results.forEach((r) => keys.add(r.object_key));
  const all = [...keys];
  for (let i = 0; i < all.length; i += 1000) {
    const batch = all.slice(i, i + 1000);
    await env.MEDIA.delete(batch);
    removed += batch.length;
  }
  const tenant = ["home", homeId] as const;
  await db.batch([
    db.prepare("DELETE FROM sync_rows WHERE tenant_kind = ? AND tenant_id = ?").bind(...tenant),
    db.prepare("DELETE FROM sync_applied WHERE tenant_kind = ? AND tenant_id = ?").bind(...tenant),
    db.prepare("DELETE FROM sync_conflicts WHERE tenant_kind = ? AND tenant_id = ?").bind(...tenant),
    db.prepare("DELETE FROM sync_counters WHERE tenant_kind = ? AND tenant_id = ?").bind(...tenant),
    db.prepare("DELETE FROM media_objects WHERE tenant_kind = ? AND tenant_id = ?").bind(...tenant),
    db.prepare("DELETE FROM push_log WHERE tenant_id = ?").bind(homeId),
    db.prepare("DELETE FROM home_invites WHERE home_id = ?").bind(homeId),
    db.prepare("DELETE FROM memberships WHERE tenant_kind = ? AND tenant_id = ?").bind(...tenant),
    db.prepare("DELETE FROM homes WHERE id = ?").bind(homeId),
  ]);
  return removed;
}
