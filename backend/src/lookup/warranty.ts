import { z } from "zod";

export const CACHE_DAYS = 180;
export const FREE_DAILY_LIMIT = 20;
export const PRO_DAILY_LIMIT = 200;
export const MAX_SEARCHES = 3;
export const MODEL = "claude-sonnet-5-5";

/** Manufacturer and retailer domains the search tool may read (AC-B10). */
export const ALLOWED_DOMAINS = [
  "rheem.com", "aosmith.com", "bradfordwhite.com", "navienusa.com", "rinnai.us", "trane.com", "carrier.com",
  "lennox.com", "goodmanmfg.com", "ruud.com", "kohler.com", "moen.com", "deltafaucet.com",
  "whirlpool.com", "ge.com", "geappliances.com", "lg.com", "samsung.com", "bosch-home.com", "frigidaire.com",
  "maytag.com", "kitchenaid.com", "electrolux.com", "squared.com", "se.com", "eaton.com", "generac.com",
  "homedepot.com", "lowes.com", "bestbuy.com", "ferguson.com",
];

export const requestSchema = z.object({
  make: z.string().trim().min(1).max(80),
  model: z.string().trim().min(1).max(80),
  category: z.string().trim().max(60).optional(),
  serial: z.string().trim().max(60).optional(),
});
export type WarrantyRequest = z.infer<typeof requestSchema>;

const nullableUrl = z.string().url().nullable().catch(null);
export const resultSchema = z.object({
  partsYears: z.number().min(0).max(100).nullable().catch(null),
  laborYears: z.number().min(0).max(100).nullable().catch(null),
  specialTerms: z.array(z.string()).catch([]),
  registrationRequired: z.boolean().catch(false),
  registrationUrl: nullableUrl,
  manualUrl: nullableUrl,
  sourceUrl: nullableUrl,
  confidence: z.enum(["high", "medium", "low"]).catch("low"),
  summary: z.string().catch(""),
});
export type WarrantyResult = z.infer<typeof resultSchema>;

function norm(s: string): string {
  return s.toLowerCase().replace(/[^a-z0-9]+/g, "");
}

/** Normalized so "Rheem" / "RHEEM " and "XG-50 T06" / "xg50t06" share one cache entry. */
export function warrantyCacheKey(make: string, model: string): string {
  return `warranty:${norm(make)}:${norm(model)}`;
}

/** Pulls the first JSON object out of model text, tolerating code fences and prose. */
export function parseWarrantyJson(text: string): WarrantyResult | null {
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start < 0 || end <= start) return null;
  try {
    const parsed = resultSchema.safeParse(JSON.parse(text.slice(start, end + 1)));
    return parsed.success ? parsed.data : null;
  } catch {
    return null;
  }
}

export interface UpstreamUsage {
  inputTokens: number;
  outputTokens: number;
  searches: number;
}

const PROMPT =
  "Find the manufacturer's residential warranty for this product using web search on the manufacturer's own site first. " +
  "Reply with ONE JSON object and nothing else, with keys: partsYears (number|null), laborYears (number|null), " +
  "specialTerms (string[], e.g. 'tank 12 years'), registrationRequired (boolean), registrationUrl (string|null), " +
  "manualUrl (string|null), sourceUrl (string|null, the page the terms came from), confidence ('high'|'medium'|'low'), " +
  "summary (one plain sentence). Use null when unknown; never guess.";

export async function askClaude(
  apiKey: string,
  req: WarrantyRequest,
  fetchFn: typeof fetch = fetch,
): Promise<{ result: WarrantyResult; usage: UpstreamUsage } | null> {
  const product = [req.make, req.model, req.category ? `(${req.category})` : ""].filter(Boolean).join(" ");
  const res = await fetchFn("https://api.anthropic.com/v1/messages", {
    method: "POST",
    headers: { "x-api-key": apiKey, "anthropic-version": "2023-06-01", "content-type": "application/json" },
    body: JSON.stringify({
      model: MODEL,
      max_tokens: 1024,
      tools: [{ type: "web_search_20250305", name: "web_search", max_uses: MAX_SEARCHES, allowed_domains: ALLOWED_DOMAINS }],
      messages: [{ role: "user", content: `${PROMPT}\n\nProduct: ${product}` }],
    }),
  });
  if (!res.ok) return null;
  const body = (await res.json()) as {
    content?: { type: string; text?: string }[];
    usage?: { input_tokens?: number; output_tokens?: number; server_tool_use?: { web_search_requests?: number } };
  };
  const text = (body.content ?? []).filter((b) => b.type === "text").map((b) => b.text ?? "").join("\n");
  const result = parseWarrantyJson(text);
  if (!result) return null;
  return {
    result,
    usage: {
      inputTokens: body.usage?.input_tokens ?? 0,
      outputTokens: body.usage?.output_tokens ?? 0,
      searches: body.usage?.server_tool_use?.web_search_requests ?? 0,
    },
  };
}
