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
