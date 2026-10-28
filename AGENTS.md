# Repository instructions

Read [CONTRIBUTING.md](CONTRIBUTING.md) before editing or reviewing. It owns the
development workflow, validation, review requirements, and release boundaries.

Keep resource accounting and operation planning in `core/`. Minecraft menu actions,
screens, and loader integration belong in `src/main/`. A client request has one
inventory owner. Never treat predicted inventory changes as server confirmation,
replay an uncertain purchase, or drop items to make space.

Before an automatic close, verify an authoritative empty cursor and settled inputs.
The checkpoint uses a valid hotbar self-swap to request vanilla's full inventory
correction, followed by an ID-bearing completion request as a server-thread barrier.
That request executes no command. A missing response must leave the menu open;
ordinary ping replies do not prove that inventory packets were processed.
Overlapping merchant interactions have no trusted identity. Discard their associations
until a server-thread barrier has drained the replies; require a fresh interaction.

Villager Trading Plus integration is optional at runtime. Preserve its screen and
controls, capture a managed request before its trade loop starts, and leave unknown
versions untouched. Changes to menu handling need cancellation, correction, and
stale-container coverage.

Keep this file short and update contracts when behavior changes. Do not add alternate
instruction files or planning documents. Use a separate read-only reviewer for
independent review; provide the contributor rules and actual validation evidence.
