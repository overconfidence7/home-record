import { Hono } from "hono";
import { bodyLimit } from "hono/body-limit";
import { z } from "zod";
import type { AppEnv, TenantKind } from "../env";
import { membershipFor, requireSession, requireTenant } from "../tenancy";
import { mergeRow, type FieldTimes, type Row } from "../sync/merge";
import { ADMIN_ROLES, accessFor, canPush, visibleRow, visibleTimes, type Access } from "../sync/policy";

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
// Home tenants have no per-role gate; every home member edits the whole home.
const OPEN_ACCESS = (userId: string): Access => ({ role: "owner", userId, leadJobs: new Set() });
const PAGE = 500;
/** Largest push body (cr:sync-limits); the phones size their batches to half of it (SyncEngine.PUSH_BUDGET_BYTES). */
export const PUSH_MAX_BYTES = 2 * 1024 * 1024;
/** Largest payload of one op, in characters; a longer one is refused on its own so it never blocks the batch. */
export const OP_MAX_PAYLOAD = 256_000;
/** How long idempotency records and the conflict log are kept. A retry older than this is merged again, not deduped. */
export const SYNC_RETENTION_MS = 90 * 24 * 60 * 60 * 1000;

const PushBody = z.object({
  deviceId: z.string().min(1).max(64),
  ops: z
    .array(
      z.object({
        outboxId: z.number().int().nonnegative(),
        entity: z.string(),
        entityId: z.string().min(1).max(64),
        op: z.enum(["upsert", "delete"]),
        payload: z.string(),
        updatedAt: z.number().int(),
      }),
    )
    .max(200),
});

type PushOp = z.infer<typeof PushBody>["ops"][number];
type Applied =
  | { result: "accepted"; seq: number; conflicts: ReturnType<typeof mergeRow>["conflicts"] }
  | { result: "role" }
  | { result: "busy" };
const TRIES = 10;

// The row's server_seq, or -1 while it does not exist: the version a write compares against (?1..?2 tenant, ?5..?6 row).
const ROW_VERSION = `COALESCE((SELECT server_seq FROM sync_rows
  WHERE tenant_kind = ?1 AND tenant_id = ?2 AND entity = ?5 AND entity_id = ?6), -1)`;
// True inside the write batch once this op (?3 device, ?4 outbox row) took the tenant's newest seq and the row is
// still at the version the merge was computed from (?7).
const CLAIMED = `EXISTS (SELECT 1 FROM sync_applied a JOIN sync_counters c ON c.tenant_kind = a.tenant_kind AND c.tenant_id = a.tenant_id
  WHERE a.tenant_kind = ?1 AND a.tenant_id = ?2 AND a.device_id = ?3 AND a.outbox_id = ?4 AND a.server_seq = c.seq)
  AND ${ROW_VERSION} = ?7`;

/**
 * Applies one op as a compare-and-swap (cr:sync-seq-race). The idempotency record and the stored row are read in one
 * batch, the merge runs here, and the write is one batch (one transaction) that claims the next seq, bumps the
 * counter, logs conflicts and writes the row only while the row is still at the version that was read. A concurrent
 * push that changed the row first makes the batch a no-op and the op is merged again on the new row, so no edit is
 * lost, a seq is never taken without its row, and a retried duplicate is acknowledged instead of failing.
 */
async function applyOp(
  db: D1Database, kind: TenantKind, tenantId: string, deviceId: string, op: PushOp, incoming: Row,
  access: Access, authoritative: boolean,
): Promise<Applied> {
  for (let attempt = 0; attempt < TRIES; attempt++) {
    const [doneRes, storedRes] = await db.batch([
      db.prepare("SELECT server_seq FROM sync_applied WHERE tenant_kind = ? AND tenant_id = ? AND device_id = ? AND outbox_id = ?")
        .bind(kind, tenantId, deviceId, op.outboxId),
      db.prepare(
        "SELECT row_json, times_json, authoritative, server_seq FROM sync_rows WHERE tenant_kind = ? AND tenant_id = ? AND entity = ? AND entity_id = ?",
      ).bind(kind, tenantId, op.entity, op.entityId),
    ]);
    const done = (doneRes.results as { server_seq: number }[])[0];
    if (done) return { result: "accepted", seq: done.server_seq, conflicts: [] };
    const stored = (storedRes.results as { row_json: string; times_json: string; authoritative: number; server_seq: number }[])[0];
    const storedRow = stored ? (JSON.parse(stored.row_json) as Row) : null;
    // AC-B3: a role may only touch the rows AC-F3 gives it; the refusal is per op like the others.
    if (!canPush(access, op.entity, storedRow, incoming)) return { result: "role" };
    const merged = mergeRow(
      stored && storedRow ? { row: storedRow, times: JSON.parse(stored.times_json) as FieldTimes, authoritative: stored.authoritative === 1 } : null,
      incoming,
      op.updatedAt,
      authoritative,
      op.entity,
    );
    // Only punches become authoritative: an Owner/Office edit of a punch pins it against crew writes.
    const pin = op.entity === "punch" && authoritative && stored ? 1 : (stored?.authoritative ?? 0);
    const key = [kind, tenantId, deviceId, op.outboxId, op.entity, op.entityId, stored?.server_seq ?? -1] as const;
    const results = await db.batch([
      db.prepare("INSERT OR IGNORE INTO sync_counters (tenant_kind, tenant_id, seq) VALUES (?, ?, 0)").bind(kind, tenantId),
      db.prepare(
        `INSERT OR IGNORE INTO sync_applied (tenant_kind, tenant_id, device_id, outbox_id, server_seq, applied_at)
         SELECT ?1, ?2, ?3, ?4, seq + 1, ?8 FROM sync_counters WHERE tenant_kind = ?1 AND tenant_id = ?2 AND ${ROW_VERSION} = ?7`,
      ).bind(...key, Date.now()),
      db.prepare(
        `UPDATE sync_counters SET seq = seq + 1 WHERE tenant_kind = ?1 AND tenant_id = ?2 AND EXISTS (SELECT 1 FROM sync_applied a
           WHERE a.tenant_kind = ?1 AND a.tenant_id = ?2 AND a.device_id = ?3 AND a.outbox_id = ?4 AND a.server_seq = sync_counters.seq + 1)
         RETURNING seq`,
      ).bind(...key.slice(0, 4)),
      ...merged.conflicts.map((x) =>
        db.prepare(
          `INSERT INTO sync_conflicts (tenant_kind, tenant_id, entity, entity_id, field, kept_json, dropped_json, reason, device_id, at)
           SELECT ?1, ?2, ?5, ?6, ?8, ?9, ?10, ?11, ?3, ?12 WHERE ${CLAIMED}`,
        ).bind(...key, x.field, JSON.stringify(x.kept ?? null), JSON.stringify(x.dropped ?? null), x.reason, Date.now()),
      ),
      // Last, because writing the row moves its version and ends the claim.
      db.prepare(
        `INSERT INTO sync_rows (tenant_kind, tenant_id, entity, entity_id, row_json, times_json, authoritative, server_seq)
         SELECT ?1, ?2, ?5, ?6, ?8, ?9, ?10, c.seq FROM sync_counters c WHERE c.tenant_kind = ?1 AND c.tenant_id = ?2 AND ${CLAIMED}
         ON CONFLICT (tenant_kind, tenant_id, entity, entity_id) DO UPDATE SET
           row_json = excluded.row_json, times_json = excluded.times_json,
           authoritative = excluded.authoritative, server_seq = excluded.server_seq`,
      ).bind(...key, JSON.stringify(merged.row), JSON.stringify(merged.times), pin),
    ]);
    const bumped = (results[2].results as { seq: number }[])[0];
    if (bumped) return { result: "accepted", seq: bumped.seq, conflicts: merged.conflicts };
  }
  return { result: "busy" };
}

const kindOf = (param: string): TenantKind => (param === "companies" ? "company" : "home");

const pushLimit = bodyLimit({ maxSize: PUSH_MAX_BYTES, onError: (c) => c.json({ error: "too_large" }, 413) });

sync.post("/:kind{companies|homes}/:tenantId/sync/push", requireSession, requireTenant(), pushLimit, async (c) => {
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
  const rejected: { outboxId: number; reason: "entity" | "payload" | "too_large" | "role" }[] = [];
  const authoritative = ADMIN_ROLES.has(role);
  const userId = c.get("session").userId;
  const access = kind === "company" ? await accessFor(db, tenantId, userId, role) : OPEN_ACCESS(userId);

  for (const op of ops) {
    if (!ENTITIES[kind].has(op.entity)) {
      rejected.push({ outboxId: op.outboxId, reason: "entity" });
      continue;
    }
    if (op.payload.length > OP_MAX_PAYLOAD) {
      rejected.push({ outboxId: op.outboxId, reason: "too_large" });
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
    const outcome = await applyOp(db, kind, tenantId, deviceId, op, incoming, access, authoritative);
    if (outcome.result === "role") {
      rejected.push({ outboxId: op.outboxId, reason: "role" });
      continue;
    }
    // Lost the race on this row every try: stop here so later ops keep their order; the phone retries the rest.
    if (outcome.result === "busy") break;
    for (const x of outcome.conflicts) conflicts.push({ entity: op.entity, entityId: op.entityId, field: x.field, reason: x.reason });
    accepted.push({ outboxId: op.outboxId, serverSeq: outcome.seq });
  }
  return c.json({ accepted, conflicts, rejected });
});

sync.get("/:kind{companies|homes}/:tenantId/sync/pull", requireSession, requireTenant(), async (c) => {
  const kind = kindOf(c.req.param("kind"));
  const tenantId = c.req.param("tenantId");
  const since = Number(c.req.query("since") ?? "0");
  if (!Number.isInteger(since) || since < 0) return c.json({ error: "invalid" }, 400);
  const session = c.get("session");
  const role = membershipFor(session, kind, tenantId)?.role ?? "";
  const access = kind === "company" ? await accessFor(c.env.DB, tenantId, session.userId, role) : OPEN_ACCESS(session.userId);
  const { results } = await c.env.DB.prepare(
    `SELECT entity, entity_id, row_json, times_json, server_seq FROM sync_rows
     WHERE tenant_kind = ?1 AND tenant_id = ?2 AND server_seq > ?3
       AND server_seq <= (SELECT seq FROM sync_counters WHERE tenant_kind = ?1 AND tenant_id = ?2)
     ORDER BY server_seq LIMIT ?4`,
  )
    .bind(kind, tenantId, since, PAGE + 1)
    .all<{ entity: string; entity_id: string; row_json: string; times_json: string; server_seq: number }>();
  const more = results.length > PAGE;
  const page = results.slice(0, PAGE);
  // Rows this role may not see are skipped, but the cursor still moves past them (AC-B3).
  const rows = page.flatMap((r) => {
    const row = visibleRow(access, r.entity, JSON.parse(r.row_json) as Row);
    if (!row) return [];
    return [{ entity: r.entity, entityId: r.entity_id, row, times: visibleTimes(access, r.entity, JSON.parse(r.times_json)), serverSeq: r.server_seq }];
  });
  return c.json({ rows, serverSeq: page.length ? page[page.length - 1].server_seq : since, more });
});


/** Daily cron (cr:sync-limits): drops idempotency records and conflict log rows older than [SYNC_RETENTION_MS]. */
export async function pruneSync(db: D1Database, now = Date.now()): Promise<void> {
  const before = now - SYNC_RETENTION_MS;
  await db.batch([
    db.prepare("DELETE FROM sync_applied WHERE applied_at < ?").bind(before),
    db.prepare("DELETE FROM sync_conflicts WHERE at < ?").bind(before),
  ]);
}
