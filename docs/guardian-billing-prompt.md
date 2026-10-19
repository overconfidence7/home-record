# Prompt for the Guardian Mode chat: add RevenueCat billing

Copy everything below the line into the Guardian Mode chat.

---

Add RevenueCat billing to Guardian Mode, matching the pattern already used in HomeRecord (House Manual). Here is everything you need.

## RevenueCat account (already exists)

- Dashboard: https://app.revenuecat.com/projects/b05900f1 — project "HomeRecord", id `b05900f1`. Reuse this project; do not create a new one. One RevenueCat project can hold several apps.
- Existing apps in it: Play Store app "House Manual" (`com.kurtz.homerecord.housemanual`, SDK key `goog_bbPJgTRveMSmAKFNMOASFSsDiqg`) and the Test Store (SDK key `test_wcsyngTbKZbKRjwmMCHjjzTWOjv`). Do not use the House Manual key in Guardian; a Play key is bound to one package name.
- Existing entitlement: `pro` (display name "Pro"). Entitlements are project-wide, so Guardian can reuse `pro` if a Guardian purchase should mean the same thing as a House Manual purchase, or add its own, e.g. `guardian_pro`, if the products are sold separately. Decide and record it in Guardian's docs/decisions.md. Default: separate entitlement `guardian_pro`, since the apps are separate purchases.
- Existing offering: `default` with packages `$rc_annual` → product `pro_annual` and `$rc_lifetime` → product `pro_lifetime` (House Manual's). Offerings are also project-wide, so Guardian needs its own offering, e.g. `guardian_default`, and the SDK must fetch it by identifier (`Purchases.sharedInstance.getOfferings { it["guardian_default"] }`), never `current`.

## What Nate has to do in the dashboard (give him this as a numbered checklist when the code is ready)

1. Project HomeRecord → Apps → "+ New" → Play Store → name "Guardian Mode", package `<Guardian's applicationId>`. Copy the new `goog_...` public SDK key.
2. Product catalog → Products → "+ New" → app "Guardian Mode" → identifiers `guardian_annual` (subscription) and `guardian_lifetime` (one-time). Also add the same two under the Test Store app so the emulator can test it.
3. Product catalog → Entitlements → `guardian_pro` (or `pro`) → attach the two products (both Play and Test Store copies).
4. Product catalog → Offerings → "+ New" → identifier `guardian_default`, packages Annual (`$rc_annual` → `guardian_annual`) and Lifetime (`$rc_lifetime` → `guardian_lifetime`). Make sure House Manual's `default` offering stays the current one.
5. Later, when Play Console exists: create the two in-app products there with the same ids, upload the Play service-account JSON under the Guardian Play Store app in RevenueCat.

Prices are set in Play Console / the Test Store product, never hardcoded in the app.

## Code pattern to copy from HomeRecord

- Dependency: `com.revenuecat.purchases:purchases` (10.28.1 or latest stable), via the version catalog.
- One `Entitlements` interface in the app, with a `StateFlow<Boolean>` for isPro, `suspend fun offers(): OffersResult` (Loaded(annual, lifetime with localized price strings) | Unavailable), `suspend fun purchase(plan)`, `suspend fun restore()`. Exactly one class imports the RevenueCat SDK (`RevenueCatEntitlements`); tests use `FakeEntitlements`. Offline or any SDK error → `Unavailable`, never a crash; the paywall shows "can't load prices" + Retry.
- `Purchases.configure(PurchasesConfiguration.Builder(context, apiKey).build())` once in Application.onCreate via the app container; `RevenueCatEntitlements.activity` is set in MainActivity.onResume and cleared in onPause because `purchase()` needs a resumed Activity.
- Plans map from package types: `PackageType.ANNUAL` → Annual, `PackageType.LIFETIME` → Lifetime. Pro = `customerInfo.entitlements["guardian_pro"]?.isActive == true`.
- Keys never live in source. `build.gradle.kts` reads, per build type, and bakes into `BuildConfig.REVENUECAT_API_KEY`:
  - debug: `revenuecat.testApiKey` in `local.properties` → `REVENUECAT_TEST_API_KEY` env var → release chain
  - release: `revenuecat.apiKey` in `local.properties` → `REVENUECAT_API_KEY` env var → `""` (free tier, Retry paywall)
  Guardian must use its own property/env names so a shared machine can hold both apps' keys: `guardian.revenuecat.apiKey`, `guardian.revenuecat.testApiKey`, `GUARDIAN_REVENUECAT_API_KEY`, `GUARDIAN_REVENUECAT_TEST_API_KEY`. The Test Store key is shared across the project: `test_wcsyngTbKZbKRjwmMCHjjzTWOjv`.
- Debug builds therefore run against the RevenueCat Test Store, which fakes the store: offerings load, purchases complete and the entitlement activates on the emulator with no Play Console. QA should verify: paywall lists Annual and Lifetime with prices, purchase unlocks Pro, restore works, airplane mode shows the Retry state.
- R8 keep rules: RevenueCat ships its own consumer rules; nothing extra needed unless lint says otherwise.
- Document all of this in Guardian's `docs/billing.md` (copy the table format from HomeRecord's), add a decisions.md entry, and keep the Play-policy review gate: no billing code ships before the Guardian Play policy review (backlog E0.1) is done — build it behind the `Entitlements` interface with the fake until then if the gate is still open.
