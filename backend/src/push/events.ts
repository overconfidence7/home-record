import type { Row } from "../sync/merge";

/**
 * Which pushes a synced change triggers (AC-B30, AC-F36, AC-F42). Pure: the caller hands in the row before and after
 * the write and the company's today/tomorrow; the result names who gets what. The phone builds the words from its own
 * strings, so a message carries only ids and the names it shows.
 *
 * - task assigned: the assignee changes to someone (a new task with an assignee counts); not for a done or deleted
 *   task, and not when you assign yourself.
 * - schedule changed: an assignment on today or tomorrow is added, removed or moved to another job or member; the
 *   member it named before and the one it names now each hear about their own day.
 * - change order signed: the status becomes SIGNED; Owner and Office hear it (the recipients are resolved later).
 * - timesheet approved: an approval appears for a member's week; that member hears it.
 * Nobody is told about their own change.
 */
export type PushType = "task_assigned" | "schedule_changed" | "change_order_signed" | "timesheet_approved";

export interface PushEvent {
  type: PushType;
  /** A user id, or "admins" for the company's Owner and Office members. */
  to: string;
  jobId: string | null;
  data: Record<string, string>;
}

export interface CompanyDays {
  today: string;
  tomorrow: string;
}

const str = (value: unknown): string | null => (typeof value === "string" && value !== "" ? value : null);
const live = (row: Row | null): row is Row => row !== null && (row.deletedAt === null || row.deletedAt === undefined);

export function pushEventsFor(entity: string, before: Row | null, after: Row, pusherId: string, days: CompanyDays): PushEvent[] {
  const events = rawEvents(entity, before, after, days);
  return events.filter((e) => e.to !== pusherId);
}

function rawEvents(entity: string, before: Row | null, after: Row, days: CompanyDays): PushEvent[] {
  switch (entity) {
    case "task": {
      const assignee = str(after.assigneeId);
      if (!assignee || !live(after) || after.done === true) return [];
      if (live(before) && str(before.assigneeId) === assignee) return [];
      const jobId = str(after.jobId);
      return [{ type: "task_assigned", to: assignee, jobId, data: { taskId: String(after.id), jobId: jobId ?? "", title: str(after.title) ?? "" } }];
    }
    case "assignment": {
      const soon = (row: Row | null) => live(row) && (row.date === days.today || row.date === days.tomorrow);
      const was = soon(before) ? before : null;
      const now = soon(after) ? after : null;
      if (!was && !now) return [];
      if (was && now && was.date === now.date && was.jobId === now.jobId && was.memberId === now.memberId) return [];
      const out: PushEvent[] = [];
      for (const row of [was, now]) {
        if (!row) continue;
        const member = str(row.memberId);
        const date = str(row.date);
        if (!member || !date || out.some((e) => e.to === member && e.data.date === date)) continue;
        const jobId = str(row.jobId);
        out.push({ type: "schedule_changed", to: member, jobId, data: { date, jobId: jobId ?? "" } });
      }
      return out;
    }
    case "change_order": {
      if (!live(after) || after.status !== "SIGNED" || (live(before) && before.status === "SIGNED")) return [];
      const jobId = str(after.jobId);
      return [{
        type: "change_order_signed", to: "admins", jobId,
        data: { changeOrderId: String(after.id), jobId: jobId ?? "", title: str(after.title) ?? "", signedBy: str(after.signedBy) ?? "" },
      }];
    }
    case "timesheet_approval": {
      const member = str(after.memberId);
      const week = str(after.weekStart);
      if (!member || !week || !live(after) || live(before)) return [];
      return [{ type: "timesheet_approved", to: member, jobId: null, data: { weekStart: week } }];
    }
    default:
      return [];
  }
}

/** Today and tomorrow as `yyyy-MM-dd` in the company's time zone (an unknown zone falls back to Eastern). */
export function companyDays(now: number, timeZone: string | null | undefined): CompanyDays {
  const fmt = (zone: string) => new Intl.DateTimeFormat("en-CA", { timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit" });
  let format: Intl.DateTimeFormat;
  try {
    format = fmt(timeZone || DEFAULT_TIME_ZONE);
  } catch {
    format = fmt(DEFAULT_TIME_ZONE);
  }
  const today = format.format(new Date(now));
  const next = new Date(`${today}T12:00:00Z`);
  next.setUTCDate(next.getUTCDate() + 1);
  return { today, tomorrow: next.toISOString().slice(0, 10) };
}

export const DEFAULT_TIME_ZONE = "America/New_York";
