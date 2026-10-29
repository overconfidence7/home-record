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
const json = async <T = Record<string, any>>(res: Response) => (await res.json()) as T;

describe("AC-H15 signing in uploads the home: PUT /homes/:id", () => {
  it("creates an offline-made home with the caller as owner, then its sync works", async () => {
    const token = await seedUser("h-u1", "h-u1@x.com");
    const id = "6f1c2a4e-1b2c-4d5e-8f90-0a1b2c3d4e5f";
    const res = await call("PUT", `/homes/${id}`, token, { name: "Maple St" });
    expect(res.status).toBe(201);
    expect(await json(res)).toEqual({ id, role: "owner" });
    const me = await json(await call("GET", "/me", token));
    expect(me.memberships).toEqual([expect.objectContaining({ kind: "home", id, role: "owner", name: "Maple St" })]);
    const push = await call("POST", `/homes/${id}/sync/push`, token, {
      deviceId: "dev-a",
      ops: [{ outboxId: 1, entity: "home", entityId: id, op: "upsert", payload: JSON.stringify({ id, name: "Maple St" }), updatedAt: 10 }],
    });
    expect(push.status).toBe(200);
    expect((await call("GET", `/homes/${id}/sync/pull?since=0`, token)).status).toBe(200);
  });

  it("is a no-op for a home the caller already owns", async () => {
    const token = await seedUser("h-u2", "h-u2@x.com");
    const id = "7f1c2a4e-1b2c-4d5e-8f90-0a1b2c3d4e5f";
    expect((await call("PUT", `/homes/${id}`, token, { name: "Oak Ave" })).status).toBe(201);
    const again = await call("PUT", `/homes/${id}`, token, { name: "Oak Ave renamed" });
    expect(again.status).toBe(200);
    const rows = await env.DB.prepare("SELECT COUNT(*) AS n FROM memberships WHERE tenant_id = ?").bind(id).first<{ n: number }>();
    expect(rows?.n).toBe(1);
  });

  it("AC-B2: someone else's home id answers 404 and grants nothing", async () => {
    const owner = await seedUser("h-u3", "h-u3@x.com");
    const other = await seedUser("h-u4", "h-u4@x.com");
    const id = "8f1c2a4e-1b2c-4d5e-8f90-0a1b2c3d4e5f";
    await call("PUT", `/homes/${id}`, owner, { name: "Birch Ln" });
    expect((await call("PUT", `/homes/${id}`, other, { name: "Mine now" })).status).toBe(404);
    expect((await call("GET", `/homes/${id}/sync/pull?since=0`, other)).status).toBe(404);
  });

  it("refuses a bad id or a missing name, and needs a session", async () => {
    const token = await seedUser("h-u5", "h-u5@x.com");
    expect((await call("PUT", "/homes/not-a-uuid", token, { name: "X" })).status).toBe(400);
    expect((await call("PUT", "/homes/9f1c2a4e-1b2c-4d5e-8f90-0a1b2c3d4e5f", token, { name: " " })).status).toBe(400);
    expect((await call("PUT", "/homes/9f1c2a4e-1b2c-4d5e-8f90-0a1b2c3d4e5f", "nope", { name: "X" })).status).toBe(401);
  });
});
