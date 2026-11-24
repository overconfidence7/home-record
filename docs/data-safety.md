# Google Play Data safety answers

What to enter in Play Console for each app. Keep it in step with docs/privacy-policy.md and the manifests. Last checked 2026-11-24 (S10) against the merged release manifests (`aapt2 dump permissions` on both release APKs), which include the permissions libraries add.

Both apps: data is encrypted in transit (HTTPS only, release builds refuse plain HTTP). No data is sold. No ads, analytics or crash reporting SDKs. Users can delete their data in the app: the cloud copy (House Manual, Account) and the whole account with everything it owns (Delete account in both apps, `DELETE /me`).

## House Manual

Permissions: INTERNET, POST_NOTIFICATIONS (reminders), VIBRATE (breaker mapping alarm). Added by libraries, no user data: ACCESS_NETWORK_STATE (sync waits for a connection; RevenueCat), WAKE_LOCK, RECEIVE_BOOT_COMPLETED and FOREGROUND_SERVICE (WorkManager: reminders and sync survive a restart), com.android.vending.BILLING (Google Play Billing through RevenueCat), USE_BIOMETRIC and USE_FINGERPRINT (androidx.biometric, pulled in by the Credential Manager; the app never asks for a fingerprint). No location, camera permission (photos go through the system camera and photo picker), contacts or microphone permission.

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

Permissions: INTERNET, CAMERA (job photos), RECORD_AUDIO (voice memos), ACCESS_FINE_LOCATION and ACCESS_COARSE_LOCATION (while in use, at clock in and clock out only, never in the background), POST_NOTIFICATIONS, VIBRATE, com.google.android.c2dm.permission.RECEIVE (Firebase Cloud Messaging, crew alerts). Added by libraries, no user data: ACCESS_NETWORK_STATE, WAKE_LOCK, RECEIVE_BOOT_COMPLETED, FOREGROUND_SERVICE, com.android.vending.BILLING, USE_BIOMETRIC and USE_FINGERPRINT, as for House Manual.

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
