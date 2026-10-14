# Decisions

Newest first. Each entry: date, decision, reason. Open questions at the bottom.

## Log

- **2026-10-13** Two apps in one repo (`:house-manual`, `:field-log`) sharing `:core` and `:core-ui`. Reason: Nate's call; the apps have different users and store listings but one data model.
- **2026-10-13** Native Android (Kotlin + Jetpack Compose), not Kotlin Multiplatform or Flutter. Reason: build constraints for the autonomous builder; matches CircuitSiegeDualFront; iOS can be revisited once the apps earn it.
- **2026-10-13** AGP 8.13.x rather than 9.x. Reason: same plugin model as CircuitSiegeDualFront (`kotlin-android` + `kotlin-compose` plugins); AGP 9 changes the Kotlin integration and is a separate upgrade task once the build is stable.
- **2026-10-13** compileSdk/targetSdk 37 (Android 17, stable since June 2026), minSdk 26.
- **2026-10-13** Mapping mode runs in the foreground activity with keep-screen-on, not a foreground service. Reason: the phone is plugged in and visible during mapping; avoids the `specialUse` foreground-service type and its Play declaration.
- **2026-10-13** House Manual panel and shutoff photos use the system camera (`TakePicture` contract); Field Log uses CameraX. Reason: Field Log needs volume-key shutter and glove-size controls; House Manual does not.
- **2026-10-13** Data-plate OCR uses ML Kit text recognition, bundled model. Reason: on-device, no network, no account; the only Google SDK in v1.
- **2026-10-13** Billing deferred to M3 behind an `Entitlements` interface; RevenueCat only in T36. Reason: cannot be verified without Play and a signed build; the rest of the app must not depend on it.
- **2026-10-13** Manual DI (`AppContainer`) instead of Hilt. Reason: two small apps; fewer annotation processors; faster builds.
- **2026-10-13** `.homerecord` is a zip of `record.json` + `media/`, optional AES-256-GCM wrapper with a PBKDF2 key (M3). Reason: inspectable, no library, matches `shared/home-record-format.md`.
- **2026-10-13** Names: HomeRecord (repo `home-record`, package root `com.kurtz.homerecord`). Reason: neutral name covering both apps; `kurtz` is a placeholder until the LLC exists.

## Open questions

- [ ] Store names for the two apps ("House Manual" and "Field Log" are working titles; check Play and trademark conflicts).
- [ ] LLC name → final package root (rename before the first Play upload; application IDs cannot change after).
- [ ] Climate zones for seasonal reminders: four coarse zones or IECC zones?

## Build decisions

- 2026-10-14: Compose BOM pinned to 2026.06.01. BOM 2026.08.00+ (Compose 1.12) requires AGP 9.1, but CLAUDE.md fixes AGP at 8.13.x. Revisit if Nate approves AGP 9.
- 2026-10-14: Versions: AGP 8.13.2, Kotlin 2.2.21, Gradle 8.14.3, Robolectric 4.15.1. KSP/Room are added when T02 needs them (KSP 2.2.21-2.0.5).
- 2026-10-14: compileSdk 37 with AGP 8.13.2 (tested to 36.1) works with `android.suppressUnsupportedCompileSdk=37.0`. The SDK package is `platforms;android-37.0`; setup-android.sh now installs that and symlinks `platforms/android-37` because AGP 8.13 looks for that directory name.
- 2026-10-14: Maven Central returns HTTP 429 under parallel resolution in the cloud proxy; use `--max-workers=2` and retry.
