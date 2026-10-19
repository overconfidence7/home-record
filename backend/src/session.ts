import { SignJWT, jwtVerify } from "jose";

export const SESSION_DAYS = 30;
const ISSUER = "homerecord";

function key(secret: string): Uint8Array {
  return new TextEncoder().encode(secret);
}

export async function issueToken(secret: string, userId: string, now = Date.now()): Promise<string> {
  const iat = Math.floor(now / 1000);
  return new SignJWT({})
    .setProtectedHeader({ alg: "HS256" })
    .setSubject(userId)
    .setIssuer(ISSUER)
    .setIssuedAt(iat)
    .setExpirationTime(iat + SESSION_DAYS * 86400)
    .sign(key(secret));
}

/** Returns the user id, or null for any invalid, expired or tampered token. */
export async function verifyToken(secret: string, token: string, now = Date.now()): Promise<string | null> {
  try {
    const { payload } = await jwtVerify(token, key(secret), {
      issuer: ISSUER,
      algorithms: ["HS256"],
      currentDate: new Date(now),
    });
    return typeof payload.sub === "string" ? payload.sub : null;
  } catch {
    return null;
  }
}
