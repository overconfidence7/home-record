import type { Bindings } from "../env";
import { accessToken, parseServiceAccount, sendData, type ServiceAccount } from "./fcm";
import type { PushEvent } from "./events";

/** How long the push log is kept (pruned by the daily cron). */
export const PUSH_LOG_RETENTION_MS = 30 * 24 * 60 * 60 * 1000;

/**
 * Delivers the events of one sync push to the Field Log phones of each recipient (AC-B30). A recipient must still be
 * a member of the company (Owner and Office for "admins"); the pusher never hears about their own change. Every
 * notification is written to `push_log` whether or not FCM is configured, so a local run shows what would have gone.
 */
export async function deliver(env: Bindings, companyId: string, pusherId: string, events: PushEvent[], now = Date.now()): Promise<void> {
  if (events.length === 0) return;
  const db = env.DB;
  const sa = parseServiceAccount(env.FCM_SERVICE_ACCOUNT);
  let access: string | null | undefined;
  const jobNames = new Map<string, string>();

  for (const event of events) {
    const recipients = await recipientsFor(db, companyId, event.to);
    const targets = recipients.filter((u) => u !== pusherId);
    if (targets.length === 0) continue;
    const data: Record<string, string> = { type: event.type, companyId, ...event.data };
    if (event.jobId) {
      if (!jobNames.has(event.jobId)) jobNames.set(event.jobId, await jobName(db, companyId, event.jobId));
      data.jobName = jobNames.get(event.jobId) ?? "";
    }
    for (const userId of targets) {
      let delivered = 0;
      if (sa) {
        if (access === undefined) access = await accessToken(env.CACHE, sa, now).catch((e) => (console.error(e), null));
        if (access) delivered = await sendToUser(db, sa, access, userId, data);
      }
      await db.prepare("INSERT INTO push_log (user_id, tenant_id, type, data_json, delivered, at) VALUES (?, ?, ?, ?, ?, ?)")
        .bind(userId, companyId, event.type, JSON.stringify(data), delivered, now)
        .run();
    }
  }
}

async function recipientsFor(db: D1Database, companyId: string, to: string): Promise<string[]> {
  if (to === "admins") {
    const { results } = await db
      .prepare("SELECT user_id FROM memberships WHERE tenant_kind = 'company' AND tenant_id = ? AND role IN ('owner', 'office') ORDER BY user_id")
      .bind(companyId)
      .all<{ user_id: string }>();
    return results.map((r) => r.user_id);
  }
  const member = await db.prepare("SELECT 1 AS ok FROM memberships WHERE tenant_kind = 'company' AND tenant_id = ? AND user_id = ?")
    .bind(companyId, to)
    .first<{ ok: number }>();
  return member ? [to] : [];
}

async function jobName(db: D1Database, companyId: string, jobId: string): Promise<string> {
  const row = await db
    .prepare("SELECT json_extract(row_json, '$.name') AS name FROM sync_rows WHERE tenant_kind = 'company' AND tenant_id = ? AND entity = 'job' AND entity_id = ?")
    .bind(companyId, jobId)
    .first<{ name: string | null }>();
  return row?.name ?? "";
}

async function sendToUser(db: D1Database, sa: ServiceAccount, access: string, userId: string, data: Record<string, string>): Promise<number> {
  const { results } = await db.prepare("SELECT token FROM push_tokens WHERE user_id = ? AND app = 'field-log'").bind(userId).all<{ token: string }>();
  let delivered = 0;
  for (const { token } of results) {
    const result = await sendData(sa, access, token, data).catch((e) => (console.error(e), "failed" as const));
    if (result === "sent") delivered++;
    if (result === "gone") await db.prepare("DELETE FROM push_tokens WHERE token = ?").bind(token).run();
  }
  return delivered;
}

/** Daily cron: drops push log rows older than [PUSH_LOG_RETENTION_MS]. */
export async function prunePushLog(db: D1Database, now = Date.now()): Promise<void> {
  await db.prepare("DELETE FROM push_log WHERE at < ?").bind(now - PUSH_LOG_RETENTION_MS).run();
}
