import { SignJWT, jwtVerify } from "jose";

export const SESSION_DAYS = 30;
/** HS256 keys shorter than this are guessable offline from one token; the Worker refuses them (fail closed). */
export const MIN_SECRET_BYTES = 32;
const ISSUER = "homerecord";

/** The secret as key bytes; throws for a missing or short JWT_SECRET so no route runs on a weak key. */
export function secretKey(secret: string | undefined): Uint8Array {
  const bytes = new TextEncoder().encode(secret ?? "");
  if (bytes.length < MIN_SECRET_BYTES) throw new Error(`JWT_SECRET must be at least ${MIN_SECRET_BYTES} bytes`);
  return bytes;
}

export interface TokenClaims {
  userId: string;
  /** The user's token_version when the token was issued. */
  version: number;
}

export async function issueToken(secret: string, userId: string, now = Date.now(), version = 0): Promise<string> {
  const iat = Math.floor(now / 1000);
  return new SignJWT({ ver: version })
    .setProtectedHeader({ alg: "HS256" })
    .setSubject(userId)
    .setIssuer(ISSUER)
    .setIssuedAt(iat)
    .setExpirationTime(iat + SESSION_DAYS * 86400)
    .sign(secretKey(secret));
}

/** The claims, or null for any invalid, expired or tampered token, or one without a version. */
export async function readToken(secret: string, token: string, now = Date.now()): Promise<TokenClaims | null> {
  const key = secretKey(secret);
  try {
    const { payload } = await jwtVerify(token, key, {
      issuer: ISSUER,
      algorithms: ["HS256"],
      currentDate: new Date(now),
    });
    if (typeof payload.sub !== "string" || typeof payload.ver !== "number") return null;
    return { userId: payload.sub, version: payload.ver };
  } catch {
    return null;
  }
}

/** Returns the user id, or null for any invalid, expired or tampered token. */
export async function verifyToken(secret: string, token: string, now = Date.now()): Promise<string | null> {
  return (await readToken(secret, token, now))?.userId ?? null;
}
