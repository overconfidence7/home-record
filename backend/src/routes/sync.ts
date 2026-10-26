import { Hono } from "hono";
import { z } from "zod";
import type { AppEnv, TenantKind } from "../env";
import { membershipFor, requireSession, requireTenant } from "../tenancy";
import { mergeRow, type FieldTimes, type Row } from "../sync/merge";

export const sync = new Hono<AppEnv>();

const COMPANY_ENTITIES = [
  "company", "member", "client", "job", "job_crew", "punch", "shift", "timesheet_approval", "job_file", "note",
  "daily_log", "task", "change_order", "assignment", "material", "share_link",
];
const HOME_ENTITIES = [
  "home", "floor", "room", "wall", "wall_photo", "opening", "stair", "overlay", "item", "warranty", "consumable",
  "service_record", "document", "photo", "contact", "maintenance_task", "panel", "breaker", "breaker_link",
];
// A job's house model (AC-F48) syncs inside the company tenant; the handoff copies it to a home tenant (AC-B40).
// The rows Field Log writes for a job today: FL-JobEdit (home, floor, room), FL-House's plan editor (wall, opening,
// stair) and job photos. Add overlays and items here when FL-House writes them.
const JOB_HOME_ENTITIES = ["home", "floor", "room", "wall", "opening", "stair", "photo"];
const ENTITIES: Record<TenantKind, Set<string>> = {
  company: new Set([...COMPANY_ENTITIES, ...JOB_HOME_ENTITIES]),
  home: new Set(HOME_ENTITIES),
};
const AUTHORITATIVE_ROLES = new Set(["owner", "office"]);
const PAGE = 500;

const PushBody = z.object({
  deviceId: z.string().min(1).max(64),
  ops: z
    .array(
      z.object({
        outboxId: z.number().int().nonnegative(),
        entity: z.string(),
        entityId: z.string().min(1).max(64),
        op: z.enum(["upsert", "delete"]),
        payload: z.string().max(256_000),
        updatedAt: z.number().int(),
      }),
    )
    .max(200),
});

const kindOf = (param: string): TenantKind => (param === "companies" ? "company" : "home");

sync.post("/:kind{companies|homes}/:tenantId/sync/push", requireSession, requireTenant(), async (c) => {
  const kind = kindOf(c.req.param("kind"));
  const tenantId = c.req.param("tenantId");
  const role = membershipFor(c.get("session"), kind, tenantId)?.role ?? "";
  const parsed = PushBody.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "invalid" }, 400);
  const { deviceId, ops } = parsed.data;
  const db = c.env.DB;
  const accepted: { outboxId: number; serverSeq: number }[] = [];
  const conflicts: { entity: string; entityId: string; field: string; reason: string }[] = [];
  // A refused op is answered on its own and the rest of the batch still applies, so one bad row never blocks a phone.
  const rejected: { outboxId: number; reason: "entity" | "payload" }[] = [];
  const authoritative = AUTHORITATIVE_ROLES.has(role);

  for (const op of ops) {
    if (!ENTITIES[kind].has(op.entity)) {
      rejected.push({ outboxId: op.outboxId, reason: "entity" });
      continue;
    }
    let incoming: Row;
    try {
      const value = JSON.parse(op.payload);
      if (!value || typeof value !== "object" || Array.isArray(value) || value.id !== op.entityId) throw new Error("shape");
      incoming = value as Row;
    } catch {
      rejected.push({ outboxId: op.outboxId, reason: "payload" });
      continue;
    }
    const done = await db
      .prepare("SELECT server_seq FROM sync_applied WHERE tenant_kind = ? AND tenant_id = ? AND device_id = ? AND outbox_id = ?")
      .bind(kind, tenantId, deviceId, op.outboxId)
      .first<{ server_seq: number }>();
    if (done) {
      accepted.push({ outboxId: op.outboxId, serverSeq: done.server_seq });
      continue;
    }
    const stored = await db
      .prepare("SELECT row_json, times_json, authoritative FROM sync_rows WHERE tenant_kind = ? AND tenant_id = ? AND entity = ? AND entity_id = ?")
      .bind(kind, tenantId, op.entity, op.entityId)
      .first<{ row_json: string; times_json: string; authoritative: number }>();
    const merged = mergeRow(
      stored ? { row: JSON.parse(stored.row_json), times: JSON.parse(stored.times_json) as FieldTimes, authoritative: stored.authoritative === 1 } : null,
      incoming,
      op.updatedAt,
      authoritative,
      op.entity,
    );
    await db.prepare("INSERT OR IGNORE INTO sync_counters (tenant_kind, tenant_id, seq) VALUES (?, ?, 0)").bind(kind, tenantId).run();
    const counter = await db
      .prepare("UPDATE sync_counters SET seq = seq + 1 WHERE tenant_kind = ? AND tenant_id = ? RETURNING seq")
      .bind(kind, tenantId)
      .first<{ seq: number }>();
    const seq = counter?.seq ?? 0;
    // Only punches become authoritative: an Owner/Office edit of a punch pins it against crew writes.
    const pin = op.entity === "punch" && authoritative && stored !== null ? 1 : (stored?.authoritative ?? 0);
    const statements = [
      db
        .prepare(
          `INSERT INTO sync_rows (tenant_kind, tenant_id, entity, entity_id, row_json, times_json, authoritative, server_seq)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?)
           ON CONFLICT (tenant_kind, tenant_id, entity, entity_id) DO UPDATE SET
             row_json = excluded.row_json, times_json = excluded.times_json,
             authoritative = excluded.authoritative, server_seq = excluded.server_seq`,
        )
        .bind(kind, tenantId, op.entity, op.entityId, JSON.stringify(merged.row), JSON.stringify(merged.times), pin, seq),
      db
        .prepare("INSERT INTO sync_applied (tenant_kind, tenant_id, device_id, outbox_id, server_seq) VALUES (?, ?, ?, ?, ?)")
        .bind(kind, tenantId, deviceId, op.outboxId, seq),
      ...merged.conflicts.map((x) =>
        db
          .prepare(
            `INSERT INTO sync_conflicts (tenant_kind, tenant_id, entity, entity_id, field, kept_json, dropped_json, reason, device_id, at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
          )
          .bind(kind, tenantId, op.entity, op.entityId, x.field, JSON.stringify(x.kept ?? null), JSON.stringify(x.dropped ?? null), x.reason, deviceId, Date.now()),
      ),
    ];
    await db.batch(statements);
    for (const x of merged.conflicts) conflicts.push({ entity: op.entity, entityId: op.entityId, field: x.field, reason: x.reason });
    accepted.push({ outboxId: op.outboxId, serverSeq: seq });
  }
  return c.json({ accepted, conflicts, rejected });
});

sync.get("/:kind{companies|homes}/:tenantId/sync/pull", requireSession, requireTenant(), async (c) => {
  const kind = kindOf(c.req.param("kind"));
  const since = Number(c.req.query("since") ?? "0");
  if (!Number.isInteger(since) || since < 0) return c.json({ error: "invalid" }, 400);
  const { results } = await c.env.DB.prepare(
    `SELECT entity, entity_id, row_json, times_json, server_seq FROM sync_rows
     WHERE tenant_kind = ? AND tenant_id = ? AND server_seq > ? ORDER BY server_seq LIMIT ?`,
  )
    .bind(kind, c.req.param("tenantId"), since, PAGE + 1)
    .all<{ entity: string; entity_id: string; row_json: string; times_json: string; server_seq: number }>();
  const more = results.length > PAGE;
  const rows = results.slice(0, PAGE).map((r) => ({
    entity: r.entity,
    entityId: r.entity_id,
    row: JSON.parse(r.row_json),
    times: JSON.parse(r.times_json),
    serverSeq: r.server_seq,
  }));
  return c.json({ rows, serverSeq: rows.length ? rows[rows.length - 1].serverSeq : since, more });
});

