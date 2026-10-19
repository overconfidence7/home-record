import { Hono } from "hono";
import { z } from "zod";
import type { AppEnv } from "../env";
import { notFound, requireSession, requireTenant, membershipFor } from "../tenancy";

export const company = new Hono<AppEnv>();

const TRIAL_DAYS = 14;
const ROLES = ["owner", "office", "lead", "crew", "sub"] as const;
const ADMIN = ["owner", "office"];
// No 0/O/1/I so a code read aloud on a job site is unambiguous.
const CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

export function newInviteCode(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(8));
  return Array.from(bytes, (b) => CODE_ALPHABET[b % CODE_ALPHABET.length]).join("");
}

function normalizeCode(raw: string): string {
  return raw.replace(/[\s-]/g, "").toUpperCase();
}

async function ensureInvite(db: D1Database, companyId: string, rotate = false): Promise<string> {
  if (!rotate) {
    const row = await db.prepare("SELECT code FROM company_invites WHERE company_id = ?").bind(companyId).first<{ code: string }>();
    if (row) return row.code;
  }
  for (let i = 0; i < 5; i++) {
    const code = newInviteCode();
    try {
      await db
        .prepare("INSERT INTO company_invites (company_id, code, created_at) VALUES (?, ?, ?) ON CONFLICT(company_id) DO UPDATE SET code = excluded.code, created_at = excluded.created_at")
        .bind(companyId, code, Date.now())
        .run();
      return code;
    } catch {
      // code collided with another company's; draw again
    }
  }
  throw new Error("invite code generation failed");
}

const createBody = z.object({
  name: z.string().trim().min(1).max(120),
  trade: z.string().trim().max(80).optional(),
  phone: z.string().trim().max(40).optional(),
});

/** AC-F1: the creator becomes Owner and the 14-day trial starts (AC-F56). */
company.post("/companies", requireSession, async (c) => {
  const parsed = createBody.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "invalid" }, 400);
  const session = c.get("session");
  const id = crypto.randomUUID();
  const now = Date.now();
  await c.env.DB.batch([
    c.env.DB.prepare("INSERT INTO companies (id, name, trade, phone, trial_ends_at, created_at) VALUES (?, ?, ?, ?, ?, ?)").bind(
      id, parsed.data.name, parsed.data.trade ?? null, parsed.data.phone ?? null, now + TRIAL_DAYS * 86400_000, now),
    c.env.DB.prepare("INSERT INTO memberships (id, user_id, tenant_kind, tenant_id, role, created_at) VALUES (?, ?, 'company', ?, 'owner', ?)").bind(
      crypto.randomUUID(), session.userId, id, now),
  ]);
  return c.json({ id, role: "owner" }, 201);
});

/** AC-F2: join with the 8-character code; joining twice is a no-op and keeps the existing role. */
company.post("/companies/join", requireSession, async (c) => {
  const body = z.object({ code: z.string().min(1).max(32) }).safeParse(await c.req.json().catch(() => null));
  if (!body.success) return c.json({ error: "invalid" }, 400);
  const row = await c.env.DB.prepare("SELECT company_id FROM company_invites WHERE code = ?")
    .bind(normalizeCode(body.data.code))
    .first<{ company_id: string }>();
  if (!row) return c.json({ error: "invalid_code" }, 404);
  const session = c.get("session");
  const existing = membershipFor(session, "company", row.company_id);
  if (existing) return c.json({ id: row.company_id, role: existing.role });
  await c.env.DB.prepare("INSERT INTO memberships (id, user_id, tenant_kind, tenant_id, role, created_at) VALUES (?, ?, 'company', ?, 'crew', ?)")
    .bind(crypto.randomUUID(), session.userId, row.company_id, Date.now())
    .run();
  return c.json({ id: row.company_id, role: "crew" }, 201);
});

const settingsSchema = z.object({
  otRule: z.enum(["none", "daily8", "weekly40", "daily8weekly40"]).optional(),
  breakRule: z.boolean().optional(),
  requireGps: z.boolean().optional(),
  punchDistanceFt: z.number().int().min(50).max(52_800).optional(),
  photoOnPunch: z.boolean().optional(),
});

const updateBody = z.object({
  name: z.string().trim().min(1).max(120).optional(),
  trade: z.string().trim().max(80).nullable().optional(),
  phone: z.string().trim().max(40).nullable().optional(),
  email: z.string().trim().max(200).nullable().optional(),
  address: z.string().trim().max(300).nullable().optional(),
  settings: settingsSchema.optional(),
});

const DEFAULT_SETTINGS = { otRule: "none", breakRule: false, requireGps: false, punchDistanceFt: 500, photoOnPunch: false };

/** FL-Company: Owner and Office only (AC-F3); everyone else gets the same 404 as a missing company. */
company.get("/:kind{companies}/:tenantId/profile", requireSession, requireTenant(ADMIN), async (c) => {
  const row = await c.env.DB.prepare("SELECT id, name, trade, phone, email, address, logo_key, settings, trial_ends_at, entitlement FROM companies WHERE id = ?")
    .bind(c.req.param("tenantId"))
    .first<Record<string, unknown> & { settings: string }>();
  if (!row) return notFound(c);
  const { settings, logo_key, trial_ends_at, ...rest } = row;
  return c.json({ ...rest, logoKey: logo_key, trialEndsAt: trial_ends_at, settings: { ...DEFAULT_SETTINGS, ...JSON.parse(settings) } });
});

company.patch("/:kind{companies}/:tenantId/profile", requireSession, requireTenant(ADMIN), async (c) => {
  const parsed = updateBody.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "invalid" }, 400);
  const id = c.req.param("tenantId");
  const current = await c.env.DB.prepare("SELECT settings FROM companies WHERE id = ?").bind(id).first<{ settings: string }>();
  if (!current) return notFound(c);
  const { settings, ...fields } = parsed.data;
  const sets: string[] = [];
  const values: unknown[] = [];
  for (const [k, v] of Object.entries(fields)) {
    sets.push(`${k} = ?`);
    values.push(v);
  }
  if (settings) {
    sets.push("settings = ?");
    values.push(JSON.stringify({ ...JSON.parse(current.settings), ...settings }));
  }
  if (sets.length) await c.env.DB.prepare(`UPDATE companies SET ${sets.join(", ")} WHERE id = ?`).bind(...values, id).run();
  return c.json({ ok: true });
});

company.get("/:kind{companies}/:tenantId/members", requireSession, requireTenant(ADMIN), async (c) => {
  const caller = membershipFor(c.get("session"), "company", c.req.param("tenantId"));
  const { results } = await c.env.DB.prepare(
    `SELECT m.user_id, m.role, m.rate_cents, u.name, u.email FROM memberships m JOIN users u ON u.id = m.user_id
     WHERE m.tenant_kind = 'company' AND m.tenant_id = ? ORDER BY m.created_at`)
    .bind(c.req.param("tenantId"))
    .all<{ user_id: string; role: string; rate_cents: number | null; name: string | null; email: string }>();
  return c.json({
    members: results.map((r) => ({
      userId: r.user_id,
      name: r.name,
      email: r.email,
      role: r.role,
      // Rates are Owner-only (SPEC FL-Team).
      rateCents: caller?.role === "owner" ? r.rate_cents : null,
    })),
  });
});

company.get("/:kind{companies}/:tenantId/invite", requireSession, requireTenant(ADMIN), async (c) => {
  const code = await ensureInvite(c.env.DB, c.req.param("tenantId"));
  return c.json({ code });
});

company.post("/:kind{companies}/:tenantId/invite/rotate", requireSession, requireTenant(ADMIN), async (c) => {
  const code = await ensureInvite(c.env.DB, c.req.param("tenantId"), true);
  return c.json({ code });
});

const memberBody = z.object({
  role: z.enum(ROLES).optional(),
  rateCents: z.number().int().min(0).max(100_000_00).nullable().optional(),
});

/**
 * Role changes: Owner may set any non-owner role; Office may only move people between lead/crew/sub.
 * The Owner's own membership is immutable here, and rates are Owner-only (AC-F3, AC-B3).
 */
company.patch("/:kind{companies}/:tenantId/members/:userId", requireSession, requireTenant(ADMIN), async (c) => {
  const parsed = memberBody.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "invalid" }, 400);
  const companyId = c.req.param("tenantId");
  const caller = membershipFor(c.get("session"), "company", companyId);
  const target = await c.env.DB.prepare("SELECT role FROM memberships WHERE tenant_kind = 'company' AND tenant_id = ? AND user_id = ?")
    .bind(companyId, c.req.param("userId"))
    .first<{ role: string }>();
  if (!caller || !target) return notFound(c);
  const { role, rateCents } = parsed.data;
  const isOwner = caller.role === "owner";
  if (target.role === "owner") return c.json({ error: "forbidden" }, 403);
  if (!isOwner && (target.role === "office" || role === "owner" || role === "office" || rateCents !== undefined)) {
    return c.json({ error: "forbidden" }, 403);
  }
  if (role === "owner") return c.json({ error: "forbidden" }, 403);
  if (role) await c.env.DB.prepare("UPDATE memberships SET role = ? WHERE tenant_kind = 'company' AND tenant_id = ? AND user_id = ?").bind(role, companyId, c.req.param("userId")).run();
  if (rateCents !== undefined) await c.env.DB.prepare("UPDATE memberships SET rate_cents = ? WHERE tenant_kind = 'company' AND tenant_id = ? AND user_id = ?").bind(rateCents, companyId, c.req.param("userId")).run();
  return c.json({ ok: true });
});

company.delete("/:kind{companies}/:tenantId/members/:userId", requireSession, requireTenant(ADMIN), async (c) => {
  const companyId = c.req.param("tenantId");
  const caller = membershipFor(c.get("session"), "company", companyId);
  const target = await c.env.DB.prepare("SELECT role FROM memberships WHERE tenant_kind = 'company' AND tenant_id = ? AND user_id = ?")
    .bind(companyId, c.req.param("userId"))
    .first<{ role: string }>();
  if (!caller || !target) return notFound(c);
  if (target.role === "owner" || (caller.role !== "owner" && target.role === "office")) return c.json({ error: "forbidden" }, 403);
  await c.env.DB.prepare("DELETE FROM memberships WHERE tenant_kind = 'company' AND tenant_id = ? AND user_id = ?").bind(companyId, c.req.param("userId")).run();
  return c.json({ ok: true });
});

/** Landing page for shared invite links: opens Field Log with the code, or shows the code to type in. */
company.get("/join/:code", (c) => {
  const code = normalizeCode(c.req.param("code"));
  if (!/^[A-Z0-9]{8}$/.test(code)) return notFound(c);
  return c.html(
    `<!doctype html><meta name="viewport" content="width=device-width,initial-scale=1"><title>Join on Field Log</title>` +
      `<body style="font-family:sans-serif;max-width:28rem;margin:2rem auto;padding:0 1rem">` +
      `<h1>Join your crew on Field Log</h1><p>Your invite code:</p>` +
      `<p style="font-size:2rem;letter-spacing:.2em;font-family:monospace">${code}</p>` +
      `<p><a href="fieldlog://join/${code}" style="font-size:1.25rem">Open Field Log</a></p>` +
      `<p>In the app, sign in, pick Join and enter the code if it does not fill in on its own.</p></body>`,
  );
});
