import type { Row } from "./merge";

/**
 * Who may push and pull which company rows (AC-F3, AC-B3). Owner and Office may do everything; Lead, Crew and Sub
 * are limited per entity. Home tenants are not gated here: every home member edits the whole home.
 *
 * - Lead: own time, plus the crew's time, tasks, files, logs, change orders, materials and schedule on the jobs
 *   they are on (job_crew or assignment); captures on any job; the job's house model.
 * - Crew and Sub: own time, captures (photo, note) on any job, and only the tasks assigned to them.
 * - Nobody but Owner and Office writes the company, members, clients, jobs, crew lists, approvals or share links.
 * - Pull: Crew and Sub get only their own punches, shifts and approvals (Lead also the time on their jobs), and
 *   nobody but Owner and Office sees pay rates.
 */
export const ADMIN_ROLES = new Set(["owner", "office"]);

const ADMIN_ONLY = new Set(["company", "member", "client", "job", "job_crew", "timesheet_approval", "share_link"]);
const OWN_TIME = new Set(["punch", "shift"]);
const LEAD_JOB_ROWS = new Set(["job_file", "daily_log", "change_order", "material", "assignment", "task"]);
const CAPTURE = new Set(["photo", "note"]);
const JOB_HOUSE = new Set(["home", "floor", "room", "wall", "opening", "stair"]);
const PRIVATE_TO_MEMBER = new Set(["punch", "shift", "timesheet_approval"]);
const ADMIN_FIELDS: Record<string, string[]> = { member: ["rateCents", "fcmToken"] };

export interface Access {
  role: string;
  userId: string;
  /** Jobs this member is on through job_crew or assignment rows; only loaded for Lead. */
  leadJobs: Set<string>;
}

const str = (value: unknown): string | null => (typeof value === "string" && value !== "" ? value : null);

/** Field of the row as it will be after the write: the incoming value when sent, else the stored one. */
const after = (stored: Row | null, incoming: Row, field: string): string | null =>
  field in incoming ? str(incoming[field]) : str(stored?.[field]);

/** True when a field the row is scoped by is left as stored (or set once on a new row). */
const unchanged = (stored: Row | null, incoming: Row, field: string): boolean =>
  stored === null || !(field in incoming) || str(incoming[field]) === str(stored[field]);

function onLeadJob(access: Access, stored: Row | null, incoming: Row): boolean {
  if (access.role !== "lead") return false;
  const jobs = [after(stored, incoming, "jobId"), str(stored?.jobId)].filter((j): j is string => j !== null);
  return jobs.length > 0 && jobs.every((j) => access.leadJobs.has(j));
}

/** May this caller write this row of a company tenant? `stored` is the row as the server has it, if any. */
export function canPush(access: Access, entity: string, stored: Row | null, incoming: Row): boolean {
  if (ADMIN_ROLES.has(access.role)) return true;
  if (ADMIN_ONLY.has(entity)) return false;
  if (OWN_TIME.has(entity)) {
    if (!unchanged(stored, incoming, "memberId")) return false;
    if (after(stored, incoming, "memberId") === access.userId) return true;
    return onLeadJob(access, stored, incoming);
  }
  if (CAPTURE.has(entity)) return true;
  if (entity === "task" && access.role !== "lead") {
    // Crew mark their own tasks done; they cannot create tasks or hand them to someone else.
    return stored !== null && str(stored.assigneeId) === access.userId && unchanged(stored, incoming, "assigneeId");
  }
  if (LEAD_JOB_ROWS.has(entity)) return onLeadJob(access, stored, incoming);
  if (JOB_HOUSE.has(entity)) return access.role === "lead";
  return false;
}

/**
 * May this caller upload the media of this company row (cr:presign-overwrite)? With the row on the server it is the
 * push rule; before the row has synced (the upload queue can run first) it is the entity rule alone: captures for
 * everyone, a Lead's job rows (the row push that follows is still checked against their jobs), the rest admin only.
 */
export function canUpload(access: Access, entity: string, stored: Row | null): boolean {
  if (stored !== null) return canPush(access, entity, stored, {});
  if (ADMIN_ROLES.has(access.role) || CAPTURE.has(entity)) return true;
  return access.role === "lead" && LEAD_JOB_ROWS.has(entity);
}

/** The row as this caller may see it on pull, or null when it is not theirs to see. */
export function visibleRow(access: Access, entity: string, row: Row): Row | null {
  if (ADMIN_ROLES.has(access.role)) return row;
  if (PRIVATE_TO_MEMBER.has(entity) && str(row.memberId) !== access.userId) {
    const jobId = str(row.jobId);
    if (!(access.role === "lead" && entity !== "timesheet_approval" && jobId !== null && access.leadJobs.has(jobId))) return null;
  }
  const hidden = ADMIN_FIELDS[entity];
  if (!hidden) return row;
  const copy = { ...row };
  for (const field of hidden) delete copy[field];
  return copy;
}

/** Field times for hidden fields are dropped too, so a client cannot learn when a rate changed. */
export function visibleTimes(access: Access, entity: string, times: Record<string, number>): Record<string, number> {
  const hidden = ADMIN_FIELDS[entity];
  if (ADMIN_ROLES.has(access.role) || !hidden) return times;
  const copy = { ...times };
  for (const field of hidden) delete copy[field];
  return copy;
}

/** Jobs a Lead is on: live job_crew and assignment rows naming them. */
export async function loadLeadJobs(db: D1Database, tenantId: string, userId: string): Promise<Set<string>> {
  const { results } = await db
    .prepare(
      `SELECT json_extract(row_json, '$.jobId') AS job_id FROM sync_rows
       WHERE tenant_kind = 'company' AND tenant_id = ? AND entity IN ('job_crew', 'assignment')
         AND json_extract(row_json, '$.memberId') = ? AND json_extract(row_json, '$.deletedAt') IS NULL`,
    )
    .bind(tenantId, userId)
    .all<{ job_id: string | null }>();
  return new Set(results.map((r) => r.job_id).filter((j): j is string => typeof j === "string"));
}

export async function accessFor(db: D1Database, tenantId: string, userId: string, role: string): Promise<Access> {
  return { role, userId, leadJobs: role === "lead" ? await loadLeadJobs(db, tenantId, userId) : new Set() };
}
