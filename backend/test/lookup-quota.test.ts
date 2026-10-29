// AC-B10: the daily lookup limit holds under a burst (cr:quota-race) and old quota rows are pruned
import { env } from "cloudflare:workers";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import app from "../src/index";
import { pruneLookupQuota } from "../src/routes/lookup";
import { FREE_DAILY_LIMIT } from "../src/lookup/warranty";
import { seedUser } from "./helpers";

const answer = {
  content: [{
    type: "text",
    text: '{"partsYears":6,"laborYears":1,"specialTerms":[],"registrationRequired":false,"registrationUrl":null,' +
      '"manualUrl":null,"sourceUrl":"https://www.rheem.com/warranty","confidence":"high","summary":"6 years parts."}',
  }],
  usage: { input_tokens: 10, output_tokens: 10 },
};

const post = (token: string) =>
  app.request(
    "/lookup/warranty",
    {
      method: "POST",
      headers: { Authorization: `Bearer ${token}`, "content-type": "application/json" },
      body: JSON.stringify({ make: "Rheem", model: "XG50T06" }),
    },
    { ...env, CLAUDE_API_KEY: "test-key" },
  );

describe("AC-B10 lookup quota", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response(JSON.stringify(answer), { status: 200 })));
  });
  afterEach(() => vi.unstubAllGlobals());

  it("a burst of concurrent lookups answers exactly the daily limit", async () => {
    const token = await seedUser("lq1", "lq1@example.com");
    const statuses = (await Promise.all(Array.from({ length: FREE_DAILY_LIMIT + 10 }, () => post(token)))).map((r) => r.status);
    expect(statuses.filter((s) => s === 200)).toHaveLength(FREE_DAILY_LIMIT);
    expect(statuses.filter((s) => s === 429)).toHaveLength(10);
    const day = new Date().toISOString().slice(0, 10);
    const row = await env.DB.prepare("SELECT used FROM lookup_quota WHERE user_id = 'lq1' AND day = ?").bind(day).first<{ used: number }>();
    expect(row?.used).toBe(FREE_DAILY_LIMIT);
  });

  it("the daily cron keeps today and yesterday and drops older days", async () => {
    await env.DB.batch(["2026-10-27", "2026-10-28", "2026-10-29"].map((day) =>
      env.DB.prepare("INSERT INTO lookup_quota (user_id, day, used) VALUES ('lq2', ?, 1)").bind(day)));
    await pruneLookupQuota(env.DB, Date.parse("2026-10-29T12:00:00Z"));
    const days = await env.DB.prepare("SELECT day FROM lookup_quota WHERE user_id = 'lq2' ORDER BY day").all<{ day: string }>();
    expect(days.results.map((r) => r.day)).toEqual(["2026-10-28", "2026-10-29"]);
  });
});
