/**
 * Field Log plans (AC-F56): a 14-day trial with everything on, then Solo (1 member) or Crew (up to 10 members),
 * flat price, no per-seat charge (SPEC pricing). The entitlement ids are RevenueCat's; the backend learns them from
 * the webhook (AC-B50) and every gate (joining, /me) reads them through here so both apps and the server agree.
 */
export const SOLO = "fieldlog_solo";
export const CREW = "fieldlog_crew";
export const HOME_PRO = "pro";
export const CREW_LIMIT = 10;
export const SOLO_LIMIT = 1;

export type CompanyPlan = "trial" | "solo" | "crew" | "expired";

export interface EntitlementRow {
  entitlement: string | null;
  entitlement_expires_at?: number | null;
  trial_ends_at?: number | null;
}

/** The entitlement still in force at `now`, or null when there is none or it lapsed. */
export function activeEntitlement(row: EntitlementRow, now: number): string | null {
  if (!row.entitlement) return null;
  const exp = row.entitlement_expires_at;
  return exp == null || exp > now ? row.entitlement : null;
}

/** A paid plan wins over the trial; the trial gives Crew's limit; after both lapse the company is expired. */
export function companyPlan(row: EntitlementRow, now: number): CompanyPlan {
  const ent = activeEntitlement(row, now);
  if (ent === CREW) return "crew";
  if (ent === SOLO) return "solo";
  if (row.trial_ends_at != null && row.trial_ends_at > now) return "trial";
  return "expired";
}

/** How many members (Owner included) the company may have; 0 when expired, so nobody new can join. */
export function memberLimit(plan: CompanyPlan): number {
  switch (plan) {
    case "trial":
    case "crew":
      return CREW_LIMIT;
    case "solo":
      return SOLO_LIMIT;
    case "expired":
      return 0;
  }
}

/**
 * True when the company's trial and paid plan have both lapsed (AC-F56 "verified on the backend"): an expired company
 * is read only, so sync push and media uploads answer 402 `subscription_required` until it renews. Pulls and reads
 * stay open so crew still see their own week. A company row that is missing is not expired here; the tenancy check
 * answers that case.
 */
export async function companyExpired(db: D1Database, companyId: string, now: number): Promise<boolean> {
  const row = await db
    .prepare("SELECT entitlement, entitlement_expires_at, trial_ends_at FROM companies WHERE id = ?")
    .bind(companyId)
    .first<EntitlementRow>();
  return row != null && companyPlan(row, now) === "expired";
}

export const SUBSCRIPTION_REQUIRED = { error: "subscription_required" } as const;
