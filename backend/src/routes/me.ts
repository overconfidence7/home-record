import { Hono } from "hono";
import type { AppEnv } from "../env";
import { requireSession } from "../tenancy";
import { DEFAULT_SETTINGS } from "./company";

export const me = new Hono<AppEnv>();
me.use("*", requireSession);

/**
 * Identity, memberships and entitlement so both apps gate consistently (AC-B50 reads these columns).
 * A company membership also carries the clock rules (require GPS, punch distance, break and overtime rule) so every
 * role clocks in under them, not only the Owner and Office who may read the profile (AC-F9, AC-F12, AC-F13, AC-F18).
 */
me.get("/", async (c) => {
  const session = c.get("session");
  const user = await c.env.DB.prepare("SELECT id, email, name FROM users WHERE id = ?")
    .bind(session.userId)
    .first<{ id: string; email: string; name: string | null }>();

  const memberships = [];
  for (const m of session.memberships) {
    const table = m.tenantKind === "company" ? "companies" : "homes";
    const cols = m.tenantKind === "company"
      ? "name, entitlement, trial_ends_at, settings"
      : "name, entitlement, NULL AS trial_ends_at, NULL AS settings";
    const row = await c.env.DB.prepare(`SELECT ${cols} FROM ${table} WHERE id = ?`)
      .bind(m.tenantId)
      .first<{ name: string; entitlement: string | null; trial_ends_at: number | null; settings: string | null }>();
    if (!row) continue;
    memberships.push({
      kind: m.tenantKind,
      id: m.tenantId,
      role: m.role,
      name: row.name,
      entitlement: row.entitlement,
      trialEndsAt: row.trial_ends_at,
      ...(m.tenantKind === "company" ? { settings: { ...DEFAULT_SETTINGS, ...JSON.parse(row.settings ?? "{}") } } : {}),
    });
  }
  return c.json({ user, memberships });
});
