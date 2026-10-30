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

/**
 * The tool the model reports its answer through (AC-B10 structured output). `strict` makes the API hold the input
 * to this schema; a client tool is used rather than `output_config.format` because web search answers carry
 * citations, which JSON output mode refuses. Ranges the API schema cannot express are checked by `resultSchema`.
 */
const nullable = (type: string) => ({ type: [type, "null"] });
export const REPORT_TOOL = {
  name: "report_warranty",
  description: "Report the warranty terms found for the product. Call this exactly once, after searching.",
  strict: true,
  input_schema: {
    type: "object",
    additionalProperties: false,
    required: [
      "partsYears", "laborYears", "specialTerms", "registrationRequired", "registrationUrl", "manualUrl", "sourceUrl",
      "confidence", "summary",
    ],
    properties: {
      partsYears: { ...nullable("number"), description: "Years of parts coverage, null when unknown" },
      laborYears: { ...nullable("number"), description: "Years of labor coverage, null when unknown" },
      specialTerms: { type: "array", items: { type: "string" }, description: "For example 'tank 12 years'" },
      registrationRequired: { type: "boolean" },
      registrationUrl: nullable("string"),
      manualUrl: nullable("string"),
      sourceUrl: { ...nullable("string"), description: "The page the terms came from" },
      confidence: { type: "string", enum: ["high", "medium", "low"] },
      summary: { type: "string", description: "One plain sentence" },
    },
  },
} as const;

/** The input of the model's `report_warranty` call, held to `resultSchema`; null when it made none or it is invalid. */
export function parseWarrantyToolInput(blocks: ContentBlock[]): WarrantyResult | null {
  const call = blocks.find((b) => b.type === "tool_use" && b.name === REPORT_TOOL.name);
  if (!call) return null;
  const parsed = resultSchema.safeParse(call.input);
  return parsed.success ? parsed.data : null;
}

/** Pulls the first JSON object out of model text, tolerating code fences and prose (fallback when no tool call). */
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

export interface ContentBlock {
  type: string;
  text?: string;
  name?: string;
  input?: unknown;
}

export interface UpstreamUsage {
  inputTokens: number;
  outputTokens: number;
  searches: number;
}

/**
 * What a paid upstream call came back with. `usage` is set whenever the upstream answered 2xx (the call was
 * billed), even when its text did not parse and `result` is null; the whole value is null when no billed
 * answer came back (HTTP error or network failure).
 */
export interface UpstreamAnswer<T> {
  result: T | null;
  usage: UpstreamUsage;
}

/** POSTs to the Messages API and reads the text and usage; null on an HTTP error or a network failure. */
export async function callMessages(
  apiKey: string,
  payload: unknown,
  fetchFn: typeof fetch,
): Promise<{ text: string; blocks: ContentBlock[]; usage: UpstreamUsage } | null> {
  let res: Response;
  try {
    res = await fetchFn("https://api.anthropic.com/v1/messages", {
      method: "POST",
      headers: { "x-api-key": apiKey, "anthropic-version": "2023-06-01", "content-type": "application/json" },
      body: JSON.stringify(payload),
    });
  } catch {
    return null;
  }
  if (!res.ok) return null;
  const body = (await res.json().catch(() => ({}))) as {
    content?: ContentBlock[];
    usage?: { input_tokens?: number; output_tokens?: number; server_tool_use?: { web_search_requests?: number } };
  };
  const blocks = Array.isArray(body.content) ? body.content : [];
  return {
    blocks,
    text: blocks.filter((b) => b.type === "text").map((b) => b.text ?? "").join("\n"),
    usage: {
      inputTokens: body.usage?.input_tokens ?? 0,
      outputTokens: body.usage?.output_tokens ?? 0,
      searches: body.usage?.server_tool_use?.web_search_requests ?? 0,
    },
  };
}

const PROMPT =
  "Find the manufacturer's residential warranty for this product using web search on the manufacturer's own site first. " +
  `Then report what you found by calling the ${REPORT_TOOL.name} tool once. Use null when unknown; never guess.`;

export async function askClaude(
  apiKey: string,
  req: WarrantyRequest,
  fetchFn: typeof fetch = fetch,
): Promise<UpstreamAnswer<WarrantyResult> | null> {
  const product = [req.make, req.model, req.category ? `(${req.category})` : ""].filter(Boolean).join(" ");
  const answer = await callMessages(apiKey, {
    model: MODEL,
    max_tokens: 1024,
    tools: [
      { type: "web_search_20250305", name: "web_search", max_uses: MAX_SEARCHES, allowed_domains: ALLOWED_DOMAINS },
      REPORT_TOOL,
    ],
    messages: [{ role: "user", content: `${PROMPT}\n\nProduct: ${product}` }],
  }, fetchFn);
  return answer && {
    result: parseWarrantyToolInput(answer.blocks) ?? parseWarrantyJson(answer.text),
    usage: answer.usage,
  };
}
