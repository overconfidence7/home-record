import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import app from "../src/index";
import { issueToken, verifyToken } from "../src/session";
import { seedCompany, seedUser } from "./helpers";

const call = (path: string, token?: string) =>
  app.request(path, { headers: token ? { Authorization: `Bearer ${token}` } : {} }, env);

describe("AC-B1 session tokens", () => {
  const secret = env.JWT_SECRET;
  it("round-trips a user id", async () => {
    expect(await verifyToken(secret, await issueToken(secret, "u1"))).toBe("u1");
  });
  it("rejects a token signed with another secret", async () => {
    expect(await verifyToken(secret, await issueToken("x".repeat(40), "u1"))).toBeNull();
  });
  it("rejects a tampered token", async () => {
    const t = await issueToken(secret, "u1");
    expect(await verifyToken(secret, t.slice(0, -2) + "xx")).toBeNull();
  });
  it("expires after 30 days", async () => {
    const issued = Date.UTC(2026, 0, 1);
    const t = await issueToken(secret, "u1", issued);
    expect(await verifyToken(secret, t, issued + 29 * 86400_000)).toBe("u1");
    expect(await verifyToken(secret, t, issued + 31 * 86400_000)).toBeNull();
  });
  it("rejects garbage", async () => {
    expect(await verifyToken(secret, "not-a-jwt")).toBeNull();
  });
});

describe("/health", () => {
  it("answers without auth", async () => {
    const res = await call("/health");
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({ ok: true, environment: "test" });
  });
});

describe("/me", () => {
  it("401 without a token", async () => {
    expect((await call("/me")).status).toBe(401);
  });
  it("401 for a valid token whose user does not exist", async () => {
    expect((await call("/me", await issueToken(env.JWT_SECRET, "ghost"))).status).toBe(401);
  });
  it("returns memberships and entitlement fields, and refreshes the token", async () => {
    const token = await seedUser("u-me", "me@example.com");
    await seedCompany("c-me", "Kurtz Builders", "u-me");
    const res = await call("/me", token);
    expect(res.status).toBe(200);
    expect(res.headers.get("X-Refreshed-Token")).toBeTruthy();
    const body = (await res.json()) as { user: { email: string }; memberships: Record<string, unknown>[] };
    expect(body.user.email).toBe("me@example.com");
    expect(body.memberships).toEqual([
      { kind: "company", id: "c-me", role: "owner", name: "Kurtz Builders", entitlement: null, trialEndsAt: 1_900_000_000_000 },
    ]);
  });
});

describe("AC-B2 tenancy", () => {
  it("members read their own tenant", async () => {
    const token = await seedUser("u-a", "a@example.com");
    await seedCompany("c-a", "A Co", "u-a");
    const res = await call("/companies/c-a", token);
    expect(res.status).toBe(200);
  });
  it("another tenant and a nonexistent tenant look identical (404, no detail)", async () => {
    const token = await seedUser("u-b", "b@example.com");
    await seedCompany("c-b", "B Co", "u-b");
    await seedCompany("c-other", "Other Co", (await seedUser("u-o", "o@example.com"), "u-o"));
    const other = await call("/companies/c-other", token);
    const missing = await call("/companies/nope", token);
    expect(other.status).toBe(404);
    expect(missing.status).toBe(404);
    expect(await other.text()).toBe(await missing.text());
  });
  it("a company id is not reachable as a home", async () => {
    const token = await seedUser("u-c", "c@example.com");
    await seedCompany("c-c", "C Co", "u-c");
    expect((await call("/homes/c-c", token)).status).toBe(404);
  });
  it("401 without a token", async () => {
    expect((await call("/companies/c-a")).status).toBe(401);
  });
});

describe("unknown routes", () => {
  it("404 json", async () => {
    const res = await call("/nothing");
    expect(res.status).toBe(404);
    expect(await res.json()).toEqual({ error: "not_found" });
  });
});
