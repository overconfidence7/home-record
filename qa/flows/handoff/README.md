# Handoff flows

Flows that need both apps on one emulator: Field Log exports a `.homerecord` and House Manual imports it (walkthrough F10 → H18, AC-F51, AC-H17, AC-C46, AC-C47). The device suite installs both apps for this folder. `X02-claim-link.yaml` (T063d4) is the first: Field Log sends the handoff, House Manual claims the link, signs in and imports. Subflows come from the per-app folders (`../field-log/_owner-company.yaml`, `../house-manual/_onboard.yaml`); each keeps its own appId.

Naming: `X01-export-import.yaml`, `X02-claim-link.yaml`. The `X` prefix keeps them out of the per-app walkthrough tables.
