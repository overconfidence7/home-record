// AC-B11: POST /lookup/dataplate; AC-B12: GET /recalls
import { env } from "cloudflare:workers";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import app from "../src/index";
import { parseDataPlateJson } from "../src/lookup/dataplate";
import { pickRecalls } from "../src/lookup/recalls";
import { seedUser } from "./helpers";

const plate = {
  content: [{ type: "text", text: '{"make":"Rheem","model":"XG50T06","serial":"0415123456","manufacturedOn":null}' }],
  usage: { input_tokens: 900, output_tokens: 40 },
};
const cpsc = [
  { RecallNumber: "24-001", Title: "Rheem water heaters recalled", RecallDate: "2024-03-01T00:00:00", Hazards: [{ Name: "Fire" }], Remedies: [{ Name: "Repair" }], URL: "https://cpsc.gov/r/1" },
  { RecallNumber: "24-002", Title: "Acme toasters recalled", Hazards: [] },
];
const hdr = (t: string) => ({ Authorization: `Bearer ${t}`, "content-type": "application/json" });
const image = "A".repeat(64);

describe("AC-B11 POST /lookup/dataplate", () => {
  let upstream: ReturnType<typeof vi.fn>;
  beforeEach(() => {
    upstream = vi.fn(async () => new Response(JSON.stringify(plate), { status: 200 }));
    vi.stubGlobal("fetch", upstream);
  });
  afterEach(() => vi.unstubAllGlobals());
  const post = (t: string, body: unknown, key: string | null = "k") =>
    app.request("/lookup/dataplate", { method: "POST", headers: hdr(t), body: JSON.stringify(body) }, { ...env, CLAUDE_API_KEY: key ?? undefined });

  it("requires a session", async () => {
    expect((await app.request("/lookup/dataplate", { method: "POST", body: "{}" }, env)).status).toBe(401);
  });
  it("rejects a missing image", async () => {
    const t = await seedUser("dp0", "dp0@example.com");
    expect((await post(t, {})).status).toBe(400);
  });
  it("returns fields, sends the image to vision and logs the cost", async () => {
    const t = await seedUser("dp1", "dp1@example.com");
    const res = await post(t, { imageBase64: image });
    expect(await res.json()).toEqual({ make: "Rheem", model: "XG50T06", serial: "0415123456", manufacturedOn: null });
    const sent = JSON.parse((upstream.mock.calls[0][1] as RequestInit).body as string);
    expect(sent.messages[0].content[0]).toMatchObject({ type: "image", source: { media_type: "image/jpeg" } });
    const log = await env.DB.prepare("SELECT kind FROM lookup_log WHERE user_id = 'dp1'").first<{ kind: string }>();
    expect(log?.kind).toBe("dataplate");
  });
  it("answers 503 without a key and 502 on unreadable output", async () => {
    const t = await seedUser("dp2", "dp2@example.com");
    expect((await post(t, { imageBase64: image }, null)).status).toBe(503);
    upstream.mockResolvedValueOnce(new Response(JSON.stringify({ content: [{ type: "text", text: "cannot read" }] }), { status: 200 }));
    expect((await post(t, { imageBase64: image })).status).toBe(502);
  });
  it("parses fenced JSON and drops bad dates", () => {
    expect(parseDataPlateJson('```{"make":"GE","model":null,"serial":"S1","manufacturedOn":"June 2020"}```')).toEqual({
      make: "GE", model: null, serial: "S1", manufacturedOn: null,
    });
  });
});

describe("AC-B12 GET /recalls", () => {
  let upstream: ReturnType<typeof vi.fn>;
  beforeEach(() => {
    upstream = vi.fn(async () => new Response(JSON.stringify(cpsc), { status: 200 }));
    vi.stubGlobal("fetch", upstream);
  });
  afterEach(() => vi.unstubAllGlobals());
  const get = (t: string, qs: string) => app.request(`/recalls?${qs}`, { headers: hdr(t) }, env);

  it("requires a session and a make", async () => {
    expect((await app.request("/recalls?make=Rheem", {}, env)).status).toBe(401);
    const t = await seedUser("rc0", "rc0@example.com");
    expect((await get(t, "model=X")).status).toBe(400);
  });
  it("filters to the make, then serves repeats from the 24 h cache", async () => {
    const t = await seedUser("rc1", "rc1@example.com");
    const first = (await (await get(t, "make=Rheem&model=XG50")).json()) as { recalls: { recallNumber: string; recallDate: string }[]; cached: boolean };
    expect(first.cached).toBe(false);
    expect(first.recalls.map((r) => r.recallNumber)).toEqual(["24-001"]);
    expect(first.recalls[0].recallDate).toBe("2024-03-01");
    const second = (await (await get(t, "make=rheem&model=xg-50")).json()) as { cached: boolean };
    expect(second.cached).toBe(true);
    expect(upstream).toHaveBeenCalledTimes(1);
  });
  it("answers 502 when CPSC fails", async () => {
    const t = await seedUser("rc2", "rc2@example.com");
    upstream.mockResolvedValueOnce(new Response("x", { status: 500 }));
    expect((await get(t, "make=Fail")).status).toBe(502);
  });
  it("pickRecalls sorts model matches first and flags them", () => {
    const more = [{ RecallNumber: "24-003", Title: "Rheem heaters recalled", Hazards: [] }, { RecallNumber: "24-004", Title: "Rheem ZZ9 heater recalled" }];
    expect(pickRecalls(more, "Rheem", "ZZ9").map((r) => [r.recallNumber, r.modelMatch])).toEqual([["24-004", true], ["24-003", false]]);
    expect(pickRecalls(cpsc, "Rheem", undefined)).toHaveLength(1);
  });
});
