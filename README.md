# HomeRecord v2

Two native Android apps on one shared house model. **House Manual** is the homeowner's record of the house: a floor plan you draw, rooms you can open in 3D with your own wall photos painted on, every household item with make, model, dates and a warranty that is looked up once and kept, the breaker panel, the shutoffs, reminders and a home report. **Field Log** runs a small contractor's jobsite: team and roles, time clock with GPS, plans and files, photos, daily logs, tasks, change orders with signature, schedule, timesheets and reports, offline first, and it builds the house model on the job and hands it to the homeowner. A Cloudflare backend handles sync, files, warranty lookup and share links.

Read [PRODUCT.md](PRODUCT.md) first (what this is for), then [SPEC.md](SPEC.md) (the contract) and [PLAN.md](PLAN.md) (the build order). Builder rules are in [CLAUDE.md](CLAUDE.md); UI rules in [docs/ui-standards.md](docs/ui-standards.md); nightly QA in [qa/](qa/).

v1 (October 13 to 19, 2026) is archived in this repo's history as tag `v1-final` and branch `archive/v1`, for reference only; the builder does not copy code from it.
