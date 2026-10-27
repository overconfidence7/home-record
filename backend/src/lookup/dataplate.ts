import { z } from "zod";
import { MODEL, callMessages, type UpstreamAnswer } from "./warranty";

/** Base64 length cap (~3.5 MB of image); the app sends a downscaled JPEG. */
export const MAX_IMAGE_BASE64 = 5_000_000;

export const dataPlateRequestSchema = z.object({
  imageBase64: z.string().min(16).max(MAX_IMAGE_BASE64),
  mediaType: z.enum(["image/jpeg", "image/png", "image/webp"]).default("image/jpeg"),
});

const nullableText = z.string().trim().min(1).max(80).nullable().catch(null);
export const dataPlateResultSchema = z.object({
  make: nullableText,
  model: nullableText,
  serial: nullableText,
  manufacturedOn: z.string().regex(/^\d{4}-\d{2}-\d{2}$/).nullable().catch(null),
});
export type DataPlateResult = z.infer<typeof dataPlateResultSchema>;

export function parseDataPlateJson(text: string): DataPlateResult | null {
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start < 0 || end <= start) return null;
  try {
    const parsed = dataPlateResultSchema.safeParse(JSON.parse(text.slice(start, end + 1)));
    return parsed.success ? parsed.data : null;
  } catch {
    return null;
  }
}

const PROMPT =
  "Read this appliance data plate or rating label. Reply with ONE JSON object and nothing else, with keys: " +
  "make (string|null), model (string|null), serial (string|null), manufacturedOn (YYYY-MM-DD|null, only if printed on the label). " +
  "Copy characters exactly; use null when you cannot read a field. Never guess.";

export async function readDataPlate(
  apiKey: string,
  req: z.infer<typeof dataPlateRequestSchema>,
  fetchFn: typeof fetch = fetch,
): Promise<UpstreamAnswer<DataPlateResult> | null> {
  const answer = await callMessages(apiKey, {
    model: MODEL,
    max_tokens: 400,
    messages: [
      {
        role: "user",
        content: [
          { type: "image", source: { type: "base64", media_type: req.mediaType, data: req.imageBase64 } },
          { type: "text", text: PROMPT },
        ],
      },
    ],
  }, fetchFn);
  return answer && { result: parseDataPlateJson(answer.text), usage: { ...answer.usage, searches: 0 } };
}
