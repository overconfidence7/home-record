import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import app from "../src/index";
import { seedCompany, seedUser } from "./helpers";

// T081d: DELETE /me removes the account and everything it owns (Play account deletion rule; privacy policy "Your choices").
const call = (method: string, path: string, token: string, body?: unknown) =>
  app.request(
    path,
    { method, headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, body: body ? JSON.stringify(body) : undefined },
    env,
  );

const count = async (sql: string, ...args: string[]) =>
  (await env.DB.prepare(sql).bind(...args).first<{ n: number }>())?.n ?? -1;

async function homeWithData(token: string, id: string) {
  expect((await call("PUT", `/homes/${id}`, token, { name: "Maple St" })).status).toBe(201);
  const push = await call("POST", `/homes/${id}/sync/push`, token, {
    deviceId: "dev-a",
    ops: [{ outboxId: 1, entity: "item", entityId: `it-${id}`, op: "upsert", payload: JSON.stringify({ id: `it-${id}`, homeId: id, name: "Furnace" }), updatedAt: 11 }],
  });
  expect(push.status).toBe(200);
  await env.MEDIA.put(`${id}/photo/ph-1/a.jpg`, "jpeg");
}

describe("T081d delete account: DELETE /me", () => {
  it("removes the user, their owned home and company with rows and files, and leaves other tenants alone", async () => {
    const token = await seedUser("ad-u1", "ad-u1@x.com");
    const other = await seedUser("ad-u2", "ad-u2@x.com");
    const home = "6a1c2a4e-1b2c-4d5e-8f90-0a1b2c3d4e5f";
    const otherHome = "7a1c2a4e-1b2c-4d5e-8f90-0a1b2c3d4e5f";
    await homeWithData(token, home);
    await homeWithData(other, otherHome);
    await seedCompany("ad-co1", "Ortiz Electric", "ad-u1");
    await env.MEDIA.put("ad-co1/photo/p1/a.jpg", "jpeg");
    await env.MEDIA.put("company/ad-co1/handoff/h1.homerecord", "zip");
    await env.DB.prepare("INSERT INTO company_invites (company_id, code, created_at) VALUES ('ad-co1', 'ADCODE1', 1)").run();
    await env.DB.prepare(
      "INSERT INTO handoffs (id, token_hash, company_id, home_name, email, object_key, size, created_by, created_at, expires_at) VALUES ('ad-h1', 'th-ad-1', 'ad-co1', 'Hale', 'h@x.com', 'company/ad-co1/handoff/h1.homerecord', 3, 'ad-u1', 1, 9999999999999)",
    ).run();
    await env.DB.prepare("INSERT INTO push_tokens (token, user_id, app, updated_at) VALUES ('fcm-ad-1', 'ad-u1', 'field-log', 1)").run();
    // A crew member of the deleted company loses the membership with it.
    await env.DB.prepare("INSERT INTO memberships (id, user_id, tenant_kind, tenant_id, role, created_at) VALUES ('m-ad-c', 'ad-u2', 'company', 'ad-co1', 'crew', 1)").run();

    const res = await call("DELETE", "/me", token);
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ deleted: true, homesRemoved: 1, companiesRemoved: 1 });
    expect(res.headers.get("X-Refreshed-Token")).toBeNull();

    expect(await count("SELECT COUNT(*) AS n FROM users WHERE id = ?", "ad-u1")).toBe(0);
    expect(await count("SELECT COUNT(*) AS n FROM memberships WHERE user_id = ?", "ad-u1")).toBe(0);
    expect(await count("SELECT COUNT(*) AS n FROM push_tokens WHERE user_id = ?", "ad-u1")).toBe(0);
    expect(await count("SELECT COUNT(*) AS n FROM homes WHERE id = ?", home)).toBe(0);
    expect(await count("SELECT COUNT(*) AS n FROM sync_rows WHERE tenant_id = ?", home)).toBe(0);
    expect((await env.MEDIA.list({ prefix: `${home}/` })).objects).toHaveLength(0);
    expect(await count("SELECT COUNT(*) AS n FROM companies WHERE id = ?", "ad-co1")).toBe(0);
    expect(await count("SELECT COUNT(*) AS n FROM company_invites WHERE company_id = ?", "ad-co1")).toBe(0);
    expect(await count("SELECT COUNT(*) AS n FROM handoffs WHERE company_id = ?", "ad-co1")).toBe(0);
    expect(await count("SELECT COUNT(*) AS n FROM memberships WHERE tenant_id = ?", "ad-co1")).toBe(0);
    expect((await env.MEDIA.list({ prefix: "ad-co1/" })).objects).toHaveLength(0);
    expect((await env.MEDIA.list({ prefix: "company/ad-co1/" })).objects).toHaveLength(0);

    expect(await count("SELECT COUNT(*) AS n FROM sync_rows WHERE tenant_id = ?", otherHome)).toBe(1);
    expect((await call("GET", `/homes/${otherHome}/sync/pull?since=0`, other)).status).toBe(200);
  });

  it("every token of the deleted account answers 401 afterwards", async () => {
    const token = await seedUser("ad-u3", "ad-u3@x.com");
    expect((await call("DELETE", "/me", token)).status).toBe(200);
    expect((await call("GET", "/me", token)).status).toBe(401);
    expect((await call("DELETE", "/me", token)).status).toBe(401);
  });

  it("a member who does not own the home leaves it; the home and its records stay for the owner", async () => {
    const owner = await seedUser("ad-u4", "ad-u4@x.com");
    const member = await seedUser("ad-u5", "ad-u5@x.com");
    const home = "8a1c2a4e-1b2c-4d5e-8f90-0a1b2c3d4e5f";
    await homeWithData(owner, home);
    await env.DB.prepare("INSERT INTO memberships (id, user_id, tenant_kind, tenant_id, role, created_at) VALUES ('m-ad-e', 'ad-u5', 'home', ?, 'editor', 1)")
      .bind(home)
      .run();
    await env.DB.prepare(
      "INSERT INTO home_invites (id, token_hash, home_id, role, created_by, created_at, expires_at, accepted_by, accepted_at) VALUES ('ad-i1', 'th-ad-i1', ?, 'editor', 'ad-u4', 1, 9999999999999, 'ad-u5', 2)",
    ).bind(home).run();

    const res = await call("DELETE", "/me", member);
    expect(await res.json()).toEqual({ deleted: true, homesRemoved: 0, companiesRemoved: 0 });
    expect(await count("SELECT COUNT(*) AS n FROM memberships WHERE user_id = ?", "ad-u5")).toBe(0);
    expect(await count("SELECT COUNT(*) AS n FROM home_invites WHERE accepted_by = ?", "ad-u5")).toBe(0);
    expect(await count("SELECT COUNT(*) AS n FROM homes WHERE id = ?", home)).toBe(1);
    expect(await count("SELECT COUNT(*) AS n FROM sync_rows WHERE tenant_id = ?", home)).toBe(1);
    expect((await call("GET", `/homes/${home}/sync/pull?since=0`, owner)).status).toBe(200);
  });

  it("without a token it is 401 and nothing goes", async () => {
    await seedUser("ad-u6", "ad-u6@x.com");
    expect((await call("DELETE", "/me", "nope")).status).toBe(401);
    expect(await count("SELECT COUNT(*) AS n FROM users WHERE id = ?", "ad-u6")).toBe(1);
  });
});
