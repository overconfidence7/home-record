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
| `JWT_SECRET` | session tokens (any long random string) |
| `GOOGLE_CLIENT_ID` | Sign in with Google ID-token audience (T008) |
| `RESEND_API_KEY` | email codes and handoff emails (T008, T0xx) |
| `CLAUDE_API_KEY` | warranty and data-plate lookup (T025) |
| `FCM_SERVICE_ACCOUNT` | push delivery (T047) |
| `REVENUECAT_WEBHOOK_SECRET` | entitlement webhook (T064) |

For local runs, create `backend/.dev.vars` (gitignored) containing at least `JWT_SECRET=<any long string>` and `ENVIRONMENT=dev`; without the secret `/auth/email/verify` answers 500, and without `ENVIRONMENT=dev` a plain `npx wrangler dev` runs as production (no `/dev/last-code` for the QA flows). The QA workflow writes both itself.

## Sign-in rate limits

`/auth/email/start` allows 5 codes per email and 20 per IP an hour; `/auth/email/verify` allows 10 checks per email (across codes, so asking for a new code does not reset it) and 30 per IP an hour, on top of the 5 wrong guesses one code allows. Over a limit the answer is 429 `{"error":"rate_limited"}`. Counters live in the D1 table `auth_limits` (migration 0006), pruned by the daily cron. The limits are off when `ENVIRONMENT=dev` (the local Worker the QA suite signs in to many times a run); they hold in every other environment, tests included. The code is logged to the console only in dev.

## Tenancy

Every row belongs to a company (Field Log) or a home (House Manual). `requireSession` loads the caller's memberships; tenant routes use `requireTenant()`, which answers 404 for any tenant the caller is not in, so other tenants are indistinguishable from missing ones.
