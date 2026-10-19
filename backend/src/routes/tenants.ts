import { Hono } from "hono";
import type { AppEnv } from "../env";
import { notFound, requireSession, requireTenant } from "../tenancy";

export const tenants = new Hono<AppEnv>();

/** Basic tenant lookup; also the reference for how every tenant-scoped route is guarded (AC-B2). */
tenants.get("/:kind{companies|homes}/:tenantId", requireSession, requireTenant(), async (c) => {
  const table = c.req.param("kind") === "companies" ? "companies" : "homes";
  const row = await c.env.DB.prepare(`SELECT * FROM ${table} WHERE id = ?`).bind(c.req.param("tenantId")).first();
  return row ? c.json(row) : notFound(c);
});
