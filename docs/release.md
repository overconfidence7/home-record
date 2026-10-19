# Release builds

- Version: edit `homerecord.versionName` in `gradle.properties`. `versionCode = major*10000 + minor*100 + patch` (0.1.0 -> 100) for both apps.
- Signing (optional): in the gitignored `local.properties` (or `HOMERECORD_STOREFILE`, `HOMERECORD_STOREPASSWORD`, `HOMERECORD_KEYALIAS`, `HOMERECORD_KEYPASSWORD` env vars):
  ```
  homerecord.storeFile=/path/to/release.jks
  homerecord.storePassword=...
  homerecord.keyAlias=...
  homerecord.keyPassword=...
  ```
  Without them `./gradlew assembleRelease` produces `app-release-unsigned.apk`.
- House Manual billing keys: see `docs/billing.md`. Debug builds read `revenuecat.testApiKey` (local.properties) then `REVENUECAT_TEST_API_KEY`, then the release chain; release builds read `revenuecat.apiKey` then `REVENUECAT_API_KEY`, else blank (free tier with the Retry paywall). Keys never land in git.
- Output: `house-manual/build/outputs/apk/release/`, `field-log/build/outputs/apk/release/`.
- Testing builds: every push to `build` runs `.github/workflows/testing-apks.yml`, which attaches debug and release APKs of both apps (signed with a throwaway testing key) to the rolling pre-release https://github.com/overconfidence7/home-record/releases/tag/testing. Install from a phone's browser. Not for the store.
