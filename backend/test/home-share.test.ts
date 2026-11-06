import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import app from "../src/index";
import { pruneHomeInvites } from "../src/routes/homeShare";
import { seedUser } from "./helpers";

// AC-H16: sharing a home with editor and viewer invite links; viewers cannot write.

const call = (method: string, path: string, token: string, body?: unknown) =>
  app.request(
    path,
    { method, headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, body: body ? JSON.stringify(body) : undefined },
    env,
  );
const json = async <T = Record<string, any>>(res: Response) => (await res.json()) as T;

let n = 0;
async function homeWithOwner(name = "Maple St") {
  n += 1;
  const owner = await seedUser(`hs-o${n}`, `hs-o${n}@x.com`);
  const id = `${String(n).padStart(8, "0")}-1b2c-4d5e-8f90-0a1b2c3d4e5f`;
  expect((await call("PUT", `/homes/${id}`, owner, { name })).status).toBe(201);
  return { owner, id, ownerId: `hs-o${n}` };
}
async function invite(home: { owner: string; id: string }, role: "editor" | "viewer") {
  const res = await call("POST", `/homes/${home.id}/invites`, home.owner, { role });
  expect(res.status).toBe(201);
  return json<{ id: string; role: string; link: string; token: string; expiresAt: number }>(res);
}
const pushHome = (homeId: string, token: string, name: string) =>
  call("POST", `/homes/${homeId}/sync/push`, token, {
    deviceId: `dev-${name}`,
    ops: [{ outboxId: 1, entity: "home", entityId: homeId, op: "upsert", payload: JSON.stringify({ id: homeId, name }), updatedAt: Date.now() }],
  });

describe("AC-H16 home sharing", () => {
  it("an editor link joins the account to the home; it can pull and push", async () => {
    const home = await homeWithOwner();
    const link = await invite(home, "editor");
    expect(link.link).toMatch(new RegExp(`/invite/${link.token}$`));
    const editor = await seedUser(`hs-e${n}`, `hs-e${n}@x.com`);
    const accepted = await call("POST", `/invite/${link.token}/accept`, editor);
    expect(accepted.status).toBe(201);
    expect(await json(accepted)).toEqual({ homeId: home.id, homeName: "Maple St", role: "editor" });
    const me = await json(await call("GET", "/me", editor));
    expect(me.memberships).toEqual([expect.objectContaining({ kind: "home", id: home.id, role: "editor" })]);
    expect((await call("GET", `/homes/${home.id}/sync/pull?since=0`, editor)).status).toBe(200);
    const push = await json(await pushHome(home.id, editor, "Maple St (edited)"));
    expect(push.accepted).toHaveLength(1);
    expect(push.rejected).toEqual([]);
  });

  it("a viewer can pull but every push op is refused with role", async () => {
    const home = await homeWithOwner();
    await pushHome(home.id, home.owner, "Maple St");
    const link = await invite(home, "viewer");
    const viewer = await seedUser(`hs-v${n}`, `hs-v${n}@x.com`);
    expect((await json(await call("POST", `/invite/${link.token}/accept`, viewer))).role).toBe("viewer");
    const pulled = await json(await call("GET", `/homes/${home.id}/sync/pull?since=0`, viewer));
    expect(JSON.stringify(pulled)).toContain("Maple St");
    const push = await json(await pushHome(home.id, viewer, "Hacked"));
    expect(push.accepted).toEqual([]);
    expect(push.rejected).toEqual([{ outboxId: 1, reason: "role" }]);
    const presign = await call("POST", "/media/presign", viewer, {
      tenantKind: "home", tenantId: home.id, entity: "photo", entityId: "p1", filename: "a.jpg", contentType: "image/jpeg", size: 10,
    });
    expect(presign.status).toBe(403);
  });

  it("a link works once; the same account taking it again is a no-op, another account gets 409", async () => {
    const home = await homeWithOwner();
    const link = await invite(home, "editor");
    const a = await seedUser(`hs-a${n}`, `hs-a${n}@x.com`);
    const b = await seedUser(`hs-b${n}`, `hs-b${n}@x.com`);
    expect((await call("POST", `/invite/${link.token}/accept`, a)).status).toBe(201);
    expect((await call("POST", `/invite/${link.token}/accept`, a)).status).toBe(200);
    expect((await call("POST", `/invite/${link.token}/accept`, b)).status).toBe(409);
    expect((await call("GET", `/homes/${home.id}/sync/pull?since=0`, b)).status).toBe(404);
  });

  it("the owner taking their own link keeps owner and uses nothing up", async () => {
    const home = await homeWithOwner();
    const link = await invite(home, "viewer");
    const res = await call("POST", `/invite/${link.token}/accept`, home.owner);
    expect(await json(res)).toEqual({ homeId: home.id, homeName: "Maple St", role: "owner" });
    const someone = await seedUser(`hs-s${n}`, `hs-s${n}@x.com`);
    expect((await call("POST", `/invite/${link.token}/accept`, someone)).status).toBe(201);
  });

  it("expired, revoked and unknown links grant nothing", async () => {
    const home = await homeWithOwner();
    const expired = await invite(home, "editor");
    await env.DB.prepare("UPDATE home_invites SET expires_at = 1 WHERE id = ?").bind(expired.id).run();
    const revoked = await invite(home, "editor");
    expect((await call("DELETE", `/homes/${home.id}/invites/${revoked.id}`, home.owner)).status).toBe(200);
    const u = await seedUser(`hs-x${n}`, `hs-x${n}@x.com`);
    expect((await call("POST", `/invite/${expired.token}/accept`, u)).status).toBe(410);
    expect((await call("POST", `/invite/${revoked.token}/accept`, u)).status).toBe(404);
    expect((await call("POST", `/invite/${"z".repeat(43)}/accept`, u)).status).toBe(404);
    expect((await call("POST", `/invite/${expired.token}/accept`, "nope")).status).toBe(401);
    await pruneHomeInvites(env.DB);
    const left = await env.DB.prepare("SELECT COUNT(*) AS c FROM home_invites WHERE home_id = ?").bind(home.id).first<{ c: number }>();
    expect(left?.c).toBe(0);
  });

  it("only the owner makes, lists and revokes links (AC-B2: others get 404)", async () => {
    const home = await homeWithOwner();
    const link = await invite(home, "editor");
    const editor = await seedUser(`hs-e2${n}`, `hs-e2${n}@x.com`);
    await call("POST", `/invite/${link.token}/accept`, editor);
    const stranger = await seedUser(`hs-st${n}`, `hs-st${n}@x.com`);
    expect((await call("POST", `/homes/${home.id}/invites`, editor, { role: "viewer" })).status).toBe(404);
    expect((await call("POST", `/homes/${home.id}/invites`, stranger, { role: "viewer" })).status).toBe(404);
    expect((await call("POST", `/homes/${home.id}/invites`, home.owner, { role: "owner" })).status).toBe(400);
    const open = await invite(home, "viewer");
    expect((await call("DELETE", `/homes/${home.id}/invites/${open.id}`, editor)).status).toBe(404);
    const ownerView = await json(await call("GET", `/homes/${home.id}/people`, home.owner));
    expect(ownerView.invites).toEqual([expect.objectContaining({ id: open.id, role: "viewer" })]);
    const editorView = await json(await call("GET", `/homes/${home.id}/people`, editor));
    expect(editorView.invites).toEqual([]);
    expect(editorView.people.map((p: any) => [p.role, p.you])).toEqual([["owner", false], ["editor", true]]);
    expect((await call("GET", `/homes/${home.id}/people`, stranger)).status).toBe(404);
  });

  it("the owner removes people, others can only leave, and the owner stays", async () => {
    const home = await homeWithOwner();
    const e = await seedUser(`hs-r1${n}`, `hs-r1${n}@x.com`);
    const v = await seedUser(`hs-r2${n}`, `hs-r2${n}@x.com`);
    await call("POST", `/invite/${(await invite(home, "editor")).token}/accept`, e);
    await call("POST", `/invite/${(await invite(home, "viewer")).token}/accept`, v);
    expect((await call("DELETE", `/homes/${home.id}/people/hs-r2${n}`, e)).status).toBe(404);
    expect((await call("DELETE", `/homes/${home.id}/people/${home.ownerId}`, e)).status).toBe(404);
    expect((await call("DELETE", `/homes/${home.id}/people/${home.ownerId}`, home.owner)).status).toBe(404);
    expect((await call("DELETE", `/homes/${home.id}/people/hs-r2${n}`, home.owner)).status).toBe(200);
    expect((await call("GET", `/homes/${home.id}/sync/pull?since=0`, v)).status).toBe(404);
    expect((await call("DELETE", `/homes/${home.id}/people/hs-r1${n}`, e)).status).toBe(200);
    const people = await json(await call("GET", `/homes/${home.id}/people`, home.owner));
    expect(people.people).toHaveLength(1);
  });

  it("the link page opens House Manual, and says when a link is used or expired", async () => {
    const home = await homeWithOwner("Oak <Ave>");
    const link = await invite(home, "viewer");
    const res = await app.request(`/invite/${link.token}`, {}, env);
    expect(res.status).toBe(200);
    const html = await res.text();
    expect(html).toContain("Oak &lt;Ave&gt;");
    expect(html).toContain(`intent://invite/${link.token}#Intent;scheme=homerecord;package=com.kurtz.homerecord.housemanual;end`);
    expect(html).toContain("can't change anything");
    await env.DB.prepare("UPDATE home_invites SET expires_at = 1 WHERE id = ?").bind(link.id).run();
    expect((await app.request(`/invite/${link.token}`, {}, env)).status).toBe(410);
    expect((await app.request(`/invite/${"q".repeat(43)}`, {}, env)).status).toBe(404);
  });

  it("deleting the cloud copy removes the home's links too", async () => {
    const home = await homeWithOwner();
    await invite(home, "editor");
    expect((await call("DELETE", `/homes/${home.id}`, home.owner)).status).toBe(200);
    const left = await env.DB.prepare("SELECT COUNT(*) AS c FROM home_invites WHERE home_id = ?").bind(home.id).first<{ c: number }>();
    expect(left?.c).toBe(0);
  });
});
