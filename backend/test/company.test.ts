import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import app from "../src/index";
import { newInviteCode } from "../src/routes/company";
import { seedUser } from "./helpers";

const call = (method: string, path: string, token: string, body?: unknown) =>
  app.request(
    path,
    { method, headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, body: body ? JSON.stringify(body) : undefined },
    env,
  );
const json = async <T = Record<string, any>>(res: Response) => (await res.json()) as T;

async function setup(tag: string) {
  const owner = await seedUser(`o-${tag}`, `o-${tag}@x.com`);
  const created = await json<{ id: string }>(await call("POST", "/companies", owner, { name: "Kurtz Builders", trade: "Remodeling", phone: "555-0100" }));
  const { code } = await json<{ code: string }>(await call("GET", `/companies/${created.id}/invite`, owner));
  return { owner, id: created.id, code };
}

async function joinAs(tag: string, code: string) {
  const token = await seedUser(`u-${tag}`, `u-${tag}@x.com`);
  const res = await call("POST", "/companies/join", token, { code });
  return { token, res, userId: `u-${tag}` };
}

describe("AC-F1 create company", () => {
  it("makes the creator Owner with a 14-day trial", async () => {
    const owner = await seedUser("o1", "o1@x.com");
    const res = await call("POST", "/companies", owner, { name: "Kurtz Builders", trade: "Remodeling", phone: "555-0100" });
    expect(res.status).toBe(201);
    const { id, role } = await json(res);
    expect(role).toBe("owner");
    const me = await json(await call("GET", "/me", owner));
    expect(me.memberships[0]).toMatchObject({ id, role: "owner", name: "Kurtz Builders" });
    const days = (me.memberships[0].trialEndsAt - Date.now()) / 86400_000;
    expect(days).toBeGreaterThan(13.9);
    expect(days).toBeLessThanOrEqual(14);
  });
  it("rejects a blank name", async () => {
    const owner = await seedUser("o2", "o2@x.com");
    expect((await call("POST", "/companies", owner, { name: "  " })).status).toBe(400);
  });
});

describe("AC-F2 invite and join", () => {
  it("generates 8-character codes from an unambiguous alphabet", () => {
    for (let i = 0; i < 50; i++) expect(newInviteCode()).toMatch(/^[ABCDEFGHJKLMNPQRSTUVWXYZ23456789]{8}$/);
  });
  it("gives a stable code until rotated, and rotation kills the old one", async () => {
    const { owner, id, code } = await setup("rot");
    expect(code).toHaveLength(8);
    expect((await json(await call("GET", `/companies/${id}/invite`, owner))).code).toBe(code);
    const rotated = (await json(await call("POST", `/companies/${id}/invite/rotate`, owner))).code;
    expect(rotated).not.toBe(code);
    expect((await joinAs("rot-old", code)).res.status).toBe(404);
    expect((await joinAs("rot-new", rotated)).res.status).toBe(201);
  });
  it("joins as Crew (lowercase and dashes tolerated) and shows up in the member list", async () => {
    const { owner, id, code } = await setup("join");
    const { res, userId } = await joinAs("join", `${code.slice(0, 4).toLowerCase()}-${code.slice(4)}`);
    expect(res.status).toBe(201);
    expect(await json(res)).toEqual({ id, role: "crew" });
    const { members } = await json(await call("GET", `/companies/${id}/members`, owner));
    expect(members.map((m: any) => [m.userId, m.role])).toEqual([["o-join", "owner"], [userId, "crew"]]);
  });
  it("joining twice keeps the existing role", async () => {
    const { owner, id, code } = await setup("twice");
    const { token, userId } = await joinAs("twice", code);
    await call("PATCH", `/companies/${id}/members/${userId}`, owner, { role: "lead" });
    const again = await call("POST", "/companies/join", token, { code });
    expect(await json(again)).toEqual({ id, role: "lead" });
  });
  it("an unknown code is a 404 invalid_code", async () => {
    const token = await seedUser("bad", "bad@x.com");
    const res = await call("POST", "/companies/join", token, { code: "ZZZZZZZZ" });
    expect(res.status).toBe(404);
    expect(await json(res)).toEqual({ error: "invalid_code" });
  });
  it("Owner changes a role and removes the member", async () => {
    const { owner, id, code } = await setup("mgmt");
    const { token, userId } = await joinAs("mgmt", code);
    expect((await call("PATCH", `/companies/${id}/members/${userId}`, owner, { role: "lead" })).status).toBe(200);
    expect((await json(await call("GET", "/me", token))).memberships[0].role).toBe("lead");
    expect((await call("DELETE", `/companies/${id}/members/${userId}`, owner)).status).toBe(200);
    expect((await json(await call("GET", "/me", token))).memberships).toEqual([]);
  });
});

describe("AC-F3 / AC-B3 role gates", () => {
  it("Crew gets 404 on team, invite, profile and every admin write", async () => {
    const { owner, id, code } = await setup("gate");
    const { token: crew, userId } = await joinAs("gate", code);
    const ownerId = "o-gate";
    for (const [m, p, b] of [
      ["GET", `/companies/${id}/members`, undefined],
      ["GET", `/companies/${id}/invite`, undefined],
      ["POST", `/companies/${id}/invite/rotate`, undefined],
      ["GET", `/companies/${id}/profile`, undefined],
      ["PATCH", `/companies/${id}/profile`, { name: "Hijacked" }],
      ["PATCH", `/companies/${id}/members/${userId}`, { role: "owner" }],
      ["DELETE", `/companies/${id}/members/${ownerId}`, undefined],
    ] as const) {
      const res = await call(m, p, crew, b);
      expect(res.status, `${m} ${p}`).toBe(404);
    }
    expect((await json(await call("GET", `/companies/${id}/profile`, owner))).name).toBe("Kurtz Builders");
  });
  it("Lead is also kept out of admin screens", async () => {
    const { owner, id, code } = await setup("lead");
    const { token: lead, userId } = await joinAs("lead", code);
    await call("PATCH", `/companies/${id}/members/${userId}`, owner, { role: "lead" });
    expect((await call("GET", `/companies/${id}/members`, lead)).status).toBe(404);
  });
  it("Office manages crew but cannot touch Owner, other Office, rates or grant elevated roles", async () => {
    const { owner, id, code } = await setup("office");
    const o = await joinAs("office-a", code);
    const other = await joinAs("office-b", code);
    const crew = await joinAs("office-c", code);
    await call("PATCH", `/companies/${id}/members/${o.userId}`, owner, { role: "office" });
    await call("PATCH", `/companies/${id}/members/${other.userId}`, owner, { role: "office" });
    const asOffice = (m: string, userId: string, b?: unknown) => call(m, `/companies/${id}/members/${userId}`, o.token, b);
    expect((await asOffice("PATCH", crew.userId, { role: "lead" })).status).toBe(200);
    expect((await asOffice("PATCH", crew.userId, { role: "office" })).status).toBe(403);
    expect((await asOffice("PATCH", crew.userId, { rateCents: 3000 })).status).toBe(403);
    expect((await asOffice("PATCH", "o-office", { role: "crew" })).status).toBe(403);
    expect((await asOffice("PATCH", other.userId, { role: "crew" })).status).toBe(403);
    expect((await asOffice("DELETE", "o-office")).status).toBe(403);
    expect((await asOffice("DELETE", other.userId)).status).toBe(403);
    expect((await asOffice("DELETE", crew.userId)).status).toBe(200);
  });
  it("only the Owner sees and sets rates; the Owner's role cannot be changed", async () => {
    const { owner, id, code } = await setup("rate");
    const o = await joinAs("rate-o", code);
    const crew = await joinAs("rate-c", code);
    await call("PATCH", `/companies/${id}/members/${o.userId}`, owner, { role: "office" });
    expect((await call("PATCH", `/companies/${id}/members/${crew.userId}`, owner, { rateCents: 3500 })).status).toBe(200);
    const asOwner = await json(await call("GET", `/companies/${id}/members`, owner));
    expect(asOwner.members.find((m: any) => m.userId === crew.userId).rateCents).toBe(3500);
    const asOffice = await json(await call("GET", `/companies/${id}/members`, o.token));
    expect(asOffice.members.every((m: any) => m.rateCents === null)).toBe(true);
    expect((await call("PATCH", `/companies/${id}/members/o-rate`, owner, { role: "crew" })).status).toBe(403);
  });
  it("another company's admin cannot see this company at all", async () => {
    const a = await setup("tenA");
    const b = await setup("tenB");
    expect((await call("GET", `/companies/${a.id}/members`, b.owner)).status).toBe(404);
    expect((await call("PATCH", `/companies/${a.id}/profile`, b.owner, { name: "x" })).status).toBe(404);
  });
});

describe("FL-Company profile", () => {
  it("returns defaults and merges setting updates", async () => {
    const { owner, id } = await setup("prof");
    const first = await json(await call("GET", `/companies/${id}/profile`, owner));
    expect(first.settings).toEqual({ otRule: "none", breakRule: false, requireGps: false, punchDistanceFt: 500, photoOnPunch: false });
    const res = await call("PATCH", `/companies/${id}/profile`, owner, { email: "office@kurtz.com", settings: { requireGps: true, punchDistanceFt: 300 } });
    expect(res.status).toBe(200);
    await call("PATCH", `/companies/${id}/profile`, owner, { settings: { photoOnPunch: true } });
    const after = await json(await call("GET", `/companies/${id}/profile`, owner));
    expect(after.email).toBe("office@kurtz.com");
    expect(after.settings).toMatchObject({ requireGps: true, punchDistanceFt: 300, photoOnPunch: true });
  });
  it("rejects an out-of-range punch distance", async () => {
    const { owner, id } = await setup("prof2");
    expect((await call("PATCH", `/companies/${id}/profile`, owner, { settings: { punchDistanceFt: 5 } })).status).toBe(400);
  });
  // AC-F9, AC-F12, AC-F13, AC-F18 (cr:fl-settings-source): clock rules reach every role through /me
  it("sends the clock rules with every company membership, Crew included", async () => {
    const { owner, id, code } = await setup("prof3");
    const crew = await joinAs("prof3", code);
    const before = (await json(await call("GET", "/me", crew.token))).memberships[0];
    expect(before.settings).toEqual({ otRule: "none", breakRule: false, requireGps: false, punchDistanceFt: 500, photoOnPunch: false });
    await call("PATCH", `/companies/${id}/profile`, owner, { settings: { requireGps: true, punchDistanceFt: 300, otRule: "daily8", breakRule: true } });
    const after = (await json(await call("GET", "/me", crew.token))).memberships[0];
    expect(after.settings).toEqual({ otRule: "daily8", breakRule: true, requireGps: true, punchDistanceFt: 300, photoOnPunch: false });
    expect(after.role).toBe("crew");
  });
});

describe("invite landing page", () => {
  it("shows the normalized code and an app link without auth", async () => {
    const res = await app.request("/join/k7m2-qx9r", {}, env);
    expect(res.status).toBe(200);
    const html = await res.text();
    expect(html).toContain("K7M2QX9R");
    expect(html).toContain("fieldlog://join/K7M2QX9R");
  });
  it("404s on a malformed code (no HTML injection)", async () => {
    expect((await app.request("/join/%3Cscript%3E", {}, env)).status).toBe(404);
  });
});
