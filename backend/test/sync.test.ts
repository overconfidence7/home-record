import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import app from "../src/index";
import { mergeRow, unionLists } from "../src/sync/merge";
import { seedCompany, seedUser } from "./helpers";

const call = (method: string, path: string, token: string, body?: unknown) =>
  app.request(
    path,
    { method, headers: { Authorization: `Bearer ${token}`, "Content-Type": "application/json" }, body: body ? JSON.stringify(body) : undefined },
    env,
  );
const json = async <T = Record<string, any>>(res: Response) => (await res.json()) as T;
const op = (outboxId: number, entityId: string, row: Record<string, unknown>, updatedAt: number, entity = "task") => ({
  outboxId, entity, entityId, op: "upsert", payload: JSON.stringify({ id: entityId, ...row }), updatedAt,
});
const push = (token: string, company: string, deviceId: string, ops: unknown[]) =>
  call("POST", `/companies/${company}/sync/push`, token, { deviceId, ops });
const pull = async (token: string, company: string, since = 0) => json(await call("GET", `/companies/${company}/sync/pull?since=${since}`, token));

describe("AC-B20 merge rules (unit)", () => {
  it("last writer wins per field, not per row", () => {
    const first = mergeRow(null, { id: "t", title: "A", done: false }, 10, false);
    const second = mergeRow({ ...first, authoritative: false }, { id: "t", title: "B" }, 20, false);
    const third = mergeRow({ ...second, authoritative: false }, { id: "t", done: true }, 15, false);
    expect(third.row).toMatchObject({ title: "B", done: true });
  });
  it("an older write to a field is dropped and logged", () => {
    const first = mergeRow(null, { id: "t", title: "New" }, 20, false);
    const stale = mergeRow({ ...first, authoritative: false }, { id: "t", title: "Old" }, 10, false);
    expect(stale.row.title).toBe("New");
    expect(stale.conflicts).toEqual([{ field: "title", kept: "New", dropped: "Old", reason: "older" }]);
  });
  it("unions only the append-only lists: a punch's edit log, by id and by value", () => {
    expect(unionLists([{ id: "a" }, { id: "b" }], [{ id: "b" }, { id: "c" }])).toEqual([{ id: "a" }, { id: "b" }, { id: "c" }]);
    const a = mergeRow(null, { id: "p", editsJson: '[{"at":1,"by":"o"}]' }, 10, false, "punch");
    const b = mergeRow({ ...a, authoritative: false }, { id: "p", editsJson: '[{"at":2,"by":"o"}]' }, 5, false, "punch");
    expect(JSON.parse(b.row.editsJson as string)).toEqual([{ at: 1, by: "o" }, { at: 2, by: "o" }]);
  });
  it("a moved room keeps only its new corners and a removed phase stays removed (cr:sync-json-union)", () => {
    const square = '[[0,0],[120,0],[120,120],[0,120]]';
    const moved = '[[24,0],[144,0],[144,120],[24,120]]';
    const first = mergeRow(null, { id: "r", polygonJson: square }, 10, false, "room");
    const second = mergeRow({ ...first, authoritative: false }, { id: "r", polygonJson: moved }, 20, false, "room");
    expect(second.row.polygonJson).toBe(moved);
    const job = mergeRow(null, { id: "j", phasesJson: '["Demo","Frame","Paint"]' }, 10, false, "job");
    const trimmed = mergeRow({ ...job, authoritative: false }, { id: "j", phasesJson: '["Demo","Paint"]' }, 20, false, "job");
    expect(JSON.parse(trimmed.row.phasesJson as string)).toEqual(["Demo", "Paint"]);
    const stale = mergeRow({ ...trimmed, authoritative: false }, { id: "j", phasesJson: '["Frame"]' }, 15, false, "job");
    expect(JSON.parse(stale.row.phasesJson as string)).toEqual(["Demo", "Paint"]);
    expect(stale.conflicts[0]).toMatchObject({ field: "phasesJson", reason: "older" });
  });
  it("an authoritative row rejects non-authoritative writes", () => {
    const first = mergeRow(null, { id: "p", at: 100 }, 10, true);
    const crew = mergeRow({ ...first, authoritative: true }, { id: "p", at: 200 }, 99, false);
    expect(crew.row.at).toBe(100);
    expect(crew.conflicts[0].reason).toBe("authoritative");
    expect(mergeRow({ ...first, authoritative: true }, { id: "p", at: 300 }, 11, true).row.at).toBe(300);
  });
});

describe("AC-B20 push and pull", () => {
  it("assigns increasing serverSeq and returns changes since a cursor", async () => {
    const owner = await seedUser("s-o1", "s-o1@x.com");
    await seedCompany("s-c1", "Co", "s-o1");
    const res = await push(owner, "s-c1", "dev-a", [op(1, "t1", { title: "One" }, 10), op(2, "t2", { title: "Two" }, 11)]);
    expect(res.status).toBe(200);
    expect((await json(res)).accepted.map((a: any) => a.serverSeq)).toEqual([1, 2]);
    const all = await pull(owner, "s-c1");
    expect(all.rows.map((r: any) => r.entityId)).toEqual(["t1", "t2"]);
    expect(all.serverSeq).toBe(2);
    expect((await pull(owner, "s-c1", 1)).rows.map((r: any) => r.entityId)).toEqual(["t2"]);
  });
  it("is idempotent on retry by (deviceId, outboxId)", async () => {
    const owner = await seedUser("s-o2", "s-o2@x.com");
    await seedCompany("s-c2", "Co", "s-o2");
    const ops = [op(7, "t1", { title: "One" }, 10)];
    const a = await json(await push(owner, "s-c2", "dev-a", ops));
    const b = await json(await push(owner, "s-c2", "dev-a", ops));
    expect(b.accepted).toEqual(a.accepted);
    expect((await pull(owner, "s-c2")).rows).toHaveLength(1);
    expect((await pull(owner, "s-c2")).serverSeq).toBe(1);
  });
  it("merges two devices editing different fields of one row", async () => {
    const owner = await seedUser("s-o3", "s-o3@x.com");
    await seedCompany("s-c3", "Co", "s-o3");
    await push(owner, "s-c3", "dev-a", [op(1, "t1", { title: "Base", done: false }, 10)]);
    await push(owner, "s-c3", "dev-a", [op(2, "t1", { title: "Edited" }, 20)]);
    await push(owner, "s-c3", "dev-b", [op(1, "t1", { done: true }, 15)]);
    expect((await pull(owner, "s-c3")).rows[0].row).toMatchObject({ title: "Edited", done: true });
  });
  it("crew cannot overwrite a punch the Owner edited", async () => {
    const owner = await seedUser("s-o4", "s-o4@x.com");
    await seedCompany("s-c4", "Co", "s-o4");
    const crew = await seedUser("s-w4", "s-w4@x.com");
    await env.DB.prepare("INSERT INTO memberships (id, user_id, tenant_kind, tenant_id, role, created_at) VALUES ('m-s-w4', 's-w4', 'company', 's-c4', 'crew', 1)").run();
    await push(crew, "s-c4", "phone", [op(1, "p1", { memberId: "s-w4", at: 1000, kind: "in" }, 10, "punch")]);
    await push(owner, "s-c4", "office", [op(1, "p1", { at: 900 }, 20, "punch")]);
    const res = await json(await push(crew, "s-c4", "phone", [op(2, "p1", { at: 1100 }, 30, "punch")]));
    expect(res.conflicts[0]).toMatchObject({ field: "at", reason: "authoritative" });
    expect((await pull(owner, "s-c4")).rows[0].row.at).toBe(900);
  });
  it("404s for another tenant and refuses an unknown entity or mismatched id per op (cr:sync-poison-op)", async () => {
    const owner = await seedUser("s-o5", "s-o5@x.com");
    await seedCompany("s-c5", "Co", "s-o5");
    const other = await seedUser("s-o6", "s-o6@x.com");
    expect((await push(other, "s-c5", "d", [])).status).toBe(404);
    expect((await call("GET", "/companies/s-c5/sync/pull", other)).status).toBe(404);
    const res = await push(owner, "s-c5", "d", [
      op(1, "x", {}, 1, "item"),
      { ...op(2, "x", {}, 1), entityId: "y" },
      { ...op(3, "z", {}, 1), payload: "not json" },
      op(4, "t1", { title: "Still syncs" }, 1),
    ]);
    expect(res.status).toBe(200);
    const body = await json(res);
    expect(body.rejected).toEqual([
      { outboxId: 1, reason: "entity" },
      { outboxId: 2, reason: "payload" },
      { outboxId: 3, reason: "payload" },
    ]);
    expect(body.accepted.map((a: any) => a.outboxId)).toEqual([4]);
    expect((await pull(owner, "s-c5")).rows.map((r: any) => r.entityId)).toEqual(["t1"]);
  });
});

describe("AC-F5 job rows sync", () => {
  it("round-trips a client, a job and its crew with the app's field names", async () => {
    const owner = await seedUser("s-o9", "s-o9@x.com");
    await seedCompany("s-c9", "Co", "s-o9");
    const job = {
      companyId: "s-c9", homeId: null, clientId: "cl1", name: "Kitchen remodel", address: "12 Elm St", lat: 40.1, lng: -75.2,
      status: "ACTIVE", phasesJson: '["Demo","Frame"]', startDate: "2026-10-20", endDate: null, budgetHours: 120,
      officeEmailsJson: '["office@x.com"]', notes: null, createdAt: 5, updatedAt: 10, deletedAt: null, source: "FIELD_LOG",
    };
    const res = await push(owner, "s-c9", "dev-a", [
      op(1, "cl1", { companyId: "s-c9", name: "Pat Smith", createdAt: 5, updatedAt: 10 }, 10, "client"),
      op(2, "j1", job, 10, "job"),
      op(3, "jc1", { jobId: "j1", memberId: "m1", createdAt: 5, updatedAt: 10, deletedAt: null }, 10, "job_crew"),
    ]);
    expect(res.status).toBe(200);
    const rows = (await pull(owner, "s-c9")).rows;
    expect(rows.map((r: any) => r.entity).sort()).toEqual(["client", "job", "job_crew"]);
    const pulled = rows.find((r: any) => r.entity === "job");
    expect(JSON.stringify(pulled)).toContain("Kitchen remodel");
    expect(JSON.stringify(pulled)).toContain("12 Elm St");
  });

  it("accepts a job's home, floor and room rows under the company tenant", async () => {
    // AC-F5, AC-F48
    const owner = await seedUser("s-o10", "s-o10@x.com");
    await seedCompany("s-c10", "Co", "s-o10");
    const res = await push(owner, "s-c10", "dev-a", [
      op(1, "h1", { name: "Elm St", createdAt: 5, updatedAt: 10 }, 10, "home"),
      op(2, "f1", { homeId: "h1", name: "Main", sortOrder: 0, createdAt: 5, updatedAt: 10 }, 10, "floor"),
      op(3, "r1", { floorId: "f1", name: "Kitchen", createdAt: 5, updatedAt: 10 }, 10, "room"),
    ]);
    expect(res.status).toBe(200);
    const rows = (await pull(owner, "s-c10")).rows;
    expect(rows.map((r: any) => r.entity).sort()).toEqual(["floor", "home", "room"]);
  });

  it("accepts the walls, doors, stairs and photos Field Log writes for a job (cr:sync-poison-op)", async () => {
    // AC-F48
    const owner = await seedUser("s-o11", "s-o11@x.com");
    await seedCompany("s-c11", "Co", "s-o11");
    const res = await json(await push(owner, "s-c11", "dev-a", [
      op(1, "w1", { roomId: "r1", edgeIndex: 0, updatedAt: 10 }, 10, "wall"),
      op(2, "o1", { roomId: "r1", kind: "DOOR", updatedAt: 10 }, 10, "opening"),
      op(3, "s1", { floorId: "f1", updatedAt: 10 }, 10, "stair"),
      op(4, "ph1", { jobId: "j1", room: "Kitchen", updatedAt: 10 }, 10, "photo"),
    ]));
    expect(res.rejected).toEqual([]);
    expect(res.accepted).toHaveLength(4);
  });
});

describe("AC-B3 sync role gates (AC-F3)", () => {
  const member = (id: string, company: string, role: string) =>
    env.DB.prepare("INSERT INTO memberships (id, user_id, tenant_kind, tenant_id, role, created_at) VALUES (?, ?, 'company', ?, ?, 1)")
      .bind(`m-${id}`, id, company, role)
      .run();
  const punch = (outboxId: number, id: string, memberId: string, jobId: string | null, at = 1000) =>
    op(outboxId, id, { companyId: "co", memberId, jobId, kind: "IN", at, updatedAt: at }, at, "punch");

  async function company(prefix: string) {
    const owner = await seedUser(`${prefix}-o`, `${prefix}-o@x.com`);
    await seedCompany(`${prefix}-c`, "Co", `${prefix}-o`);
    const crew = await seedUser(`${prefix}-w`, `${prefix}-w@x.com`);
    await member(`${prefix}-w`, `${prefix}-c`, "crew");
    const lead = await seedUser(`${prefix}-l`, `${prefix}-l@x.com`);
    await member(`${prefix}-l`, `${prefix}-c`, "lead");
    return { owner, crew, lead, co: `${prefix}-c`, crewId: `${prefix}-w`, leadId: `${prefix}-l`, ownerId: `${prefix}-o` };
  }

  it("a Crew token cannot read other members' punches, shifts, approvals or anyone's rate", async () => {
    const t = await company("b3a");
    await push(t.owner, t.co, "office", [
      punch(1, "p-own", t.crewId, "j1"),
      punch(2, "p-other", t.leadId, "j1"),
      op(3, "s-other", { memberId: t.leadId, jobId: "j1", startAt: 1 }, 1, "shift"),
      op(4, "ap-other", { memberId: t.leadId, weekStart: "2026-10-19", approvedBy: t.ownerId }, 1, "timesheet_approval"),
      op(5, t.crewId, { companyId: t.co, name: "Sam", role: "crew", rateCents: 3200, fcmToken: "tok" }, 1, "member"),
    ]);
    const crewView = await pull(t.crew, t.co);
    expect(crewView.rows.map((r: any) => r.entityId).sort()).toEqual([t.crewId, "p-own"].sort());
    const self = crewView.rows.find((r: any) => r.entity === "member");
    expect(self.row).not.toHaveProperty("rateCents");
    expect(self.row).not.toHaveProperty("fcmToken");
    expect(self.times).not.toHaveProperty("rateCents");
    // The cursor moves past the hidden rows so the phone does not ask for them again.
    expect(crewView.serverSeq).toBe(5);
    expect((await pull(t.owner, t.co)).rows).toHaveLength(5);
    expect((await pull(t.owner, t.co)).rows.find((r: any) => r.entity === "member").row.rateCents).toBe(3200);
  });

  it("a Crew token cannot approve timesheets, edit another member's punch, or change members, the company, jobs or clients", async () => {
    const t = await company("b3b");
    await push(t.owner, t.co, "office", [punch(1, "p-lead", t.leadId, "j1"), punch(2, "p-crew", t.crewId, "j1")]);
    const res = await json(await push(t.crew, t.co, "phone", [
      op(1, "ap1", { memberId: t.crewId, weekStart: "2026-10-19", approvedBy: t.crewId }, 5, "timesheet_approval"),
      punch(2, "p-lead", t.leadId, "j1", 2000),
      op(3, "p-crew", { memberId: t.leadId }, 2000, "punch"),
      op(4, t.crewId, { companyId: t.co, role: "owner", rateCents: 9900 }, 5, "member"),
      op(5, t.co, { name: "Mine now" }, 5, "company"),
      op(6, "j2", { companyId: t.co, name: "Side job" }, 5, "job"),
      op(7, "cl1", { companyId: t.co, name: "Pat" }, 5, "client"),
      op(8, "jc1", { jobId: "j1", memberId: t.crewId }, 5, "job_crew"),
      punch(9, "p-new", t.crewId, "j1", 3000),
      op(10, "ph1", { jobId: "j1", room: "Kitchen" }, 5, "photo"),
      op(11, "n1", { jobId: "j1", day: "2026-10-27", text: "Panel is behind the dryer" }, 5, "note"),
    ]));
    expect(res.rejected).toEqual([1, 2, 3, 4, 5, 6, 7, 8].map((outboxId) => ({ outboxId, reason: "role" })));
    expect(res.accepted.map((a: any) => a.outboxId)).toEqual([9, 10, 11]);
    const owner = (await pull(t.owner, t.co)).rows;
    expect(owner.find((r: any) => r.entityId === "p-lead").row.at).toBe(1000);
    expect(owner.find((r: any) => r.entityId === "p-crew").row.memberId).toBe(t.crewId);
    expect(owner.some((r: any) => r.entity === "timesheet_approval" || r.entity === "company")).toBe(false);
  });

  it("Crew may only mark their own tasks; Lead edits tasks and files on their jobs only", async () => {
    const t = await company("b3c");
    await push(t.owner, t.co, "office", [
      op(1, "jc1", { jobId: "j1", memberId: t.leadId }, 1, "job_crew"),
      op(2, "t-mine", { jobId: "j1", title: "Hang door", assigneeId: t.crewId, done: false }, 1, "task"),
      op(3, "t-other", { jobId: "j1", title: "Patch wall", assigneeId: t.leadId, done: false }, 1, "task"),
    ]);
    const crew = await json(await push(t.crew, t.co, "phone", [
      op(1, "t-mine", { done: true }, 5, "task"),
      op(2, "t-other", { done: true }, 5, "task"),
      op(3, "t-mine", { assigneeId: t.leadId }, 6, "task"),
      op(4, "t-new", { jobId: "j1", title: "Mine", assigneeId: t.crewId }, 5, "task"),
      op(5, "f1", { jobId: "j1", name: "plan.pdf", mime: "application/pdf" }, 5, "job_file"),
    ]));
    expect(crew.accepted.map((a: any) => a.outboxId)).toEqual([1]);
    expect(crew.rejected.map((r: any) => r.outboxId)).toEqual([2, 3, 4, 5]);
    const lead = await json(await push(t.lead, t.co, "lead", [
      op(1, "t-lead", { jobId: "j1", title: "Trim", assigneeId: t.crewId }, 5, "task"),
      op(2, "f1", { jobId: "j1", name: "plan.pdf", mime: "application/pdf" }, 5, "job_file"),
      op(3, "f2", { jobId: "j9", name: "other.pdf", mime: "application/pdf" }, 5, "job_file"),
      op(4, "t-other", { jobId: "j9" }, 6, "task"),
      op(5, "r1", { floorId: "fl1", name: "Kitchen" }, 5, "room"),
    ]));
    expect(lead.accepted.map((a: any) => a.outboxId)).toEqual([1, 2, 5]);
    expect(lead.rejected.map((r: any) => r.outboxId)).toEqual([3, 4]);
    const crewRoom = await json(await push(t.crew, t.co, "phone", [op(6, "r2", { floorId: "fl1", name: "Bath" }, 5, "room")]));
    expect(crewRoom.rejected).toEqual([{ outboxId: 6, reason: "role" }]);
  });

  it("Lead clocks in the crew only on jobs they are assigned to, and sees time only on those jobs", async () => {
    const t = await company("b3d");
    await push(t.owner, t.co, "office", [
      op(1, "a1", { jobId: "j1", memberId: t.leadId, date: "2026-10-27" }, 1, "assignment"),
      punch(2, "p-j2", t.crewId, "j2"),
    ]);
    const res = await json(await push(t.lead, t.co, "lead", [
      { ...punch(1, "p-j1", t.crewId, "j1"), payload: JSON.stringify({ id: "p-j1", memberId: t.crewId, jobId: "j1", kind: "IN", at: 1000, clockedInBy: t.leadId }) },
      punch(2, "p-j2b", t.crewId, "j2"),
      op(3, "p-j2", { at: 1 }, 5, "punch"),
      punch(4, "p-me", t.leadId, null),
      op(5, "ap1", { memberId: t.crewId, weekStart: "2026-10-19", approvedBy: t.leadId }, 5, "timesheet_approval"),
    ]));
    expect(res.accepted.map((a: any) => a.outboxId)).toEqual([1, 4]);
    expect(res.rejected.map((r: any) => r.outboxId)).toEqual([2, 3, 5]);
    const seen = (await pull(t.lead, t.co)).rows.filter((r: any) => r.entity === "punch").map((r: any) => r.entityId).sort();
    expect(seen).toEqual(["p-j1", "p-me"]);
  });

  it("a removed crew assignment ends the Lead's access to that job", async () => {
    const t = await company("b3e");
    await push(t.owner, t.co, "office", [op(1, "jc1", { jobId: "j1", memberId: t.leadId, deletedAt: null }, 1, "job_crew")]);
    expect((await json(await push(t.lead, t.co, "lead", [punch(1, "p1", t.crewId, "j1")]))).accepted).toHaveLength(1);
    await push(t.owner, t.co, "office", [op(2, "jc1", { deletedAt: 50 }, 50, "job_crew")]);
    expect((await json(await push(t.lead, t.co, "lead", [punch(2, "p2", t.crewId, "j1")]))).rejected).toEqual([{ outboxId: 2, reason: "role" }]);
  });
});
