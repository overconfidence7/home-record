/** A fixed-window limit: at most [max] hits per [windowMs]. */
export interface Limit {
  name: string;
  max: number;
  windowMs: number;
}

const HOUR = 3_600_000;

export const START_PER_EMAIL: Limit = { name: "start-email", max: 5, windowMs: HOUR };
export const START_PER_IP: Limit = { name: "start-ip", max: 20, windowMs: HOUR };
/**
 * Code checks per email across every code issued in the window; a new start does not reset it.
 * Counted before the check (so parallel guesses cannot slip past), which also counts the one right guess.
 */
export const VERIFY_PER_EMAIL: Limit = { name: "verify-email", max: 10, windowMs: HOUR };
export const VERIFY_PER_IP: Limit = { name: "verify-ip", max: 30, windowMs: HOUR };

const key = (limit: Limit, subject: string) => `${limit.name}:${subject}`;

/** Counts one hit and returns false when it goes over the limit. One atomic statement, so concurrent requests cannot both slip under. */
export async function hit(db: D1Database, limit: Limit, subject: string, now = Date.now()): Promise<boolean> {
  const row = await db
    .prepare(
      "INSERT INTO auth_limits (key, count, window_start) VALUES (?1, 1, ?2) " +
        "ON CONFLICT(key) DO UPDATE SET " +
        "count = CASE WHEN window_start <= ?2 - ?3 THEN 1 ELSE count + 1 END, " +
        "window_start = CASE WHEN window_start <= ?2 - ?3 THEN ?2 ELSE window_start END " +
        "RETURNING count",
    )
    .bind(key(limit, subject), now, limit.windowMs)
    .first<{ count: number }>();
  return (row?.count ?? 0) <= limit.max;
}

/** Limits hold everywhere except the local dev Worker the QA suite signs in to many times a run. */
export function limitsApply(env: { ENVIRONMENT: string }): boolean {
  return env.ENVIRONMENT !== "dev";
}

/** Drops windows older than a day; run from the daily cron. */
export async function pruneLimits(db: D1Database, now = Date.now()): Promise<void> {
  await db.prepare("DELETE FROM auth_limits WHERE window_start < ?").bind(now - 24 * HOUR).run();
}

/** The caller's IP as Cloudflare reports it; requests without it (tests, local tools) share one bucket. */
export function clientIp(headers: Headers): string {
  return headers.get("cf-connecting-ip") ?? "unknown";
}
