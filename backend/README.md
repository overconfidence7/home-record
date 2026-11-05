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
| `FCM_SERVICE_ACCOUNT` | push delivery (T047): the whole service-account JSON key of the Firebase project (role Firebase Cloud Messaging API Admin); without it nothing is sent and pushes are only logged |
| `REVENUECAT_WEBHOOK_SECRET` | entitlement webhook (T064a): in RevenueCat → Integrations → Webhooks, URL `https://<api domain>/webhooks/revenuecat` and the same value as the Authorization header (sent bare or as `Bearer <secret>`); without it the webhook answers 503 |

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

## Share links (AC-B35, AC-B36)

A link is a synced `share_link` row (Owner and Office only): `kind` `gallery`, `log` or `co`, `jobId`, `targetId` (the daily log or change order id; none for a gallery), a random `token` (16–64 of `A-Za-z0-9_-`) and `revoked`. Migration 0013 indexes the token. No account is needed to open one; a revoked or deleted link, or one whose job or record is deleted, answers 404 "This link is turned off."

- `GET /share/gallery/:token` the job's uploaded photos by day (newest day first) with captions.
- `GET /share/log/:token` one daily log: weather, crew hours, work done, tasks, change orders, delays, visitors, safety, client notes and the log's photos.
- `GET /share/co/:token` the change order (lines, total, photos); while it is not signed, a name field and a signature pad. `POST /share/co/:token` `{name, aspect, strokes: [{points: [x, y, ...]}]}` (0..1 pad coordinates, as the app's `Signature`; at least 0.2 pad widths of ink) marks it `SIGNED` with `signedBy`, `signedAt`, `signatureJson`, `signedVia: "web"` as a server write (next seq, so phones pull it; Owner and Office get the `change_order_signed` push). 409 once signed or declined.
- `GET /share/:kind/:token/photo/:photoId` streams a photo from the `MEDIA` binding: for a gallery any live photo of the job, for a log or change order only the photos on it.

## Handoff (AC-B40)

Field Log sends a job's `.homerecord` bundle to the homeowner; House Manual claims it and imports it (AC-H17).

- `POST /handoff?companyId=&jobId=&email=&homeName=` (Owner, Office or Lead of the company; Crew/Sub 403, another company 404) with the raw bundle as the body and a `Content-Length` (411 without one, 413 above 100 MB, 400 `not_a_bundle` unless it starts like a zip). Stores it in R2 at `company/<co>/handoff/<id>.homerecord`, records it in `handoffs` (migration 0014, only a SHA-256 of the claim token is kept), emails the homeowner the link through Resend (502 `send_failed` removes both again), answers 201 `{id, email, expiresAt, size}`. Links work for 30 days.
- `GET /handoff/:token` the page the email links to: who sent it, the home, "Open in House Manual" (`intent://handoff/<token>#Intent;scheme=homerecord;package=com.kurtz.homerecord.housemanual;end`, which goes to Google Play without the app). 404 for an unknown token, 410 once expired.
- `POST /handoff/:token/claim` (any signed-in account): the first account to claim owns the handoff (claiming again from it is fine, any other account gets 409); answers `{homeName, companyName, size, claimedAt, expiresAt, bundlePath}`. 410 once expired.
- `GET /handoff/:token/bundle` the claimer downloads the zip (404 for anyone else). House Manual imports it locally with the preview and conflicts, and its own sync then copies the home into the account's home tenant.
- The daily cron (`pruneHandoffs`) deletes expired handoffs and their bundles. In dev, `GET /dev/last-handoff?email=` returns the newest token for that address.

## Push notifications (AC-B30)

- `PUT /me/push-token` `{token, app: "field-log" | "house-manual"}` registers a phone's FCM token for the caller (204); a token already on another account moves to the caller. `DELETE /me/push-token` `{token}` removes the caller's own token. `POST /auth/revoke` drops all of the account's tokens.
- An accepted company sync op can trigger a push (`src/push/events.ts`): a task assigned to someone (to the assignee), an assignment on today or tomorrow added, removed or moved (to the member, in the company's `settings.timeZone`, Eastern by default), a change order turning `SIGNED` (to Owner and Office), a timesheet approval (to the member). The pusher is never notified, and a recipient must still be a member.
- Delivery is FCM HTTP v1, data-only (`type`, `companyId`, ids, `jobName`, `title`), high priority, 24 h TTL, to every Field Log token of the recipient, after the push has answered. A token FCM reports unregistered is deleted.
- Each notification is logged in `push_log` (migration 0011, pruned after 30 days by the daily cron) with the number of phones FCM accepted. In dev, `GET /dev/pushes` lists the newest 50.
