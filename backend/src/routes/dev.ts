import { Hono } from "hono";
import { z } from "zod";
import type { AppEnv } from "../env";
import { normalizeEmail } from "../auth/emailCode";

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
    "sync_conflicts", "sync_applied", "sync_rows", "sync_counters", "company_invites",
    "memberships", "email_codes", "companies", "homes", "users",
  ];
  await env.DB.batch(tables.map((t) => env.DB.prepare(`DELETE FROM ${t}`)));
  let cursor: string | undefined;
  do {
    const page = await env.CACHE.list({ prefix: "devcode:", cursor });
    await Promise.all(page.keys.map((k) => env.CACHE.delete(k.name)));
    cursor = page.list_complete ? undefined : page.cursor;
  } while (cursor);
}
