// T058: share links. AC-B35 (gallery and daily log pages, revocable) and AC-B36 (change order page with a signature
// pad; POST stores the signature, marks Signed, and the phones see it on their next pull, AC-F40).
import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import app from "../src/index";
import { inkLength, lineCents } from "../src/routes/share";
import { seedCompany, seedUser } from "./helpers";

const call = (method: string, path: string, token: string, body?: unknown) =>
  app.request(
    path,
    { method, headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, body: body ? JSON.stringify(body) : undefined },
    env,
  );
const op = (outboxId: number, entity: string, row: Record<string, unknown>, updatedAt = Date.now()) => ({
  outboxId, entity, entityId: row.id as string, op: "upsert", payload: JSON.stringify(row), updatedAt,
});
const push = (token: string, company: string, ops: unknown[]) => call("POST", `/companies/${company}/sync/push`, token, { deviceId: "d1", ops });
const get = (path: string) => app.request(path, {}, env);
const post = (path: string, body: unknown) =>
  app.request(path, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }, env);

const SIGNATURE = [{ points: [0.1, 0.5, 0.3, 0.3, 0.5, 0.6, 0.7, 0.4] }];

async function seed(prefix: string) {
  const owner = await seedUser(`${prefix}-own`, `${prefix}-own@example.com`);
  const co = `${prefix}-co`;
  await seedCompany(co, "Hale Electric", `${prefix}-own`);
  const job = `${prefix}-j`;
  const res = await push(owner, co, [
    op(1, "job", { id: job, name: "Hale kitchen", address: "12 Elm St" }),
    op(2, "photo", { id: `${prefix}-p1`, jobId: job, homeId: "h", path: "", r2Key: `company/${co}/photo/${prefix}-p1/a.jpg`, takenAt: Date.UTC(2026, 9, 21, 15), caption: "Panel before <b>" }),
    op(3, "photo", { id: `${prefix}-p2`, jobId: job, homeId: "h", path: "", r2Key: `company/${co}/photo/${prefix}-p2/b.jpg`, takenAt: Date.UTC(2026, 9, 22, 15), caption: "Rough-in done" }),
    op(4, "photo", { id: `${prefix}-p3`, jobId: "other-job", homeId: "h", path: "", r2Key: `company/${co}/photo/${prefix}-p3/c.jpg`, takenAt: 1, caption: "Another job" }),
    op(5, "daily_log", {
      id: `${prefix}-log`, jobId: job, day: "2026-10-22", status: "SENT",
      crewJson: JSON.stringify({ value: [{ memberId: "m1", name: "Luis Ortega", minutes: 465 }] }),
      workDoneJson: JSON.stringify({ value: ["Ran the kitchen circuits"] }),
      photosJson: JSON.stringify({ value: [`${prefix}-p2`] }),
      delaysJson: JSON.stringify([{ reason: "INSPECTION", note: "Inspector came late" }]),
      clientNotes: "Cabinets go in Monday",
    }),
    op(6, "change_order", {
      id: `${prefix}-c`, jobId: job, title: "Add a circuit for the fridge", clientName: "Dana Hale", status: "DRAFT",
      linesJson: JSON.stringify([{ id: "l1", description: "20 A circuit", qtyMilli: 1000, unitCents: 18500 }, { id: "l2", description: "Wire, ft", qtyMilli: 1500, unitCents: 9600 }]),
      totalCents: 32900, photoIdsJson: JSON.stringify([`${prefix}-p1`]),
    }),
    op(7, "share_link", { id: `${prefix}-sg`, kind: "gallery", jobId: job, token: `${prefix}-gallery-token-0001`, revoked: false }),
    op(8, "share_link", { id: `${prefix}-sl`, kind: "log", jobId: job, targetId: `${prefix}-log`, token: `${prefix}-log-token-00000001`, revoked: false }),
    op(9, "share_link", { id: `${prefix}-sc`, kind: "co", jobId: job, targetId: `${prefix}-c`, token: `${prefix}-co-token-000000001`, revoked: false }),
  ]);
  expect(((await res.json()) as { accepted: unknown[] }).accepted).toHaveLength(9);
  await env.MEDIA.put(`company/${co}/photo/${prefix}-p1/a.jpg`, "jpeg-bytes-1", { httpMetadata: { contentType: "image/jpeg" } });
  await env.MEDIA.put(`company/${co}/photo/${prefix}-p2/b.jpg`, "jpeg-bytes-2", { httpMetadata: { contentType: "image/jpeg" } });
  await env.MEDIA.put(`company/${co}/photo/${prefix}-p3/c.jpg`, "jpeg-bytes-3", { httpMetadata: { contentType: "image/jpeg" } });
  return { owner, co, job };
}

describe("AC-B35 share links: gallery and daily log", () => {
  it("the gallery shows the job's photos by day with captions, escaped, and serves only that job's photos", async () => {
    await seed("sh1");
    const res = await get("/share/gallery/sh1-gallery-token-0001");
    expect(res.status).toBe(200);
    expect(res.headers.get("Cache-Control")).toBe("no-store");
    const html = await res.text();
    expect(html).toContain("Hale kitchen");
    expect(html).toContain("12 Elm St");
    expect(html).toContain("Panel before &lt;b&gt;");
    expect(html).not.toContain("Panel before <b>");
    expect(html).toContain("Rough-in done");
    expect(html).not.toContain("Another job");
    // Newest day first: Thursday the 22nd before Wednesday the 21st.
    expect(html.indexOf("October 22")).toBeLessThan(html.indexOf("October 21"));
    const photo = await get("/share/gallery/sh1-gallery-token-0001/photo/sh1-p1");
    expect(photo.status).toBe(200);
    expect(photo.headers.get("Content-Type")).toBe("image/jpeg");
    expect(await photo.text()).toBe("jpeg-bytes-1");
    expect((await get("/share/gallery/sh1-gallery-token-0001/photo/sh1-p3")).status).toBe(404);
    expect((await get("/share/gallery/not-a-real-token-000000/photo/sh1-p1")).status).toBe(404);
  });

  it("the log page shows that day's sections and only the log's photos", async () => {
    await seed("sh2");
    const res = await get("/share/log/sh2-log-token-00000001");
    expect(res.status).toBe(200);
    const html = await res.text();
    for (const text of ["Daily log", "Thursday, October 22, 2026", "Luis Ortega · 7h 45m", "Ran the kitchen circuits", "Inspection: Inspector came late", "Cabinets go in Monday"]) {
      expect(html).toContain(text);
    }
    expect(html).toContain("/share/log/sh2-log-token-00000001/photo/sh2-p2");
    expect(html).not.toContain("sh2-p1");
    expect((await get("/share/log/sh2-log-token-00000001/photo/sh2-p2")).status).toBe(200);
    expect((await get("/share/log/sh2-log-token-00000001/photo/sh2-p1")).status).toBe(404);
    // A token only opens the kind it was made for.
    expect((await get("/share/gallery/sh2-log-token-00000001")).status).toBe(404);
  });

  it("a revoked or deleted link, or one whose job is deleted, is turned off", async () => {
    const { owner, co, job } = await seed("sh3");
    expect((await get("/share/gallery/sh3-gallery-token-0001")).status).toBe(200);
    await push(owner, co, [op(20, "share_link", { id: "sh3-sg", revoked: true })]);
    const off = await get("/share/gallery/sh3-gallery-token-0001");
    expect(off.status).toBe(404);
    expect(await off.text()).toContain("This link is turned off.");
    expect((await get("/share/gallery/sh3-gallery-token-0001/photo/sh3-p1")).status).toBe(404);
    await push(owner, co, [op(21, "share_link", { id: "sh3-sl", deletedAt: Date.now() })]);
    expect((await get("/share/log/sh3-log-token-00000001")).status).toBe(404);
    await push(owner, co, [op(22, "job", { id: job, deletedAt: Date.now() })]);
    expect((await get("/share/co/sh3-co-token-000000001")).status).toBe(404);
    expect((await get("/share/co/bad token")).status).toBe(404);
  });

  it("only Owner and Office can make a link", async () => {
    await seedUser("sh4-crew", "sh4-crew@example.com");
    const { co } = await seed("sh4");
    await env.DB.prepare("INSERT INTO memberships (id, user_id, tenant_kind, tenant_id, role, created_at) VALUES ('m-sh4', 'sh4-crew', 'company', ?, 'crew', 1)").bind(co).run();
    const { issueToken } = await import("../src/session");
    const crew = await issueToken(env.JWT_SECRET, "sh4-crew");
    const res = await push(crew, co, [op(99, "share_link", { id: "sh4-x", kind: "gallery", jobId: "sh4-j", token: "sh4-crew-made-token-01" })]);
    const body = (await res.json()) as { rejected?: { reason: string }[] };
    expect(body, JSON.stringify(body)).toEqual({ accepted: [], conflicts: [], rejected: [{ outboxId: 99, reason: "role" }] });
    expect((await get("/share/gallery/sh4-crew-made-token-01")).status).toBe(404);
  });
});

describe("AC-B36 change order share page and web signature", () => {
  it("shows the order with lines, total and its photos, and a signature pad while it is not signed", async () => {
    await seed("sh5");
    const res = await get("/share/co/sh5-co-token-000000001");
    expect(res.status).toBe(200);
    const html = await res.text();
    for (const text of ["Add a circuit for the fridge", "For Dana Hale", "Waiting for signature", "20 A circuit", "$185.00", "1.5", "$144.00", "$329.00", "Sign to approve", 'id="pad"']) {
      expect(html).toContain(text);
    }
    expect(html).toContain("/share/co/sh5-co-token-000000001/photo/sh5-p1");
    expect((await get("/share/co/sh5-co-token-000000001/photo/sh5-p1")).status).toBe(200);
    expect((await get("/share/co/sh5-co-token-000000001/photo/sh5-p2")).status).toBe(404);
  });

  it("AC-F40 POST signs once: Signed with name, time and strokes, pulled by the phone, and the page shows the signature", async () => {
    const { owner, co } = await seed("sh6");
    const before = (await (await call("GET", `/companies/${co}/sync/pull?since=0`, owner)).json()) as { serverSeq: number };
    expect((await post("/share/co/sh6-co-token-000000001", { name: "", strokes: SIGNATURE })).status).toBe(400);
    // A dot is not a signature.
    expect((await post("/share/co/sh6-co-token-000000001", { name: "Dana Hale", strokes: [{ points: [0.5, 0.5] }] })).status).toBe(400);
    expect((await post("/share/co/sh6-co-token-000000001", { name: "Dana Hale", strokes: [{ points: [0.5, 2] }] })).status).toBe(400);
    const res = await post("/share/co/sh6-co-token-000000001", { name: "Dana Hale", aspect: 1 / 3, strokes: SIGNATURE });
    expect(res.status).toBe(200);
    const pulled = (await (await call("GET", `/companies/${co}/sync/pull?since=${before.serverSeq}`, owner)).json()) as {
      rows: { entity: string; row: Record<string, unknown> }[];
    };
    const row = pulled.rows.find((r) => r.entity === "change_order")?.row;
    expect(row?.status).toBe("SIGNED");
    expect(row?.signedBy).toBe("Dana Hale");
    expect(typeof row?.signedAt).toBe("number");
    expect(JSON.parse(String(row?.signatureJson))).toEqual(SIGNATURE);
    expect(row?.totalCents).toBe(32900);
    expect(row?.title).toBe("Add a circuit for the fridge");
    // Owner and Office hear it (AC-B30).
    const log = await env.DB.prepare("SELECT type, data_json FROM push_log WHERE tenant_id = ?").bind(co).all<{ type: string; data_json: string }>();
    expect(log.results.map((r) => r.type)).toContain("change_order_signed");
    // Signing again is refused; the page now shows the signature and no pad.
    expect((await post("/share/co/sh6-co-token-000000001", { name: "Someone else", strokes: SIGNATURE })).status).toBe(409);
    const html = await (await get("/share/co/sh6-co-token-000000001")).text();
    expect(html).toContain("Signed by Dana Hale");
    expect(html).toContain("<svg");
    expect(html).not.toContain('id="pad"');
  });

  it("a phone edit made before the web signature does not undo it", async () => {
    const { owner, co } = await seed("sh7");
    const earlier = Date.now() - 60_000;
    expect((await post("/share/co/sh7-co-token-000000001", { name: "Dana Hale", strokes: SIGNATURE })).status).toBe(200);
    await push(owner, co, [op(30, "change_order", { id: "sh7-c", status: "DRAFT", signedBy: null }, earlier)]);
    const html = await (await get("/share/co/sh7-co-token-000000001")).text();
    expect(html).toContain("Signed by Dana Hale");
  });

  it("R11: a phone edit made after the web signature (an offline phone's later clock) does not undo it either", async () => {
    const { owner, co } = await seed("sh9");
    expect((await post("/share/co/sh9-co-token-000000001", { name: "Dana Hale", strokes: SIGNATURE })).status).toBe(200);
    const later = Date.now() + 60_000;
    const res = await push(owner, co, [
      op(31, "change_order", { id: "sh9-c", title: "Changed after signing", status: "DRAFT", signedBy: null, signatureJson: null, totalCents: 1 }, later),
    ]);
    const body = (await res.json()) as { conflicts: { field: string; reason: string }[] };
    expect(body.conflicts.map((x) => x.reason)).toContain("signed");
    // A second signature from a phone that signed offline does not replace the client's either.
    await push(owner, co, [op(32, "change_order", { id: "sh9-c", status: "SIGNED", signedBy: "Owner on site", signedVia: null }, later + 1)]);
    const html = await (await get("/share/co/sh9-co-token-000000001")).text();
    expect(html).toContain("Signed by Dana Hale");
    expect(html).toContain("Add a circuit for the fridge");
    expect(html).toContain("$329.00");
    expect(html).not.toContain("Changed after signing");
  });

  it("a revoked change order link cannot sign", async () => {
    const { owner, co } = await seed("sh8");
    await push(owner, co, [op(40, "share_link", { id: "sh8-sc", revoked: true })]);
    expect((await post("/share/co/sh8-co-token-000000001", { name: "Dana Hale", strokes: SIGNATURE })).status).toBe(404);
  });

  it("ink and line math match the app (unit)", () => {
    expect(inkLength([{ points: [0, 0, 0.3, 0] }], 1)).toBeCloseTo(0.3);
    expect(inkLength([{ points: [0, 0, 0, 0.3] }], 0.5)).toBeCloseTo(0.15);
    expect(lineCents(1500, 9600)).toBe(14400);
    expect(lineCents(333, 1)).toBe(0);
    expect(lineCents(500, 1)).toBe(1);
  });
});
