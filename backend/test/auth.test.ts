import { env } from "cloudflare:workers";
import { SignJWT, generateKeyPair } from "jose";
import { afterEach, describe, expect, it, vi } from "vitest";
import app from "../src/index";
import { checkCode, hashCode } from "../src/auth/emailCode";
import { verifyGoogleIdToken } from "../src/auth/google";
import { verifyToken } from "../src/session";
import { START_PER_EMAIL, hit } from "../src/auth/rateLimit";

const post = (path: string, body: unknown) =>
  app.request(path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }, env);

async function plantCode(email: string, code: string, expiresAt = Date.now() + 60_000) {
  await env.DB.prepare(
    "INSERT OR REPLACE INTO email_codes (email, code_hash, expires_at, attempts) VALUES (?, ?, ?, 0)",
  ).bind(email, await hashCode(env.JWT_SECRET, email, code), expiresAt).run();
}

describe("AC-B1 email code", () => {
  it("start stores a hashed code and dev mode accepts it", async () => {
    const res = await post("/auth/email/start", { email: "Dev@Example.com" });
    expect(res.status).toBe(200);
    const row = await env.DB.prepare("SELECT code_hash, expires_at FROM email_codes WHERE email = ?").bind("dev@example.com").first<{ code_hash: string; expires_at: number }>();
    expect(row?.code_hash).toHaveLength(64);
    expect(row!.expires_at).toBeGreaterThan(Date.now() + 9 * 60_000);
  });
  it("verify signs in, creates the user once, and returns a valid token", async () => {
    await plantCode("a@example.com", "123456");
    const res = await post("/auth/email/verify", { email: "a@example.com", code: "123456" });
    expect(res.status).toBe(200);
    const { token } = (await res.json()) as { token: string };
    const userId = await verifyToken(env.JWT_SECRET, token);
    expect(userId).toBeTruthy();
    await plantCode("a@example.com", "654321");
    const again = (await (await post("/auth/email/verify", { email: "a@example.com", code: "654321" })).json()) as { token: string };
    expect(await verifyToken(env.JWT_SECRET, again.token)).toBe(userId);
  });
  it("a code works once", async () => {
    await plantCode("b@example.com", "111111");
    expect((await post("/auth/email/verify", { email: "b@example.com", code: "111111" })).status).toBe(200);
    expect((await post("/auth/email/verify", { email: "b@example.com", code: "111111" })).status).toBe(401);
  });
  it("rejects a wrong code and locks after five", async () => {
    await plantCode("c@example.com", "222222");
    for (let i = 0; i < 5; i++) expect(await checkCode(env.DB, env.JWT_SECRET, "c@example.com", "000000")).toBe("invalid");
    expect(await checkCode(env.DB, env.JWT_SECRET, "c@example.com", "222222")).toBe("locked");
  });
  it("rejects an expired code", async () => {
    await plantCode("d@example.com", "333333", Date.now() - 1);
    expect((await post("/auth/email/verify", { email: "d@example.com", code: "333333" })).status).toBe(401);
  });
  it("rejects malformed input", async () => {
    expect((await post("/auth/email/verify", { email: "nope", code: "12" })).status).toBe(400);
  });
});

describe("AC-B1 sign-in rate limits", () => {
  const from = (ip: string) => (path: string, body: unknown, e: object = env) =>
    app.request(path, { method: "POST", headers: { "Content-Type": "application/json", "CF-Connecting-IP": ip }, body: JSON.stringify(body) }, e);
  afterEach(() => vi.restoreAllMocks());

  it("allows five code requests per email an hour, from any IP", async () => {
    for (let i = 0; i < 5; i++) expect((await from(`10.0.1.${i}`)("/auth/email/start", { email: "flood@example.com" })).status).toBe(200);
    const res = await from("10.0.1.9")("/auth/email/start", { email: "Flood@Example.com" });
    expect(res.status).toBe(429);
    expect(await res.json()).toEqual({ error: "rate_limited" });
  });
  it("allows twenty code requests per IP an hour, across emails", async () => {
    const ip = from("10.0.2.1");
    for (let i = 0; i < 20; i++) expect((await ip("/auth/email/start", { email: `spray${i}@example.com` })).status).toBe(200);
    expect((await ip("/auth/email/start", { email: "spray-last@example.com" })).status).toBe(429);
    expect((await from("10.0.2.2")("/auth/email/start", { email: "spray-last@example.com" })).status).toBe(200);
  });
  it("a new code does not reset the guess count: ten checks an hour per email, then even the right code is refused", async () => {
    let n = 0;
    const guess = (code: string) => from(`10.0.3.${n++}`)("/auth/email/verify", { email: "guess@example.com", code });
    await plantCode("guess@example.com", "424242");
    for (let i = 0; i < 5; i++) expect((await guess("000000")).status).toBe(401);
    expect(await (await guess("424242")).json()).toEqual({ error: "locked" });
    await plantCode("guess@example.com", "434343"); // what a new /email/start does: fresh code, attempts 0
    for (let i = 0; i < 4; i++) expect((await guess("000000")).status).toBe(401);
    const refused = await guess("434343");
    expect(refused.status).toBe(429);
    expect(await refused.json()).toEqual({ error: "rate_limited" });
  });
  it("allows thirty code checks per IP an hour, across emails", async () => {
    const ip = from("10.0.4.1");
    for (let i = 0; i < 30; i++) expect((await ip("/auth/email/verify", { email: `ipguess${i}@example.com`, code: "000000" })).status).toBe(401);
    await plantCode("ipguess-last@example.com", "515151");
    expect((await ip("/auth/email/verify", { email: "ipguess-last@example.com", code: "515151" })).status).toBe(429);
    expect((await from("10.0.4.2")("/auth/email/verify", { email: "ipguess-last@example.com", code: "515151" })).status).toBe(200);
  });
  it("a window resets after an hour", async () => {
    const t = 1_800_000_000_000;
    for (let i = 0; i < 5; i++) expect(await hit(env.DB, START_PER_EMAIL, "window@example.com", t + i)).toBe(true);
    expect(await hit(env.DB, START_PER_EMAIL, "window@example.com", t + 10)).toBe(false);
    expect(await hit(env.DB, START_PER_EMAIL, "window@example.com", t + 3_600_000)).toBe(true);
  });
  it("the local dev Worker has no limits and is the only place the code is logged", async () => {
    const log = vi.spyOn(console, "log");
    const devEnv = { ...env, ENVIRONMENT: "dev" };
    for (let i = 0; i < 7; i++) expect((await from("10.0.5.1")("/auth/email/start", { email: "qa-loop@example.com" }, devEnv)).status).toBe(200);
    expect(log.mock.calls.some((c) => String(c[0]).includes("sign-in code for qa-loop@example.com"))).toBe(true);
    log.mockClear();
    expect((await from("10.0.5.2")("/auth/email/start", { email: "quiet@example.com" })).status).toBe(200);
    expect(log.mock.calls.some((c) => String(c[0]).includes("sign-in code"))).toBe(false);
  });
});

describe("AC-B1 Google ID token", () => {
  const clientId = "client-123.apps.googleusercontent.com";
  async function token(claims: Record<string, unknown>, aud = clientId, expSec = 3600) {
    const { publicKey, privateKey } = await generateKeyPair("RS256");
    const now = Math.floor(Date.now() / 1000);
    const jwt = await new SignJWT(claims)
      .setProtectedHeader({ alg: "RS256" })
      .setIssuer("https://accounts.google.com")
      .setAudience(aud)
      .setIssuedAt(now)
      .setExpirationTime(now + expSec)
      .sign(privateKey);
    return { jwt, getKey: async () => publicKey };
  }
  it("accepts a valid token", async () => {
    const { jwt, getKey } = await token({ sub: "g1", email: "Pat@Example.com", name: "Pat", email_verified: true });
    expect(await verifyGoogleIdToken(jwt, clientId, getKey)).toEqual({ sub: "g1", email: "pat@example.com", name: "Pat" });
  });
  it("rejects another audience", async () => {
    const { jwt, getKey } = await token({ sub: "g1", email: "p@example.com" }, "other");
    expect(await verifyGoogleIdToken(jwt, clientId, getKey)).toBeNull();
  });
  it("rejects an expired token", async () => {
    const { jwt, getKey } = await token({ sub: "g1", email: "p@example.com" }, clientId, -10);
    expect(await verifyGoogleIdToken(jwt, clientId, getKey)).toBeNull();
  });
  it("rejects an unverified email and a token signed by another key", async () => {
    const a = await token({ sub: "g1", email: "p@example.com", email_verified: false });
    expect(await verifyGoogleIdToken(a.jwt, clientId, a.getKey)).toBeNull();
    const b = await token({ sub: "g1", email: "p@example.com" });
    const c = await token({ sub: "g1", email: "p@example.com" });
    expect(await verifyGoogleIdToken(b.jwt, clientId, c.getKey)).toBeNull();
  });
  it("the route refuses a bad token", async () => {
    expect((await post("/auth/google", { idToken: "x".repeat(20) })).status).toBe(400);
  });
});
