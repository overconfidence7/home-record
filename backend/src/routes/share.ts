import { Hono, type Context } from "hono";
import { bodyLimit } from "hono/body-limit";
import { z } from "zod";
import type { AppEnv } from "../env";
import type { FieldTimes, Row } from "../sync/merge";
import { companyDays, DEFAULT_TIME_ZONE, pushEventsFor } from "../push/events";
import { deliver } from "../push/notify";

/**
 * Share links (AC-B35, AC-B36). Owner or Office makes a `share_link` row in the app (kind gallery, log or co; the
 * job; for a log or change order the row's id in `targetId`; a random token) and it reaches this server by sync.
 * Anyone with the token sees a read-only page: the job's photo gallery, one daily log, or a change order the client
 * can sign. A revoked or deleted link, or one whose job or record is gone, answers "This link is turned off."
 * Nothing on these pages needs an account, so they show only what the link names, never other jobs or members.
 */
export const share = new Hono<AppEnv>();

export type ShareKind = "gallery" | "log" | "co";
const TOKEN = /^[A-Za-z0-9_-]{16,64}$/;
const ID = /^[A-Za-z0-9._-]{1,64}$/;

interface Link {
  companyId: string;
  kind: ShareKind;
  jobId: string;
  targetId: string | null;
  token: string;
}

const live = (row: Row | null): row is Row => row !== null && (row.deletedAt === null || row.deletedAt === undefined);
const str = (v: unknown): string | null => (typeof v === "string" && v.trim() !== "" ? v : null);
const num = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);

/** The live link for this token and kind, or null; the job must be live too. */
export async function findLink(db: D1Database, kind: ShareKind, token: string): Promise<Link | null> {
  if (!TOKEN.test(token)) return null;
  const { results } = await db
    .prepare(
      `SELECT tenant_id, row_json FROM sync_rows
       WHERE tenant_kind = 'company' AND entity = 'share_link' AND json_extract(row_json, '$.token') = ?`,
    )
    .bind(token)
    .all<{ tenant_id: string; row_json: string }>();
  for (const r of results) {
    const row = JSON.parse(r.row_json) as Row;
    if (!live(row) || row.revoked === true || row.revoked === 1 || row.kind !== kind) continue;
    const jobId = str(row.jobId);
    if (!jobId || !live(await rowOf(db, r.tenant_id, "job", jobId))) continue;
    return { companyId: r.tenant_id, kind, jobId, targetId: str(row.targetId), token };
  }
  return null;
}

async function rowOf(db: D1Database, companyId: string, entity: string, id: string): Promise<Row | null> {
  const r = await db
    .prepare("SELECT row_json FROM sync_rows WHERE tenant_kind = 'company' AND tenant_id = ? AND entity = ? AND entity_id = ?")
    .bind(companyId, entity, id)
    .first<{ row_json: string }>();
  return r ? (JSON.parse(r.row_json) as Row) : null;
}

/** Live photos of a job that are uploaded (have an R2 key), oldest first. */
async function jobPhotos(db: D1Database, companyId: string, jobId: string): Promise<Row[]> {
  const { results } = await db
    .prepare(
      `SELECT row_json FROM sync_rows WHERE tenant_kind = 'company' AND tenant_id = ? AND entity = 'photo'
       AND json_extract(row_json, '$.jobId') = ?`,
    )
    .bind(companyId, jobId)
    .all<{ row_json: string }>();
  return results
    .map((r) => JSON.parse(r.row_json) as Row)
    .filter((p) => live(p) && str(p.r2Key) !== null)
    .sort((a, b) => (num(a.takenAt) ?? 0) - (num(b.takenAt) ?? 0));
}

async function companyOf(db: D1Database, companyId: string): Promise<{ name: string; zone: string }> {
  const r = await db.prepare("SELECT name, settings FROM companies WHERE id = ?").bind(companyId).first<{ name: string; settings: string | null }>();
  let zone = DEFAULT_TIME_ZONE;
  try {
    const z = (JSON.parse(r?.settings ?? "{}") as { timeZone?: string }).timeZone;
    if (z) {
      new Intl.DateTimeFormat("en-US", { timeZone: z });
      zone = z;
    }
  } catch {
    zone = DEFAULT_TIME_ZONE;
  }
  return { name: r?.name ?? "", zone };
}

/** The change order a co link names: its `targetId`, or (older links) the job's change order carrying the token. */
async function changeOrderFor(db: D1Database, link: Link): Promise<Row | null> {
  if (link.targetId) {
    const row = await rowOf(db, link.companyId, "change_order", link.targetId);
    return live(row) && row.jobId === link.jobId ? row : null;
  }
  const r = await db
    .prepare(
      `SELECT row_json FROM sync_rows WHERE tenant_kind = 'company' AND tenant_id = ? AND entity = 'change_order'
       AND json_extract(row_json, '$.shareToken') = ?`,
    )
    .bind(link.companyId, link.token)
    .first<{ row_json: string }>();
  const row = r ? (JSON.parse(r.row_json) as Row) : null;
  return live(row) && row.jobId === link.jobId ? row : null;
}

// ---- HTML ----

const esc = (s: unknown): string =>
  String(s ?? "").replace(/[&<>"']/g, (ch) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[ch] ?? ch);

const money = (cents: number) =>
  (cents < 0 ? "-$" : "$") + (Math.abs(cents) / 100).toLocaleString("en-US", { minimumFractionDigits: 2, maximumFractionDigits: 2 });

const dayFmt = (zone: string) => new Intl.DateTimeFormat("en-US", { timeZone: zone, weekday: "long", month: "long", day: "numeric", year: "numeric" });
const timeFmt = (zone: string) => new Intl.DateTimeFormat("en-US", { timeZone: zone, month: "short", day: "numeric", year: "numeric", hour: "numeric", minute: "2-digit" });
const isoDay = (ms: number, zone: string) => new Intl.DateTimeFormat("en-CA", { timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit" }).format(ms);

const STYLE = `
:root{--bg:#fbf8f4;--surface:#fff;--text:#1d1b19;--muted:#5f5a54;--line:#e4ded6;--accent:#8a4b1f;--onaccent:#fff}
@media (prefers-color-scheme:dark){:root{--bg:#171513;--surface:#221f1c;--text:#ece6df;--muted:#b3aaa0;--line:#3a3530;--accent:#f0b48a;--onaccent:#3b1d06}}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--text);font:16px/1.5 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif}
main{max-width:760px;margin:0 auto;padding:24px 16px 48px}h1{font-size:1.6rem;line-height:1.25;margin:4px 0 4px}
h2{font-size:1.1rem;margin:28px 0 8px}.muted{color:var(--muted)}.from{font-size:.9rem;color:var(--muted);margin:0}
.card{background:var(--surface);border:1px solid var(--line);border-radius:16px;padding:16px;margin-top:16px}
.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:12px}
figure{margin:0}figure img{width:100%;aspect-ratio:4/3;object-fit:cover;border-radius:12px;background:var(--line);display:block}
figcaption{font-size:.9rem;margin-top:4px}table{width:100%;border-collapse:collapse}
th,td{text-align:left;padding:8px 4px;border-bottom:1px solid var(--line);vertical-align:top}td.n,th.n{text-align:right;white-space:nowrap}
.total td{font-weight:700;border-bottom:0}ul{padding-left:20px;margin:4px 0}
.status{display:inline-block;padding:2px 10px;border-radius:999px;border:1px solid var(--line);font-size:.9rem}
label{display:block;font-weight:600;margin:12px 0 4px}input[type=text]{width:100%;min-height:48px;font:inherit;padding:8px 12px;border:1px solid var(--muted);border-radius:8px;background:var(--surface);color:var(--text)}
canvas{width:100%;height:auto;aspect-ratio:3/1;touch-action:none;border:1px solid var(--muted);border-radius:12px;background:#fff;display:block}
.row{display:flex;gap:12px;margin-top:12px;flex-wrap:wrap}button{min-height:48px;padding:0 24px;font:inherit;font-weight:600;border-radius:999px;border:1px solid var(--muted);background:transparent;color:var(--text)}
button.primary{background:var(--accent);color:var(--onaccent);border-color:var(--accent)}button:disabled{opacity:.4}
.sig{max-width:320px;background:#fff;border-radius:8px;border:1px solid var(--line)}.error{color:#b3261e}
@media (prefers-color-scheme:dark){.error{color:#f2b8b5}}`;

function page(title: string, body: string): string {
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">` +
    `<meta name="robots" content="noindex"><title>${esc(title)}</title><style>${STYLE}</style></head><body><main>${body}</main></body></html>`;
}

const PAGE_HEADERS = { "Cache-Control": "no-store", "Referrer-Policy": "no-referrer", "X-Robots-Tag": "noindex" };

function turnedOff(c: Context) {
  return c.html(
    page("Link turned off", `<h1>This link is turned off.</h1><p class="muted">Ask the contractor who sent it for a new one.</p>`),
    404,
    PAGE_HEADERS,
  );
}

const photoUrl = (link: Link, id: string) => `/share/${link.kind}/${encodeURIComponent(link.token)}/photo/${encodeURIComponent(id)}`;

function figure(link: Link, p: Row): string {
  const caption = str(p.caption);
  return `<figure><img loading="lazy" src="${esc(photoUrl(link, String(p.id)))}" alt="${esc(caption ?? "Job photo")}">` +
    (caption ? `<figcaption>${esc(caption)}</figcaption>` : "") + `</figure>`;
}

function header(company: string, job: Row, what: string): string {
  const address = str(job.address);
  return `<p class="from">${esc(company)}${company ? " · " : ""}${esc(what)}</p><h1>${esc(job.name)}</h1>` +
    (address ? `<p class="muted">${esc(address)}</p>` : "");
}

// ---- Gallery (AC-B35, AC-F28) ----

share.get("/share/gallery/:token", async (c) => {
  const db = c.env.DB;
  const link = await findLink(db, "gallery", c.req.param("token"));
  if (!link) return turnedOff(c);
  const job = await rowOf(db, link.companyId, "job", link.jobId);
  if (!live(job)) return turnedOff(c);
  const { name, zone } = await companyOf(db, link.companyId);
  const photos = await jobPhotos(db, link.companyId, link.jobId);
  const byDay = new Map<string, Row[]>();
  for (const p of photos) {
    const day = isoDay(num(p.takenAt) ?? 0, zone);
    byDay.set(day, [...(byDay.get(day) ?? []), p]);
  }
  const days = [...byDay.entries()].sort(([a], [b]) => (a < b ? 1 : -1));
  const body = header(name, job, "Job photos") +
    (days.length === 0
      ? `<div class="card"><p>No photos yet.</p><p class="muted">Photos show here as the crew takes them.</p></div>`
      : days.map(([, list]) =>
          `<h2>${esc(dayFmt(zone).format(num(list[0].takenAt) ?? 0))}</h2><div class="grid">${list.map((p) => figure(link, p)).join("")}</div>`,
        ).join(""));
  return c.html(page(`${String(job.name ?? "Job")} photos`, body), 200, PAGE_HEADERS);
});

// ---- Daily log (AC-B35) ----

function section(value: unknown): unknown {
  if (typeof value !== "string") return null;
  try {
    const parsed = JSON.parse(value) as unknown;
    return parsed && typeof parsed === "object" && !Array.isArray(parsed) && "value" in parsed ? (parsed as { value: unknown }).value : parsed;
  } catch {
    return null;
  }
}
const list = (v: unknown): unknown[] => (Array.isArray(v) ? v : []);
const hours = (minutes: number) => `${Math.floor(minutes / 60)}h ${String(minutes % 60).padStart(2, "0")}m`;
const DELAY_WORDS: Record<string, string> = {
  WEATHER: "Weather", MATERIALS: "Materials", INSPECTION: "Inspection", CLIENT: "Client", OTHER: "Other",
};
const words = (key: string) => DELAY_WORDS[key] ?? key.charAt(0) + key.slice(1).toLowerCase().replace(/_/g, " ");

share.get("/share/log/:token", async (c) => {
  const db = c.env.DB;
  const link = await findLink(db, "log", c.req.param("token"));
  if (!link || !link.targetId) return turnedOff(c);
  const [job, log] = [await rowOf(db, link.companyId, "job", link.jobId), await rowOf(db, link.companyId, "daily_log", link.targetId)];
  if (!live(job) || !live(log) || log.jobId !== link.jobId) return turnedOff(c);
  const { name } = await companyOf(db, link.companyId);
  const parts: string[] = [];
  const day = str(log.day);
  const weather = section(log.loggedWeatherJson) as Record<string, unknown> | null;
  if (weather && typeof weather === "object") {
    const hi = num(weather.highF), lo = num(weather.lowF), rain = num(weather.precipIn);
    const bits = [str(weather.conditions) && weather.conditions !== "unknown" ? words(String(weather.conditions).toUpperCase()) : null,
      hi !== null ? `High ${hi}°F` : null, lo !== null ? `low ${lo}°F` : null, rain ? `${rain} in rain` : null].filter(Boolean);
    if (bits.length) parts.push(`<h2>Weather</h2><p>${esc(bits.join(", "))}</p>`);
  }
  const crew = list(section(log.crewJson)) as Record<string, unknown>[];
  if (crew.length) {
    parts.push(`<h2>Crew</h2><ul>${crew.map((m) => `<li>${esc(m.name)}${num(m.minutes) !== null ? ` · ${hours(num(m.minutes) ?? 0)}` : ""}</li>`).join("")}</ul>`);
  }
  const work = list(section(log.workDoneJson)).filter((w) => typeof w === "string" && w.trim());
  if (work.length) parts.push(`<h2>Work done</h2><ul>${work.map((w) => `<li>${esc(w)}</li>`).join("")}</ul>`);
  const tasks = list(section(log.tasksJson)) as Record<string, unknown>[];
  if (tasks.length) parts.push(`<h2>Tasks finished</h2><ul>${tasks.map((t) => `<li>${esc(t.title)}</li>`).join("")}</ul>`);
  const cos = list(section(log.changeOrdersJson)) as Record<string, unknown>[];
  if (cos.length) parts.push(`<h2>Change orders</h2><ul>${cos.map((x) => `<li>${esc(x.title)} · ${money(num(x.totalCents) ?? 0)}</li>`).join("")}</ul>`);
  const delays = list(section(log.delaysJson)) as Record<string, unknown>[];
  if (delays.length) {
    parts.push(`<h2>Delays</h2><ul>${delays.map((d) => `<li>${esc(words(String(d.reason ?? "OTHER")))}${str(d.note) ? `: ${esc(d.note)}` : ""}</li>`).join("")}</ul>`);
  }
  for (const [title, value] of [["Visitors", log.visitors], ["Safety", log.safety], ["Notes for the client", log.clientNotes]] as const) {
    if (str(value)) parts.push(`<h2>${title}</h2><p>${esc(value)}</p>`);
  }
  const photoIds = new Set(list(section(log.photosJson)).filter((x): x is string => typeof x === "string"));
  const photos = (await jobPhotos(db, link.companyId, link.jobId)).filter((p) => photoIds.has(String(p.id)));
  if (photos.length) parts.push(`<h2>Photos</h2><div class="grid">${photos.map((p) => figure(link, p)).join("")}</div>`);
  const dayText = day ? dayFmt("UTC").format(Date.parse(`${day}T12:00:00Z`)) : "";
  const body = header(name, job, `Daily log${dayText ? ` · ${dayText}` : ""}`) +
    (parts.length ? parts.join("") : `<div class="card"><p>Nothing was logged this day.</p></div>`);
  return c.html(page(`${String(job.name ?? "Job")} daily log ${day ?? ""}`.trim(), body), 200, PAGE_HEADERS);
});

// ---- Change order (AC-B36, AC-F40) ----

function lines(row: Row): { description: string; qtyMilli: number; unitCents: number }[] {
  try {
    return (JSON.parse(String(row.linesJson ?? "[]")) as Record<string, unknown>[]).map((l) => ({
      description: String(l.description ?? ""),
      qtyMilli: num(l.qtyMilli) ?? 1000,
      unitCents: num(l.unitCents) ?? 0,
    }));
  } catch {
    return [];
  }
}
/** Same rounding as the app's MoneyMath.lineCents: quantity in thousandths × unit cents, half up, once per line. */
export const lineCents = (qtyMilli: number, unitCents: number) => {
  const p = qtyMilli * unitCents;
  return p >= 0 ? Math.floor((p + 500) / 1000) : -Math.floor((-p + 500) / 1000);
};
const qtyText = (milli: number) => String(milli / 1000);

function strokesSvg(json: unknown): string {
  let strokes: { points: number[] }[] = [];
  try {
    strokes = JSON.parse(String(json ?? "[]")) as { points: number[] }[];
  } catch {
    return "";
  }
  const paths = strokes.map((s) => {
    const p = s.points ?? [];
    const d: string[] = [];
    for (let i = 0; i + 1 < p.length; i += 2) d.push(`${i === 0 ? "M" : "L"}${(p[i] * 300).toFixed(1)} ${(p[i + 1] * 100).toFixed(1)}`);
    return d.length ? `<path d="${d.join(" ")}"/>` : "";
  }).join("");
  return paths
    ? `<svg class="sig" viewBox="0 0 300 100" role="img" aria-label="Signature"><g fill="none" stroke="#000" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">${paths}</g></svg>`
    : "";
}

const SIGN_SCRIPT = `
(function(){var c=document.getElementById('pad'),x=c.getContext('2d'),s=[],cur=null,btn=document.getElementById('sign'),nm=document.getElementById('name'),err=document.getElementById('err');
function size(){var r=c.getBoundingClientRect(),d=window.devicePixelRatio||1;c.width=r.width*d;c.height=r.height*d;draw();}
function ink(){var a=c.height/c.width,t=0;s.forEach(function(k){for(var i=2;i<k.length;i+=2){t+=Math.hypot(k[i]-k[i-2],(k[i+1]-k[i-1])*a);}});return t;}
function draw(){x.clearRect(0,0,c.width,c.height);x.strokeStyle='#000';x.lineWidth=2.5*(window.devicePixelRatio||1);x.lineCap='round';x.lineJoin='round';
s.forEach(function(k){x.beginPath();for(var i=0;i<k.length;i+=2){var px=k[i]*c.width,py=k[i+1]*c.height;if(i)x.lineTo(px,py);else x.moveTo(px,py);}if(k.length===2)x.lineTo(k[0]*c.width+.5,k[1]*c.height);x.stroke();});
btn.disabled=!(nm.value.trim()&&ink()>=0.2);}
function pt(e){var r=c.getBoundingClientRect();return[Math.min(1,Math.max(0,(e.clientX-r.left)/r.width)),Math.min(1,Math.max(0,(e.clientY-r.top)/r.height))];}
c.addEventListener('pointerdown',function(e){c.setPointerCapture(e.pointerId);cur=pt(e);s.push(cur);draw();});
c.addEventListener('pointermove',function(e){if(!cur)return;var p=pt(e);cur.push(p[0],p[1]);draw();});
['pointerup','pointercancel'].forEach(function(n){c.addEventListener(n,function(){cur=null;});});
document.getElementById('clear').addEventListener('click',function(){s=[];draw();});nm.addEventListener('input',draw);
btn.addEventListener('click',function(){btn.disabled=true;err.textContent='';
fetch(location.pathname,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({name:nm.value.trim(),aspect:c.height/c.width,strokes:s.map(function(k){return{points:k.map(function(v){return Math.round(v*10000)/10000;})};})})})
.then(function(r){if(r.ok){location.reload();}else{err.textContent=r.status===409?'This change order is already signed.':'Could not save the signature. Try again.';draw();}})
.catch(function(){err.textContent='Could not save the signature. Check the connection and try again.';draw();});});
window.addEventListener('resize',size);size();})();`;

share.get("/share/co/:token", async (c) => {
  const db = c.env.DB;
  const link = await findLink(db, "co", c.req.param("token"));
  if (!link) return turnedOff(c);
  const job = await rowOf(db, link.companyId, "job", link.jobId);
  const co = await changeOrderFor(db, link);
  if (!live(job) || !co) return turnedOff(c);
  const { name, zone } = await companyOf(db, link.companyId);
  const items = lines(co);
  const total = num(co.totalCents) ?? items.reduce((t, l) => t + lineCents(l.qtyMilli, l.unitCents), 0);
  const status = String(co.status ?? "DRAFT");
  const signed = status === "SIGNED";
  const parts: string[] = [header(name, job, "Change order"), `<h2 style="margin-top:8px">${esc(co.title)}</h2>`];
  const meta = [str(co.clientName) ? `For ${esc(co.clientName)}` : null,
    `<span class="status">${signed ? "Signed" : status === "DECLINED" ? "Declined" : "Waiting for signature"}</span>`].filter(Boolean);
  parts.push(`<p>${meta.join(" · ")}</p>`);
  if (str(co.description)) parts.push(`<p>${esc(co.description)}</p>`);
  parts.push(`<div class="card"><table><thead><tr><th>Item</th><th class="n">Qty</th><th class="n">Price each</th><th class="n">Amount</th></tr></thead><tbody>` +
    items.map((l) => `<tr><td>${esc(l.description)}</td><td class="n">${esc(qtyText(l.qtyMilli))}</td><td class="n">${money(l.unitCents)}</td><td class="n">${money(lineCents(l.qtyMilli, l.unitCents))}</td></tr>`).join("") +
    `<tr class="total"><td colspan="3">Total</td><td class="n">${money(total)}</td></tr></tbody></table></div>`);
  let photoIds: string[] = [];
  try {
    photoIds = (JSON.parse(String(co.photoIdsJson ?? "[]")) as unknown[]).filter((x): x is string => typeof x === "string");
  } catch {
    photoIds = [];
  }
  const photos = (await jobPhotos(db, link.companyId, link.jobId)).filter((p) => photoIds.includes(String(p.id)))
    .sort((a, b) => photoIds.indexOf(String(a.id)) - photoIds.indexOf(String(b.id)));
  if (photos.length) parts.push(`<h2>Photos</h2><div class="grid">${photos.map((p) => figure(link, p)).join("")}</div>`);
  if (signed) {
    const at = num(co.signedAt);
    parts.push(`<h2>Signature</h2>${strokesSvg(co.signatureJson)}<p>Signed by ${esc(co.signedBy)}${at ? `, ${esc(timeFmt(zone).format(at))}` : ""}</p>`);
  } else if (status !== "DECLINED") {
    parts.push(`<div class="card"><h2 style="margin-top:0">Sign to approve</h2><p class="muted">Signing approves this change and its total of ${money(total)}.</p>` +
      `<label for="name">Your name</label><input id="name" type="text" autocomplete="name" value="${esc(co.clientName ?? "")}">` +
      `<label for="pad">Sign here</label><canvas id="pad" aria-label="Signature pad: draw your signature with a finger or mouse"></canvas>` +
      `<p id="err" class="error" role="alert"></p><div class="row"><button id="sign" class="primary" disabled>Sign</button><button id="clear" type="button">Clear</button></div></div>` +
      `<script>${SIGN_SCRIPT}</script>`);
  }
  return c.html(page(`${String(co.title ?? "Change order")}`, parts.join("")), 200, PAGE_HEADERS);
});

const SignBody = z.object({
  name: z.string().trim().min(1).max(120),
  aspect: z.number().min(0.05).max(2).optional(),
  strokes: z.array(z.object({ points: z.array(z.number().min(0).max(1)).max(4000).refine((p) => p.length % 2 === 0) })).min(1).max(200),
});

/** Ink drawn, in pad widths, as the app's Signature.inkLength counts it. */
export function inkLength(strokes: { points: number[] }[], aspect: number): number {
  let total = 0;
  for (const s of strokes) for (let i = 2; i + 1 < s.points.length; i += 2) {
    total += Math.hypot(s.points[i] - s.points[i - 2], (s.points[i + 1] - s.points[i - 1]) * aspect);
  }
  return total;
}
export const MIN_INK = 0.2;
const TRIES = 10;

/**
 * Writes fields onto a stored row as the server (AC-B36): one transaction takes the tenant's next seq and writes the
 * row only while it is still at the version that was read, so the phones pull the change like any other and a push
 * that landed in between is never overwritten. Each written field's time is `at`, so it wins over older phone edits.
 */
async function serverWrite(
  db: D1Database, companyId: string, entity: string, id: string, decide: (row: Row) => Row | null, at: number,
): Promise<{ before: Row; after: Row } | "refused" | "missing" | "busy"> {
  for (let attempt = 0; attempt < TRIES; attempt++) {
    const stored = await db
      .prepare("SELECT row_json, times_json, server_seq FROM sync_rows WHERE tenant_kind = 'company' AND tenant_id = ? AND entity = ? AND entity_id = ?")
      .bind(companyId, entity, id)
      .first<{ row_json: string; times_json: string; server_seq: number }>();
    if (!stored) return "missing";
    const before = JSON.parse(stored.row_json) as Row;
    const patch = decide(before);
    if (!patch) return "refused";
    const after: Row = { ...before, ...patch };
    const times: FieldTimes = JSON.parse(stored.times_json) as FieldTimes;
    for (const k of Object.keys(patch)) times[k] = Math.max(at, (times[k] ?? 0) + 1);
    const version = `(SELECT server_seq FROM sync_rows WHERE tenant_kind = 'company' AND tenant_id = ?1 AND entity = ?2 AND entity_id = ?3)`;
    const results = await db.batch([
      db.prepare(`UPDATE sync_counters SET seq = seq + 1 WHERE tenant_kind = 'company' AND tenant_id = ?1 AND ${version} = ?4 RETURNING seq`)
        .bind(companyId, entity, id, stored.server_seq),
      db.prepare(
        `UPDATE sync_rows SET row_json = ?5, times_json = ?6,
           server_seq = (SELECT seq FROM sync_counters WHERE tenant_kind = 'company' AND tenant_id = ?1)
         WHERE tenant_kind = 'company' AND tenant_id = ?1 AND entity = ?2 AND entity_id = ?3 AND server_seq = ?4`,
      ).bind(companyId, entity, id, stored.server_seq, JSON.stringify(after), JSON.stringify(times)),
    ]);
    if ((results[0].results as unknown[]).length > 0) return { before, after };
  }
  return "busy";
}

share.post("/share/co/:token", bodyLimit({ maxSize: 256 * 1024, onError: (c) => c.json({ error: "too_large" }, 413) }), async (c) => {
  const db = c.env.DB;
  const link = await findLink(db, "co", c.req.param("token"));
  if (!link) return c.json({ error: "not_found" }, 404);
  const co = await changeOrderFor(db, link);
  if (!co) return c.json({ error: "not_found" }, 404);
  const parsed = SignBody.safeParse(await c.req.json().catch(() => null));
  if (!parsed.success) return c.json({ error: "bad_request" }, 400);
  const { name, strokes } = parsed.data;
  const aspect = parsed.data.aspect ?? 1 / 3;
  if (inkLength(strokes, aspect) < MIN_INK) return c.json({ error: "not_signed" }, 400);
  const now = Date.now();
  const { zone } = await companyOf(db, link.companyId);
  const written = await serverWrite(db, link.companyId, "change_order", String(co.id), (row) => {
    if (!live(row) || row.status === "SIGNED" || row.status === "DECLINED") return null;
    const items = lines(row);
    return {
      status: "SIGNED",
      signedBy: name,
      signedAt: now,
      signatureJson: JSON.stringify(strokes),
      signedVia: "web",
      totalCents: num(row.totalCents) ?? items.reduce((t, l) => t + lineCents(l.qtyMilli, l.unitCents), 0),
      updatedAt: now,
    };
  }, now);
  if (written === "refused") return c.json({ error: "already_signed" }, 409);
  if (written === "missing") return c.json({ error: "not_found" }, 404);
  if (written === "busy") return c.json({ error: "busy" }, 503);
  // AC-B30: Owner and Office hear it like a phone signing; the phones pick the row up on their next pull (AC-F40).
  const events = pushEventsFor("change_order", written.before, written.after, "share-link", companyDays(now, zone));
  if (events.length > 0) {
    const sending = deliver(c.env, link.companyId, "share-link", events).catch((e) => console.error(e));
    try {
      c.executionCtx.waitUntil(sending);
    } catch {
      await sending;
    }
  }
  return c.json({ ok: true, status: "SIGNED", signedAt: now });
});

// ---- Photos on a share page ----

share.get("/share/:kind{gallery|log|co}/:token/photo/:photoId", async (c) => {
  const db = c.env.DB;
  const kind = c.req.param("kind") as ShareKind;
  const photoId = c.req.param("photoId");
  if (!ID.test(photoId)) return c.json({ error: "not_found" }, 404);
  const link = await findLink(db, kind, c.req.param("token"));
  if (!link) return c.json({ error: "not_found" }, 404);
  const photo = await rowOf(db, link.companyId, "photo", photoId);
  if (!live(photo) || photo.jobId !== link.jobId || !str(photo.r2Key)) return c.json({ error: "not_found" }, 404);
  // A log or change order link shows only the photos on that log or order, not the whole job.
  if (kind === "log" && link.targetId) {
    const log = await rowOf(db, link.companyId, "daily_log", link.targetId);
    if (!list(section(log?.photosJson)).includes(photoId)) return c.json({ error: "not_found" }, 404);
  }
  if (kind === "co") {
    const co = await changeOrderFor(db, link);
    let ids: unknown[] = [];
    try {
      ids = JSON.parse(String(co?.photoIdsJson ?? "[]")) as unknown[];
    } catch {
      ids = [];
    }
    if (!ids.includes(photoId)) return c.json({ error: "not_found" }, 404);
  }
  const object = await c.env.MEDIA.get(String(photo.r2Key));
  if (!object) return c.json({ error: "not_found" }, 404);
  return new Response(object.body, {
    headers: {
      "Content-Type": object.httpMetadata?.contentType ?? "image/jpeg",
      "Cache-Control": "private, max-age=3600",
      "Referrer-Policy": "no-referrer",
    },
  });
});
