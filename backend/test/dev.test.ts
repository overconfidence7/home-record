import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import app from "../src/index";

const devEnv = { ...env, ENVIRONMENT: "dev" };
const call = (path: string, init?: RequestInit, e: object = devEnv) => app.request(path, init, e);
const post = (path: string, body: unknown, e: object = devEnv) =>
  call(path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }, e);

describe("AC-B2 dev routes", () => {
  it("last-code returns the code just issued", async () => {
    await post("/auth/email/start", { email: "Qa@Example.com" });
    const res = await call("/dev/last-code?email=qa@example.com");
    expect(res.status).toBe(200);
    const { code } = (await res.json()) as { code: string };
    expect(code).toMatch(/^\d{6}$/);
    const ok = await post("/auth/email/verify", { email: "qa@example.com", code });
    expect(ok.status).toBe(200);
  });
  it("seed creates QA Electric with an owner and a crew member, reset clears it", async () => {
    const res = await post("/dev/seed", { scenario: "regression" });
    expect(res.status).toBe(200);
    const n = await env.DB.prepare("SELECT COUNT(*) AS n FROM memberships WHERE tenant_id = 'qa-company'").first<{ n: number }>();
    expect(n?.n).toBe(2);
    expect((await post("/dev/reset", {})).status).toBe(200);
    const u = await env.DB.prepare("SELECT COUNT(*) AS n FROM users").first<{ n: number }>();
    expect(u?.n).toBe(0);
  });
  it("reset also clears media objects, the lookup log and lookup quotas (cr:dev-reset-tables)", async () => {
    await env.DB.prepare("INSERT INTO media_objects (object_key, tenant_kind, tenant_id, created_at) VALUES ('co/photo/p/a.jpg', 'company', 'co', 1)").run();
    await env.DB.prepare(
      "INSERT INTO lookup_log (id, user_id, kind, cache_key, input_tokens, output_tokens, searches, created_at) VALUES ('l1', 'u', 'warranty', '', 1, 1, 0, 1)",
    ).run();
    await env.CACHE.put("quota:lookup:u:2026-10-27", "3");
    await env.DB.prepare("INSERT INTO lookup_quota (user_id, day, used) VALUES ('u', '2026-10-27', 3)").run();
    await env.CACHE.put("warranty:keep", "{}");
    await env.MEDIA.put("co/photo/p/a.jpg", "x");
    expect((await post("/dev/reset", {})).status).toBe(200);
    for (const t of ["media_objects", "lookup_log", "lookup_quota"]) {
      expect((await env.DB.prepare(`SELECT COUNT(*) AS n FROM ${t}`).first<{ n: number }>())?.n).toBe(0);
    }
    expect(await env.CACHE.get("quota:lookup:u:2026-10-27")).toBeNull();
    expect(await env.CACHE.get("warranty:keep")).toBe("{}");
    expect(await env.MEDIA.get("co/photo/p/a.jpg")).toBeNull();
  });
  it("is absent unless ENVIRONMENT=dev", async () => {
    const prod = { ...env, ENVIRONMENT: "production" };
    for (const path of ["/dev/last-code?email=a@b.co", "/dev/reset", "/dev/seed"]) {
      expect((await call(path, { method: path.includes("last") ? "GET" : "POST" }, prod)).status).toBe(404);
    }
    await Promise.resolve(post("/auth/email/start", { email: "p@example.com" }, prod)).catch(() => null);
    expect(await env.CACHE.get("devcode:p@example.com")).toBeNull();
  });
});
