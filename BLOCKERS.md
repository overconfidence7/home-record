# Blockers

## Needs Nate

(Things the builder cannot do without Nate: credentials, accounts, purchases, device-only checks.)

- Cloudflare: an API token with Workers, D1, R2 and KV permissions as the `CLOUDFLARE_API_TOKEN` environment variable in the "Android build" cloud environment, plus the account id as `CLOUDFLARE_ACCOUNT_ID`. Until then the builder tests the Worker locally (`npm test`, `wrangler dev`) and does not deploy. Needed from T007 for deploys, not for progress.
- Backend secrets (set with `wrangler secret put` once the Worker exists; the builder lists the exact names in `backend/README.md`): `CLAUDE_API_KEY` (warranty lookup, T025), `RESEND_API_KEY` (email codes and handoff emails, T008), `GOOGLE_CLIENT_ID` (Sign in with Google, T008; create a Web client id and an Android client id with the debug and release SHA-1s in Google Cloud Console), `FCM_SERVICE_ACCOUNT` (push, T047; create a Firebase project used only for Cloud Messaging and download `google-services.json` into both app modules), `REVENUECAT_WEBHOOK_SECRET` (T064), `JWT_SECRET` (any long random string).
- RevenueCat: Field Log needs its own RevenueCat app (package `com.kurtz.homerecord.fieldlog`), entitlements `fieldlog_solo` and `fieldlog_crew`, an offering with monthly packages for each, Test Store products, and the public SDK keys added to `docs/billing.md` and the cloud environment (`REVENUECAT_FL_API_KEY`, `REVENUECAT_FL_TEST_API_KEY`). House Manual keys from v1 stay valid (same package name).

## Decisions to confirm

(Judgment calls the builder made. Nate deletes a line once confirmed or writes the correction.)

- 2026-10-19 (Nate, at re-scope): v1 docs carried into this repo are `docs/voice.md`, `docs/billing.md`, `scripts/setup-android.sh`, `scripts/ci-gradle.sh` and `.github/workflows/testing-apks.yml`. No code. Confirm or strike.
- 2026-10-19 (Nate, at re-scope): push notifications use Firebase Cloud Messaging for delivery only (free; Cloudflare has no push service). No other Firebase product. Confirm or choose "no push in v2".
- 2026-10-19 (Nate, at re-scope): email codes and handoff emails go through Resend (free tier 3,000/month). Confirm or name another sender.
- 2026-10-19 (Nate, at re-scope): Field Log pricing stays at Solo $39/mo and Crew $79/mo (up to 10), 14-day trial; House Manual at free / $24.99 yr / $59 lifetime with a 15-item free limit. Confirm or change.
- 2026-10-19 (builder, T008c): Google sign-in needs `google.webClientId` in local.properties (BuildConfig.GOOGLE_WEB_CLIENT_ID); blank means the Google button reports a failure and email code still works. Sign-in lives on the More tab root until the HM-Account and FL-Team screens land. Cleartext HTTP is allowed in all builds for now (for 10.0.2.2); restrict to debug at S8.
- 2026-10-19 (builder, T009): invite link is `<API_BASE_URL>/join/<code>`, a Worker page that opens `fieldlog://join/<code>`. A real https link that opens the app directly (Android App Links) needs a production domain and assetlinks.json from Nate. Join is limited to one company per account in v1. Rotating the code is the only way to revoke an invite. Role rules: Owner sets Office/Lead/Crew/Sub and rates; Office manages only Lead/Crew/Sub; the Owner's own role is fixed.
- 2026-10-21 (builder, judge:hm-skip-drops-floors): SPEC HM-Onboarding says Skip creates a single "Main" floor, but PRODUCT.md step 1 says the house type seeds floors (Two-story = Main, Upper, + Basement). Builder now seeds the implied floors on Skip (no rooms) per PRODUCT.md; confirm or revert.
