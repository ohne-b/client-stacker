# Repository instructions

Read [CONTRIBUTING.md](CONTRIBUTING.md) before editing or reviewing. It owns the
development workflow, validation, review requirements, and release boundaries.

Keep resource accounting and operation planning in `core/`. Minecraft menu actions,
screens, and loader integration belong in `src/main/`. A client request has one
inventory owner. Never treat predicted inventory changes as server confirmation,
replay an uncertain purchase, or drop items to make space.

Villager Trading Plus integration is optional at runtime. Preserve its screen and
controls, capture a managed request before its trade loop starts, and leave unknown
versions untouched. Changes to menu handling need cancellation, correction, and
stale-container coverage.

Keep this file short and update contracts when behavior changes. Do not add alternate
instruction files or planning documents. Use a separate read-only reviewer for
independent review; provide the contributor rules and actual validation evidence.
