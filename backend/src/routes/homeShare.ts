import { Hono, type Context } from "hono";
import { z } from "zod";
import type { AppEnv } from "../env";
import { membershipFor, notFound, requireSession, requireTenant } from "../tenancy";
import { newToken } from "./handoff";

/**
 * HM-Share (AC-H16): the owner of a home invites family as editors and a buyer or house-sitter as a viewer, one link
 * per person. The link opens a small page that hands the token to House Manual (or Google Play when it is not
 * installed); after sign-in the app accepts it, the account becomes a member of the home and its normal sync pulls
 * the home down. Editors edit the whole home; viewers read it and the server refuses their writes (sync push and
 * uploads). The owner sees everyone, removes people and revokes unused links; anyone else may leave.
 */
export const homeShare = new Hono<AppEnv>();

export const INVITE_DAYS = 14;
export const HOME_ROLES = ["editor", "viewer"] as const;
const TOKEN = /^[A-Za-z0-9_-]{32,64}$/;
const ID = /^[A-Za-z0-9._-]{1,64}$/;

/** A viewer may read a home but never change it (AC-H16). */
export const readOnlyHomeRole = (role: string | undefined): boolean => role === "viewer";

export const devInviteKey = (ownerEmail: string) => `devinvite:${ownerEmail}`;

const InviteBody = z.object({ role: z.enum(HOME_ROLES) });

async function inviteHash(token: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`home-invite:${token}`));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

interface InviteRow {
  id: string;
  home_id: string;
  role: "editor" | "viewer";
  created_by: string;
  expires_at: number;
  accepted_by: string | null;
}

async function findInvite(db: D1Database, token: string): Promise<InviteRow | null> {
  if (!TOKEN.test(token)) return null;
  return db
    .prepare("SELECT id, home_id, role, created_by, expires_at, accepted_by FROM home_invites WHERE token_hash = ?")
    .bind(await inviteHash(token))
    .first<InviteRow>();
}

// ---- Owner: make, list and revoke links; list and remove people ----

homeShare.post("/:kind{homes}/:tenantId/invites", requireSession, requireTenant(["owner"]), async (c) => {
  const parsed = InviteBody.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "invalid" }, 400);
  const homeId = c.req.param("tenantId");
  const now = Date.now();
  const id = crypto.randomUUID();
  const token = newToken();
  const expiresAt = now + INVITE_DAYS * 86_400_000;
  await c.env.DB.prepare(
    "INSERT INTO home_invites (id, token_hash, home_id, role, created_by, created_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
  )
    .bind(id, await inviteHash(token), homeId, parsed.data.role, c.get("session").userId, now, expiresAt)
    .run();
  if (c.env.ENVIRONMENT === "dev") {
    // Flows join with the link the owner just made without reading a share sheet (/dev/last-invite, Q005).
    const owner = await c.env.DB.prepare("SELECT email FROM users WHERE id = ?").bind(c.get("session").userId).first<{ email: string }>();
    if (owner) await c.env.CACHE.put(devInviteKey(owner.email), token, { expirationTtl: INVITE_DAYS * 86_400 });
  }
  return c.json({ id, role: parsed.data.role, link: `${new URL(c.req.url).origin}/invite/${token}`, token, expiresAt }, 201);
});

homeShare.delete("/:kind{homes}/:tenantId/invites/:inviteId", requireSession, requireTenant(["owner"]), async (c) => {
  const inviteId = c.req.param("inviteId");
  if (!ID.test(inviteId)) return notFound(c);
  const removed = await c.env.DB.prepare("DELETE FROM home_invites WHERE id = ? AND home_id = ? AND accepted_by IS NULL")
    .bind(inviteId, c.req.param("tenantId"))
    .run();
  return removed.meta.changes === 1 ? c.json({ ok: true }) : notFound(c);
});

/** Everyone on the home, owner first; the owner also gets the open links (unused, unexpired). */
homeShare.get("/:kind{homes}/:tenantId/people", requireSession, requireTenant(), async (c) => {
  const homeId = c.req.param("tenantId");
  const session = c.get("session");
  const role = membershipFor(session, "home", homeId)?.role;
  const { results } = await c.env.DB.prepare(
    `SELECT m.user_id, m.role, u.name, u.email FROM memberships m JOIN users u ON u.id = m.user_id
     WHERE m.tenant_kind = 'home' AND m.tenant_id = ?
     ORDER BY CASE m.role WHEN 'owner' THEN 0 WHEN 'editor' THEN 1 WHEN 'member' THEN 1 ELSE 2 END, m.created_at`,
  )
    .bind(homeId)
    .all<{ user_id: string; role: string; name: string | null; email: string }>();
  const people = results.map((r) => ({
    userId: r.user_id,
    name: r.name,
    email: r.email,
    // 'member' is the pre-sharing name of an editor
    role: r.role === "member" ? "editor" : r.role,
    you: r.user_id === session.userId,
  }));
  let invites: { id: string; role: string; createdAt: number; expiresAt: number }[] = [];
  if (role === "owner") {
    const open = await c.env.DB.prepare(
      "SELECT id, role, created_at, expires_at FROM home_invites WHERE home_id = ? AND accepted_by IS NULL AND expires_at > ? ORDER BY created_at",
    )
      .bind(homeId, Date.now())
      .all<{ id: string; role: string; created_at: number; expires_at: number }>();
    invites = open.results.map((r) => ({ id: r.id, role: r.role, createdAt: r.created_at, expiresAt: r.expires_at }));
  }
  return c.json({ people, invites });
});

/** The owner removes someone; anyone else may remove only themselves (leave). The owner cannot be removed. */
homeShare.delete("/:kind{homes}/:tenantId/people/:userId", requireSession, requireTenant(), async (c) => {
  const homeId = c.req.param("tenantId");
  const target = c.req.param("userId");
  const session = c.get("session");
  const callerRole = membershipFor(session, "home", homeId)?.role;
  if (callerRole !== "owner" && target !== session.userId) return notFound(c);
  const removed = await c.env.DB.prepare(
    "DELETE FROM memberships WHERE tenant_kind = 'home' AND tenant_id = ? AND user_id = ? AND role <> 'owner'",
  )
    .bind(homeId, target)
    .run();
  return removed.meta.changes === 1 ? c.json({ ok: true }) : notFound(c);
});

// ---- The link ----

const esc = (s: unknown): string =>
  String(s ?? "").replace(/[&<>"']/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[ch] ?? ch);

const STYLE = `
:root{--bg:#fbf8f4;--text:#1d1b19;--muted:#5f5a54;--accent:#8a4b1f;--onaccent:#fff}
@media (prefers-color-scheme:dark){:root{--bg:#171513;--text:#ece6df;--muted:#b3aaa0;--accent:#f0b48a;--onaccent:#3b1d06}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:16px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:560px;margin:0 auto;padding:32px 16px 48px}h1{font-size:1.6rem;line-height:1.25;margin:4px 0 8px}.muted{color:var(--muted)}
a.button{display:inline-flex;align-items:center;min-height:48px;padding:0 24px;border-radius:999px;background:var(--accent);color:var(--onaccent);font-weight:600;text-decoration:none;margin-top:16px}`;

function page(c: Context, status: 200 | 404 | 410, title: string, body: string) {
  return c.html(
    `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">` +
      `<meta name="robots" content="noindex"><title>${esc(title)}</title><style>${STYLE}</style></head><body><main>${body}</main></body></html>`,
    status,
    { "Cache-Control": "no-store", "Referrer-Policy": "no-referrer", "X-Robots-Tag": "noindex" },
  );
}

/** Opens House Manual at the invite; without the app installed, Android sends the person to its store page. */
export const inviteAppLink = (token: string) =>
  `intent://invite/${token}#Intent;scheme=homerecord;package=com.kurtz.homerecord.housemanual;end`;

const notWorking = (c: Context) =>
  page(c, 404, "Link not found", `<h1>This link doesn't work.</h1><p class="muted">It may have been used already or turned off. Ask for a new one.</p>`);

homeShare.get("/invite/:token", async (c) => {
  const token = c.req.param("token");
  const row = await findInvite(c.env.DB, token);
  if (!row || row.accepted_by !== null) return notWorking(c);
  const home = await c.env.DB.prepare("SELECT name FROM homes WHERE id = ?").bind(row.home_id).first<{ name: string }>();
  if (!home) return notWorking(c);
  if (row.expires_at <= Date.now()) {
    return page(c, 410, "Link expired", `<h1>This link has expired.</h1><p class="muted">Ask for a new link to ${esc(home.name)}.</p>`);
  }
  const what = row.role === "editor"
    ? "You can see and update everything in it: rooms, items, warranties, documents and reminders."
    : "You can see everything in it: rooms, items, warranties, documents and shutoffs. You can't change anything.";
  return page(
    c, 200, home.name,
    `<p class="muted">House Manual</p><h1>You're invited to ${esc(home.name)}</h1><p>${what}</p>` +
      `<a class="button" href="${esc(inviteAppLink(token))}">Open in House Manual</a>` +
      `<p class="muted">Opening it on a phone without House Manual takes you to Google Play first.</p>`,
  );
});

/**
 * Accepts the invite for the signed-in account: it becomes an editor or viewer of the home. Taking your own link again
 * (or any link of a home you are already on) answers your current role and uses nothing up; a link someone else took
 * answers 409, an expired one 410.
 */
homeShare.post("/invite/:token/accept", requireSession, async (c) => {
  const db = c.env.DB;
  const row = await findInvite(db, c.req.param("token"));
  if (!row) return notFound(c);
  const session = c.get("session");
  const home = await db.prepare("SELECT name FROM homes WHERE id = ?").bind(row.home_id).first<{ name: string }>();
  if (!home) return notFound(c);
  const existing = membershipFor(session, "home", row.home_id);
  if (existing) return c.json({ homeId: row.home_id, homeName: home.name, role: existing.role === "member" ? "editor" : existing.role });
  if (row.accepted_by !== null) return c.json({ error: "used" }, 409);
  if (row.expires_at <= Date.now()) return c.json({ error: "expired" }, 410);
  const now = Date.now();
  const taken = await db.prepare("UPDATE home_invites SET accepted_by = ?, accepted_at = ? WHERE id = ? AND accepted_by IS NULL")
    .bind(session.userId, now, row.id)
    .run();
  if (taken.meta.changes !== 1) return c.json({ error: "used" }, 409);
  await db.prepare(
    "INSERT INTO memberships (id, user_id, tenant_kind, tenant_id, role, created_at) VALUES (?, ?, 'home', ?, ?, ?) ON CONFLICT DO NOTHING",
  )
    .bind(crypto.randomUUID(), session.userId, row.home_id, row.role, now)
    .run();
  return c.json({ homeId: row.home_id, homeName: home.name, role: row.role }, 201);
});

/** Daily cron: expired links that nobody used go. */
export async function pruneHomeInvites(db: D1Database, now = Date.now()): Promise<void> {
  await db.prepare("DELETE FROM home_invites WHERE expires_at <= ? AND accepted_by IS NULL").bind(now).run();
}
