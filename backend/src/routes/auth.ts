import { Hono } from "hono";
import { z } from "zod";
import type { AppEnv } from "../env";
import { issueToken } from "../session";
import { CODE_MINUTES, checkCode, generateCode, hashCode, normalizeEmail, sendCode } from "../auth/emailCode";
import { devCodeKey } from "./dev";
import { verifyGoogleIdToken } from "../auth/google";

export const auth = new Hono<AppEnv>();

const emailSchema = z.object({ email: z.string().email() });
const verifySchema = z.object({ email: z.string().email(), code: z.string().regex(/^\d{6}$/) });
const googleSchema = z.object({ idToken: z.string().min(10) });

async function upsertUser(db: D1Database, email: string, name: string | null, googleSub: string | null): Promise<string> {
  const existing = await db.prepare("SELECT id, google_sub FROM users WHERE email = ?").bind(email).first<{ id: string; google_sub: string | null }>();
  if (existing) {
    if (googleSub && !existing.google_sub) {
      await db.prepare("UPDATE users SET google_sub = ? WHERE id = ?").bind(googleSub, existing.id).run();
    }
    return existing.id;
  }
  const id = crypto.randomUUID();
  await db.prepare("INSERT INTO users (id, email, name, google_sub, created_at) VALUES (?, ?, ?, ?, ?)").bind(id, email, name, googleSub, Date.now()).run();
  return id;
}

auth.post("/email/start", async (c) => {
  const parsed = emailSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "bad_request" }, 400);
  const email = normalizeEmail(parsed.data.email);
  const code = generateCode();
  await c.env.DB.prepare(
    "INSERT INTO email_codes (email, code_hash, expires_at, attempts) VALUES (?, ?, ?, 0) " +
      "ON CONFLICT(email) DO UPDATE SET code_hash = excluded.code_hash, expires_at = excluded.expires_at, attempts = 0",
  )
    .bind(email, await hashCode(email, code), Date.now() + CODE_MINUTES * 60_000)
    .run();
  if (c.env.ENVIRONMENT === "dev") await c.env.CACHE.put(devCodeKey(email), code, { expirationTtl: CODE_MINUTES * 60 });
  if (!(await sendCode(c.env, email, code))) return c.json({ error: "send_failed" }, 502);
  return c.json({ ok: true });
});

auth.post("/email/verify", async (c) => {
  const parsed = verifySchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "bad_request" }, 400);
  const email = normalizeEmail(parsed.data.email);
  const result = await checkCode(c.env.DB, email, parsed.data.code);
  if (result !== "ok") return c.json({ error: result === "invalid" ? "invalid_code" : result }, 401);
  const userId = await upsertUser(c.env.DB, email, null, null);
  return c.json({ token: await issueToken(c.env.JWT_SECRET, userId) });
});

auth.post("/google", async (c) => {
  const parsed = googleSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success || !c.env.GOOGLE_CLIENT_ID) return c.json({ error: "bad_request" }, 400);
  const identity = await verifyGoogleIdToken(parsed.data.idToken, c.env.GOOGLE_CLIENT_ID);
  if (!identity) return c.json({ error: "unauthorized" }, 401);
  const userId = await upsertUser(c.env.DB, identity.email, identity.name, identity.sub);
  return c.json({ token: await issueToken(c.env.JWT_SECRET, userId) });
});
