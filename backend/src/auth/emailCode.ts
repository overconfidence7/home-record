export const CODE_MINUTES = 10;
export const MAX_ATTEMPTS = 5;

export function generateCode(): string {
  const n = crypto.getRandomValues(new Uint32Array(1))[0] % 1_000_000;
  return n.toString().padStart(6, "0");
}

export async function hashCode(email: string, code: string): Promise<string> {
  const bytes = new TextEncoder().encode(`${email}:${code}`);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function normalizeEmail(raw: string): string {
  return raw.trim().toLowerCase();
}

export type VerifyResult = "ok" | "invalid" | "expired" | "locked";

/** Checks and consumes a code. Wrong guesses count; five locks the code until a new one is requested. */
export async function checkCode(db: D1Database, email: string, code: string, now = Date.now()): Promise<VerifyResult> {
  const row = await db
    .prepare("SELECT code_hash, expires_at, attempts FROM email_codes WHERE email = ?")
    .bind(email)
    .first<{ code_hash: string; expires_at: number; attempts: number }>();
  if (!row) return "invalid";
  if (row.expires_at < now) return "expired";
  if (row.attempts >= MAX_ATTEMPTS) return "locked";
  if (row.code_hash !== (await hashCode(email, code))) {
    await db.prepare("UPDATE email_codes SET attempts = attempts + 1 WHERE email = ?").bind(email).run();
    return "invalid";
  }
  await db.prepare("DELETE FROM email_codes WHERE email = ?").bind(email).run();
  return "ok";
}

/** Sends through Resend. Without a key the code is logged only in dev; production fails the send, and tests just skip it. */
export async function sendCode(env: { RESEND_API_KEY?: string; ENVIRONMENT: string }, email: string, code: string): Promise<boolean> {
  if (!env.RESEND_API_KEY) {
    if (env.ENVIRONMENT === "dev") console.log(`[dev] sign-in code for ${email}: ${code}`);
    return env.ENVIRONMENT !== "production";
  }
  const res = await fetch("https://api.resend.com/emails", {
    method: "POST",
    headers: { Authorization: `Bearer ${env.RESEND_API_KEY}`, "Content-Type": "application/json" },
    body: JSON.stringify({
      from: "HomeRecord <login@homerecord.app>",
      to: [email],
      subject: `Your HomeRecord code is ${code}`,
      text: `Your sign-in code is ${code}. It works for ${CODE_MINUTES} minutes.`,
    }),
  });
  return res.ok;
}
