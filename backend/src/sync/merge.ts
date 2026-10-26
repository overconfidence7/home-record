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
 * The only fields merged as a union (AC-F53). They are append-only logs, so nothing is ever removed from them.
 * Every other field, `*Json` lists included (polygons, corners, lines, phases, tags), is last-writer-wins: a union
 * there would mix old and new corners or bring back removed entries. Keep in sync with the app's `RowMerge`.
 */
export const UNION_FIELDS: Record<string, ReadonlySet<string>> = {
  punch: new Set(["editsJson"]),
};

export const isUnionField = (entity: string, field: string) => UNION_FIELDS[entity]?.has(field) ?? false;

/**
 * Field-level merge (AC-F53). Fields are last-writer-wins by the op's updatedAt, with ties going to the later push;
 * the append-only lists in [UNION_FIELDS] are unioned. A row marked authoritative (a punch edited by Owner/Office)
 * rejects every non-authoritative write and logs it.
 */
export function mergeRow(
  existing: Stored | null,
  incoming: Row,
  at: number,
  incomingAuthoritative: boolean,
  entity = "",
): Merged {
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
    const a = isUnionField(entity, field) ? parseArray(current) : null;
    const b = a ? parseArray(value) : null;
    if (a && b) {
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
