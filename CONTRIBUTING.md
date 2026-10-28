# Contributing

The canonical repository is [ohne-b/client-stacker](https://github.com/ohne-b/client-stacker).
Read this guide before editing, testing, or reviewing. Client Stacker uses the
[MIT license](LICENSE.md).

## Scope

Client Stacker automates ordinary crafting and trading on the client. It does not
change recipes, prices, item counts, server rules, or interaction reach. Fabric and
NeoForge are supported platforms; Forge is outside the project scope.

Preserve unrelated changes, settings, credentials, saves, logs, and backups. Use
disposable worlds and synthetic inventories for tests. Never run automated inventory
tests against a player's normal world or a public server.

## Development

Run Gradle with JDK 25. Minecraft 1.21.x uses a JDK 21 toolchain; 26.x uses JDK 25.
The committed Gradle wrapper resolves build dependencies. Set `JDK21` and `JDK25` to the respective JDK
directories when both toolchains are installed outside standard locations.
Set `JAVA_HOME` to JDK 25 when it is not already the default.

```powershell
.\gradlew.bat :core:test
.\gradlew.bat :1.21.11-fabric:build
.\gradlew.bat :1.21.11-neoforge:build
.\gradlew.bat :1.21.11-fabric:runClient
```

Version-specific dependencies live under `versions/`. Shared source is processed by
Stonecutter; keep conditions at API boundaries and use the shared core for behavior.
Run one target at a time locally when memory or disk space is limited. Build output,
test worlds, local toolchains, and screenshots are ignored. Screenshots belong in
`artifacts/` and must not expose private data.

Prefer concrete classes, composition, and existing game APIs. Add dependencies only
when they remove substantial work. Do not add forwarding layers, speculative APIs,
or a second implementation of the same accounting rule. Keep UI text in the English
message catalog and use vanilla controls. The README is a short user entry point;
put contributor rules here and technical contracts in AGENTS.

## Branches and commits

Inspect `git status` and the diff before editing. Use descriptive `feat/` or `fix/`
branches from current `main`, focused conventional commits, and conventional PR
titles, for example `fix(trading): stop when the merchant changes`.

Keep substantial work in meaningful commits. Do not add conversational history or
assistant branding. Verify the configured author and committer before committing.
Maintainer contributions use the verified public `ohne-b` noreply identity, never a
private name or email address.

Integrate current main before final validation and again before an authorized merge
if it advances. Preserve others' work; do not reset or stash it to simplify a merge.
Keep incomplete PRs in draft. Implementation does not authorize merging, releasing,
deploying, or deleting files and caches.

## Validation and review

- Run meaningful core tests for accounting, capacity, limits, and failure paths.
- Build each affected Minecraft/loader target. A successful build is not an in-game
  compatibility test.
- Exercise affected UI, inventory, and cancellation behavior in an isolated world.
- Test VTP with its exact supported artifact, and also test with VTP absent.
- Run `git diff --check`; verify changed commands, metadata, and relative links.
- Record exact commands, results, and untested areas. Do not report pending checks,
  expected behavior, or a local prediction as a successful result.

Every PR needs independent critical review by a human or a separate read-only
reviewer who did not write the change. Supply the goal, final revision, acceptance
criteria, these instructions, and actual test evidence. Resolve blocking findings
and have substantive fixes rechecked. The author's reread is not independent review.

The PR description explains the concrete problem, resulting behavior, and relevant
validation. Use the template to record the tested revision and review results.

## Releases

Releases need explicit authorization. Publish only validated artifacts for explicitly
tested Minecraft versions. Use consistent filenames and concise release notes with
relevant PR numbers. Never broaden a dependency range to imply untested compatibility.
Keep upstream availability gaps distinct from tested support.

When merging is authorized, use a merge commit with a conventional subject and PR
number, such as `feat(trading): add emerald preparation (#12)`. Do not use the default
GitHub merge subject, squash, or rewrite published history without authorization.
Normal commits and merges do not publish a release.

Never put credentials in source or logs, copy a local CLI token into an automation
secret, or remove caches, worktrees, artifacts, or backups without authorization.
