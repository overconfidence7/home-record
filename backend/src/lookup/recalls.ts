import { z } from "zod";

export const RECALL_CACHE_SECONDS = 24 * 3600;
const CPSC_URL = "https://www.saferproducts.gov/RestWebServices/Recall";

export const recallQuerySchema = z.object({
  make: z.string().trim().min(1).max(80),
  model: z.string().trim().max(80).optional(),
});

export interface Recall {
  recallNumber: string;
  title: string;
  recallDate: string | null;
  hazard: string | null;
  remedy: string | null;
  url: string | null;
  /** True when the recall text names the model; recalls usually name product lines, so false is not 'safe'. */
  modelMatch: boolean;
}

const norm = (s: string) => s.toLowerCase().replace(/[^a-z0-9]+/g, "");

export function recallsCacheKey(make: string, model: string | undefined): string {
  return `recalls:${norm(make)}:${norm(model ?? "")}`;
}

interface CpscRecall {
  RecallNumber?: string;
  Title?: string;
  RecallDate?: string;
  Hazards?: { Name?: string }[];
  Remedies?: { Name?: string }[];
  URL?: string;
}

/** Keeps recalls that name the make; ones that also name the model sort first. */
export function pickRecalls(raw: CpscRecall[], make: string, model: string | undefined): Recall[] {
  const mk = norm(make);
  const md = model ? norm(model) : "";
  const about = (r: CpscRecall) => norm(`${r.Title ?? ""} ${(r.Hazards ?? []).map((h) => h.Name).join(" ")}`);
  const byMake = raw.filter((r) => about(r).includes(mk) || norm(JSON.stringify(r)).includes(mk));
  const withMatch = byMake.map((r) => ({ r, modelMatch: md !== "" && norm(JSON.stringify(r)).includes(md) }));
  withMatch.sort((x, y) => Number(y.modelMatch) - Number(x.modelMatch));
  return withMatch.slice(0, 10).map(({ r, modelMatch }) => ({
    recallNumber: r.RecallNumber ?? "",
    title: r.Title ?? "",
    recallDate: r.RecallDate ? r.RecallDate.slice(0, 10) : null,
    hazard: r.Hazards?.[0]?.Name ?? null,
    remedy: r.Remedies?.[0]?.Name ?? null,
    url: r.URL ?? null,
    modelMatch,
  }));
}

export async function fetchRecalls(make: string, model: string | undefined, fetchFn: typeof fetch = fetch): Promise<Recall[] | null> {
  const q = new URLSearchParams({ format: "json", ProductName: make });
  const res = await fetchFn(`${CPSC_URL}?${q}`);
  if (!res.ok) return null;
  const raw = (await res.json().catch(() => null)) as CpscRecall[] | null;
  if (!Array.isArray(raw)) return null;
  return pickRecalls(raw, make, model);
}
