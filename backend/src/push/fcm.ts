import { SignJWT, importPKCS8 } from "jose";

/**
 * Firebase Cloud Messaging HTTP v1, delivery only (AC-B30). The Worker signs a service-account JWT (RS256, jose),
 * trades it for an OAuth access token (cached in KV for 50 minutes) and sends data-only messages; the phone writes
 * the notification text from its own strings. Without `FCM_SERVICE_ACCOUNT` nothing is sent.
 */
export interface ServiceAccount {
  project_id: string;
  client_email: string;
  private_key: string;
  token_uri: string;
}

export const FCM_SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
const DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token";
const ACCESS_CACHE_SECONDS = 50 * 60;

/** The service account from the secret's JSON (as Google Cloud downloads it), or null when unset or unreadable. */
export function parseServiceAccount(raw: string | undefined): ServiceAccount | null {
  if (!raw) return null;
  try {
    const value = JSON.parse(raw) as Partial<ServiceAccount>;
    if (!value.project_id || !value.client_email || !value.private_key) return null;
    return {
      project_id: value.project_id,
      client_email: value.client_email,
      private_key: value.private_key,
      token_uri: value.token_uri || DEFAULT_TOKEN_URI,
    };
  } catch {
    return null;
  }
}

export async function accessToken(cache: KVNamespace, sa: ServiceAccount, now = Date.now()): Promise<string | null> {
  const key = `fcm:access:${sa.client_email}`;
  const hit = await cache.get(key);
  if (hit) return hit;
  const iat = Math.floor(now / 1000);
  const assertion = await new SignJWT({ scope: FCM_SCOPE })
    .setProtectedHeader({ alg: "RS256", typ: "JWT" })
    .setIssuer(sa.client_email)
    .setSubject(sa.client_email)
    .setAudience(sa.token_uri)
    .setIssuedAt(iat)
    .setExpirationTime(iat + 3600)
    .sign(await importPKCS8(sa.private_key, "RS256"));
  const res = await fetch(sa.token_uri, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer", assertion }).toString(),
  });
  if (!res.ok) {
    console.error(`fcm: token exchange answered ${res.status}`);
    return null;
  }
  const body = (await res.json()) as { access_token?: string };
  if (!body.access_token) return null;
  await cache.put(key, body.access_token, { expirationTtl: ACCESS_CACHE_SECONDS });
  return body.access_token;
}

export type SendResult = "sent" | "gone" | "failed";

/** Sends one data message to one phone. "gone" means FCM no longer knows the token and it should be dropped. */
export async function sendData(sa: ServiceAccount, access: string, token: string, data: Record<string, string>): Promise<SendResult> {
  const res = await fetch(`https://fcm.googleapis.com/v1/projects/${encodeURIComponent(sa.project_id)}/messages:send`, {
    method: "POST",
    headers: { Authorization: `Bearer ${access}`, "Content-Type": "application/json" },
    body: JSON.stringify({ message: { token, data, android: { priority: "HIGH", ttl: "86400s" } } }),
  });
  if (res.ok) return "sent";
  const body = (await res.json().catch(() => null)) as { error?: { details?: { errorCode?: string }[] } } | null;
  const codes = (body?.error?.details ?? []).map((d) => d.errorCode);
  if (res.status === 404 || codes.includes("UNREGISTERED") || codes.includes("SENDER_ID_MISMATCH")) return "gone";
  console.error(`fcm: send answered ${res.status} ${codes.join(",")}`);
  return "failed";
}
