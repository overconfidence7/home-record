# HomeRecord backend

Hono Worker on Cloudflare: D1 (SQLite), R2 (media), KV (cache, rate limits). See SPEC.md section 7.

```
npm ci        # .npmrc sets legacy-peer-deps (npm 10 cannot resolve the pool's peer set otherwise)
npm test      # vitest in the workers pool; applies migrations/ to a local D1
npm run dev   # wrangler dev with ENVIRONMENT=dev (/dev/* helpers on, sign-in limits off)
```

`ENVIRONMENT` defaults to `production` in `wrangler.toml`, so a plain `wrangler deploy` or `npx wrangler dev` never exposes `/dev/last-code`, `/dev/reset` or `/dev/seed`. Dev mode is on only when asked for: `npm run dev`, or `ENVIRONMENT=dev` in `backend/.dev.vars` (what the QA workflow writes).

## Deploy (needs Nate, see BLOCKERS.md)

1. `wrangler d1 create homerecord`, `wrangler kv namespace create CACHE`, `wrangler r2 bucket create homerecord-media`; put the ids in `wrangler.toml`.
2. `wrangler d1 migrations apply homerecord --remote`
3. Set the secrets below, then `npm run deploy` (`wrangler deploy --var ENVIRONMENT:production`). Check `GET /health` answers `"environment":"production"`.

## Secrets (`wrangler secret put NAME`; never in source; local dev uses `.dev.vars`)

| Name | Used by |
| --- | --- |
| `JWT_SECRET` | session tokens and the email-code HMAC; at least 32 bytes (`openssl rand -base64 48`), shorter and every signed-in route and sign-in answers 500 |
| `GOOGLE_CLIENT_ID` | Sign in with Google ID-token audience (T008) |
| `RESEND_API_KEY` | email codes and handoff emails (T008, T0xx) |
| `R2_ACCOUNT_ID` | presigned media URLs: the Cloudflare account that owns the bucket |
| `R2_ACCESS_KEY_ID` | presigned media URLs: an R2 API token scoped to `homerecord-media` |
| `R2_SECRET_ACCESS_KEY` | presigned media URLs: that token's secret |
| `CLAUDE_API_KEY` | warranty and data-plate lookup (T025) |
| `FCM_SERVICE_ACCOUNT` | push delivery (T047) |
| `REVENUECAT_WEBHOOK_SECRET` | entitlement webhook (T064) |

For local runs, create `backend/.dev.vars` (gitignored) containing at least `JWT_SECRET=<a random string of at least 32 bytes>` and `ENVIRONMENT=dev`; without the secret (or with a shorter one) sign-in answers 500, and without `ENVIRONMENT=dev` a plain `npx wrangler dev` runs as production (no `/dev/last-code` for the QA flows). The QA workflow writes both itself.

## Sign-in rate limits

`/auth/email/start` allows 5 codes per email and 20 per IP an hour; `/auth/email/verify` allows 10 checks per email (across codes, so asking for a new code does not reset it) and 30 per IP an hour, on top of the 5 wrong guesses one code allows. Over a limit the answer is 429 `{"error":"rate_limited"}`. Counters live in the D1 table `auth_limits` (migration 0006), pruned by the daily cron. The limits are off when `ENVIRONMENT=dev` (the local Worker the QA suite signs in to many times a run); they hold in every other environment, tests included. The code is logged to the console only in dev.

## Sessions

Tokens are HS256 JWTs (30 days, refreshed on every signed-in request through `X-Refreshed-Token`). Each carries the user's `token_version` (`users.token_version`, migration 0008) as `ver`; `requireSession` refuses any other version. `POST /auth/revoke` bumps it, which signs out every phone of that account at once (204); signing in again issues a token at the new version. Email codes are stored as an HMAC-SHA256 under `JWT_SECRET`, so a copy of `email_codes` alone does not give the codes away.

## Sync limits

`POST /…/sync/push` takes at most 2 MiB (413 `{"error":"too_large"}` above it) and 200 ops; an op whose payload is over 256,000 characters is refused on its own (`rejected` reason `too_large`) and the rest of the batch applies. The apps size their batches to 1 MiB and set an oversized change aside without sending it. The daily cron deletes `sync_applied` idempotency records (`applied_at`, migration 0009) and `sync_conflicts` rows older than 90 days; `sync_rows` is never pruned.

## Media (R2)

One bucket, `homerecord-media`: the `MEDIA` binding in `wrangler.toml` (the orphan cron deletes through it) and `MEDIA_BUCKET` in `src/media/presign.ts` (presigned URLs point at it) must name the same bucket; a test holds them together. Presigning needs the secrets `R2_ACCOUNT_ID`, `R2_ACCESS_KEY_ID` and `R2_SECRET_ACCESS_KEY` (an R2 API token for that bucket); without them both routes answer 503.

- `POST /media/presign` `{tenantKind, tenantId, entity, entityId, filename, size, contentType}` signs one 10-minute PUT for the file of a row the caller may write (only `photo`, `wall_photo`, `note` at most 25 MB, `document` and `job_file` at most 100 MB; 413 over the cap). The URL signs `Content-Length` and `Content-Type`, so the upload must send exactly those. The first caller to sign a key owns it; only they, or a company's Owner or Office, may sign another PUT for it (403). Company rows follow the sync write rules (`canUpload` in `src/sync/policy.ts`).
- `POST /media/presign-get` `{tenantKind, tenantId, key}` signs one 7-day GET for an object of a live row of that tenant the caller may see; never a PUT.

## Tenancy

Every row belongs to a company (Field Log) or a home (House Manual). `requireSession` loads the caller's memberships; tenant routes use `requireTenant()`, which answers 404 for any tenant the caller is not in, so other tenants are indistinguishable from missing ones.
