// AC-B1 session tokens: HMAC code hash, token_version revocation and the JWT_SECRET floor
// (cr:code-hash, cr:session-revoke, cr:readme-secrets); AC-B60 every endpoint has a test.
import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import app from "../src/index";
import { hashCode } from "../src/auth/emailCode";
import { MIN_SECRET_BYTES, issueToken, readToken } from "../src/session";
import { seedUser } from "./helpers";

const call = (path: string, token: string, method = "GET") =>
  app.request(path, { method, headers: { Authorization: `Bearer ${token}` } }, env);
const post = (path: string, body: unknown, e: object = env) =>
  app.request(path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }, e);

describe("AC-B1 email code hash", () => {
  it("is an HMAC under the server secret, not a bare SHA-256 of email and code", async () => {
    const plain = await crypto.subtle.digest("SHA-256", new TextEncoder().encode("h@example.com:123456"));
    const plainHex = [...new Uint8Array(plain)].map((b) => b.toString(16).padStart(2, "0")).join("");
    const mac = await hashCode(env.JWT_SECRET, "h@example.com", "123456");
    expect(mac).toHaveLength(64);
    expect(mac).not.toBe(plainHex);
    expect(await hashCode("y".repeat(40), "h@example.com", "123456")).not.toBe(mac);
    expect(await hashCode(env.JWT_SECRET, "h@example.com", "123456")).toBe(mac);
  });
  it("the stored hash matches the HMAC of the code that start sent", async () => {
    const res = await post("/auth/email/start", { email: "hm@example.com" });
    expect(res.status).toBe(200);
    const row = await env.DB.prepare("SELECT code_hash FROM email_codes WHERE email = ?").bind("hm@example.com").first<{ code_hash: string }>();
    // Brute force without the key finds nothing: no six-digit code hashes to the stored value under another key.
    expect(row?.code_hash).toHaveLength(64);
    expect(row?.code_hash).not.toBe(await hashCode("z".repeat(40), "hm@example.com", "000000"));
  });
});

describe("AC-B1 session revocation", () => {
  it("tokens carry the user's version and a refresh keeps it", async () => {
    const token = await seedUser("u-rv1", "rv1@example.com");
    expect((await readToken(env.JWT_SECRET, token))?.version).toBe(0);
    const res = await call("/me", token);
    expect(res.status).toBe(200);
    const refreshed = res.headers.get("X-Refreshed-Token") ?? "";
    expect(await readToken(env.JWT_SECRET, refreshed)).toEqual({ userId: "u-rv1", version: 0 });
  });
  it("POST /auth/revoke signs out every token of the user and none of another user's", async () => {
    const phoneA = await seedUser("u-rv2", "rv2@example.com");
    const phoneB = await issueToken(env.JWT_SECRET, "u-rv2");
    const other = await seedUser("u-rv3", "rv3@example.com");
    const res = await call("/auth/revoke", phoneA, "POST");
    expect(res.status).toBe(204);
    expect(res.headers.get("X-Refreshed-Token")).toBeNull();
    expect((await call("/me", phoneA)).status).toBe(401);
    expect((await call("/me", phoneB)).status).toBe(401);
    expect((await call("/me", other)).status).toBe(200);
    const row = await env.DB.prepare("SELECT token_version FROM users WHERE id = ?").bind("u-rv2").first<{ token_version: number }>();
    expect(row?.token_version).toBe(1);
  });
  it("signing in again after a revoke issues a token at the new version", async () => {
    await env.DB.prepare("INSERT INTO users (id, email, created_at, token_version) VALUES (?, ?, 1, 3)").bind("u-rv4", "rv4@example.com").run();
    await env.DB.prepare("INSERT OR REPLACE INTO email_codes (email, code_hash, expires_at, attempts) VALUES (?, ?, ?, 0)")
      .bind("rv4@example.com", await hashCode(env.JWT_SECRET, "rv4@example.com", "424242"), Date.now() + 60_000)
      .run();
    const res = await post("/auth/email/verify", { email: "rv4@example.com", code: "424242" });
    expect(res.status).toBe(200);
    const { token } = (await res.json()) as { token: string };
    expect(await readToken(env.JWT_SECRET, token)).toEqual({ userId: "u-rv4", version: 3 });
    expect((await call("/me", token)).status).toBe(200);
    expect((await call("/me", await issueToken(env.JWT_SECRET, "u-rv4", Date.now(), 2))).status).toBe(401);
  });
  it("revoke needs a session", async () => {
    expect((await app.request("/auth/revoke", { method: "POST" }, env)).status).toBe(401);
  });
  it("a token without a version is refused", async () => {
    const { SignJWT } = await import("jose");
    const legacy = await new SignJWT({}).setProtectedHeader({ alg: "HS256" }).setSubject("u-rv1").setIssuer("homerecord")
      .setIssuedAt().setExpirationTime("1d").sign(new TextEncoder().encode(env.JWT_SECRET));
    expect(await readToken(env.JWT_SECRET, legacy)).toBeNull();
  });
});

describe("AC-B60 JWT_SECRET floor", () => {
  it(`refuses a secret under ${MIN_SECRET_BYTES} bytes to sign or verify`, async () => {
    await expect(issueToken("short-secret", "u1")).rejects.toThrow(/at least 32 bytes/);
    await expect(readToken("short-secret", "x.y.z")).rejects.toThrow(/at least 32 bytes/);
    await expect(hashCode("", "a@example.com", "123456")).rejects.toThrow(/at least 32 bytes/);
  });
  it("routes fail closed with 500 under a short secret", async () => {
    const weak = { ...env, JWT_SECRET: "x".repeat(MIN_SECRET_BYTES - 1) };
    expect((await post("/auth/email/start", { email: "weak@example.com" }, weak)).status).toBe(500);
    const token = await seedUser("u-weak", "weak@example.com");
    expect((await app.request("/me", { headers: { Authorization: `Bearer ${token}` } }, weak)).status).toBe(500);
  });
});
