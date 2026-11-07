import { Hono } from "hono";
import { z } from "zod";
import type { AppEnv } from "../env";
import { requireSession } from "../tenancy";
import { DEFAULT_SETTINGS } from "./company";
import { activeEntitlement, companyPlan, memberLimit } from "../billing/plan";
import { deleteTenant } from "./tenants";

export const me = new Hono<AppEnv>();
me.use("*", requireSession);

/**
 * Identity, memberships and entitlement so both apps gate consistently (AC-B50 reads these columns).
 * A company membership also carries the clock rules (require GPS, punch distance, break and overtime rule) so every
 * role clocks in under them, not only the Owner and Office who may read the profile (AC-F9, AC-F12, AC-F13, AC-F18).
 * It also carries the company's phone for every role and its email for Owner and Office (the AC-B3 column split), so the
 * daily log PDF a Lead or the office sends is headed with the company's contact line (AC-F33).
 */
me.get("/", async (c) => {
  const session = c.get("session");
  const user = await c.env.DB.prepare("SELECT id, email, name FROM users WHERE id = ?")
    .bind(session.userId)
    .first<{ id: string; email: string; name: string | null }>();

  const now = Date.now();
  const memberships = [];
  for (const m of session.memberships) {
    const table = m.tenantKind === "company" ? "companies" : "homes";
    const cols = m.tenantKind === "company"
      ? "name, entitlement, entitlement_expires_at, trial_ends_at, settings, phone, email"
      : "name, entitlement, entitlement_expires_at, NULL AS trial_ends_at, NULL AS settings, NULL AS phone, NULL AS email";
    const row = await c.env.DB.prepare(`SELECT ${cols} FROM ${table} WHERE id = ?`)
      .bind(m.tenantId)
      .first<{ name: string; entitlement: string | null; entitlement_expires_at: number | null; trial_ends_at: number | null; settings: string | null; phone: string | null; email: string | null }>();
    if (!row) continue;
    memberships.push({
      kind: m.tenantKind,
      id: m.tenantId,
      role: m.role,
      name: row.name,
      // AC-B50: only an entitlement still in force; a lapsed one reads as none so every phone gates alike
      entitlement: activeEntitlement(row, now),
      entitlementExpiresAt: activeEntitlement(row, now) ? row.entitlement_expires_at : null,
      trialEndsAt: row.trial_ends_at,
      ...(m.tenantKind === "company"
        ? {
            plan: companyPlan(row, now),
            memberLimit: memberLimit(companyPlan(row, now)),
            settings: { ...DEFAULT_SETTINGS, ...JSON.parse(row.settings ?? "{}") },
            phone: row.phone,
            email: m.role === "owner" || m.role === "office" ? row.email : null,
          }
        : {}),
    });
  }
  return c.json({ user, memberships });
});

const TokenBody = z.object({ token: z.string().min(1).max(4096), app: z.enum(["field-log", "house-manual"]) });

/**
 * Registers this phone's FCM token for the caller (AC-B30). A token signed in to another account before moves to
 * this one, so a shared phone only hears about the account signed in on it now.
 */
me.put("/push-token", async (c) => {
  const parsed = TokenBody.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "invalid" }, 400);
  await c.env.DB.prepare(
    `INSERT INTO push_tokens (token, user_id, app, updated_at) VALUES (?, ?, ?, ?)
     ON CONFLICT (token) DO UPDATE SET user_id = excluded.user_id, app = excluded.app, updated_at = excluded.updated_at`,
  )
    .bind(parsed.data.token, c.get("session").userId, parsed.data.app, Date.now())
    .run();
  return c.body(null, 204);
});

/** Forgets this phone's token on sign out; only the caller's own token can be removed. */
me.delete("/push-token", async (c) => {
  const parsed = z.object({ token: z.string().min(1).max(4096) }).safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "invalid" }, 400);
  await c.env.DB.prepare("DELETE FROM push_tokens WHERE token = ? AND user_id = ?").bind(parsed.data.token, c.get("session").userId).run();
  return c.body(null, 204);
});

/**
 * T081d (Play's account deletion rule): deletes the caller's account. Every home and company the caller owns goes
 * with all its rows and files, as delete cloud copy does; memberships elsewhere end (the records they made stay with
 * that home or company); then the user's push tokens, push log, lookup log and quota, sign-in code, handoffs it claimed
 * (and their bundles), invites it accepted, and the user row. Every token of the account answers 401 afterwards. Answers how many
 * homes and companies went.
 */
me.delete("/", async (c) => {
  const session = c.get("session");
  const db = c.env.DB;
  const user = await db.prepare("SELECT email FROM users WHERE id = ?").bind(session.userId).first<{ email: string }>();
  let homes = 0;
  let companies = 0;
  for (const m of session.memberships) {
    if (m.role !== "owner") continue;
    await deleteTenant(c.env, m.tenantKind, m.tenantId);
    if (m.tenantKind === "home") homes++;
    else companies++;
  }
  const userId = session.userId;
  // A handoff the caller claimed is bound to this account only; it and its bundle go rather than becoming claimable again.
  const claimed = await db.prepare("SELECT object_key FROM handoffs WHERE claimed_by = ?").bind(userId).all<{ object_key: string }>();
  if (claimed.results.length > 0) await c.env.MEDIA.delete(claimed.results.map((r) => r.object_key));
  await db.batch([
    db.prepare("DELETE FROM memberships WHERE user_id = ?").bind(userId),
    db.prepare("DELETE FROM push_tokens WHERE user_id = ?").bind(userId),
    db.prepare("DELETE FROM push_log WHERE user_id = ?").bind(userId),
    db.prepare("DELETE FROM lookup_log WHERE user_id = ?").bind(userId),
    db.prepare("DELETE FROM lookup_quota WHERE user_id = ?").bind(userId),
    db.prepare("DELETE FROM task_seen WHERE member_id = ?").bind(userId),
    db.prepare("UPDATE media_objects SET owner_user_id = NULL WHERE owner_user_id = ?").bind(userId),
    db.prepare("DELETE FROM handoffs WHERE claimed_by = ?").bind(userId),
    db.prepare("DELETE FROM home_invites WHERE accepted_by = ?").bind(userId),
    db.prepare("DELETE FROM email_codes WHERE email = ?").bind(user?.email ?? ""),
    db.prepare("DELETE FROM users WHERE id = ?").bind(userId),
  ]);
  c.set("sessionEnded", true);
  return c.json({ deleted: true, homesRemoved: homes, companiesRemoved: companies });
});
