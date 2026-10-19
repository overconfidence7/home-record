# Play Console Data safety notes (draft)

Answers for both apps, checked against the manifests and code at S10 (2026-10-18). Check again before each release.

## House Manual (`com.kurtz.homerecord.housemanual`)

| Question | Answer | Why |
| --- | --- | --- |
| Does the app collect or share user data? | Collects: Purchase history only. Shares: none. | Everything the user enters (panel, breakers, rooms, equipment, photos, reminders) stays in app-private storage on the device. |
| Data collected | **Purchases** (annual or lifetime Pro status) | Google Play Billing, through RevenueCat. RevenueCat receives an anonymous app user ID and purchase receipts. |
| Optional or required | Optional (only if the user buys Pro) | The free tier works with no network. |
| Processed ephemerally? | No (RevenueCat keeps entitlement records) | |
| Encrypted in transit | Yes (HTTPS) | |
| Can users request deletion? | Purchases are tied to the Google Play account. The app has no account. | |
| Photos / files | Not collected. Photos stay on the device. They leave only when the user exports a PDF or `.homerecord` bundle through the Android share sheet. | |
| Camera | Photos are taken through the system camera app, and no CAMERA permission is declared. They are used for panel, shutoff and equipment photos and for label scanning. Scanning runs on the device with a bundled ML Kit model. Nothing is uploaded. | |
| Location, contacts, identifiers, ads | None. No advertising ID, no analytics, no crash reporting SDK. | |
| Notifications | `POST_NOTIFICATIONS` for the reminders the user sets up. | |
| Backups | `allowBackup=false`. The encrypted `.homerecord` backup is optional and the user starts it. | |

## Field Log (`com.kurtz.homerecord.fieldlog`)

| Question | Answer | Why |
| --- | --- | --- |
| Does the app collect or share user data? | No. No data collected, none shared. | No network access and no SDKs that send data. |
| Camera | Job photos, stored in app-private storage. | |
| Microphone | Voice memos (`RECORD_AUDIO`), stored in app-private storage. | |
| Export | The handoff `.homerecord` bundle and PDF report are written to cache and shared only through the share sheet the user opens. | |
| Location, contacts, identifiers, ads | None. | |

## Security practices (both apps)

- Encrypted in transit: House Manual purchase traffic only (HTTPS). Field Log has no traffic.
- Passphrase encryption (AES-256-GCM, PBKDF2) for `.homerecord` backups.
- Independent security review: no.
