// AC-B21: presigned R2 URLs, tenant-scoped keys, orphan cleanup
import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import app from "../src/index";
import { parseMediaKey, removeOrphanedMedia } from "../src/routes/media";
import { presignUrl } from "../src/media/presign";
import { seedCompany, seedUser } from "./helpers";

const r2 = { R2_ACCOUNT_ID: "acct", R2_ACCESS_KEY_ID: "AKID", R2_SECRET_ACCESS_KEY: "secret", R2_BUCKET: "homerecord-media" };
const body = { tenantKind: "company", tenantId: "co-media", entity: "photo", entityId: "p1", filename: "a.jpg" };
const post = (token: string, payload: unknown, extra = r2) =>
  app.request(
    "/media/presign",
    { method: "POST", headers: { Authorization: `Bearer ${token}`, "content-type": "application/json" }, body: JSON.stringify(payload) },
    { ...env, ...extra },
  );

describe("AC-B21 presign", () => {
  it("signs a deterministic URL", async () => {
    const creds = { accountId: "acct", accessKeyId: "AKID", secretAccessKey: "secret", bucket: "b" };
    const at = new Date("2026-10-25T12:00:00Z");
    const a = await presignUrl(creds, "PUT", "t/photo/1/a b.jpg", 600, at);
    expect(a).toBe(await presignUrl(creds, "PUT", "t/photo/1/a b.jpg", 600, at));
    expect(a).toContain("https://acct.r2.cloudflarestorage.com/b/t/photo/1/a%20b.jpg?");
    expect(a).toContain("X-Amz-Expires=600");
    expect(a).toContain("X-Amz-Date=20261025T120000Z");
    expect(a).toMatch(/X-Amz-Signature=[0-9a-f]{64}$/);
    expect(await presignUrl(creds, "GET", "t/photo/1/a b.jpg", 600, at)).not.toBe(a);
  });

  it("returns a 10 minute PUT and 7 day GET for a member, keyed tenant/entity/id/filename", async () => {
    const token = await seedUser("u-media", "media@example.com");
    await seedCompany("co-media", "Media Co", "u-media");
    const res = await post(token, body);
    expect(res.status).toBe(200);
    const json = (await res.json()) as { key: string; putUrl: string; getUrl: string };
    expect(json.key).toBe("co-media/photo/p1/a.jpg");
    expect(json.putUrl).toContain("X-Amz-Expires=600");
    expect(json.getUrl).toContain("X-Amz-Expires=604800");
  });

  it("404s for a tenant the caller is not in, 400s on a path-like name, 503s without R2 keys", async () => {
    const token = await seedUser("u-media2", "media2@example.com");
    expect((await post(token, body)).status).toBe(404);
    await seedCompany("co-media2", "Media Co 2", "u-media2");
    expect((await post(token, { ...body, tenantId: "co-media2", filename: "../x" })).status).toBe(400);
    expect((await post(token, { ...body, tenantId: "co-media2" }, {} as typeof r2)).status).toBe(503);
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
