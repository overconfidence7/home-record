# Billing (RevenueCat)

House Manual Pro is sold through RevenueCat. Field Log has no billing in v1.

## RevenueCat account (set up 2026-10-18)

| Item | Value |
| --- | --- |
| Dashboard | https://app.revenuecat.com/projects/b05900f1 |
| Project | HomeRecord (`b05900f1`) |
| Play Store app | House Manual, package `com.kurtz.homerecord.housemanual`, RevenueCat app id `app4db0975176` |
| Entitlement | `pro` (display name "Pro") |
| Offering | `default` (current) with packages `$rc_annual` → `pro_annual` and `$rc_lifetime` → `pro_lifetime` |
| Public SDK key, Play Store app | `goog_bbPJgTRveMSmAKFNMOASFSsDiqg` |
| Public SDK key, Test Store | `test_wcsyngTbKZbKRjwmMCHjjzTWOjv` |

Both keys are RevenueCat *public* SDK keys: RevenueCat designs them to ship inside the APK, they cannot read or change account data, and the Play key only works from the signed package above. They are kept out of source anyway (see below) so a key swap never needs a code change. Never put a RevenueCat *secret* key (`sk_...`) anywhere in this repo.

The Test Store is RevenueCat's fake store: with the `test_` key the SDK returns the offering and completes purchases without Google Play, so the paywall, plan list, purchase and restore can be exercised on an emulator before the app is in Play Console. Test Store products are created in the dashboard under the Test Store app with the same identifiers (`pro_annual`, `pro_lifetime`) as the Play products will have.

## Where the build reads the keys

`house-manual/build.gradle.kts` resolves, in this order, and bakes the result into `BuildConfig.REVENUECAT_API_KEY`:

| Build type | 1st | 2nd | 3rd |
| --- | --- | --- | --- |
| debug | `revenuecat.testApiKey` in `local.properties` | `REVENUECAT_TEST_API_KEY` env var | the release chain below |
| release | `revenuecat.apiKey` in `local.properties` | `REVENUECAT_API_KEY` env var | `""` → `UnavailableEntitlements` (free tier, Retry paywall) |

So:

- Nate's PC (`local.properties`, gitignored):
  ```
  revenuecat.apiKey=goog_bbPJgTRveMSmAKFNMOASFSsDiqg
  revenuecat.testApiKey=test_wcsyngTbKZbKRjwmMCHjjzTWOjv
  ```
- Cloud builder environment ("Android build"), environment variables:
  ```
  REVENUECAT_API_KEY=goog_bbPJgTRveMSmAKFNMOASFSsDiqg
  REVENUECAT_TEST_API_KEY=test_wcsyngTbKZbKRjwmMCHjjzTWOjv
  ```
- Antigravity QA runs on Nate's PC and therefore gets the Test Store key in debug builds: the paywall must show "Annual" and "Lifetime" with prices, and a Test Store purchase must unlock Pro (AC-95, AC-96).

## Still waiting on Nate / Play Console

- Confirm the RevenueCat account email (banner in the dashboard).
- Play Console: create the app, in-app products `pro_annual` (subscription, yearly) and `pro_lifetime` (one-time), then a Google Cloud service-account JSON with Play Developer API access, uploaded under the RevenueCat Play Store app. Until then real purchases cannot be verified and the Play key only loads an empty offering.
- Prices are set in Play Console (and in the Test Store products for emulator testing); the app never hardcodes a price.
