export type Row = Record<string, unknown>;
export type FieldTimes = Record<string, number>;

export interface Stored {
  row: Row;
  times: FieldTimes;
  authoritative: boolean;
}

export interface Conflict {
  field: string;
  kept: unknown;
  dropped: unknown;
  reason: "older" | "authoritative";
}

export interface Merged {
  row: Row;
  times: FieldTimes;
  conflicts: Conflict[];
}

const sameValue = (a: unknown, b: unknown) => JSON.stringify(a) === JSON.stringify(b);

function parseArray(value: unknown): unknown[] | null {
  if (Array.isArray(value)) return value;
  if (typeof value !== "string" || !value.startsWith("[")) return null;
  try {
    const parsed = JSON.parse(value);
    return Array.isArray(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

const keyOf = (item: unknown) =>
  item && typeof item === "object" && "id" in item ? `id:${(item as { id: unknown }).id}` : JSON.stringify(item);

/** Union by `id` (or by value) keeping existing order, then new items. Stored as the same string form as the input. */
export function unionLists(existing: unknown[], incoming: unknown[]): unknown[] {
  const seen = new Set(existing.map(keyOf));
  const out = [...existing];
  for (const item of incoming) {
    if (!seen.has(keyOf(item))) {
      seen.add(keyOf(item));
      out.push(item);
    }
  }
  return out;
}

/**
 * Field-level merge (AC-F53). Scalars are last-writer-wins by the op's updatedAt, with ties going to the later push.
 * Fields named `*Json` that hold arrays are unioned. A row marked authoritative (a punch edited by Owner/Office)
 * rejects every non-authoritative write and logs it.
 */
export function mergeRow(existing: Stored | null, incoming: Row, at: number, incomingAuthoritative: boolean): Merged {
  if (!existing) {
    const times: FieldTimes = {};
    for (const k of Object.keys(incoming)) times[k] = at;
    return { row: { ...incoming }, times, conflicts: [] };
  }
  const row: Row = { ...existing.row };
  const times: FieldTimes = { ...existing.times };
  const conflicts: Conflict[] = [];
  const locked = existing.authoritative && !incomingAuthoritative;
  for (const [field, value] of Object.entries(incoming)) {
    if (field === "id") continue;
    if (locked) {
      if (!sameValue(row[field], value)) conflicts.push({ field, kept: row[field], dropped: value, reason: "authoritative" });
      continue;
    }
    const current = row[field];
    const a = parseArray(current);
    const b = parseArray(value);
    if (field.endsWith("Json") && a && b) {
      const merged = unionLists(a, b);
      row[field] = typeof current === "string" ? JSON.stringify(merged) : merged;
      times[field] = Math.max(times[field] ?? 0, at);
      continue;
    }
    const t = times[field];
    if (t === undefined || at >= t) {
      row[field] = value;
      times[field] = at;
    } else if (!sameValue(current, value)) {
      conflicts.push({ field, kept: current, dropped: value, reason: "older" });
    }
  }
  return { row, times, conflicts };
}
