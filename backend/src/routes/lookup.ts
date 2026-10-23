import { Hono } from "hono";
import type { AppEnv } from "../env";
import { requireSession } from "../tenancy";
import {
  CACHE_DAYS, FREE_DAILY_LIMIT, PRO_DAILY_LIMIT, askClaude, requestSchema, resultSchema, warrantyCacheKey,
} from "../lookup/warranty";

export const lookup = new Hono<AppEnv>();
lookup.use("/lookup/*", requireSession);

async function dailyLimit(db: D1Database, userId: string): Promise<number> {
  const pro = await db
    .prepare(
      "SELECT 1 FROM memberships m LEFT JOIN companies c ON m.tenant_kind = 'company' AND c.id = m.tenant_id " +
        "LEFT JOIN homes h ON m.tenant_kind = 'home' AND h.id = m.tenant_id " +
        "WHERE m.user_id = ? AND (c.entitlement = 'pro' OR h.entitlement = 'pro') LIMIT 1",
    )
    .bind(userId)
    .first();
  return pro ? PRO_DAILY_LIMIT : FREE_DAILY_LIMIT;
}

/** Counts every request against the account's UTC-day bucket, cached or not, so the cap is predictable. */
async function takeQuota(env: AppEnv["Bindings"], userId: string): Promise<boolean> {
  const day = new Date().toISOString().slice(0, 10);
  const key = `quota:lookup:${userId}:${day}`;
  const used = Number((await env.CACHE.get(key)) ?? "0");
  if (used >= (await dailyLimit(env.DB, userId))) return false;
  await env.CACHE.put(key, String(used + 1), { expirationTtl: 2 * 86400 });
  return true;
}

lookup.post("/lookup/warranty", async (c) => {
  const parsed = requestSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "bad_request" }, 400);
  const userId = c.get("session").userId;
  if (!(await takeQuota(c.env, userId))) return c.json({ error: "rate_limited" }, 429);

  const key = warrantyCacheKey(parsed.data.make, parsed.data.model);
  const hit = await c.env.CACHE.get(key);
  if (hit) {
    let raw: unknown = null;
    try { raw = JSON.parse(hit); } catch { /* corrupt entry: fall through to a fresh lookup */ }
    const cached = resultSchema.safeParse(raw);
    if (cached.success) return c.json({ ...cached.data, cached: true });
  }
  if (!c.env.CLAUDE_API_KEY) return c.json({ error: "unavailable" }, 503);
  const found = await askClaude(c.env.CLAUDE_API_KEY, parsed.data);
  if (!found) return c.json({ error: "lookup_failed" }, 502);

  await c.env.CACHE.put(key, JSON.stringify(found.result), { expirationTtl: CACHE_DAYS * 86400 });
  await c.env.DB.prepare(
    "INSERT INTO lookup_log (id, user_id, kind, cache_key, input_tokens, output_tokens, searches, created_at) VALUES (?, ?, 'warranty', ?, ?, ?, ?, ?)",
  )
    .bind(crypto.randomUUID(), userId, key, found.usage.inputTokens, found.usage.outputTokens, found.usage.searches, Date.now())
    .run();
  return c.json({ ...found.result, cached: false });
});
