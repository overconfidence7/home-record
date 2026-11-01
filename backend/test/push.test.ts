// AC-B30: push notifications through FCM (delivery only): tokens per member; task assigned, schedule changed for
// tomorrow, change order signed, timesheet approved. AC-F36 (assigning a task notifies the assignee) and AC-F42
// (changing tomorrow's assignment notifies the crew) on the server side.
import { env } from "cloudflare:workers";
import { exportPKCS8, generateKeyPair } from "jose";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import app from "../src/index";
import { companyDays, pushEventsFor } from "../src/push/events";
import { parseServiceAccount } from "../src/push/fcm";
import { PUSH_LOG_RETENTION_MS, prunePushLog } from "../src/push/notify";
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
const push = (token: string, company: string, ops: unknown[]) =>
  call("POST", `/companies/${company}/sync/push`, token, { deviceId: "d1", ops });
const addMember = (company: string, userId: string, role: string) =>
  env.DB.prepare("INSERT INTO memberships (id, user_id, tenant_kind, tenant_id, role, created_at) VALUES (?, ?, 'company', ?, ?, 1)")
    .bind(`m-${company}-${userId}`, userId, company, role)
    .run();
const log = async (company: string) =>
  (await env.DB.prepare("SELECT user_id, type, data_json, delivered FROM push_log WHERE tenant_id = ? ORDER BY id").bind(company)
    .all<{ user_id: string; type: string; data_json: string; delivered: number }>()).results
    .map((r) => ({ userId: r.user_id, type: r.type, data: JSON.parse(r.data_json) as Record<string, string>, delivered: r.delivered }));

const days = { today: "2026-11-01", tomorrow: "2026-11-02" };

describe("AC-B30 pushEventsFor (unit)", () => {
  it("AC-F36 a task assigned to someone else notifies them once; reassigning, not editing, notifies again", () => {
    const task = { id: "t1", jobId: "j1", title: "Set the outlets", assigneeId: "crew" };
    expect(pushEventsFor("task", null, task, "owner", days)).toEqual([
      { type: "task_assigned", to: "crew", jobId: "j1", data: { taskId: "t1", jobId: "j1", title: "Set the outlets" } },
    ]);
    expect(pushEventsFor("task", task, { ...task, title: "Set all outlets" }, "owner", days)).toEqual([]);
    expect(pushEventsFor("task", task, { ...task, assigneeId: "lead" }, "owner", days).map((e) => e.to)).toEqual(["lead"]);
  });
  it("no task push for yourself, an unassigned, done or deleted task", () => {
    const task = { id: "t1", jobId: "j1", title: "Trim", assigneeId: "owner" };
    expect(pushEventsFor("task", null, task, "owner", days)).toEqual([]);
    expect(pushEventsFor("task", null, { ...task, assigneeId: null }, "x", days)).toEqual([]);
    expect(pushEventsFor("task", null, { ...task, done: true }, "x", days)).toEqual([]);
    expect(pushEventsFor("task", null, { ...task, deletedAt: 5 }, "x", days)).toEqual([]);
  });
  it("AC-F42 a schedule change on today or tomorrow tells the member, a later day does not", () => {
    const a = { id: "a1", jobId: "j1", memberId: "crew", date: "2026-11-02" };
    expect(pushEventsFor("assignment", null, a, "owner", days)).toEqual([
      { type: "schedule_changed", to: "crew", jobId: "j1", data: { date: "2026-11-02", jobId: "j1" } },
    ]);
    expect(pushEventsFor("assignment", null, { ...a, date: "2026-11-05" }, "owner", days)).toEqual([]);
    expect(pushEventsFor("assignment", a, { ...a }, "owner", days)).toEqual([]);
    expect(pushEventsFor("assignment", a, { ...a, deletedAt: 9 }, "owner", days).map((e) => e.to)).toEqual(["crew"]);
    // Moved from tomorrow to next week: they hear that tomorrow changed.
    expect(pushEventsFor("assignment", a, { ...a, date: "2026-11-09" }, "owner", days).map((e) => e.data.date)).toEqual(["2026-11-02"]);
  });
  it("AC-F42 handing tomorrow's job to another member tells both", () => {
    const a = { id: "a1", jobId: "j1", memberId: "crew", date: "2026-11-02" };
    expect(pushEventsFor("assignment", a, { ...a, memberId: "lead" }, "owner", days).map((e) => e.to)).toEqual(["crew", "lead"]);
  });
  it("a change order turning SIGNED tells the admins once; a timesheet approval tells its member", () => {
    const co = { id: "co1", jobId: "j1", title: "Add a circuit", status: "SENT" };
    expect(pushEventsFor("change_order", co, { ...co, status: "SIGNED", signedBy: "Dana Hale" }, "lead", days)).toEqual([
      { type: "change_order_signed", to: "admins", jobId: "j1", data: { changeOrderId: "co1", jobId: "j1", title: "Add a circuit", signedBy: "Dana Hale" } },
    ]);
    expect(pushEventsFor("change_order", { ...co, status: "SIGNED" }, { ...co, status: "SIGNED" }, "lead", days)).toEqual([]);
    const ap = { id: "ap1", memberId: "crew", weekStart: "2026-10-26", approvedBy: "owner" };
    expect(pushEventsFor("timesheet_approval", null, ap, "owner", days)).toEqual([
      { type: "timesheet_approved", to: "crew", jobId: null, data: { weekStart: "2026-10-26" } },
    ]);
    expect(pushEventsFor("timesheet_approval", ap, { ...ap, at: 7 }, "owner", days)).toEqual([]);
    expect(pushEventsFor("note", null, { id: "n" }, "owner", days)).toEqual([]);
  });
  it("today and tomorrow follow the company's time zone, Eastern by default", () => {
    const lateEvening = Date.UTC(2026, 10, 2, 3, 30); // 22:30 on Nov 1 in New York, 19:30 in Los Angeles
    expect(companyDays(lateEvening, undefined)).toEqual({ today: "2026-11-01", tomorrow: "2026-11-02" });
    expect(companyDays(lateEvening, "UTC")).toEqual({ today: "2026-11-02", tomorrow: "2026-11-03" });
    expect(companyDays(Date.UTC(2026, 11, 31, 12), "Not/AZone")).toEqual({ today: "2026-12-31", tomorrow: "2027-01-01" });
  });
  it("reads the FCM service account from the secret's JSON", () => {
    expect(parseServiceAccount(undefined)).toBeNull();
    expect(parseServiceAccount("{not json")).toBeNull();
    expect(parseServiceAccount(JSON.stringify({ project_id: "p" }))).toBeNull();
    expect(parseServiceAccount(JSON.stringify({ project_id: "p", client_email: "e@p.iam", private_key: "k" }))?.token_uri)
      .toBe("https://oauth2.googleapis.com/token");
  });
});

describe("AC-B30 PUT and DELETE /me/push-token", () => {
  it("registers a token per member and app, moves it to whoever signs in on that phone, and removes only your own", async () => {
    const a = await seedUser("pt-a", "pt-a@example.com");
    const b = await seedUser("pt-b", "pt-b@example.com");
    expect((await call("PUT", "/me/push-token", a, { token: "", app: "field-log" })).status).toBe(400);
    expect((await call("PUT", "/me/push-token", a, { token: "tok-1", app: "web" })).status).toBe(400);
    expect((await app.request("/me/push-token", { method: "PUT" }, env)).status).toBe(401);
    expect((await call("PUT", "/me/push-token", a, { token: "tok-1", app: "field-log" })).status).toBe(204);
    const owner = () => env.DB.prepare("SELECT user_id FROM push_tokens WHERE token = 'tok-1'").first<{ user_id: string }>();
    expect((await owner())?.user_id).toBe("pt-a");
    expect((await call("PUT", "/me/push-token", b, { token: "tok-1", app: "field-log" })).status).toBe(204);
    expect((await owner())?.user_id).toBe("pt-b");
    expect((await call("DELETE", "/me/push-token", a, { token: "tok-1" })).status).toBe(204);
    expect((await owner())?.user_id).toBe("pt-b");
    expect((await call("DELETE", "/me/push-token", b, { token: "tok-1" })).status).toBe(204);
    expect(await owner()).toBeNull();
  });
  it("signing out every phone drops the account's tokens", async () => {
    const a = await seedUser("pt-r", "pt-r@example.com");
    await call("PUT", "/me/push-token", a, { token: "tok-r", app: "field-log" });
    expect((await call("POST", "/auth/revoke", a)).status).toBe(204);
    expect(await env.DB.prepare("SELECT 1 FROM push_tokens WHERE user_id = 'pt-r'").first()).toBeNull();
  });
});

describe("AC-B30 sync push sends the notifications", () => {
  it("without FCM configured, nothing is fetched and the push log records who would have heard", async () => {
    const fetchSpy = vi.fn();
    vi.stubGlobal("fetch", fetchSpy);
    try {
      const owner = await seedUser("pn-own", "pn-own@example.com");
      await seedUser("pn-crew", "pn-crew@example.com");
      await seedCompany("pn-co", "Hale Electric", "pn-own");
      await addMember("pn-co", "pn-crew", "crew");
      const res = await push(owner, "pn-co", [
        op(1, "job", { id: "pn-j", name: "Hale kitchen" }),
        op(2, "task", { id: "pn-t", jobId: "pn-j", title: "Set the outlets", assigneeId: "pn-crew" }),
        op(3, "task", { id: "pn-t2", jobId: "pn-j", title: "Mine", assigneeId: "pn-own" }),
        op(4, "task", { id: "pn-t3", jobId: "pn-j", title: "Stranger", assigneeId: "not-a-member" }),
      ]);
      expect(res.status).toBe(200);
      expect(fetchSpy).not.toHaveBeenCalled();
      expect(await log("pn-co")).toEqual([
        {
          userId: "pn-crew", type: "task_assigned", delivered: 0,
          data: { type: "task_assigned", companyId: "pn-co", taskId: "pn-t", jobId: "pn-j", title: "Set the outlets", jobName: "Hale kitchen" },
        },
      ]);
      // A retried push of the same ops is deduped and sends nothing again.
      await push(owner, "pn-co", [op(2, "task", { id: "pn-t", jobId: "pn-j", title: "Set the outlets", assigneeId: "pn-crew" })]);
      expect(await log("pn-co")).toHaveLength(1);
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("a change order signed on a lead's phone tells the Owner and Office, not the lead", async () => {
    const lead = await seedUser("pc-lead", "pc-lead@example.com");
    await seedUser("pc-own", "pc-own@example.com");
    await seedUser("pc-off", "pc-off@example.com");
    await seedCompany("pc-co", "Hale Electric", "pc-own");
    await addMember("pc-co", "pc-off", "office");
    await addMember("pc-co", "pc-lead", "lead");
    await env.DB.prepare(
      `INSERT INTO sync_rows (tenant_kind, tenant_id, entity, entity_id, row_json, times_json, authoritative, server_seq)
       VALUES ('company', 'pc-co', 'job_crew', 'jc', ?, '{}', 0, 0)`,
    ).bind(JSON.stringify({ id: "jc", jobId: "pc-j", memberId: "pc-lead" })).run();
    await push(lead, "pc-co", [op(1, "change_order", { id: "pc-c", jobId: "pc-j", title: "Add a circuit", status: "SENT" })]);
    expect(await log("pc-co")).toEqual([]);
    await push(lead, "pc-co", [op(2, "change_order", { id: "pc-c", jobId: "pc-j", status: "SIGNED", signedBy: "Dana Hale" })]);
    expect((await log("pc-co")).map((r) => [r.userId, r.type, r.data.signedBy])).toEqual([
      ["pc-off", "change_order_signed", "Dana Hale"],
      ["pc-own", "change_order_signed", "Dana Hale"],
    ]);
  });

  describe("with FCM configured", () => {
    let fetchMock: ReturnType<typeof vi.fn>;
    let serviceAccount: string;
    beforeEach(async () => {
      const { privateKey } = await generateKeyPair("RS256", { extractable: true });
      serviceAccount = JSON.stringify({
        project_id: "homerecord-push", client_email: "push@homerecord-push.iam.gserviceaccount.com",
        private_key: await exportPKCS8(privateKey), token_uri: "https://oauth2.googleapis.com/token",
      });
      fetchMock = vi.fn(async (url: string, init: RequestInit) => {
        if (url === "https://oauth2.googleapis.com/token") return new Response(JSON.stringify({ access_token: "ya29.test" }), { status: 200 });
        const token = (JSON.parse(init.body as string) as { message: { token: string } }).message.token;
        if (token === "tok-gone") {
          return new Response(JSON.stringify({ error: { status: "NOT_FOUND", details: [{ errorCode: "UNREGISTERED" }] } }), { status: 404 });
        }
        return new Response(JSON.stringify({ name: "projects/homerecord-push/messages/1" }), { status: 200 });
      });
      vi.stubGlobal("fetch", fetchMock);
    });
    afterEach(() => vi.unstubAllGlobals());

    it("AC-F36 AC-F42 sends data messages to each Field Log phone of the member and drops a dead token", async () => {
      const owner = await seedUser("pf-own", "pf-own@example.com");
      const crew = await seedUser("pf-crew", "pf-crew@example.com");
      await seedCompany("pf-co", "Hale Electric", "pf-own");
      await addMember("pf-co", "pf-crew", "crew");
      for (const [token, app] of [["tok-phone", "field-log"], ["tok-gone", "field-log"], ["tok-hm", "house-manual"]]) {
        await call("PUT", "/me/push-token", crew, { token, app });
      }
      const fcmEnv = { ...env, FCM_SERVICE_ACCOUNT: serviceAccount };
      const tomorrow = companyDays(Date.now(), undefined).tomorrow;
      const res = await app.request(
        "/companies/pf-co/sync/push",
        {
          method: "POST",
          headers: { Authorization: `Bearer ${owner}`, "Content-Type": "application/json" },
          body: JSON.stringify({ deviceId: "d1", ops: [op(1, "assignment", { id: "pf-a", jobId: "pf-j", memberId: "pf-crew", date: tomorrow })] }),
        },
        fcmEnv,
      );
      expect(res.status).toBe(200);

      const tokenCalls = fetchMock.mock.calls.filter(([u]) => u === "https://oauth2.googleapis.com/token");
      expect(tokenCalls).toHaveLength(1);
      const assertion = new URLSearchParams(tokenCalls[0][1].body as string).get("assertion") ?? "";
      const claims = JSON.parse(atob(assertion.split(".")[1].replace(/-/g, "+").replace(/_/g, "/")));
      expect(claims).toMatchObject({ iss: "push@homerecord-push.iam.gserviceaccount.com", scope: "https://www.googleapis.com/auth/firebase.messaging" });

      const sends = fetchMock.mock.calls.filter(([u]) => String(u).includes("fcm.googleapis.com"));
      expect(sends.map(([u]) => u)).toEqual(Array(2).fill("https://fcm.googleapis.com/v1/projects/homerecord-push/messages:send"));
      const messages = sends.map(([, init]) => JSON.parse(init.body as string).message);
      expect(messages.map((m) => m.token).sort()).toEqual(["tok-gone", "tok-phone"]);
      expect(messages[0]).toMatchObject({ data: { type: "schedule_changed", companyId: "pf-co", date: tomorrow, jobId: "pf-j" }, android: { priority: "HIGH" } });
      expect(messages[0].notification).toBeUndefined();
      expect(sends[0][1].headers).toMatchObject({ Authorization: "Bearer ya29.test" });

      expect((await log("pf-co")).map((r) => [r.userId, r.type, r.delivered])).toEqual([["pf-crew", "schedule_changed", 1]]);
      const left = await env.DB.prepare("SELECT token FROM push_tokens WHERE user_id = 'pf-crew' ORDER BY token").all<{ token: string }>();
      expect(left.results.map((r) => r.token)).toEqual(["tok-hm", "tok-phone"]);
    });

    it("timesheet approval reaches the member; the access token is reused from the cache", async () => {
      const owner = await seedUser("pa-own", "pa-own@example.com");
      const crew = await seedUser("pa-crew", "pa-crew@example.com");
      await seedCompany("pa-co", "Hale Electric", "pa-own");
      await addMember("pa-co", "pa-crew", "crew");
      await call("PUT", "/me/push-token", crew, { token: "tok-pa", app: "field-log" });
      const fcmEnv = { ...env, FCM_SERVICE_ACCOUNT: serviceAccount };
      const send = (outboxId: number, id: string, week: string) =>
        app.request("/companies/pa-co/sync/push", {
          method: "POST",
          headers: { Authorization: `Bearer ${owner}`, "Content-Type": "application/json" },
          body: JSON.stringify({ deviceId: "d1", ops: [op(outboxId, "timesheet_approval", { id, memberId: "pa-crew", weekStart: week, approvedBy: "pa-own", at: 1 })] }),
        }, fcmEnv);
      await send(1, "pa-1", "2026-10-19");
      await send(2, "pa-2", "2026-10-26");
      const sends = fetchMock.mock.calls.filter(([u]) => String(u).includes("fcm.googleapis.com"));
      expect(sends.map(([, i]) => JSON.parse(i.body as string).message.data.weekStart)).toEqual(["2026-10-19", "2026-10-26"]);
      // The first test of this block may have cached the token already; either way at most one exchange happens.
      expect(fetchMock.mock.calls.filter(([u]) => u === "https://oauth2.googleapis.com/token").length).toBeLessThanOrEqual(1);
    });
  });

  it("the daily cron drops push log rows older than 30 days", async () => {
    const now = Date.now();
    await env.DB.batch([
      env.DB.prepare("INSERT INTO push_log (user_id, tenant_id, type, data_json, delivered, at) VALUES ('u', 'prune-co', 't', '{}', 0, ?)").bind(now - PUSH_LOG_RETENTION_MS - 1),
      env.DB.prepare("INSERT INTO push_log (user_id, tenant_id, type, data_json, delivered, at) VALUES ('u', 'prune-co', 't', '{}', 0, ?)").bind(now),
    ]);
    await prunePushLog(env.DB, now);
    expect(await log("prune-co")).toHaveLength(1);
  });
});
