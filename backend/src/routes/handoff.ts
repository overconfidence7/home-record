import { Hono, type Context } from "hono";
import { z } from "zod";
import type { AppEnv } from "../env";
import { normalizeEmail } from "../auth/emailCode";
import { membershipFor, requireSession } from "../tenancy";
import { HANDOFF_PER_COMPANY, HANDOFF_PER_EMAIL, hit, limitsApply } from "../auth/rateLimit";

/**
 * Handoff to the homeowner (AC-B40, AC-F51). Owner, Office or a Lead sends the job's `.homerecord` bundle (the same
 * file FL-Handoff exports) with the homeowner's email: the bundle goes to R2 and the homeowner gets a claim link.
 * The link opens a small page that hands the token to House Manual. Claiming needs a signed-in House Manual account
 * and binds the handoff to it; that account then downloads the bundle and imports it with the usual preview and
 * conflicts (AC-H17), and its normal sync copies the home into its own home tenant (AC-H15). Links expire after
 * 30 days and the daily cron removes the expired bundles.
 */
export const handoff = new Hono<AppEnv>();

export const HANDOFF_DAYS = 30;
/** Cloudflare's request body cap on the Free and Pro plans; a larger home goes by the exported file instead. */
export const MAX_BUNDLE_BYTES = 100 * 1024 * 1024;
const SENDERS = new Set(["owner", "office", "lead"]);
const TOKEN = /^[A-Za-z0-9_-]{32,64}$/;
const ID = /^[A-Za-z0-9._-]{1,64}$/;
export const devHandoffKey = (email: string) => `devhandoff:${email}`;

const SendQuery = z.object({
  companyId: z.string().regex(ID),
  jobId: z.string().regex(ID).optional(),
  email: z.string().trim().email().max(254),
  homeName: z.string().trim().min(1).max(120),
});

interface HandoffRow {
  id: string;
  company_id: string;
  home_name: string;
  email: string;
  object_key: string;
  size: number;
  expires_at: number;
  claimed_by: string | null;
  claimed_at: number | null;
}

export function newToken(): string {
  const bytes = crypto.getRandomValues(new Uint8Array(32));
  return btoa(String.fromCharCode(...bytes)).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

export async function tokenHash(token: string): Promise<string> {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(`handoff:${token}`));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function findHandoff(db: D1Database, token: string): Promise<HandoffRow | null> {
  if (!TOKEN.test(token)) return null;
  return db
    .prepare("SELECT id, company_id, home_name, email, object_key, size, expires_at, claimed_by, claimed_at FROM handoffs WHERE token_hash = ?")
    .bind(await tokenHash(token))
    .first<HandoffRow>();
}

const dayText = (ms: number) => new Intl.DateTimeFormat("en-US", { month: "long", day: "numeric", year: "numeric", timeZone: "UTC" }).format(ms);

/** The claim email through Resend. Without a key: logged in dev, refused in production, skipped in tests. */
export async function sendClaimEmail(
  env: { RESEND_API_KEY?: string; ENVIRONMENT: string },
  to: string, companyName: string, homeName: string, link: string, expiresAt: number,
): Promise<boolean> {
  const from = companyName || "Your contractor";
  const text =
    `${from} finished work on ${homeName} and sent you the record of it: the rooms, the photos of what is inside the walls, ` +
    `the items they installed and their documents.\n\nOpen this link on your phone to add it to House Manual:\n${link}\n\n` +
    `The link works until ${dayText(expiresAt)}. If you did not expect this, you can ignore it.`;
  if (!env.RESEND_API_KEY) {
    if (env.ENVIRONMENT === "dev") console.log(`[dev] handoff link for ${to}: ${link}`);
    return env.ENVIRONMENT !== "production";
  }
  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${env.RESEND_API_KEY}`, "Content-Type": "application/json" },
    body: JSON.stringify({ from: "HomeRecord <handoff@homerecord.app>", to: [to], subject: `${from} sent you the record of ${homeName}`, text }),
  });
  return res.ok;
}

/** Zip files start with a local file header, "PK\x03\x04". */
async function looksLikeZip(bucket: R2Bucket, key: string): Promise<boolean> {
  const head = await bucket.get(key, { range: { offset: 0, length: 4 } });
  if (!head) return false;
  const b = new Uint8Array(await head.arrayBuffer());
  return b.length === 4 && b[0] === 0x50 && b[1] === 0x4b && b[2] === 0x03 && b[3] === 0x04;
}

// ---- Send (Field Log) ----

handoff.post("/handoff", requireSession, async (c) => {
  const parsed = SendQuery.safeParse(c.req.query());
  if (!parsed.success) return c.json({ error: "bad_request" }, 400);
  const { companyId, jobId, homeName } = parsed.data;
  const email = normalizeEmail(parsed.data.email);
  const session = c.get("session");
  const member = membershipFor(session, "company", companyId);
  if (!member) return c.json({ error: "not_found" }, 404);
  if (!SENDERS.has(member.role)) return c.json({ error: "forbidden" }, 403);
  const length = Number(c.req.header("Content-Length") ?? "NaN");
  if (!Number.isInteger(length) || length <= 0) return c.json({ error: "length_required" }, 411);
  if (length > MAX_BUNDLE_BYTES) return c.json({ error: "too_large", maxBytes: MAX_BUNDLE_BYTES }, 413);
  // [r12:handoff-rate-limit]: counted before the body is read, per sending company and per homeowner address.
  if (limitsApply(c.env)) {
    const [companyOk, emailOk] = await Promise.all([
      hit(c.env.DB, HANDOFF_PER_COMPANY, companyId),
      hit(c.env.DB, HANDOFF_PER_EMAIL, email),
    ]);
    if (!companyOk || !emailOk) return c.json({ error: "rate_limited" }, 429);
  }
  const body = c.req.raw.body;
  if (!body) return c.json({ error: "bad_request" }, 400);

  const id = crypto.randomUUID();
  const key = `company/${companyId}/handoff/${id}.homerecord`;
  const sized = new FixedLengthStream(length);
  const piping = body.pipeTo(sized.writable).catch(() => undefined);
  try {
    await c.env.MEDIA.put(key, sized.readable, { httpMetadata: { contentType: "application/zip" } });
  } catch {
    return c.json({ error: "bad_request" }, 400);
  }
  await piping;
  if (!(await looksLikeZip(c.env.MEDIA, key))) {
    await c.env.MEDIA.delete(key);
    return c.json({ error: "not_a_bundle" }, 400);
  }

  const now = Date.now();
  const expiresAt = now + HANDOFF_DAYS * 86_400_000;
  const token = newToken();
  try {
    await c.env.DB.prepare(
      `INSERT INTO handoffs (id, token_hash, company_id, job_id, home_name, email, object_key, size, created_by, created_at, expires_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    )
      .bind(id, await tokenHash(token), companyId, jobId ?? null, homeName, email, key, length, session.userId, now, expiresAt)
      .run();
  } catch {
    // No row points at the bundle, so the cron would never remove it: delete it now.
    await c.env.MEDIA.delete(key);
    return c.json({ error: "server_error" }, 500);
  }
  const company = await c.env.DB.prepare("SELECT name FROM companies WHERE id = ?").bind(companyId).first<{ name: string }>();
  const link = `${new URL(c.req.url).origin}/handoff/${token}`;
  if (c.env.ENVIRONMENT === "dev") await c.env.CACHE.put(devHandoffKey(email), token, { expirationTtl: HANDOFF_DAYS * 86_400 });
  if (!(await sendClaimEmail(c.env, email, company?.name ?? "", homeName, link, expiresAt))) {
    await c.env.DB.prepare("DELETE FROM handoffs WHERE id = ?").bind(id).run();
    await c.env.MEDIA.delete(key);
    return c.json({ error: "send_failed" }, 502);
  }
  return c.json({ id, email, expiresAt, size: length }, 201);
});

// ---- The link in the email ----

const esc = (s: unknown): string =>
  String(s ?? "").replace(/[&<>"']/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[ch] ?? ch);

const STYLE = `
:root{--bg:#fbf8f4;--surface:#fff;--text:#1d1b19;--muted:#5f5a54;--line:#e4ded6;--accent:#8a4b1f;--onaccent:#fff}
@media (prefers-color-scheme:dark){:root{--bg:#171513;--surface:#221f1c;--text:#ece6df;--muted:#b3aaa0;--line:#3a3530;--accent:#f0b48a;--onaccent:#3b1d06}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:16px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:560px;margin:0 auto;padding:32px 16px 48px}h1{font-size:1.6rem;line-height:1.25;margin:4px 0 8px}.muted{color:var(--muted)}
a.button{display:inline-flex;align-items:center;min-height:48px;padding:0 24px;border-radius:999px;background:var(--accent);color:var(--onaccent);font-weight:600;text-decoration:none;margin-top:16px}`;

const PAGE_HEADERS = { "Cache-Control": "no-store", "Referrer-Policy": "no-referrer", "X-Robots-Tag": "noindex" };

function page(c: Context, status: 200 | 404 | 410, title: string, body: string) {
  return c.html(
    `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">` +
      `<meta name="robots" content="noindex"><title>${esc(title)}</title><style>${STYLE}</style></head><body><main>${body}</main></body></html>`,
    status,
    PAGE_HEADERS,
  );
}

/** Opens House Manual at the claim screen; without the app installed, Android sends the person to its store page. */
export const appLink = (token: string) =>
  `intent://handoff/${token}#Intent;scheme=homerecord;package=com.kurtz.homerecord.housemanual;end`;

handoff.get("/handoff/:token", async (c) => {
  const token = c.req.param("token");
  const row = await findHandoff(c.env.DB, token);
  if (!row) return page(c, 404, "Link not found", `<h1>This link doesn't work.</h1><p class="muted">Check that the whole link was copied, or ask your contractor to send it again.</p>`);
  const company = await c.env.DB.prepare("SELECT name FROM companies WHERE id = ?").bind(row.company_id).first<{ name: string }>();
  const from = company?.name || "Your contractor";
  if (row.expires_at <= Date.now()) {
    return page(c, 410, "Link expired", `<h1>This link has expired.</h1><p class="muted">Ask ${esc(from)} to send the record of ${esc(row.home_name)} again.</p>`);
  }
  return page(
    c, 200, `Record of ${row.home_name}`,
    `<p class="muted">${esc(from)}</p><h1>The record of ${esc(row.home_name)}</h1>` +
      `<p>Rooms, photos of what is inside the walls, the items installed and their documents. Add it to House Manual to keep it with your home.</p>` +
      `<a class="button" href="${esc(appLink(token))}">Open in House Manual</a>` +
      `<p class="muted">The link works until ${esc(dayText(row.expires_at))}. Opening it on a phone without House Manual takes you to Google Play first.</p>`,
  );
});

// ---- Claim and download (House Manual) ----

handoff.post("/handoff/:token/claim", requireSession, async (c) => {
  const db = c.env.DB;
  const token = c.req.param("token");
  const row = await findHandoff(db, token);
  if (!row) return c.json({ error: "not_found" }, 404);
  if (row.expires_at <= Date.now()) return c.json({ error: "expired" }, 410);
  const userId = c.get("session").userId;
  if (row.claimed_by === null) {
    const now = Date.now();
    const claimed = await db.prepare("UPDATE handoffs SET claimed_by = ?, claimed_at = ? WHERE id = ? AND claimed_by IS NULL")
      .bind(userId, now, row.id)
      .run();
    if (claimed.meta.changes === 1) {
      row.claimed_by = userId;
      row.claimed_at = now;
    } else {
      row.claimed_by = (await db.prepare("SELECT claimed_by FROM handoffs WHERE id = ?").bind(row.id).first<{ claimed_by: string }>())?.claimed_by ?? null;
    }
  }
  if (row.claimed_by !== userId) return c.json({ error: "claimed" }, 409);
  const company = await db.prepare("SELECT name FROM companies WHERE id = ?").bind(row.company_id).first<{ name: string }>();
  return c.json({
    homeName: row.home_name,
    companyName: company?.name ?? "",
    size: row.size,
    claimedAt: row.claimed_at,
    expiresAt: row.expires_at,
    bundlePath: `/handoff/${token}/bundle`,
  });
});

handoff.get("/handoff/:token/bundle", requireSession, async (c) => {
  const row = await findHandoff(c.env.DB, c.req.param("token"));
  if (!row || row.claimed_by !== c.get("session").userId) return c.json({ error: "not_found" }, 404);
  if (row.expires_at <= Date.now()) return c.json({ error: "expired" }, 410);
  const object = await c.env.MEDIA.get(row.object_key);
  if (!object) return c.json({ error: "not_found" }, 404);
  const name = `${row.home_name.replace(/[^A-Za-z0-9 ._-]/g, "").trim() || "Home"}.homerecord`;
  return new Response(object.body, {
    headers: {
      "Content-Type": "application/zip",
      "Content-Length": String(object.size),
      "Content-Disposition": `attachment; filename="${name}"`,
      "Cache-Control": "no-store",
    },
  });
});

/** Daily cron: expired handoffs lose their bundle and their row, claimed or not. */
export async function pruneHandoffs(env: AppEnv["Bindings"], now = Date.now()): Promise<number> {
  const { results } = await env.DB.prepare("SELECT id, object_key FROM handoffs WHERE expires_at <= ? LIMIT 500")
    .bind(now)
    .all<{ id: string; object_key: string }>();
  if (results.length === 0) return 0;
  await env.MEDIA.delete(results.map((r) => r.object_key));
  await env.DB.batch(results.map((r) => env.DB.prepare("DELETE FROM handoffs WHERE id = ?").bind(r.id)));
  return results.length;
}
