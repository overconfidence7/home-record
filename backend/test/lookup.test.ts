// AC-B10: warranty lookup, cache by normalized make+model, rate limit, cost log
import { env } from "cloudflare:workers";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import app from "../src/index";
import {
  FREE_DAILY_LIMIT, REPORT_TOOL, parseWarrantyJson, parseWarrantyToolInput, warrantyCacheKey,
} from "../src/lookup/warranty";
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
  it("reads the strict report_warranty tool call and checks its ranges", () => { // AC-B10 structured output
    const input = {
      partsYears: 10, laborYears: null, specialTerms: [], registrationRequired: false, registrationUrl: null,
      manualUrl: "https://www.trane.com/manual.pdf", sourceUrl: "https://www.trane.com/warranty", confidence: "medium",
      summary: "10 years parts.",
    };
    const call = (i: unknown) => [{ type: "text", text: "Found it." }, { type: "tool_use", name: "report_warranty", input: i }];
    expect(parseWarrantyToolInput(call(input))).toEqual(input);
    expect(parseWarrantyToolInput(call({ ...input, partsYears: 500 }))?.partsYears).toBeNull();
    expect(parseWarrantyToolInput([{ type: "tool_use", name: "other", input }])).toBeNull();
    expect(parseWarrantyToolInput([{ type: "text", text: JSON.stringify(input) }])).toBeNull();
    const schema = REPORT_TOOL.input_schema;
    expect(REPORT_TOOL.strict).toBe(true);
    expect(schema.additionalProperties).toBe(false);
    expect([...schema.required].sort()).toEqual(Object.keys(schema.properties).sort());
  });
});

describe("AC-B10 POST /lookup/warranty", () => {
  let upstream: ReturnType<typeof vi.fn>;
  beforeEach(() => {
    upstream = vi.fn(async () => new Response(JSON.stringify(recorded), { status: 200 }));
    vi.stubGlobal("fetch", upstream);
  });
  afterEach(() => vi.unstubAllGlobals());

  it("asks for the answer through the strict tool and stores what it reports", async () => { // AC-B10
    upstream.mockResolvedValue(new Response(JSON.stringify({
      content: [
        { type: "server_tool_use", name: "web_search" },
        { type: "text", text: "The Trane page lists the terms." },
        {
          type: "tool_use", id: "t1", name: "report_warranty",
          input: {
            partsYears: 10, laborYears: 1, specialTerms: ["compressor 12 years"], registrationRequired: true,
            registrationUrl: "https://www.trane.com/register", manualUrl: null, sourceUrl: "https://www.trane.com/warranty",
            confidence: "high", summary: "10 years parts, 1 year labor.",
          },
        },
      ],
      stop_reason: "tool_use",
      usage: { input_tokens: 900, output_tokens: 120, server_tool_use: { web_search_requests: 1 } },
    }), { status: 200 }));
    const token = await seedUser("lu7", "lu7@example.com");
    const res = await post(token, { make: "Trane", model: "XR14" });
    expect(res.status).toBe(200);
    const body = await res.json() as { partsYears: number; specialTerms: string[] };
    expect(body.partsYears).toBe(10);
    expect(body.specialTerms).toEqual(["compressor 12 years"]);
    const sent = JSON.parse(upstream.mock.calls[0][1].body as string) as { tools: { name: string; strict?: boolean }[] };
    expect(sent.tools.map((t) => t.name)).toEqual(["web_search", "report_warranty"]);
    expect(sent.tools[1].strict).toBe(true);
  });

  it("requires a session", async () => {
    const res = await app.request("/lookup/warranty", { method: "POST", body: "{}" }, env);
    expect(res.status).toBe(401);
  });

  it("rejects a missing model", async () => {
    const token = await seedUser("lu0", "lu0@example.com");
    expect((await post(token, { make: "Rheem" })).status).toBe(400);
  });

  it("treats a corrupt cache entry as a miss", async () => { // AC-B10
    const token = await seedUser("lu9", "lu9@example.com");
    await env.CACHE.put(warrantyCacheKey("Corrupt", "C1"), "{not json");
    const res = await post(token, { make: "Corrupt", model: "C1" });
    expect(res.status).toBe(200);
    expect(((await res.json()) as { cached: boolean }).cached).toBe(false);
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

  it("logs the cost of a billed answer that does not parse and gives the request back", async () => { // AC-B10
    upstream.mockResolvedValue(new Response(JSON.stringify({
      content: [{ type: "text", text: "I could not find it." }],
      usage: { input_tokens: 800, output_tokens: 20, server_tool_use: { web_search_requests: 3 } },
    }), { status: 200 }));
    const token = await seedUser("lu4", "lu4@example.com");
    expect((await post(token, { make: "Acme", model: "Z2" })).status).toBe(502);
    const log = await env.DB.prepare(
      "SELECT kind, input_tokens AS i, output_tokens AS o, searches AS s FROM lookup_log WHERE user_id = 'lu4'",
    ).all();
    expect(log.results).toEqual([{ kind: "warranty", i: 800, o: 20, s: 3 }]);
    expect(await env.CACHE.get(warrantyCacheKey("Acme", "Z2"))).toBeNull();
    const day = new Date().toISOString().slice(0, 10);
    const quota = await env.DB.prepare("SELECT used FROM lookup_quota WHERE user_id = 'lu4' AND day = ?").bind(day).first<{ used: number }>();
    expect(quota?.used).toBe(0);
  });

  it("failed lookups do not use up the daily limit", async () => { // AC-B10
    const token = await seedUser("lu5", "lu5@example.com");
    upstream.mockResolvedValue(new Response("boom", { status: 500 }));
    for (let i = 0; i < FREE_DAILY_LIMIT; i++) expect((await post(token, { make: "Acme", model: "Z3" })).status).toBe(502);
    upstream.mockRejectedValueOnce(new TypeError("network down"));
    expect((await post(token, { make: "Acme", model: "Z3" })).status).toBe(502);
    upstream.mockResolvedValue(new Response(JSON.stringify(recorded), { status: 200 }));
    expect((await post(token, { make: "Rheem", model: "XG40T06" })).status).toBe(200);
    const n = await env.DB.prepare("SELECT COUNT(*) AS n FROM lookup_log WHERE user_id = 'lu5'").first<{ n: number }>();
    expect(n?.n).toBe(1); // only the billed answer is a cost row
  });

  it("limits a free account to 20 lookups a day", async () => {
    const token = await seedUser("lu3", "lu3@example.com");
    for (let i = 0; i < FREE_DAILY_LIMIT; i++) expect((await post(token, { make: "Rheem", model: "XG50T06" })).status).toBe(200);
    expect((await post(token, { make: "Rheem", model: "XG50T06" })).status).toBe(429);
  });
});
