import { Hono } from "hono";
import { z } from "zod";
import type { AppEnv } from "../env";
import { requireSession } from "../tenancy";
import { DEFAULT_SETTINGS } from "./company";
import { activeEntitlement, companyPlan, memberLimit } from "../billing/plan";

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
