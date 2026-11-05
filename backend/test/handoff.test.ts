// T063c: AC-B40 handoff. `POST /handoff` stores the bundle in R2 and emails the homeowner a claim link; claiming with a
// House Manual account binds the handoff to that account, which downloads the bundle to import it into its home
// (AC-H17, then its own sync); links expire in 30 days and the cron removes expired bundles.
import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import app from "../src/index";
import { HANDOFF_DAYS, MAX_BUNDLE_BYTES, appLink, pruneHandoffs, tokenHash } from "../src/routes/handoff";
import { seedCompany, seedUser } from "./helpers";

const ZIP = new Uint8Array([0x50, 0x4b, 0x03, 0x04, 1, 2, 3, 4, 5, 6, 7, 8]);

function send(token: string, query: Record<string, string>, body: Uint8Array = ZIP) {
  return app.request(
    `/handoff?${new URLSearchParams(query)}`,
    { method: "POST", headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/zip", "Content-Length": String(body.length) }, body },
    env,
  );
}
const authed = (method: string, path: string, token: string) =>
  app.request(path, { method, headers: { Authorization: `Bearer ${token}` } }, env);

async function seed(prefix: string, role = "owner") {
  const sender = await seedUser(`${prefix}-own`, `${prefix}-own@example.com`);
  const co = `${prefix}-co`;
  await seedCompany(co, "Hale Electric", `${prefix}-own`, role);
  const homeowner = await seedUser(`${prefix}-hm`, `${prefix}-dana@example.com`);
  const stranger = await seedUser(`${prefix}-x`, `${prefix}-x@example.com`);
  return { sender, co, homeowner, stranger };
}

/** Sends a handoff and reads the claim token the way a QA flow does (dev only) or from the stored row (tests). */
async function sendAndToken(prefix: string, sender: string, co: string): Promise<{ token: string; id: string }> {
  const res = await send(sender, { companyId: co, jobId: `${prefix}-job`, email: ` ${prefix}-Dana@Example.com `, homeName: "12 Elm St" });
  expect(res.status).toBe(201);
  const { id } = (await res.json()) as { id: string };
  // Tests run with ENVIRONMENT=test, so no dev cache: plant a known token on the row instead.
  const token = `${prefix}-token-abcdefghijklmnopqrstuvwxyz0123`.replace(/[^A-Za-z0-9_-]/g, "x");
  await env.DB.prepare("UPDATE handoffs SET token_hash = ? WHERE id = ?").bind(await tokenHash(token), id).run();
  return { token, id };
}

describe("AC-B40 POST /handoff", () => {
  it("stores the bundle in R2 under the company and records who it is for, with a 30-day expiry", async () => {
    const { sender, co } = await seed("hs1");
    const before = Date.now();
    const res = await send(sender, { companyId: co, jobId: "j1", email: "Dana@Example.com", homeName: "12 Elm St" });
    expect(res.status).toBe(201);
    const body = (await res.json()) as { id: string; email: string; expiresAt: number; size: number };
    expect(body.email).toBe("dana@example.com");
    expect(body.size).toBe(ZIP.length);
    expect(body.expiresAt).toBeGreaterThanOrEqual(before + HANDOFF_DAYS * 86_400_000);
    const row = await env.DB.prepare("SELECT object_key, home_name, job_id, claimed_by FROM handoffs WHERE id = ?")
      .bind(body.id)
      .first<{ object_key: string; home_name: string; job_id: string; claimed_by: string | null }>();
    expect(row).toMatchObject({ home_name: "12 Elm St", job_id: "j1", claimed_by: null });
    expect(row?.object_key).toBe(`company/${co}/handoff/${body.id}.homerecord`);
    const stored = await env.MEDIA.get(row?.object_key ?? "");
    expect(new Uint8Array(await (stored as R2ObjectBody).arrayBuffer())).toEqual(ZIP);
  });

  it("lets Owner, Office and Lead send; Crew is refused and another company's id is a 404", async () => {
    for (const role of ["office", "lead"]) {
      const { sender, co } = await seed(`hs2-${role}`, role);
      expect((await send(sender, { companyId: co, email: "a@example.com", homeName: "Home" })).status).toBe(201);
    }
    const crew = await seed("hs2-crew", "crew");
    expect((await send(crew.sender, { companyId: crew.co, email: "a@example.com", homeName: "Home" })).status).toBe(403);
    const other = await seed("hs2-other");
    expect((await send(other.stranger, { companyId: other.co, email: "a@example.com", homeName: "Home" })).status).toBe(404);
    expect((await app.request(`/handoff?companyId=${other.co}&email=a@example.com&homeName=H`, { method: "POST", body: ZIP }, env)).status).toBe(401);
  });

  it("refuses a bad email, a missing home name, an empty body and a file that is not a zip, leaving nothing in R2", async () => {
    const { sender, co } = await seed("hs3");
    expect((await send(sender, { companyId: co, email: "not-an-email", homeName: "Home" })).status).toBe(400);
    expect((await send(sender, { companyId: co, email: "a@example.com", homeName: " " })).status).toBe(400);
    expect((await send(sender, { companyId: co, email: "a@example.com", homeName: "Home" }, new Uint8Array())).status).toBe(411);
    const notZip = await send(sender, { companyId: co, email: "a@example.com", homeName: "Home" }, new TextEncoder().encode("hello there"));
    expect(notZip.status).toBe(400);
    expect(await notZip.json()).toEqual({ error: "not_a_bundle" });
    expect((await env.MEDIA.list({ prefix: `company/${co}/handoff/` })).objects).toHaveLength(0);
    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM handoffs WHERE company_id = ?").bind(co).first("n")).toBe(0);
  });

  it("answers 413 above the request cap before reading the body", async () => {
    const { sender, co } = await seed("hs4");
    const res = await app.request(
      `/handoff?companyId=${co}&email=a@example.com&homeName=Home`,
      { method: "POST", headers: { Authorization: `Bearer ${sender}`, "Content-Length": String(MAX_BUNDLE_BYTES + 1) }, body: ZIP },
      env,
    );
    expect(res.status).toBe(413);
  });
});

describe("AC-B40 claim link", () => {
  it("the emailed link opens a page naming the contractor and the home, with a button into House Manual", async () => {
    const { sender, co } = await seed("hc1");
    const { token } = await sendAndToken("hc1", sender, co);
    const res = await app.request(`/handoff/${token}`, {}, env);
    expect(res.status).toBe(200);
    const html = await res.text();
    expect(html).toContain("Hale Electric");
    expect(html).toContain("The record of 12 Elm St");
    expect(html).toContain(appLink(token).replace(/&/g, "&amp;"));
    expect(res.headers.get("Cache-Control")).toBe("no-store");
    expect((await app.request("/handoff/unknown-token-unknown-token-unknown-00", {}, env)).status).toBe(404);
  });

  it("claiming binds the handoff to the first account; that account downloads the bundle, anyone else gets 409/404", async () => {
    const { sender, co, homeowner, stranger } = await seed("hc2");
    const { token } = await sendAndToken("hc2", sender, co);
    expect((await app.request(`/handoff/${token}/claim`, { method: "POST" }, env)).status).toBe(401);
    const claim = await authed("POST", `/handoff/${token}/claim`, homeowner);
    expect(claim.status).toBe(200);
    expect(await claim.json()).toMatchObject({ homeName: "12 Elm St", companyName: "Hale Electric", size: ZIP.length, bundlePath: `/handoff/${token}/bundle` });
    // Claiming again from the same account (a second tap, a reinstall) is fine.
    expect((await authed("POST", `/handoff/${token}/claim`, homeowner)).status).toBe(200);
    expect((await authed("POST", `/handoff/${token}/claim`, stranger)).status).toBe(409);

    const bundle = await authed("GET", `/handoff/${token}/bundle`, homeowner);
    expect(bundle.status).toBe(200);
    expect(bundle.headers.get("Content-Type")).toBe("application/zip");
    expect(bundle.headers.get("Content-Disposition")).toContain("12 Elm St.homerecord");
    expect(new Uint8Array(await bundle.arrayBuffer())).toEqual(ZIP);
    expect((await authed("GET", `/handoff/${token}/bundle`, stranger)).status).toBe(404);
    expect((await authed("GET", `/handoff/${token}/bundle`, sender)).status).toBe(404);
  });

  it("links expire after 30 days: the page says so, claim and download answer 410, and the cron removes the bundle", async () => {
    const { sender, co, homeowner } = await seed("hc3");
    const { token, id } = await sendAndToken("hc3", sender, co);
    expect((await authed("POST", `/handoff/${token}/claim`, homeowner)).status).toBe(200);
    await env.DB.prepare("UPDATE handoffs SET expires_at = ? WHERE id = ?").bind(Date.now() - 1, id).run();
    const pageRes = await app.request(`/handoff/${token}`, {}, env);
    expect(pageRes.status).toBe(410);
    expect(await pageRes.text()).toContain("This link has expired.");
    expect((await authed("POST", `/handoff/${token}/claim`, homeowner)).status).toBe(410);
    expect((await authed("GET", `/handoff/${token}/bundle`, homeowner)).status).toBe(410);

    expect(await pruneHandoffs(env)).toBeGreaterThanOrEqual(1);
    expect(await env.DB.prepare("SELECT COUNT(*) AS n FROM handoffs WHERE id = ?").bind(id).first("n")).toBe(0);
    expect(await env.MEDIA.get(`company/${co}/handoff/${id}.homerecord`)).toBeNull();
  });

  it("stores only a hash of the token", async () => {
    const { sender, co } = await seed("hc4");
    const { token, id } = await sendAndToken("hc4", sender, co);
    const stored = await env.DB.prepare("SELECT token_hash FROM handoffs WHERE id = ?").bind(id).first<string>("token_hash");
    expect(stored).not.toContain(token);
    expect(stored).toBe(await tokenHash(token));
  });
});

describe("AC-B40 dev path for QA flows", () => {
  it("in dev, /dev/last-handoff gives the newest claim token for an email, and that token claims", async () => {
    const devEnv = { ...env, ENVIRONMENT: "dev" };
    const { sender, co, homeowner } = await seed("hd1");
    const res = await app.request(
      `/handoff?companyId=${co}&email=hd1-dana@example.com&homeName=Ortiz%20house`,
      { method: "POST", headers: { Authorization: `Bearer ${sender}`, "Content-Length": String(ZIP.length) }, body: ZIP },
      devEnv,
    );
    expect(res.status).toBe(201);
    const last = await app.request("/dev/last-handoff?email=HD1-Dana@example.com", {}, devEnv);
    expect(last.status).toBe(200);
    const { token, path } = (await last.json()) as { token: string; path: string };
    expect(path).toBe(`/handoff/${token}`);
    const claim = await app.request(`/handoff/${token}/claim`, { method: "POST", headers: { Authorization: `Bearer ${homeowner}` } }, devEnv);
    expect(claim.status).toBe(200);
    expect((await app.request("/dev/last-handoff?email=hd1-dana@example.com", {}, env)).status).toBe(404);
  });
});
