// AWS Signature V4 query-string presigning for R2's S3 endpoint (no SDK; Web Crypto only).
export interface R2Credentials {
  accountId: string;
  accessKeyId: string;
  secretAccessKey: string;
  bucket: string;
}

/**
 * The one media bucket. The `MEDIA` binding in wrangler.toml names the same bucket (a test holds them together), so
 * the cron deletes from the bucket the presigned URLs point at (cr:media-cron-scope).
 */
export const MEDIA_BUCKET = "homerecord-media";

const enc = new TextEncoder();
const hex = (buf: ArrayBuffer) => [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");

async function hmac(key: ArrayBuffer | Uint8Array, data: string): Promise<ArrayBuffer> {
  const k = await crypto.subtle.importKey("raw", key as BufferSource, { name: "HMAC", hash: "SHA-256" }, false, ["sign"]);
  return crypto.subtle.sign("HMAC", k, enc.encode(data));
}

async function sha256Hex(data: string): Promise<string> {
  return hex(await crypto.subtle.digest("SHA-256", enc.encode(data)));
}

/** RFC 3986 encoding of one path segment or query value. */
const rfc3986 = (s: string) => encodeURIComponent(s).replace(/[!'()*]/g, (c) => `%${c.charCodeAt(0).toString(16).toUpperCase()}`);

export async function presignUrl(
  creds: R2Credentials,
  method: "GET" | "PUT",
  key: string,
  expiresSeconds: number,
  now: Date = new Date(),
  signedHeaders: Record<string, string> = {},
): Promise<string> {
  const host = `${creds.accountId}.r2.cloudflarestorage.com`;
  const amzDate = now.toISOString().replace(/[-:]/g, "").replace(/\.\d{3}/, "");
  const day = amzDate.slice(0, 8);
  const scope = `${day}/auto/s3/aws4_request`;
  const path = "/" + [creds.bucket, ...key.split("/")].map(rfc3986).join("/");
  // Signed headers (lower-case names, sorted) must be sent with exactly these values, so a PUT signed for a size
  // and type cannot carry anything else (cr:presign-size).
  const headers: Record<string, string> = { host };
  for (const [name, value] of Object.entries(signedHeaders)) headers[name.toLowerCase()] = value.trim();
  const names = Object.keys(headers).sort();
  const params: Record<string, string> = {
    "X-Amz-Algorithm": "AWS4-HMAC-SHA256",
    "X-Amz-Credential": `${creds.accessKeyId}/${scope}`,
    "X-Amz-Date": amzDate,
    "X-Amz-Expires": String(expiresSeconds),
    "X-Amz-SignedHeaders": names.join(";"),
  };
  const query = Object.keys(params)
    .sort()
    .map((k) => `${rfc3986(k)}=${rfc3986(params[k])}`)
    .join("&");
  const canonicalHeaders = names.map((n) => `${n}:${headers[n]}\n`).join("");
  const canonical = [method, path, query, canonicalHeaders, params["X-Amz-SignedHeaders"], "UNSIGNED-PAYLOAD"].join("\n");
  const toSign = ["AWS4-HMAC-SHA256", amzDate, scope, await sha256Hex(canonical)].join("\n");
  let k: ArrayBuffer = await hmac(enc.encode("AWS4" + creds.secretAccessKey), day);
  for (const part of ["auto", "s3", "aws4_request"]) k = await hmac(k, part);
  const signature = hex(await hmac(k, toSign));
  return `https://${host}${path}?${query}&X-Amz-Signature=${signature}`;
}
