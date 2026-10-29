import type { Context, MiddlewareHandler } from "hono";
import type { AppEnv, Membership, Session, TenantKind } from "./env";
import { issueToken, readToken } from "./session";

export async function loadMemberships(db: D1Database, userId: string): Promise<Membership[]> {
  const { results } = await db
    .prepare("SELECT tenant_kind, tenant_id, role FROM memberships WHERE user_id = ? ORDER BY created_at")
    .bind(userId)
    .all<{ tenant_kind: TenantKind; tenant_id: string; role: string }>();
  return results.map((r) => ({ tenantKind: r.tenant_kind, tenantId: r.tenant_id, role: r.role }));
}

/**
 * Requires a valid bearer token of the user's current token_version, loads memberships, and refreshes
 * the token on use (AC-B1). A token from before a revoke (`POST /auth/revoke`) answers 401.
 */
export const requireSession: MiddlewareHandler<AppEnv> = async (c, next) => {
  const header = c.req.header("Authorization") ?? "";
  const token = header.startsWith("Bearer ") ? header.slice(7) : "";
  const claims = token ? await readToken(c.env.JWT_SECRET, token) : null;
  if (!claims) return c.json({ error: "unauthorized" }, 401);
  const user = await c.env.DB.prepare("SELECT token_version FROM users WHERE id = ?")
    .bind(claims.userId)
    .first<{ token_version: number }>();
  if (!user || user.token_version !== claims.version) return c.json({ error: "unauthorized" }, 401);
  const session: Session = { userId: claims.userId, memberships: await loadMemberships(c.env.DB, claims.userId) };
  c.set("session", session);
  await next();
  if (c.get("sessionEnded")) return;
  c.res.headers.set("X-Refreshed-Token", await issueToken(c.env.JWT_SECRET, claims.userId, Date.now(), user.token_version));
};

/**
 * The caller's membership in a tenant, or null. Callers answer null with 404 so a
 * request for someone else's tenant is indistinguishable from one that does not exist (AC-B2).
 */
export function membershipFor(session: Session, kind: TenantKind, tenantId: string): Membership | null {
  return session.memberships.find((m) => m.tenantKind === kind && m.tenantId === tenantId) ?? null;
}

export function notFound(c: Context): Response {
  return c.json({ error: "not_found" }, 404);
}

/** Middleware for /:kind/:tenantId/* routes: 404 unless the session belongs to that tenant. */
export function requireTenant(allowedRoles?: string[]): MiddlewareHandler<AppEnv> {
  return async (c, next) => {
    const kind = c.req.param("kind") === "companies" ? "company" : c.req.param("kind") === "homes" ? "home" : null;
    const membership = kind ? membershipFor(c.get("session"), kind, c.req.param("tenantId") ?? "") : null;
    if (!membership || (allowedRoles && !allowedRoles.includes(membership.role))) return notFound(c);
    await next();
  };
}
