// AC-B10: warranty lookup, cache by normalized make+model, rate limit, cost log
import { env } from "cloudflare:workers";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import app from "../src/index";
import { FREE_DAILY_LIMIT, parseWarrantyJson, warrantyCacheKey } from "../src/lookup/warranty";
import { seedUser } from "./helpers";

const recorded = {
  content: [
    { type: "server_tool_use", name: "web_search" },
    {
      type: "text",
      text:
        '```json\n{"partsYears":6,"laborYears":1,"specialTerms":["tank 6 years"],"registrationRequired":true,' +
        '"registrationUrl":"https://www.rheem.com/register","manualUrl":null,"sourceUrl":"https://www.rheem.com/warranty",' +
        '"confidence":"high","summary":"6 years parts, 1 year labor."}\n```',
    },
  ],
  usage: { input_tokens: 1200, output_tokens: 150, server_tool_use: { web_search_requests: 2 } },
};

const post = (token: string, body: unknown) =>
  app.request(
    "/lookup/warranty",
    { method: "POST", headers: { Authorization: `Bearer ${token}`, "content-type": "application/json" }, body: JSON.stringify(body) },
    { ...env, CLAUDE_API_KEY: "test-key" },
  );

describe("AC-B10 normalization", () => {
  it("shares one cache key across spelling variants", () => {
    expect(warrantyCacheKey("Rheem", "XG-50 T06")).toBe(warrantyCacheKey(" RHEEM ", "xg50t06"));
    expect(warrantyCacheKey("Rheem", "A1")).not.toBe(warrantyCacheKey("Rheem", "A2"));
  });
  it("parses fenced JSON and rejects prose", () => {
    expect(parseWarrantyJson("Here: " + JSON.stringify({ partsYears: 5, confidence: "weird" }))?.confidence).toBe("low");
    expect(parseWarrantyJson("no idea")).toBeNull();
  });
});

describe("AC-B10 POST /lookup/warranty", () => {
  let upstream: ReturnType<typeof vi.fn>;
  beforeEach(() => {
    upstream = vi.fn(async () => new Response(JSON.stringify(recorded), { status: 200 }));
    vi.stubGlobal("fetch", upstream);
  });
  afterEach(() => vi.unstubAllGlobals());

  it("requires a session", async () => {
    const res = await app.request("/lookup/warranty", { method: "POST", body: "{}" }, env);
    expect(res.status).toBe(401);
  });

  it("rejects a missing model", async () => {
    const token = await seedUser("lu0", "lu0@example.com");
    expect((await post(token, { make: "Rheem" })).status).toBe(400);
  });

  it("returns terms, restricts domains and searches, then serves repeats from the cache", async () => {
    const token = await seedUser("lu1", "lu1@example.com");
    const first = await post(token, { make: "Rheem", model: "XG50T06", category: "Water heater" });
    expect(first.status).toBe(200);
    const body = (await first.json()) as Record<string, unknown>;
    expect(body).toMatchObject({ partsYears: 6, laborYears: 1, registrationRequired: true, cached: false });
    const sent = JSON.parse((upstream.mock.calls[0][1] as RequestInit).body as string);
    expect(sent.tools[0].max_uses).toBe(3);
    expect(sent.tools[0].allowed_domains).toContain("rheem.com");

    const second = await post(token, { make: "rheem", model: "xg-50-t06" });
    expect(((await second.json()) as { cached: boolean }).cached).toBe(true);
    expect(upstream).toHaveBeenCalledTimes(1);

    const log = await env.DB.prepare("SELECT COUNT(*) AS n, SUM(searches) AS s FROM lookup_log WHERE user_id = 'lu1'").first<{ n: number; s: number }>();
    expect(log).toEqual({ n: 1, s: 2 });
  });

  it("answers 502 when upstream fails and does not cache", async () => {
    upstream.mockResolvedValue(new Response("boom", { status: 500 }));
    const token = await seedUser("lu2", "lu2@example.com");
    expect((await post(token, { make: "Acme", model: "Z1" })).status).toBe(502);
    expect(await env.CACHE.get(warrantyCacheKey("Acme", "Z1"))).toBeNull();
  });

  it("limits a free account to 20 lookups a day", async () => {
    const token = await seedUser("lu3", "lu3@example.com");
    for (let i = 0; i < FREE_DAILY_LIMIT; i++) expect((await post(token, { make: "Rheem", model: "XG50T06" })).status).toBe(200);
    expect((await post(token, { make: "Rheem", model: "XG50T06" })).status).toBe(429);
  });
});
