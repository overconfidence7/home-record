import { Hono } from "hono";
import type { AppEnv } from "../env";
import { requireSession } from "../tenancy";
import {
  CACHE_DAYS, FREE_DAILY_LIMIT, PRO_DAILY_LIMIT, askClaude, requestSchema, resultSchema, warrantyCacheKey,
  type UpstreamUsage,
} from "../lookup/warranty";
import { dataPlateRequestSchema, readDataPlate } from "../lookup/dataplate";
import { RECALL_CACHE_SECONDS, fetchRecalls, recallQuerySchema, recallsCacheKey } from "../lookup/recalls";

export const lookup = new Hono<AppEnv>();
lookup.use("/lookup/*", requireSession);
lookup.use("/recalls", requireSession);

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

/** Gives back the request taken by [takeQuota] when the lookup could not answer (502/503), so failures cost nothing. */
async function refundQuota(env: AppEnv["Bindings"], userId: string): Promise<void> {
  const key = `quota:lookup:${userId}:${new Date().toISOString().slice(0, 10)}`;
  const used = Number((await env.CACHE.get(key)) ?? "0");
  if (used > 0) await env.CACHE.put(key, String(used - 1), { expirationTtl: 2 * 86400 });
}

/** AC-B10: one cost row per billed upstream call, whether or not its answer parsed. */
async function logCost(db: D1Database, userId: string, kind: string, cacheKey: string, usage: UpstreamUsage): Promise<void> {
  await db
    .prepare(
      "INSERT INTO lookup_log (id, user_id, kind, cache_key, input_tokens, output_tokens, searches, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
    )
    .bind(crypto.randomUUID(), userId, kind, cacheKey, usage.inputTokens, usage.outputTokens, usage.searches, Date.now())
    .run();
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
  if (!c.env.CLAUDE_API_KEY) {
    await refundQuota(c.env, userId);
    return c.json({ error: "unavailable" }, 503);
  }
  const found = await askClaude(c.env.CLAUDE_API_KEY, parsed.data);
  if (found) await logCost(c.env.DB, userId, "warranty", key, found.usage);
  if (!found?.result) {
    await refundQuota(c.env, userId);
    return c.json({ error: "lookup_failed" }, 502);
  }
  await c.env.CACHE.put(key, JSON.stringify(found.result), { expirationTtl: CACHE_DAYS * 86400 });
  return c.json({ ...found.result, cached: false });
});

/** AC-B11: shares the warranty rate-limit bucket; the image is never stored. */
lookup.post("/lookup/dataplate", async (c) => {
  const parsed = dataPlateRequestSchema.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "bad_request" }, 400);
  const userId = c.get("session").userId;
  if (!(await takeQuota(c.env, userId))) return c.json({ error: "rate_limited" }, 429);
  if (!c.env.CLAUDE_API_KEY) {
    await refundQuota(c.env, userId);
    return c.json({ error: "unavailable" }, 503);
  }
  const found = await readDataPlate(c.env.CLAUDE_API_KEY, parsed.data);
  if (found) await logCost(c.env.DB, userId, "dataplate", "", found.usage);
  if (!found?.result) {
    await refundQuota(c.env, userId);
    return c.json({ error: "lookup_failed" }, 502);
  }
  return c.json(found.result);
});

/** AC-B12: CPSC recalls proxy, cached 24 h per make+model. Free, so it takes no quota. */
lookup.get("/recalls", async (c) => {
  const parsed = recallQuerySchema.safeParse({ make: c.req.query("make"), model: c.req.query("model") });
  if (!parsed.success) return c.json({ error: "bad_request" }, 400);
  const key = recallsCacheKey(parsed.data.make, parsed.data.model);
  const hit = await c.env.CACHE.get(key);
  if (hit) {
    try {
      return c.json({ recalls: JSON.parse(hit), cached: true });
    } catch { /* corrupt entry: refetch */ }
  }
  const recalls = await fetchRecalls(parsed.data.make, parsed.data.model);
  if (!recalls) return c.json({ error: "lookup_failed" }, 502);
  await c.env.CACHE.put(key, JSON.stringify(recalls), { expirationTtl: RECALL_CACHE_SECONDS });
  return c.json({ recalls, cached: false });
});
