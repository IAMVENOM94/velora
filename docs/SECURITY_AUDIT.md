# Security and publication review — 2026-09-19

This is a scoped local portfolio audit, not a penetration test or a guarantee of absence of secrets. It covers source/configuration/scripts, ignored local configuration, known credential/key formats, credential-bearing URLs, IP/email/path candidates and the repository's existing reachable/reflog history where present. Third-party dependency caches and compiled outputs are excluded from the source publication set. Binary assets and archived packages are not treated as text; local archives, game binaries and generated outputs remain excluded.

The scan records locations and issue types, never secret values. Generic installation paths, loopback/emulator addresses, semantic version strings and explicit example/test literals are not treated as live credentials. Local absolute developer paths were inventoried; default Steam paths are retained as installation conventions.

No new LICENSE was selected: source and visual-asset reuse rights require owner review. Git author/committer email metadata is inherited from the existing local identity and history; review this personal metadata before public publication. No history was rewritten and no remote publication was performed.

## Findings

- **Publication blocker — historical fixed login/password literals:** early `app/src/main/java/app/velora/MainActivity.kt`, blob `a68ceb5ede95`. The current screen no longer has those defaults. Their origin is unconfirmed; do not publish the history until confirmed synthetic, or rotate any real credential and agree on the history remediation. Editing the current file cannot erase old objects.
- **Local credentials:** root `.env` contains database/storage/development credentials; it stays ignored and is not modified. Exact comparisons of sufficiently long sensitive `.env` values against 131 historical blobs found no matches.
- `.env.example` contains examples/placeholders; `scripts/setup-dev.ps1` generates random local values. `scripts/scan-release-apk.ps1` contains secret-detection patterns, not a private key.
- `local.properties` is local Android SDK configuration and remains ignored. `captures/` includes UI/account/session data and remains ignored; no capture was promoted to portfolio media.
- No provider-token/private-key content was confirmed in the source publication set. Historical placeholder/test data is not evidence of a live credential; the older login pair above remains unresolved.
- `npm audit` reports **12 vulnerable package entries: 5 high and 7 moderate**, zero critical, in the installed backend dependency tree. This includes direct and transitive packages; counts are not twelve independently demonstrated exploits. Suggested remediations include major-version changes, so no `audit fix --force` was applied. See `DEPENDENCY_AUDIT.md`.
- Production SMS is a nonfunctional adapter; production Android push registration is not wired. Development assertions/Premium must not be represented as secure production integrations.

## History and scope

Existing branch `master` and its history were retained. Existing uncommitted application changes were preserved and included in the audited local snapshot. There was no existing remote. The original working copy and captures stay local; generated output, databases and runtime dependencies are excluded by `.gitignore`.
