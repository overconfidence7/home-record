import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import app from "../src/index";
import { seedUser } from "./helpers";

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
    ops: [
      { outboxId: 1, entity: "home", entityId: id, op: "upsert", payload: JSON.stringify({ id, name: "Maple St" }), updatedAt: 10 },
      { outboxId: 2, entity: "item", entityId: "it-1", op: "upsert", payload: JSON.stringify({ id: "it-1", homeId: id, name: "Furnace" }), updatedAt: 11 },
    ],
  });
  expect(push.status).toBe(200);
  await env.MEDIA.put(`${id}/photo/ph-1/a.jpg`, "jpeg");
  await env.MEDIA.put(`${id}/document/doc-1/manual.pdf`, "pdf");
  await env.DB.prepare("INSERT INTO media_objects (object_key, tenant_kind, tenant_id, created_at) VALUES (?, 'home', ?, 1)")
    .bind(`${id}/photo/ph-1/a.jpg`, id)
    .run();
}

describe("AC-H15 delete cloud copy: DELETE /homes/:id", () => {
  it("removes the home's rows, media, membership and the home, and leaves other homes alone", async () => {
    const token = await seedUser("hd-u1", "hd-u1@x.com");
    const id = "1a1c2a4e-1b2c-4d5e-8f90-0a1b2c3d4e5f";
    const other = "2a1c2a4e-1b2c-4d5e-8f90-0a1b2c3d4e5f";
    await homeWithData(token, id);
    await homeWithData(token, other);

    const res = await call("DELETE", `/homes/${id}`, token);
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ id, mediaRemoved: 2 });

    expect(await count("SELECT COUNT(*) AS n FROM sync_rows WHERE tenant_id = ?", id)).toBe(0);
    expect(await count("SELECT COUNT(*) AS n FROM sync_applied WHERE tenant_id = ?", id)).toBe(0);
    expect(await count("SELECT COUNT(*) AS n FROM sync_counters WHERE tenant_id = ?", id)).toBe(0);
    expect(await count("SELECT COUNT(*) AS n FROM media_objects WHERE tenant_id = ?", id)).toBe(0);
    expect(await count("SELECT COUNT(*) AS n FROM memberships WHERE tenant_id = ?", id)).toBe(0);
    expect(await count("SELECT COUNT(*) AS n FROM homes WHERE id = ?", id)).toBe(0);
    expect((await env.MEDIA.list({ prefix: `${id}/` })).objects).toHaveLength(0);

    expect(await count("SELECT COUNT(*) AS n FROM sync_rows WHERE tenant_id = ?", other)).toBe(2);
    expect((await env.MEDIA.list({ prefix: `${other}/` })).objects).toHaveLength(2);
    expect((await call("GET", `/homes/${other}/sync/pull?since=0`, token)).status).toBe(200);
  });

  it("the same phone can sign in again and upload the home afresh", async () => {
    const token = await seedUser("hd-u2", "hd-u2@x.com");
    const id = "3a1c2a4e-1b2c-4d5e-8f90-0a1b2c3d4e5f";
    await homeWithData(token, id);
    expect((await call("DELETE", `/homes/${id}`, token)).status).toBe(200);
    // The old membership is gone from the next request's session: the pull answers 404, a new PUT creates it again.
    expect((await call("GET", `/homes/${id}/sync/pull?since=0`, token)).status).toBe(404);
    await homeWithData(token, id);
    const pull = await call("GET", `/homes/${id}/sync/pull?since=0`, token);
    expect(pull.status).toBe(200);
  });

  it("AC-B2: only the owner can delete; anyone else gets 404 and nothing goes", async () => {
    const owner = await seedUser("hd-u3", "hd-u3@x.com");
    const stranger = await seedUser("hd-u4", "hd-u4@x.com");
    const member = await seedUser("hd-u5", "hd-u5@x.com");
    const id = "4a1c2a4e-1b2c-4d5e-8f90-0a1b2c3d4e5f";
    await homeWithData(owner, id);
    await env.DB.prepare("INSERT INTO memberships (id, user_id, tenant_kind, tenant_id, role, created_at) VALUES ('m-hd-v', 'hd-u5', 'home', ?, 'member', 1)")
      .bind(id)
      .run();
    expect((await call("DELETE", `/homes/${id}`, stranger)).status).toBe(404);
    expect((await call("DELETE", `/homes/${id}`, member)).status).toBe(404);
    expect((await call("DELETE", `/homes/${id}`, "nope")).status).toBe(401);
    expect(await count("SELECT COUNT(*) AS n FROM sync_rows WHERE tenant_id = ?", id)).toBe(2);
    expect((await env.MEDIA.list({ prefix: `${id}/` })).objects).toHaveLength(2);
  });

  it("a company id is not a home: DELETE /companies/:id is not a route", async () => {
    const token = await seedUser("hd-u6", "hd-u6@x.com");
    expect((await call("DELETE", "/companies/5a1c2a4e-1b2c-4d5e-8f90-0a1b2c3d4e5f", token)).status).toBe(404);
  });
});
