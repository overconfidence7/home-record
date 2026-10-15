# Progress log

One line per run, newest at the bottom: `YYYY-MM-DD HH:MM TZ — tasks — tests passed/total — commit`.

2026-10-13 — setup complete
2026-10-14 (Eastern, time n/a) — T01 scaffold; fixed setup-android.sh platform name (android-37.0 + symlink); tests 1/1 pass; assembleDebug+lintDebug green — emulator: launch both apps, each shows centered 'HomeRecord' — 2cf1617
2026-10-14 21:50 ET — T02 Room schema+DAOs for 10 entities (SDK re-setup; Maven 429s needed retries) — 15/15 pass; assembleDebug+lintDebug green — emulator: nothing new visible; both apps still launch showing 'HomeRecord' — 46c84bb+
2026-10-14 22:30 ET — T03 repositories (Home/Panel/Shutoff/Equipment), MediaFileStore with path sanitizer, AppContainer+Application in both apps — 24/24 pass; assembleDebug+lintDebug green — emulator: nothing new visible; both apps still launch showing 'HomeRecord' (check no crash from new Application class)
2026-10-14 23:10 ET — T04 navigation host, start-destination logic, HM-Onboarding (VM, screen, atomic createHomeWithRooms) — 31/31 pass; assembleDebug+lintDebug green — emulator: fresh install shows 'Set up your home'; Continue disabled until name typed; pick rooms+custom, Continue lands on 'Panel' placeholder; force-stop and relaunch shows home name (hub placeholder)
2026-10-15 — T04a not started: baseline build blocked, Maven Central HTTP 429 persisted across 4 attempts incl. --max-workers=2 and waits; no code changed; build branch unchanged (last green 4-T04 commit) — emulator: nothing new
2026-10-15 ~11:50 ET — T04a not started: baseline testDebugUnitTest fails (DaoTest: Robolectric cannot fetch org.robolectric:android-all-instrumented:14-robolectric-10818077-i7 through proxy; assembleDebug fine; full build took 24 min after SDK re-setup; android-37 symlink had to point at android-37.0 not 37.2); no code changed — emulator: nothing new
2026-10-15 ~12:40 ET — baseline only, T04a not started: SDK was missing in container, re-ran setup-android.sh and repointed platforms/android-37 -> android-37.0 (script leaves it at 37.2, which Gradle rejects: fix script next run); assembleDebug testDebugUnitTest green (31/31) in 20m cold; added .kotlin/ to .gitignore — emulator: nothing new
