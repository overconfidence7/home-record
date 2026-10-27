// AC-B21: presigned R2 URLs, tenant-scoped keys, orphan cleanup
import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import app from "../src/index";
import { MEDIA_MAX_BYTES, parseMediaKey, removeOrphanedMedia } from "../src/routes/media";
import { MEDIA_BUCKET, presignUrl } from "../src/media/presign";
import wranglerToml from "../wrangler.toml?raw";
import { seedCompany, seedUser } from "./helpers";

const r2 = { R2_ACCOUNT_ID: "acct", R2_ACCESS_KEY_ID: "AKID", R2_SECRET_ACCESS_KEY: "secret" };
const body = { tenantKind: "company", tenantId: "co-media", entity: "photo", entityId: "p1", filename: "a.jpg", size: 2048, contentType: "image/jpeg" };
const call = (path: string, token: string, payload: unknown, extra: object = r2) =>
  app.request(
    path,
    { method: "POST", headers: { Authorization: `Bearer ${token}`, "content-type": "application/json" }, body: JSON.stringify(payload) },
    { ...env, ...extra },
  );
const post = (token: string, payload: unknown, extra: object = r2) => call("/media/presign", token, payload, extra);
const get = (token: string, payload: unknown, extra: object = r2) => call("/media/presign-get", token, payload, extra);

async function member(companyId: string, userId: string, role: string) {
  const token = await seedUser(userId, `${userId}@example.com`);
  await env.DB.prepare("INSERT INTO memberships (id, user_id, tenant_kind, tenant_id, role, created_at) VALUES (?, ?, 'company', ?, ?, 1)")
    .bind(`m-${companyId}-${userId}`, userId, companyId, role)
    .run();
  return token;
}

async function syncRow(tenant: string, entity: string, id: string, row: Record<string, unknown>) {
  await env.DB.prepare(
    "INSERT INTO sync_rows (tenant_kind, tenant_id, entity, entity_id, row_json, times_json, authoritative, server_seq) VALUES ('company',?,?,?,?, '{}', 0, 1)",
  ).bind(tenant, entity, id, JSON.stringify({ id, ...row })).run();
}

describe("AC-B21 presign", () => {
  it("signs a deterministic URL", async () => {
    const creds = { accountId: "acct", accessKeyId: "AKID", secretAccessKey: "secret", bucket: "b" };
    const at = new Date("2026-10-25T12:00:00Z");
    const a = await presignUrl(creds, "PUT", "t/photo/1/a b.jpg", 600, at);
    expect(a).toBe(await presignUrl(creds, "PUT", "t/photo/1/a b.jpg", 600, at));
    expect(a).toContain("https://acct.r2.cloudflarestorage.com/b/t/photo/1/a%20b.jpg?");
    expect(a).toContain("X-Amz-Expires=600");
    expect(a).toContain("X-Amz-Date=20261025T120000Z");
    expect(a).toContain("X-Amz-SignedHeaders=host&");
    expect(a).toMatch(/X-Amz-Signature=[0-9a-f]{64}$/);
    expect(await presignUrl(creds, "GET", "t/photo/1/a b.jpg", 600, at)).not.toBe(a);
  });

  it("signs size and type into a PUT, so another length or type gets another signature (cr:presign-size)", async () => {
    const creds = { accountId: "acct", accessKeyId: "AKID", secretAccessKey: "secret", bucket: "b" };
    const at = new Date("2026-10-25T12:00:00Z");
    const signed = (len: string, type: string) =>
      presignUrl(creds, "PUT", "t/photo/1/a.jpg", 600, at, { "Content-Type": type, "content-length": len });
    const a = await signed("2048", "image/jpeg");
    expect(a).toContain("X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost&");
    expect(await signed("2048", "image/jpeg")).toBe(a);
    expect(await signed("4096", "image/jpeg")).not.toBe(a);
    expect(await signed("2048", "text/html")).not.toBe(a);
  });

  it("returns a 10 minute PUT for a member, keyed tenant/entity/id/filename, signed for the size and type, with no GET", async () => {
    const token = await seedUser("u-media", "media@example.com");
    await seedCompany("co-media", "Media Co", "u-media");
    const res = await post(token, body);
    expect(res.status).toBe(200);
    const json = (await res.json()) as { key: string; putUrl: string; getUrl?: string };
    expect(json.key).toBe("co-media/photo/p1/a.jpg");
    expect(json.putUrl).toContain(`/${MEDIA_BUCKET}/co-media/photo/p1/a.jpg?`);
    expect(json.putUrl).toContain("X-Amz-Expires=600");
    expect(json.putUrl).toContain("X-Amz-SignedHeaders=content-length%3Bcontent-type%3Bhost");
    expect(json.getUrl).toBeUndefined();
  });

  it("404s for a tenant the caller is not in, 400s on a path-like name or a non-media entity, 413s over the cap, 503s without R2 keys", async () => {
    const token = await seedUser("u-media2", "media2@example.com");
    expect((await post(token, body)).status).toBe(404);
    await seedCompany("co-media2", "Media Co 2", "u-media2");
    const b = { ...body, tenantId: "co-media2" };
    expect((await post(token, { ...b, filename: "../x" })).status).toBe(400);
    expect((await post(token, { ...b, entity: "punch" })).status).toBe(400);
    expect((await post(token, { ...b, size: 0 })).status).toBe(400);
    expect((await post(token, { ...b, contentType: "image/jpeg\r\nx-amz-acl: public" })).status).toBe(400);
    const tooBig = await post(token, { ...b, size: MEDIA_MAX_BYTES.photo + 1 });
    expect(tooBig.status).toBe(413);
    expect((await post(token, { ...b, entity: "job_file", size: MEDIA_MAX_BYTES.photo + 1, contentType: "application/pdf" })).status).toBe(200);
    expect((await post(token, b, {})).status).toBe(503);
  });

  it("a member cannot sign a PUT over another member's object; the uploader and the Owner can (cr:presign-overwrite)", async () => {
    const owner = await seedUser("u-ow", "ow@example.com");
    await seedCompany("co-ow", "Overwrite Co", "u-ow");
    const crewA = await member("co-ow", "u-crew-a", "crew");
    const crewB = await member("co-ow", "u-crew-b", "crew");
    const photo = { ...body, tenantId: "co-ow", entityId: "p-a" };
    expect((await post(crewA, photo)).status).toBe(200);
    expect((await post(crewA, photo)).status).toBe(200); // the uploader retries
    expect((await post(crewB, photo)).status).toBe(403);
    expect((await post(owner, photo)).status).toBe(200);
    const claimed = await env.DB.prepare("SELECT owner_user_id FROM media_objects WHERE object_key = 'co-ow/photo/p-a/a.jpg'").first<{ owner_user_id: string }>();
    expect(claimed?.owner_user_id).toBe("u-crew-a");
  });

  it("follows the row's write rule: Crew cannot upload a job file, a Lead only on their job, nobody on a deleted row", async () => {
    const owner = await seedUser("u-rule", "rule@example.com");
    await seedCompany("co-rule", "Rule Co", "u-rule");
    const crew = await member("co-rule", "u-rule-crew", "crew");
    const lead = await member("co-rule", "u-rule-lead", "lead");
    const plan = { ...body, tenantId: "co-rule", entity: "job_file", filename: "plan.pdf", contentType: "application/pdf" };
    expect((await post(crew, { ...plan, entityId: "f-new" })).status).toBe(403);
    expect((await post(lead, { ...plan, entityId: "f-new" })).status).toBe(200); // row not synced yet: entity rule
    await syncRow("co-rule", "job_crew", "jc1", { jobId: "j-lead", memberId: "u-rule-lead" });
    await syncRow("co-rule", "job_file", "f-mine", { jobId: "j-lead" });
    await syncRow("co-rule", "job_file", "f-other", { jobId: "j-other" });
    expect((await post(lead, { ...plan, entityId: "f-mine" })).status).toBe(200);
    expect((await post(lead, { ...plan, entityId: "f-other" })).status).toBe(403);
    expect((await post(owner, { ...plan, entityId: "f-other" })).status).toBe(200);
    await syncRow("co-rule", "photo", "p-gone", { jobId: "j-lead", deletedAt: 1761500000000 });
    expect((await post(crew, { ...body, tenantId: "co-rule", entityId: "p-gone" })).status).toBe(404);
  });

  it("presign-get signs only a GET for a live row of this tenant the caller may see", async () => {
    const token = await seedUser("u-get", "get@example.com");
    await seedCompany("co-get", "Get Co", "u-get");
    const crew = await member("co-get", "u-get-crew", "crew");
    await syncRow("co-get", "job_file", "f1", { jobId: "j1" });
    await syncRow("co-get", "photo", "p-gone", { deletedAt: 1761500000000 });
    const req = { tenantKind: "company", tenantId: "co-get", key: "co-get/job_file/f1/plan.pdf" };
    for (const t of [token, crew]) {
      const res = await get(t, req);
      expect(res.status).toBe(200);
      const json = (await res.json()) as { getUrl: string; putUrl?: string };
      expect(json.getUrl).toContain(`/${MEDIA_BUCKET}/co-get/job_file/f1/plan.pdf?`);
      expect(json.getUrl).toContain("X-Amz-Expires=604800");
      expect(json.putUrl).toBeUndefined();
    }
    expect((await get(token, { ...req, key: "co-other/job_file/f1/plan.pdf" })).status).toBe(404);
    expect((await get(token, { ...req, key: "co-get/punch/f1/x.json" })).status).toBe(404);
    expect((await get(token, { ...req, key: "co-get/job_file/missing/plan.pdf" })).status).toBe(404);
    expect((await get(token, { ...req, key: "co-get/photo/p-gone/a.jpg" })).status).toBe(404);
    expect((await get(token, { ...req, key: "co-get/job_file/../plan.pdf" })).status).toBe(400);
    expect((await get(await seedUser("u-get-out", "out@example.com"), req)).status).toBe(404);
    expect((await get(token, req, {})).status).toBe(503);
  });

  it("signs against the bucket the MEDIA binding (and so the orphan cron) uses (cr:media-cron-scope)", () => {
    expect(wranglerToml).toMatch(new RegExp(`binding = "MEDIA"\\s*\\nbucket_name = "${MEDIA_BUCKET}"`));
  });

  it("cron removes unreferenced objects older than 30 days and keeps referenced ones", async () => {
    const old = Date.now() - 31 * 86_400_000;
    for (const k of ["co-x/photo/1/gone.jpg", "co-x/photo/2/kept.jpg"]) {
      await env.MEDIA.put(k, "x");
      await env.DB.prepare("INSERT INTO media_objects (object_key, tenant_kind, tenant_id, created_at) VALUES (?, 'company', 'co-x', ?)").bind(k, old).run();
    }
    await env.DB.prepare(
      "INSERT INTO sync_rows (tenant_kind, tenant_id, entity, entity_id, row_json, times_json, authoritative, server_seq) VALUES ('company','co-x','photo','2',?, '{}', 0, 1)",
    ).bind('{"file":"co-x/photo/2/kept.jpg"}').run();
    expect(await removeOrphanedMedia(env)).toBe(1);
    expect(await env.MEDIA.get("co-x/photo/1/gone.jpg")).toBeNull();
    expect(await env.MEDIA.get("co-x/photo/2/kept.jpg")).not.toBeNull();
  });

  it("cron keeps a photo and a voice memo whose rows store no key, and removes media of a deleted or missing row", async () => {
    const old = Date.now() - 31 * 86_400_000;
    const keys = [
      "co-y/photo/p-live/a.jpg", // live photo row, key not in its JSON
      "co-y/note/n-live/memo.m4a", // live voice memo row
      "co-y/photo/p-gone/b.jpg", // soft-deleted photo row
      "co-y/photo/p-none/c.jpg", // no row at all
      "co-y/photo/p-other/d.jpg", // row with this id exists only in another tenant
    ];
    for (const k of keys) {
      await env.MEDIA.put(k, "x");
      await env.DB.prepare("INSERT INTO media_objects (object_key, tenant_kind, tenant_id, created_at) VALUES (?, 'company', 'co-y', ?)").bind(k, old).run();
    }
    const row = (tenant: string, entity: string, id: string, json: string) =>
      env.DB.prepare(
        "INSERT INTO sync_rows (tenant_kind, tenant_id, entity, entity_id, row_json, times_json, authoritative, server_seq) VALUES ('company',?,?,?,?, '{}', 0, 1)",
      ).bind(tenant, entity, id, json).run();
    await row("co-y", "photo", "p-live", '{"id":"p-live","jobId":"j1","caption":"Panel"}');
    await row("co-y", "note", "n-live", '{"id":"n-live","jobId":"j1","deletedAt":null}');
    await row("co-y", "photo", "p-gone", '{"id":"p-gone","deletedAt":1761500000000}');
    await row("co-z", "photo", "p-other", '{"id":"p-other"}');

    expect(await removeOrphanedMedia(env)).toBe(3);
    expect(await env.MEDIA.get("co-y/photo/p-live/a.jpg")).not.toBeNull();
    expect(await env.MEDIA.get("co-y/note/n-live/memo.m4a")).not.toBeNull();
    for (const k of keys.slice(2)) expect(await env.MEDIA.get(k)).toBeNull();
    // Kept objects are looked at again in another 30 days, not on the next cron.
    expect(await removeOrphanedMedia(env)).toBe(0);
  });

  it("parses tenant/entity/id/filename keys and rejects other shapes", () => {
    expect(parseMediaKey("co/photo/p1/a.jpg")).toEqual({ tenantId: "co", entity: "photo", entityId: "p1" });
    expect(parseMediaKey("co/photo/a.jpg")).toBeNull();
    expect(parseMediaKey("co//p1/a.jpg")).toBeNull();
  });
});
