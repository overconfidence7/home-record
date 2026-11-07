# Google Play Data safety answers

What to enter in Play Console for each app. Keep it in step with docs/privacy-policy.md and the manifests. Last checked 2026-11-07 against `house-manual/src/main/AndroidManifest.xml`, `field-log/src/main/AndroidManifest.xml` and `core/src/main/AndroidManifest.xml`.

Both apps: data is encrypted in transit (HTTPS only, release builds refuse plain HTTP). No data is sold. No ads, analytics or crash reporting SDKs. Users can ask for deletion (cloud copy in the app; whole account by email until T081d adds it in the app).

## House Manual

Permissions: INTERNET, POST_NOTIFICATIONS (reminders), VIBRATE (breaker mapping alarm). No location, camera permission (photos go through the system camera and photo picker), contacts or microphone permission.

| Data type (Play category) | Collected | Shared | Optional | Purpose |
| --- | --- | --- | --- | --- |
| Email address (Personal info) | Yes, when signed in | No | Yes | Account management |
| Photos (Photos and videos) | Yes, when signed in with Pro | No | Yes | App functionality (sync) |
| Files and docs | Yes, when signed in with Pro | No | Yes | App functionality (sync) |
| Other user-generated content (home records) | Yes, when signed in with Pro | No | Yes | App functionality (sync) |
| Purchase history (Financial info) | Yes, if Pro is bought | No | Yes | App functionality |
| Photos sent for data plate reading | Processed, not stored | Processed by Anthropic as our service provider (not "shared" under Play's definition) | Yes | App functionality |

Not collected: location, contacts, messages, audio, health, web history, device IDs beyond the app user ID RevenueCat uses for purchases.

## Field Log

Permissions: INTERNET, CAMERA (job photos), RECORD_AUDIO (voice memos), ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION (while in use, at clock in and clock out only, never in the background), POST_NOTIFICATIONS, VIBRATE.

| Data type (Play category) | Collected | Shared | Optional | Purpose |
| --- | --- | --- | --- | --- |
| Email address (Personal info) | Yes | No | No (sign in is needed for a company) | Account management |
| Name and phone of clients (Personal info) | Yes | No | Yes | App functionality |
| Precise location | Yes, at punches | No | Yes, unless the company requires it | App functionality |
| Photos (Photos and videos) | Yes | No | Yes | App functionality |
| Voice or sound recordings (Audio) | Yes, voice memos | No | Yes | App functionality |
| Files and docs | Yes | No | Yes | App functionality |
| Other user-generated content (logs, tasks, change orders, signatures) | Yes | No | Yes | App functionality |
| Purchase history (Financial info) | Yes | No | No for owners, who pick a plan | App functionality |
| Device or other IDs (push token) | Yes | No | Yes | App functionality (alerts) |

Share links: a link the user makes shows the chosen photos or log to whoever has it. That is the user's own sharing, not "sharing" by the developer under Play's definition.
