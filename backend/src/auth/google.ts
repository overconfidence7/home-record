import { createRemoteJWKSet, jwtVerify, type JWTVerifyGetKey } from "jose";

const GOOGLE_JWKS = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));
const ISSUERS = ["https://accounts.google.com", "accounts.google.com"];

export interface GoogleIdentity {
  sub: string;
  email: string;
  name: string | null;
}

/** Verifies a Google ID token's signature, issuer, audience and expiry. Returns null when anything is off (AC-B1). */
export async function verifyGoogleIdToken(
  idToken: string,
  clientId: string,
  getKey: JWTVerifyGetKey = GOOGLE_JWKS,
  now = Date.now(),
): Promise<GoogleIdentity | null> {
  try {
    const { payload } = await jwtVerify(idToken, getKey, {
      issuer: ISSUERS,
      audience: clientId,
      currentDate: new Date(now),
    });
    if (typeof payload.sub !== "string" || typeof payload.email !== "string") return null;
    if (payload.email_verified === false) return null;
    return { sub: payload.sub, email: payload.email.toLowerCase(), name: typeof payload.name === "string" ? payload.name : null };
  } catch {
    return null;
  }
}
