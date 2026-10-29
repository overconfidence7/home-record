import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import app from "../src/index";
import { OP_MAX_PAYLOAD, PUSH_MAX_BYTES, SYNC_RETENTION_MS, pruneSync } from "../src/routes/sync";
import { seedCompany, seedUser } from "./helpers";

// AC-B20 (cr:sync-limits): the push body is capped, an oversized op is refused on its own, and the sync bookkeeping
// tables are pruned by the daily cron.
const push = (token: string, company: string, body: string) =>
  app.request(
    `/companies/${company}/sync/push`,
    { method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, body },
    env,
  );
const op = (outboxId: number, entityId: string, row: Record<string, unknown>, updatedAt = 10) => ({
  outboxId, entity: "task", entityId, op: "upsert", payload: JSON.stringify({ id: entityId, ...row }), updatedAt,
});

describe("AC-B20 push limits", () => {
  it("answers 413 to a body over the limit and applies nothing", async () => {
    const owner = await seedUser("sl-o1", "sl-o1@x.com");
    await seedCompany("sl-c1", "Limits", "sl-o1");
    const big = "x".repeat(OP_MAX_PAYLOAD - 100);
    const ops = Array.from({ length: Math.ceil(PUSH_MAX_BYTES / big.length) + 1 }, (_, i) => op(i + 1, `t${i}`, { title: big }));
    const res = await push(owner, "sl-c1", JSON.stringify({ deviceId: "d", ops }));
    expect(res.status).toBe(413);
    expect(await res.json()).toEqual({ error: "too_large" });
    const pulled = (await (await app.request("/companies/sl-c1/sync/pull?since=0", { headers: { Authorization: `Bearer ${owner}` } }, env)).json()) as { rows: unknown[] };
    expect(pulled.rows).toEqual([]);
  });

  it("refuses an op whose payload is over the per-op limit on its own; the rest of the batch applies", async () => {
    const owner = await seedUser("sl-o2", "sl-o2@x.com");
    await seedCompany("sl-c2", "Limits", "sl-o2");
    const ops = [op(1, "a", { title: "Fine" }), op(2, "b", { title: "y".repeat(OP_MAX_PAYLOAD) }), op(3, "c", { title: "Also fine" })];
    const res = await push(owner, "sl-c2", JSON.stringify({ deviceId: "d", ops }));
    expect(res.status).toBe(200);
    const body = (await res.json()) as { accepted: { outboxId: number }[]; rejected: { outboxId: number; reason: string }[] };
    expect(body.accepted.map((a) => a.outboxId)).toEqual([1, 3]);
    expect(body.rejected).toEqual([{ outboxId: 2, reason: "too_large" }]);
  });
});

describe("AC-B20 pruning", () => {
  it("drops idempotency records and conflicts older than the retention window and keeps newer ones", async () => {
    const owner = await seedUser("sl-o3", "sl-o3@x.com");
    await seedCompany("sl-c3", "Prune", "sl-o3");
    const send = (ops: unknown[]) => push(owner, "sl-c3", JSON.stringify({ deviceId: "d", ops }));
    expect((await send([op(1, "t", { title: "New" }, 20)])).status).toBe(200);
    expect((await send([op(2, "t", { title: "Old" }, 10)])).status).toBe(200); // logs an "older" conflict
    const now = Date.now();
    const count = async (table: string) =>
      (await env.DB.prepare(`SELECT COUNT(*) AS n FROM ${table} WHERE tenant_id = 'sl-c3'`).first<{ n: number }>())?.n;
    expect(await count("sync_applied")).toBe(2);
    expect(await count("sync_conflicts")).toBe(1);

    await pruneSync(env.DB, now);
    expect(await count("sync_applied")).toBe(2);
    expect(await count("sync_conflicts")).toBe(1);

    await env.DB.prepare("UPDATE sync_applied SET applied_at = ? WHERE tenant_id = 'sl-c3' AND outbox_id = 1").bind(now - SYNC_RETENTION_MS - 1).run();
    await pruneSync(env.DB, now);
    expect(await count("sync_applied")).toBe(1);
    expect(await count("sync_conflicts")).toBe(1);

    await pruneSync(env.DB, now + SYNC_RETENTION_MS + 1);
    expect(await count("sync_applied")).toBe(0);
    expect(await count("sync_conflicts")).toBe(0);
    // The rows themselves are never pruned.
    expect((await env.DB.prepare("SELECT COUNT(*) AS n FROM sync_rows WHERE tenant_id = 'sl-c3'").first<{ n: number }>())?.n).toBe(1);
  });

  it("the daily cron prunes sync bookkeeping", async () => {
    const owner = await seedUser("sl-o4", "sl-o4@x.com");
    await seedCompany("sl-c4", "Cron", "sl-o4");
    expect((await push(owner, "sl-c4", JSON.stringify({ deviceId: "d", ops: [op(1, "t", { title: "A" })] }))).status).toBe(200);
    await env.DB.prepare("UPDATE sync_applied SET applied_at = 0 WHERE tenant_id = 'sl-c4'").run();
    const pending: Promise<unknown>[] = [];
    const ctx = { waitUntil: (p: Promise<unknown>) => pending.push(p), passThroughOnException: () => {} } as unknown as ExecutionContext;
    app.scheduled({ cron: "0 3 * * *", scheduledTime: Date.now() } as ScheduledEvent, env, ctx);
    await Promise.all(pending);
    expect((await env.DB.prepare("SELECT COUNT(*) AS n FROM sync_applied WHERE tenant_id = 'sl-c4'").first<{ n: number }>())?.n).toBe(0);
  });
});
