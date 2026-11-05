import { Hono } from "hono";
import { z } from "zod";
import type { AppEnv } from "../env";
import { CREW, HOME_PRO, SOLO } from "../billing/plan";

/**
 * RevenueCat webhook (AC-B50). RevenueCat posts every purchase, renewal, cancellation and expiry with the
 * Authorization header set in its dashboard; we compare it to `REVENUECAT_WEBHOOK_SECRET` and keep the entitlement
 * on the tenant the purchase belongs to, so `GET /me` (and every gate on the server) answers the same thing on
 * every phone of the company. The apps log in to RevenueCat with `company:<companyId>` (Field Log, the Owner buys
 * for the company, AC-F56) or `home:<homeId>` (House Manual Pro); other app user ids are acknowledged and ignored.
 *
 * Event types: a purchase, renewal, plan change, uncancel, extension or temporary grant sets the entitlement and
 * its expiry; a cancellation or billing issue keeps it until the expiry RevenueCat sends; EXPIRATION clears it;
 * TRANSFER clears it on the ids it moved away from (the new owner's next event sets theirs). Each event id is
 * applied once, and an event older than the last one applied to that tenant changes nothing.
 */
export const billing = new Hono<AppEnv>();

const GRANT = new Set([
  "INITIAL_PURCHASE", "RENEWAL", "PRODUCT_CHANGE", "UNCANCELLATION", "NON_RENEWING_PURCHASE",
  "SUBSCRIPTION_EXTENDED", "TEMPORARY_ENTITLEMENT_GRANT", "CANCELLATION", "BILLING_ISSUE",
]);

const Event = z.object({
  id: z.string().min(1).max(128),
  type: z.string().min(1).max(64),
  app_user_id: z.string().max(256).nullish(),
  original_app_user_id: z.string().max(256).nullish(),
  aliases: z.array(z.string().max(256)).max(100).nullish(),
  entitlement_ids: z.array(z.string().max(128)).max(50).nullish(),
  entitlement_id: z.string().max(128).nullish(),
  expiration_at_ms: z.number().nullish(),
  event_timestamp_ms: z.number().nullish(),
  transferred_from: z.array(z.string().max(256)).max(100).nullish(),
});
const Body = z.object({ event: Event });
type RcEvent = z.infer<typeof Event>;

interface Tenant {
  table: "companies" | "homes";
  id: string;
}

/** `company:<id>` or `home:<id>`; anything else (an anonymous RevenueCat id) is not ours to track. */
export function tenantOf(appUserId: string | null | undefined): Tenant | null {
  const m = /^(company|home):([A-Za-z0-9._-]{1,64})$/.exec(appUserId ?? "");
  if (!m) return null;
  return { table: m[1] === "company" ? "companies" : "homes", id: m[2] };
}

/** The first tracked tenant among the ids RevenueCat names for the buyer. */
function tenantFor(e: RcEvent): Tenant | null {
  for (const id of [e.app_user_id, e.original_app_user_id, ...(e.aliases ?? [])]) {
    const t = tenantOf(id);
    if (t) return t;
  }
  return null;
}

/** Which of our entitlements the event grants for that kind of tenant; Crew wins over Solo if both are listed. */
export function grantedEntitlement(table: Tenant["table"], ids: string[]): string | null {
  if (table === "homes") return ids.includes(HOME_PRO) ? HOME_PRO : null;
  if (ids.includes(CREW)) return CREW;
  if (ids.includes(SOLO)) return SOLO;
  return null;
}

/**
 * True when an event about Solo would replace a Crew still in force (an Owner who moved up to Crew gets the old
 * Solo subscription's cancellation or renewal later); Crew is the larger plan and wins until it ends.
 */
export function outranked(
  entitlement: string,
  current: { entitlement: string | null; entitlement_expires_at: number | null } | null,
  now: number,
): boolean {
  if (entitlement !== SOLO || current?.entitlement !== CREW) return false;
  return current.entitlement_expires_at == null || current.entitlement_expires_at > now;
}

/** Constant-time compare so the secret cannot be guessed a byte at a time. */
function sameSecret(a: string, b: string): boolean {
  const x = new TextEncoder().encode(a);
  const y = new TextEncoder().encode(b);
  let diff = x.length ^ y.length;
  for (let i = 0; i < Math.max(x.length, y.length); i++) diff |= (x[i] ?? 0) ^ (y[i] ?? 0);
  return diff === 0;
}

billing.post("/webhooks/revenuecat", async (c) => {
  const secret = c.env.REVENUECAT_WEBHOOK_SECRET;
  if (!secret) return c.json({ error: "unavailable" }, 503);
  const header = c.req.header("Authorization") ?? "";
  if (!sameSecret(header, secret) && !sameSecret(header, `Bearer ${secret}`)) return c.json({ error: "unauthorized" }, 401);
  const parsed = Body.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "invalid" }, 400);
  const e = parsed.data.event;
  const now = Date.now();
  const at = e.event_timestamp_ms ?? now;

  const seen = await c.env.DB.prepare("SELECT 1 FROM billing_events WHERE id = ?").bind(e.id).first();
  if (seen) return c.json({ ok: true, duplicate: true });
  // The event id is recorded only once the event has been applied, so a retry after a failure applies it again.
  const handled = () =>
    c.env.DB.prepare("INSERT INTO billing_events (id, app_user_id, type, received_at) VALUES (?, ?, ?, ?) ON CONFLICT(id) DO NOTHING")
      .bind(e.id, e.app_user_id ?? "", e.type, now)
      .run();
  const done = async (body: Record<string, unknown>) => {
    await handled();
    return c.json(body);
  };

  const apply = (t: Tenant, entitlement: string | null, expiresAt: number | null) =>
    c.env.DB.prepare(
      `UPDATE ${t.table} SET entitlement = ?, entitlement_expires_at = ?, entitlement_event_at = ?
       WHERE id = ? AND (entitlement_event_at IS NULL OR entitlement_event_at <= ?)`,
    ).bind(entitlement, expiresAt, at, t.id, at).run();

  if (e.type === "TRANSFER") {
    for (const id of e.transferred_from ?? []) {
      const t = tenantOf(id);
      if (t) await apply(t, null, null);
    }
    return done({ ok: true });
  }
  const tenant = tenantFor(e);
  if (!tenant) return done({ ok: true, ignored: "app_user_id" });
  const ids = e.entitlement_ids ?? (e.entitlement_id ? [e.entitlement_id] : []);
  const entitlement = grantedEntitlement(tenant.table, ids);
  const current = await c.env.DB.prepare(`SELECT entitlement, entitlement_expires_at FROM ${tenant.table} WHERE id = ?`)
    .bind(tenant.id)
    .first<{ entitlement: string | null; entitlement_expires_at: number | null }>();
  if (e.type === "EXPIRATION") {
    // A subscription that ends clears only its own entitlement: Solo running out leaves a Crew the company holds.
    if (entitlement && current?.entitlement && current.entitlement !== entitlement) return done({ ok: true, ignored: "other_entitlement" });
    await apply(tenant, null, null);
    return done({ ok: true });
  }
  if (!GRANT.has(e.type)) return done({ ok: true, ignored: "type" });
  if (!entitlement) return done({ ok: true, ignored: "entitlement" });
  if (outranked(entitlement, current ?? null, now)) return done({ ok: true, ignored: "outranked" });
  await apply(tenant, entitlement, e.expiration_at_ms ?? null);
  return done({ ok: true });
});
