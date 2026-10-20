# QA fixtures

Test inputs the flows and the walkthrough use. Committed here so the cloud suite and the Antigravity run see the same files. Keep each under 1 MB.

| File | Used by | What it is |
| --- | --- | --- |
| `dataplate-rheem.jpg` | H4, F10 | Photo of a water heater data plate with a readable model and serial |
| `wall-kitchen.jpg` | H9 | A plain finished wall photographed at an angle, all four corners in frame |
| `wall-rough-in.jpg` | F10 | A framed wall with visible studs, wiring and a pipe, before drywall |
| `plan.pdf` | F7 | A one-page PDF floor plan |
| `receipt.jpg` | F14 | A store receipt with a visible total |

The builder adds the files at PLAN Q005 (photographs or synthetic images it generates are both fine; they must be free of personal data). The device suite pushes this folder to `/sdcard/Download/qa-fixtures/` before the flows run so a flow can pick them from the gallery.
