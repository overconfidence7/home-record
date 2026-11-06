import { devInviteKey } from "./homeShare";
import { Hono } from "hono";
import { z } from "zod";
import type { AppEnv } from "../env";
import { normalizeEmail } from "../auth/emailCode";
import { devHandoffKey } from "./handoff";

/** QA helpers. Mounted only when ENVIRONMENT=dev; any other value leaves these paths as plain 404s. */
export const dev = new Hono<AppEnv>();

export const devCodeKey = (email: string) => `devcode:${email}`;

const QA_COMPANY_ID = "qa-company";
const QA_USERS = [
  { id: "qa-owner", email: "owner@qa.test", name: "Olive Owner", role: "owner" },
  { id: "qa-crew", email: "crew@qa.test", name: "Casey Crew", role: "crew" },
];

dev.get("/last-code", async (c) => {
  const email = normalizeEmail(c.req.query("email") ?? "");
  if (!email) return c.json({ error: "bad_request" }, 400);
  const code = await c.env.CACHE.get(devCodeKey(email));
  return code ? c.json({ code }) : c.json({ error: "not_found" }, 404);
});

/** The token of the newest handoff sent to this email, for flows that claim it without reading the email. */
dev.get("/last-handoff", async (c) => {
  const email = normalizeEmail(c.req.query("email") ?? "");
  if (!email) return c.json({ error: "bad_request" }, 400);
  const token = await c.env.CACHE.get(devHandoffKey(email));
  return token ? c.json({ token, path: `/handoff/${token}` }) : c.json({ error: "not_found" }, 404);
});

/** The token of the newest home invite link this owner made (AC-H16), for flows that join without a share sheet. */
dev.get("/last-invite", async (c) => {
  const email = normalizeEmail(c.req.query("email") ?? "");
  if (!email) return c.json({ error: "bad_request" }, 400);
  const token = await c.env.CACHE.get(devInviteKey(email));
  return token ? c.json({ token, path: `/invite/${token}` }) : c.json({ error: "not_found" }, 404);
});

/** The newest notifications the Worker sent (or would have, without FCM), for flows that check a push went out. */
dev.get("/pushes", async (c) => {
  const { results } = await c.env.DB.prepare("SELECT user_id, type, data_json, delivered, at FROM push_log ORDER BY id DESC LIMIT 50")
    .all<{ user_id: string; type: string; data_json: string; delivered: number; at: number }>();
  return c.json({
    pushes: results.map((r) => ({ userId: r.user_id, type: r.type, data: JSON.parse(r.data_json), delivered: r.delivered, at: r.at })),
  });
});

/**
 * Moves a company's clock back [days] (default 15) so its 14-day trial has ended (walkthrough F17, AC-F56): the
 * company the email owns gets created_at and trial_ends_at that much earlier. A paid entitlement is left alone.
 */
dev.post("/age-company", async (c) => {
  const parsed = z
    .object({ email: z.string().min(3), days: z.number().int().min(1).max(365).default(15) })
    .safeParse(await c.req.json().catch(() => ({})));
  if (!parsed.success) return c.json({ error: "bad_request" }, 400);
  const email = normalizeEmail(parsed.data.email);
  const row = await c.env.DB.prepare(
    `SELECT m.tenant_id AS company_id FROM memberships m JOIN users u ON u.id = m.user_id
     WHERE u.email = ? AND m.tenant_kind = 'company' AND m.role = 'owner' LIMIT 1`,
  ).bind(email).first<{ company_id: string }>();
  if (!row) return c.json({ error: "not_found" }, 404);
  const shift = parsed.data.days * 86_400_000;
  await c.env.DB.prepare("UPDATE companies SET created_at = created_at - ?, trial_ends_at = trial_ends_at - ? WHERE id = ?")
    .bind(shift, shift, row.company_id)
    .run();
  const company = await c.env.DB.prepare("SELECT trial_ends_at FROM companies WHERE id = ?").bind(row.company_id).first<{ trial_ends_at: number }>();
  return c.json({ ok: true, companyId: row.company_id, trialEndsAt: company?.trial_ends_at ?? null });
});

dev.post("/reset", async (c) => {
  await resetAll(c.env);
  return c.json({ ok: true });
});

dev.post("/seed", async (c) => {
  const parsed = z.object({ scenario: z.string().default("regression") }).safeParse(await c.req.json().catch(() => ({})));
  if (!parsed.success || parsed.data.scenario !== "regression") return c.json({ error: "bad_request" }, 400);
  await resetAll(c.env);
  const now = Date.now();
  const db = c.env.DB;
  await db
    .prepare("INSERT INTO companies (id, name, trade, trial_ends_at, created_at) VALUES (?, 'QA Electric', 'Electrical', ?, ?)")
    .bind(QA_COMPANY_ID, now + 14 * 86_400_000, now)
    .run();
  for (const u of QA_USERS) {
    await db.prepare("INSERT INTO users (id, email, name, created_at) VALUES (?, ?, ?, ?)").bind(u.id, u.email, u.name, now).run();
    await db
      .prepare("INSERT INTO memberships (id, user_id, tenant_kind, tenant_id, role, created_at) VALUES (?, ?, 'company', ?, ?, ?)")
      .bind(`m-${QA_COMPANY_ID}-${u.id}`, u.id, QA_COMPANY_ID, u.role, now)
      .run();
  }
  return c.json({ ok: true, companyId: QA_COMPANY_ID, members: QA_USERS.map((u) => ({ email: u.email, role: u.role })) });
});

async function resetAll(env: AppEnv["Bindings"]) {
  const tables = [
    "billing_events", "handoffs", "home_invites", "push_tokens", "push_log", "sync_conflicts", "sync_applied", "sync_rows", "sync_counters", "company_invites",
    "memberships", "email_codes", "auth_limits", "media_objects", "lookup_log", "lookup_quota", "companies", "homes", "users",
  ];
  await env.DB.batch(tables.map((t) => env.DB.prepare(`DELETE FROM ${t}`)));
  // Codes and lookup quotas; cached lookup answers stay (they belong to no account).
  for (const prefix of ["devcode:", "devhandoff:", "devinvite:", "quota:"]) {
    let cursor: string | undefined;
    do {
      const page = await env.CACHE.list({ prefix, cursor });
      await Promise.all(page.keys.map((k) => env.CACHE.delete(k.name)));
      cursor = page.list_complete ? undefined : page.cursor;
    } while (cursor);
  }
  let after: string | undefined;
  do {
    const page = await env.MEDIA.list({ cursor: after });
    if (page.objects.length > 0) await env.MEDIA.delete(page.objects.map((o) => o.key));
    after = page.truncated ? page.cursor : undefined;
  } while (after);
}
