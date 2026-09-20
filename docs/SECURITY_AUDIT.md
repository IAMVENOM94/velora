# Security and publication review — updated 2026-09-20

This is a scoped local portfolio audit, not a penetration test or a guarantee of absence of secrets. It covers source/configuration/scripts, ignored local configuration, known credential/key formats, credential-bearing URLs, IP/email/path candidates and the repository's existing reachable/reflog history where present. Third-party dependency caches and compiled outputs are excluded from the source publication set. Binary assets and archived packages are not treated as text; local archives, game binaries and generated outputs remain excluded.

The scan records locations and issue types, never secret values. Generic installation paths, loopback/emulator addresses, semantic version strings and explicit example/test literals are not treated as live credentials. Local absolute developer paths were inventoried; default Steam paths are retained as installation conventions.

No new LICENSE was selected: source and visual-asset reuse rights require owner review. Git author/committer email metadata is inherited from the existing local identity and history; review this personal metadata before public publication. The owner explicitly retained the existing public commit email. Publication history was cleaned in a separate clone with owner approval; the original repository stays local.

## Findings

- **Historical login defaults resolved:** the owner confirmed they were fictional UI prototype data and approved cleanup. The two nonempty initializers in the original checkpoint were replaced with empty values in a separate publication clone. All seven original commits and their metadata/messages were preserved apart from the required tree/parent hashes; the old blob is absent after reflog/object cleanup. Current HEAD tree and all 108 tracked file contents matched the original before this documentation update. Benign project/URI scheme identifiers were preserved. External credential rotation was not needed for the owner-confirmed fictional data.
- **Local credentials:** root `.env` contains database/storage/development credentials; it stays ignored and is not modified. Exact comparisons of sufficiently long sensitive `.env` values against 131 historical blobs found no matches.
- `.env.example` contains examples/placeholders; `scripts/setup-dev.ps1` generates random local values. `scripts/scan-release-apk.ps1` contains secret-detection patterns, not a private key.
- `local.properties` is local Android SDK configuration and remains ignored. `captures/` includes UI/account/session data and remains ignored; no capture was promoted to portfolio media.
- No provider-token/private-key content was confirmed in the source publication set. Historical placeholder/test data is not evidence of a live credential; the older login pair above has been removed from publication history.
- `npm audit` reports **12 vulnerable package entries: 5 high and 7 moderate**, zero critical, in the installed backend dependency tree. This includes direct and transitive packages; counts are not twelve independently demonstrated exploits. Suggested remediations include major-version changes, so no `audit fix --force` was applied. See `DEPENDENCY_AUDIT.md`.
- Production SMS is a nonfunctional adapter; production Android push registration is not wired. Development assertions/Premium must not be represented as secure production integrations.

## History and scope

Existing branch `master` and its history were retained. Existing uncommitted application changes were preserved and included in the audited local snapshot. There was no existing remote. The original working copy and captures stay local; generated output, databases and runtime dependencies are excluded by `.gitignore`.

## Publication verification — 2026-09-20

The full cleaned history (188 blobs and 7 commit metadata objects before this documentation commit) was scanned for provider tokens, private keys, credential URLs, secret assignments, local credential exact matches and forbidden generated/configuration files. No unresolved findings remained. Reviewed matches were synthetic integration-test accounts, API examples, environment-variable URL templates and key-detection expressions. The historical login and nonempty login initializers were absent. The old blob is absent from the publication object database. This is a scoped publication secret-scan PASS, not a production security assessment; the 12 dependency findings remain open.

Backend compilation and all 18 unit tests passed again. Android debug compilation and all 11 JVM tests passed in a forced fresh run (55 tasks executed). Tests ran in the original local environment against tracked source proven byte-identical to the cleaned publication snapshot. No live integration or device tests were claimed.
