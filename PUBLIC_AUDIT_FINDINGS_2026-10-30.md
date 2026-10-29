# Deep Public-Readiness Findings — 2026-10-30

Status: **NO-GO for public visibility**. This document records confirmed source/repository observations from the deep review. It is not a security certification.

## Scope and evidence limits

Reviewed the current `release/public-readiness-audit` branch's README, Android manifest, Containerfile, reproducible build script, Onionmasq pin file, APK workflow, diagnostic workflow, validation workflow, security preflight workflow, privacy-policy source and bundled HTML, PR #7/#8 metadata and patches, release metadata, and branch comparison. The connected GitHub interface did not provide a complete raw clone, complete all-ref history scan, or usable logs for the recent failed jobs. GitHub code-search results were empty for several queries; that is not evidence that secrets are absent.

## Confirmed blockers

### 1. Privacy policy is materially mismatched to this fork

Files:
- `doc/PRIVACY_POLICY.md`
- `app/src/main/assets/help/PRIVACY_POLICY.md.html`

Both identify **The Tor Project, Inc.** as developer and cover Tor Browser desktop, Tor Browser Android, and TorVPN together. They make categorical statements including no personal/sensitive data collection, no telemetry, no third-party sharing, and that all installed/protected-app preferences are never transmitted. Those claims are not established by this review for this specific community fork. The bundled HTML mirrors the same mismatch, so editing only the Markdown source would leave the in-app copy misleading.

Required action before public release: inspect all runtime network paths and storage, decide who actually operates this fork and provides support, then rewrite both source and bundled policy consistently. Do not use Tor Project contact details as if they were the fork maintainer's contact. Do not make blanket claims until each has evidence. This is a user-facing policy change and should receive a final human review.

### 2. Build inputs are not fully immutable

- `scripts/onionmasq.hash` contains `vpn-1.9.1Beta`, a tag name, not a full source commit SHA. The reproducible build script therefore depends on the tag resolving to a particular upstream commit at build time.
- `Containerfile` starts from `containers.torproject.org/tpo/tpa/base-images/debian:trixie`, a movable tag rather than an image digest.
- The Android command-line tools and NDK downloads have explicit SHA-256 checks; that is good, but it does not pin the base image or the Onionmasq tag target.

Required action: resolve and record the exact upstream Onionmasq commit corresponding to the intended version; verify its provenance and compatibility before changing the pin. Pin the base image to a verified digest and document a deliberate update procedure. Do not guess a SHA.

### 3. Repository protections are not evidenced

The latest repository inventory reported `protected: false` for `main` and visible branches. No required-check or approval policy was evidenced by the available inspection. This is a risk because automation and future public contributions may touch sensitive VPN code.

Required action: configure branch protection/rules for `main` with required successful validation, review requirements appropriate to the maintainer setup, and prevent force pushes/deletion where supported. The current connector may not have permission to change repository administration settings.

### 4. Existing releases need separate review

The Releases API returned published release `tor2-build-88` (published 2026-10-24) with four APK assets, including a universal APK of 280,855,812 bytes and architecture-specific APKs. Asset metadata marks them as uploaded; the release workflow/source comments identify the artifacts as unofficial debug-signed builds. Asset metadata included SHA-256 digests, but this review did not download and inspect each APK, native library, embedded policy, signing metadata, or all older releases.

Required action: enumerate every release and asset, download them to a controlled analysis environment, hash and inspect package IDs, manifest permissions, signing certs, bundled files, native libraries, embedded URLs, and secret-like strings. Decide whether any existing release should remain available after the repo becomes public. Do not delete or replace assets without explicit approval.

### 5. CI failures remain undiagnosed

Recent validation and security-preflight runs failed within seconds. The connector returned empty job-step summaries and could not retrieve logs for the referenced jobs. The source workflow files on the audit branch now use immutable SHA pins for the reviewed external actions, but a successful new run has not been observed. The Android build failure root cause remains unknown.

Required action: obtain a fresh run on the audit branch and preserve full logs as short-lived artifacts. Diagnose the first actual failing command; do not assume action pinning explains the Android compile failure. Do not label the build fixed until build, unit tests, lint, staging APK, and available emulator smoke tests pass.

### 6. Public history is not cleared

A current-tree scan, indexed code search, and a sample release listing are not a substitute for scanning every branch, tag, commit, release asset, and Actions artifact. No complete history-aware secret scan has been completed in this environment. Ignore rules for local properties files do not prove that such files were never committed.

Required action: run a secret scanner over all refs/tags from a complete clone and inspect historical large/binary files and artifacts. If credentials are found, revoke them before considering history rewriting. Review closed and open PRs and all branch-only commits before deciding which refs to preserve. Never infer “clean” from empty search results.

## Additional repository observations

- The Android manifest requests `QUERY_ALL_PACKAGES`, internet/network-state, boot-completed, wake-lock, foreground-service, notifications, and vibration permissions. It sets `allowBackup=false` and `usesCleartextTraffic=false`. These settings are useful but do not establish that traffic is leak-free or that every privacy claim is true.
- The source includes a circumvention API client that requests `https://bridges.torproject.org/moat/circumvention/` over HTTPS with certificate pinning. The policy should describe the actual app behavior and distinguish service endpoints from personal-data collection.
- The validation workflow builds a staging APK and runs unit tests, ktlint, and Android lint. Its successful execution has not been verified for the audit branch.
- The manual APK workflow defaults both `run_apk_build` and `publish_release` to false and documents the artifacts as unofficial/debug-signed. That is a useful opt-in gate, but historical releases still exist and need review.
- PR #7 remains draft/open and changes the connect screen's view effects/touch animation. Keep it separate; do not merge or modify it without explicit approval.
- PR #8 remains draft/open and adds a write-capable automated fix workflow. It has been pinned to an immutable action commit, but its end-to-end behavior is unverified. Review token scope, fork/PR event semantics, OpenRouter secret handling, and the exact permissions before merging.
- The audit branch compares to `main` as 6 commits ahead and 0 behind at the time of this review. Its changes are README/build-workflow pinning and audit documentation; it has not been merged.

## Required exit criteria

Do not switch the repository to public until all of the following have evidence:
1. Full all-ref history and artifact secret scan completed; any findings remediated and credentials rotated.
2. Both privacy-policy copies accurately describe this fork and are checked against code paths.
3. Source provenance, BSD 3-Clause obligations, third-party notices, bundled assets, and branding/trademark presentation reviewed.
4. Onionmasq source and container base inputs pinned to verified immutable identifiers.
5. All workflows and write-capable automation reviewed; branch protection and required checks configured where possible.
6. Clean reproducible build, unit tests, format/lint, staging build, and device/emulator regression checks pass.
7. VPN-routing, DNS, IPv4/IPv6, reconnect, failure, and process-death behavior tested before any security/leak-resistance claims.
8. All branches, PRs, releases, and artifacts have a documented disposition; no UI/UX changes merged without approval.
9. A final go/no-go review is recorded and the maintainer explicitly approves changing visibility.

**Decision today: NO-GO. Keep the repository private.**


## Workflow-specific security review update

### CI guard design and observed failure boundary

`scripts/verify_ci_security.sh` statically checks all workflow `uses:` references for a full 40-character SHA and a semantic-version comment, and checks that the APK build/release gates, source-import branch restriction, and backup exclusions remain in place. The fixture test script includes positive and negative cases for mutable action tags, missing comments, missing release gates, invalid shell syntax, source-import restriction, and backup exclusions.

This is a useful offline policy guard, but it is a text-pattern checker, not a complete YAML semantic/security validator. It does not itself prove action SHAs correspond to the comments, review permissions, assess third-party action source, validate workflow event privilege boundaries, or prove VPN behavior. Those need separate review.

The failed run's job metadata was retrievable, but the step list and logs were not: the GitHub API wrapper returned the jobs with `steps: null` and `logs_url: null`, and log download failed. Therefore the precise failing command is still unknown. Do not state that the mutable-action guard is the proven failing step without logs; it is a plausible failure source based on the guard's checks and branch differences, not confirmed run evidence.

### AutoFix PR #8 — privilege review required

PR #8 remains draft/open. Its workflow uses `workflow_run` after failed PR validation and requests `contents: write`, `pull-requests: write`, `issues: write`, `actions: read`, and `id-token: write`. It passes `OPENROUTER_API_KEY` to the Claude action and sets `ANTHROPIC_BASE_URL` to OpenRouter. It restricts execution to failed pull-request runs from the same repository and excludes branches starting `claude/`, but the job still runs in a privileged automation context while processing a PR branch.

Before merging, verify from the pinned action's source/documentation which permissions are strictly required. Remove unused write scopes and `id-token: write` if not needed. Review how the action checks out and edits PR content, prompt-injection exposure from repository files/logs, whether it can access secrets or alter workflow files, and whether generated commits are isolated to a new branch. Confirm that no action can publish releases or modify repository settings. The existing prompt guardrails are not a substitute for least-privilege token scopes and independent human review.

### APK release workflow — distribution constraints

The manual APK workflow creates an ephemeral debug key, emits debug-signed APKs, runs a limited emulator launch smoke test, and verifies package metadata/checksums. This is not equivalent to a stable production release: ephemeral signing keys prevent normal in-place upgrades across builds, the smoke test does not validate VPN traffic containment, and debug signing is not a trusted publisher identity. Keep this clearly labeled experimental. Before any public distribution, decide whether APK artifacts should be published at all, and separately review every historical release asset.

### Branch configuration and policy enforcement

The available review reported no protected branch on `main` or the visible branches. The connector may not have administrative permission to set repository rules. Treat protection as unresolved until verified in repository settings; a workflow file alone cannot enforce required review/merge policy if the repository settings do not require it.


## Branch disposition matrix (refreshed 2026-10-30)

Comparison against `main` confirms these branches are divergent, so they must not be blindly merged or deleted:

| Branch | Ahead / behind main | Files in reported diff | Disposition |
|---|---:|---|---|
| `feat/subtle-cute-connect-polish` | 7 / 3 | `ConnectFragment.kt`, `fragment_connect.xml`, new connect halo drawable | Keep separate; explicit UI/UX changes are outside this audit's authorization. |
| `ci/claude-android-autofix` | 5 / 3 | new `.github/workflows/claude-android-autofix.yml` | Keep separate; review workflow permissions and prompt-injection boundaries before any merge. |
| `codespaces-build-test` | 3 / 4 | new devcontainer config; modified `Containerfile` | Keep for review; compare its container edits against the audit branch's build-input changes. |
| `fix/android-sdk-package` | 1 / 4 | modified `Containerfile` | Check whether its package fix is superseded or conflicts; do not delete based on commit count alone. |
| `release/public-readiness-audit` | 6 / 0 at the earlier comparison | README/build-input documentation, action pinning and audit documents | Preparation-only branch; not merged and not build-verified. |

The compare API provides a file summary but not a full enough patch/history audit to conclude that any branch is disposable. No branches have been deleted.

## Access and verification constraints

The connected repository metadata confirms the repository remains private and the connected identity has admin/maintain/push permissions. That does not imply the connector exposes every GitHub API capability. In this pass:
- Job listings were available, but step lists were null and downloadable job-log blobs returned `BlobNotFound`.
- The artifacts endpoint returned no artifacts for the specific failed CI run checked; this is not a statement about all historical release assets.
- Repository settings indicate the repository is private; branch-protection/ruleset enforcement has not been verified and was not changed.
- A complete local clone was not available through the connector. Consequently no all-ref history scanner or local APK static-analysis tool was run here.

## Next technical actions, in order

1. Obtain a new validation run with logs accessible without activating the privileged AutoFix path; diagnose the earliest failing step.
2. Verify upstream Onionmasq tag resolution and replace tag-based pinning only with a reviewed full commit SHA; pin the base container image only to a digest verified from its registry.
3. Review the full Android source paths for data flows and the exact VPN tunnel lifecycle, then correct both policy copies after the maintainer/support identity is defined.
4. Obtain a complete clone in a controlled environment and scan all refs, tags, historical blobs, releases, and downloadable artifacts with a local secret scanner; rotate any exposed credential before considering history rewriting.
5. Establish required checks and branch rules in GitHub settings; then run unit, lint, staging APK, device/emulator and VPN leak-regression tests.
6. Re-evaluate all release assets and the two draft PRs; record the disposition and only then produce a final go/no-go checklist.
