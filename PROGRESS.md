# Progress log

One line per run, newest at the bottom: `YYYY-MM-DD HH:MM TZ — tasks — tests passed/total — commit`.

2026-10-13 — setup complete
2026-10-14 (Eastern, time n/a) — T01 scaffold; fixed setup-android.sh platform name (android-37.0 + symlink); tests 1/1 pass; assembleDebug+lintDebug green — emulator: launch both apps, each shows centered 'HomeRecord' — 2cf1617
2026-10-14 21:50 ET — T02 Room schema+DAOs for 10 entities (SDK re-setup; Maven 429s needed retries) — 15/15 pass; assembleDebug+lintDebug green — emulator: nothing new visible; both apps still launch showing 'HomeRecord' — 46c84bb+
