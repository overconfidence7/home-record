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
- House Manual billing needs `revenuecat.apiKey=goog_...` in `local.properties`.
- Output: `house-manual/build/outputs/apk/release/`, `field-log/build/outputs/apk/release/`.
