import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import app from "../src/index";
import { companyPlan, memberLimit } from "../src/billing/plan";
import { grantedEntitlement, tenantOf } from "../src/routes/billing";
import { seedCompany, seedUser } from "./helpers";

const SECRET = "rc-webhook-secret-for-tests";
const withSecret = { ...env, REVENUECAT_WEBHOOK_SECRET: SECRET };
const json = async <T = Record<string, any>>(res: Response) => (await res.json()) as T;

let seq = 0;
const hook = (event: Record<string, unknown>, auth: string | null = SECRET, bindings: object = withSecret) =>
  app.request(
    "/webhooks/revenuecat",
    {
      method: "POST",
      headers: { "Content-Type": "application/json", ...(auth !== null && { Authorization: auth }) },
      body: JSON.stringify({ api_version: "1.0", event: { id: `evt-${++seq}`, ...event } }),
    },
    bindings,
  );
const call = (method: string, path: string, token: string, body?: unknown) =>
  app.request(path, { method, headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, body: body ? JSON.stringify(body) : undefined }, env);
const myCompany = async (token: string) => (await json(await call("GET", "/me", token))).memberships.find((m: any) => m.kind === "company");

describe("AC-F56 plans", () => {
  const now = 1_800_000_000_000;
  it("trial gives Crew's 10, Solo 1, Crew 10, expired 0", () => {
    expect(companyPlan({ entitlement: null, trial_ends_at: now + 1 }, now)).toBe("trial");
    expect(companyPlan({ entitlement: null, trial_ends_at: now - 1 }, now)).toBe("expired");
    expect(companyPlan({ entitlement: "fieldlog_solo", entitlement_expires_at: now + 1, trial_ends_at: now - 1 }, now)).toBe("solo");
    expect(companyPlan({ entitlement: "fieldlog_crew", entitlement_expires_at: null, trial_ends_at: now + 1 }, now)).toBe("crew");
    expect(companyPlan({ entitlement: "fieldlog_crew", entitlement_expires_at: now - 1, trial_ends_at: now - 1 }, now)).toBe("expired");
    expect([memberLimit("trial"), memberLimit("solo"), memberLimit("crew"), memberLimit("expired")]).toEqual([10, 1, 10, 0]);
  });
  it("maps app user ids and entitlements", () => {
    expect(tenantOf("company:co-1")).toEqual({ table: "companies", id: "co-1" });
    expect(tenantOf("home:h-1")).toEqual({ table: "homes", id: "h-1" });
    expect(tenantOf("$RCAnonymousID:abc")).toBeNull();
    expect(grantedEntitlement("companies", ["fieldlog_solo", "fieldlog_crew"])).toBe("fieldlog_crew");
    expect(grantedEntitlement("companies", ["pro"])).toBeNull();
    expect(grantedEntitlement("homes", ["pro"])).toBe("pro");
  });
});

describe("AC-B50 RevenueCat webhook", () => {
  it("refuses a missing or wrong secret and answers 503 when none is configured", async () => {
    expect((await hook({ type: "TEST" }, null)).status).toBe(401);
    expect((await hook({ type: "TEST" }, "Bearer nope")).status).toBe(401);
    expect((await hook({ type: "TEST" }, SECRET, env)).status).toBe(503);
    expect((await hook({ type: "TEST" }, `Bearer ${SECRET}`)).status).toBe(200);
  });

  it("a Crew purchase shows in /me for every member; expiry clears it", async () => {
    const owner = await seedUser("bo1", "bo1@x.com");
    const crew = await seedUser("bc1", "bc1@x.com");
    await seedCompany("bco1", "Hale Electric", "bo1");
    await env.DB.prepare("INSERT INTO memberships (id, user_id, tenant_kind, tenant_id, role, created_at) VALUES ('m-bc1', 'bc1', 'company', 'bco1', 'crew', 1)").run();
    const expires = Date.now() + 30 * 86400_000;
    const res = await hook({ type: "INITIAL_PURCHASE", app_user_id: "company:bco1", entitlement_ids: ["fieldlog_crew"], expiration_at_ms: expires, event_timestamp_ms: 1000 });
    expect(res.status).toBe(200);
    for (const token of [owner, crew]) {
      expect(await myCompany(token)).toMatchObject({ entitlement: "fieldlog_crew", entitlementExpiresAt: expires, plan: "crew", memberLimit: 10 });
    }
    // a cancellation keeps the plan until it runs out
    await hook({ type: "CANCELLATION", app_user_id: "company:bco1", entitlement_ids: ["fieldlog_crew"], expiration_at_ms: expires, event_timestamp_ms: 2000 });
    expect((await myCompany(crew)).entitlement).toBe("fieldlog_crew");
    await hook({ type: "EXPIRATION", app_user_id: "company:bco1", entitlement_ids: ["fieldlog_crew"], event_timestamp_ms: 3000 });
    // seedCompany's trial runs to 2030, so the company falls back to the trial, not to nothing
    expect(await myCompany(crew)).toMatchObject({ entitlement: null, plan: "trial" });
  });

  it("applies an event id once and never lets an older event undo a newer one", async () => {
    await seedUser("bo2", "bo2@x.com");
    await seedCompany("bco2", "Ortiz Plumbing", "bo2");
    const body = { id: "evt-fixed", type: "INITIAL_PURCHASE", app_user_id: "company:bco2", entitlement_ids: ["fieldlog_solo"], event_timestamp_ms: 5000 };
    const send = () => app.request("/webhooks/revenuecat", { method: "POST", headers: { Authorization: SECRET, "Content-Type": "application/json" }, body: JSON.stringify({ event: body }) }, withSecret);
    expect(await json(await send())).toMatchObject({ ok: true });
    expect(await json(await send())).toMatchObject({ ok: true, duplicate: true });
    await hook({ type: "EXPIRATION", app_user_id: "company:bco2", event_timestamp_ms: 4000 });
    const row = await env.DB.prepare("SELECT entitlement FROM companies WHERE id = 'bco2'").first<{ entitlement: string }>();
    expect(row?.entitlement).toBe("fieldlog_solo");
  });

  it("finds the company through aliases, ignores anonymous buyers and clears on transfer", async () => {
    await seedUser("bo3", "bo3@x.com");
    await seedCompany("bco3", "Kurtz Builders", "bo3");
    expect(await json(await hook({ type: "INITIAL_PURCHASE", app_user_id: "$RCAnonymousID:1", entitlement_ids: ["fieldlog_crew"] })))
      .toMatchObject({ ignored: "app_user_id" });
    await hook({ type: "RENEWAL", app_user_id: "$RCAnonymousID:2", aliases: ["$RCAnonymousID:2", "company:bco3"], entitlement_ids: ["fieldlog_crew"], event_timestamp_ms: 10 });
    let row = await env.DB.prepare("SELECT entitlement FROM companies WHERE id = 'bco3'").first<{ entitlement: string | null }>();
    expect(row?.entitlement).toBe("fieldlog_crew");
    await hook({ type: "TRANSFER", transferred_from: ["company:bco3"], event_timestamp_ms: 20 });
    row = await env.DB.prepare("SELECT entitlement FROM companies WHERE id = 'bco3'").first<{ entitlement: string | null }>();
    expect(row?.entitlement).toBeNull();
  });

  it("House Manual Pro lands on the home", async () => {
    await env.DB.prepare("INSERT INTO homes (id, name, created_at) VALUES ('bh1', 'Maple St', 1)").run();
    await hook({ type: "NON_RENEWING_PURCHASE", app_user_id: "home:bh1", entitlement_ids: ["pro"], event_timestamp_ms: 10 });
    const row = await env.DB.prepare("SELECT entitlement, entitlement_expires_at FROM homes WHERE id = 'bh1'").first<{ entitlement: string; entitlement_expires_at: number | null }>();
    expect(row).toEqual({ entitlement: "pro", entitlement_expires_at: null });
  });
});

describe("AC-F56 member limit on join", () => {
  async function companyWithCode(tag: string) {
    const owner = await seedUser(`lo-${tag}`, `lo-${tag}@x.com`);
    const { id } = await json<{ id: string }>(await call("POST", "/companies", owner, { name: `Co ${tag}` }));
    const { code } = await json<{ code: string }>(await call("GET", `/companies/${id}/invite`, owner));
    return { id, code };
  }
  const join = async (tag: string, code: string) => call("POST", "/companies/join", await seedUser(`lj-${tag}`, `lj-${tag}@x.com`), { code });

  it("Solo is the Owner alone", async () => {
    const { id, code } = await companyWithCode("solo");
    await hook({ type: "INITIAL_PURCHASE", app_user_id: `company:${id}`, entitlement_ids: ["fieldlog_solo"], expiration_at_ms: Date.now() + 86400_000 });
    const res = await join("solo1", code);
    expect(res.status).toBe(409);
    expect(await json(res)).toMatchObject({ error: "member_limit", plan: "solo", limit: 1 });
  });

  it("the trial and Crew take up to 10 members, Owner included", async () => {
    const { code } = await companyWithCode("crew");
    for (let i = 0; i < 9; i++) expect((await join(`crew${i}`, code)).status).toBe(201);
    const res = await join("crew-tenth", code);
    expect(res.status).toBe(409);
    expect(await json(res)).toMatchObject({ error: "member_limit", plan: "trial", limit: 10 });
  });

  it("an expired company takes nobody new", async () => {
    const { id, code } = await companyWithCode("exp");
    await env.DB.prepare("UPDATE companies SET trial_ends_at = 1 WHERE id = ?").bind(id).run();
    expect((await join("exp1", code)).status).toBe(402);
  });
});
