import { env } from "cloudflare:workers";
import { issueToken } from "../src/session";

export async function seedUser(id: string, email: string) {
  await env.DB.prepare("INSERT INTO users (id, email, created_at) VALUES (?, ?, ?)").bind(id, email, 1).run();
  return issueToken(env.JWT_SECRET, id);
}

export async function seedCompany(id: string, name: string, ownerId: string, role = "owner") {
  await env.DB.prepare("INSERT INTO companies (id, name, trial_ends_at, created_at) VALUES (?, ?, ?, ?)")
    .bind(id, name, 1_900_000_000_000, 1)
    .run();
  await env.DB.prepare("INSERT INTO memberships (id, user_id, tenant_kind, tenant_id, role, created_at) VALUES (?, ?, 'company', ?, ?, 1)")
    .bind(`m-${id}-${ownerId}`, ownerId, id, role)
    .run();
}
