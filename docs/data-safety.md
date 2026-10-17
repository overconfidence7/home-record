# Play Console "Data safety" notes (draft)

Answers for both apps, based on the code as of T39. Re-check before each release.

## House Manual (`com.kurtz.homerecord.housemanual`)

| Question | Answer | Why |
| --- | --- | --- |
| Does the app collect or share user data? | Collects: Purchase history only. Shares: none. | Everything the user enters (panel, breakers, rooms, equipment, photos, reminders) stays in app-private storage on the device. |
| Data collected | **Purchases** (subscription / lifetime entitlement status) | Handled by Google Play Billing through RevenueCat. RevenueCat receives an anonymous app user ID and purchase receipts. |
| Optional or required | Optional (only if the user buys Pro) | The free tier works with no network. |
| Processed ephemerally? | No (RevenueCat keeps entitlement records) | |
| Encrypted in transit | Yes (HTTPS) | |
| Can users request deletion? | Purchases are tied to the Google Play account; no account exists in the app. | |
| Photos / files | Not collected. Photos stay on the device; they leave only when the user exports a PDF or `.homerecord` bundle through the Android share sheet. | |
| Camera | Used for panel / shutoff / equipment photos and label OCR. OCR runs on device (bundled ML Kit model). Nothing is uploaded. | |
| Location, contacts, identifiers, ads | None. No advertising ID, no analytics, no crash reporting SDK. | |
| Notifications | `POST_NOTIFICATIONS` for maintenance reminders the user sets. | |
| Backups | `allowBackup=false`. Optional encrypted `.homerecord` export is user initiated. | |

## Field Log (`com.kurtz.homerecord.fieldlog`)

| Question | Answer | Why |
| --- | --- | --- |
| Does the app collect or share user data? | No. No data collected, none shared. | No network access, no SDKs that transmit data. |
| Camera | Job photos, stored in app-private storage. | |
| Microphone | Voice notes (`RECORD_AUDIO`), stored in app-private storage. | |
| Export | Close-out `.homerecord` bundle and PDF report are written to cache and shared only through the share sheet the user opens. | |
| Location, contacts, identifiers, ads | None. | |

## Security practices (both apps)

- Data encrypted in transit: House Manual billing traffic only (HTTPS); Field Log has no traffic.
- Optional passphrase encryption (AES-256-GCM, PBKDF2) for `.homerecord` backups.
- Independent security review: no.
