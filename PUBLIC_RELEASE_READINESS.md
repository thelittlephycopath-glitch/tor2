# Public Release Readiness Audit

Status: **NOT CLEARED FOR PUBLIC VISIBILITY OR DISTRIBUTION**

This document tracks preparation for making the `thelittlephycopath-glitch/tor2` repository public. It is a preliminary repository review, not a certification that every file, commit, branch, binary, dependency, or workflow is safe.

## Release invariants

- Preserve the current UI/UX unless the maintainer explicitly approves a UI change.
- Do not merge experimental branches or publish a release merely to make the repository look ready.
- Never commit credentials, signing keys, private tokens, local machine configuration, private logs, or personal data.
- Do not weaken CI, privacy, VPN-routing, TLS, or security controls to obtain a green build.
- Keep the application clearly identified as an unofficial community fork unless affiliation and branding rights are established.
- Do not describe an APK as tested, secure, anonymous, or production-ready without evidence supporting that exact claim.

## Preliminary findings (2026-10-30)

### Confirmed blockers

- [ ] **Build is not verified.** The latest observed main-branch Android validation run failed. Workflow logs could not be retrieved through the available connector, so the root cause remains unconfirmed.
- [ ] **Public-facing README needs correction.** It still contains stale upstream GitLab build instructions and a developer-token setup path. Reconcile instructions with the actual GitHub-based build, clearly distinguish debug artifacts from official releases, and avoid implying Tor Project endorsement.
- [ ] **Privacy-policy accuracy needs a full review.** The current policy names The Tor Project as developer and makes broad claims about collection, transmission, and local-only processing. Verify every claim against this fork's actual code, permissions, network behavior, third-party libraries, and any optional network-learning functionality before presenting it as this project's policy.
- [ ] **Public identity and attribution need legal review.** Preserve required upstream copyright and license notices, document modifications, check trademark/branding use, and state the project's unofficial status. Do not replace upstream copyright or imply endorsement.
- [ ] **Git history and all refs are not cleared.** A default-branch tree review and limited indexed code searches do not inspect every historical commit, tag, branch, release asset, or artifact. A full history secret scan and review of all refs is required before switching repository visibility.
- [ ] **Open pull requests need disposition.** PR #8 (AutoFix workflow) and PR #7 (UI motion polish) are still open/draft. Review each diff and CI state; do not merge UI changes without explicit approval.

### Initial repository observations

- The repository is currently private; default branch is `main`.
- The recursive tree contains 1,672 paths at the time of the preliminary review.
- Visible branches at review time: `main`, `ci/claude-android-autofix`, `codespaces-build-test`, `feat/subtle-cute-connect-polish`, and `fix/android-sdk-package`.
- The repository has a BSD 3-Clause license file with upstream Tor Project copyright. Verify source provenance and obligations before publishing modifications or binaries.
- `.gitignore` excludes `local.properties` and `secrets.properties`, but ignore rules do not prove these files were never committed in history.
- The APK workflow is manual and both build and release inputs default to false. Review all workflows and permissions again before enabling public distribution.
- Limited GitHub indexed searches for common credential markers returned no results. This is **not** a complete secret scan and must not be treated as one.

## Required audit sequence

### 1. Preserve and inventory
- [ ] Record all branches, tags, releases, open/closed PRs, Actions artifacts, and workflow runs.
- [ ] Compare each non-main branch against `main`; identify unique commits and unmerged user work before pruning anything.
- [ ] Inventory tracked files, generated binaries, archives, test fixtures, screenshots, logs, and large files.
- [ ] Do not delete a branch until its unique work is reviewed and safely preserved.

### 2. Scan all history and distribution surfaces
- [ ] Run a history-aware secret scanner (for example, Gitleaks) over **all refs and tags**, not only the current tree.
- [ ] Check for API keys, access tokens, passwords, private keys, keystores, signing configs, personal information, internal URLs, and accidentally committed local files.
- [ ] Review release assets and Actions artifacts separately; Git history scanning does not cover them.
- [ ] If a secret is found, revoke/rotate it first, then remove it from every affected history/ref as appropriate. A rewritten history does not make an exposed credential safe.
- [ ] Check submodules, dependency source imports, vendored code, generated offline help, and bundled assets for provenance and license requirements.

### 3. Legal, attribution, and documentation
- [ ] Confirm upstream source license and notices for all copied/modified files and included assets.
- [ ] Confirm that project name, icon, screenshots, and wording do not suggest official Tor Project affiliation.
- [ ] Rewrite the README for this repository's real build path, supported Android versions/ABIs, known limitations, installation/uninstall warnings, and reproducible build steps.
- [ ] Publish an accurate project-specific privacy policy and threat model; clearly distinguish implemented behavior from plans.
- [ ] Add contribution guidance, security vulnerability reporting instructions, support boundaries, and a changelog/release process.
- [ ] Verify third-party notices and source/license availability for distributed binaries.

### 4. Technical and security verification
- [ ] Diagnose and fix the actual CI failure from runner/job evidence; do not guess at a source-code fix.
- [ ] Obtain a clean build from a clean checkout using the pinned toolchain.
- [ ] Pass unit tests, lint, formatting, security preflight, and available integration/instrumentation tests.
- [ ] Verify APK package ID, version, ABI coverage, manifest permissions, backup rules, network security configuration, native libraries, and signing metadata.
- [ ] Test install, upgrade/reinstall, launch, VPN permission flow, connection/disconnection, network changes, failure/recovery, and uninstall on representative Android versions/devices.
- [ ] Verify no traffic-leak claims are made without tests that support them; test IPv4/IPv6, DNS, captive portals, process death, and kill-switch/routing behavior where implemented.
- [ ] Review dependency versions, known vulnerabilities, provenance, and reproducibility.
- [ ] Keep release publishing opt-in and separated from ordinary CI. Do not use ephemeral debug signing for a stable upgradeable public release.

### 5. Public repository configuration
- [ ] Review workflow permissions, third-party actions, immutable SHA pins, fork PR behavior, and all write-capable automation.
- [ ] Ensure untrusted pull requests cannot access secrets or trigger privileged workflows unsafely.
- [ ] Add appropriate branch protection/rules, required checks, and CODEOWNERS/review policy where supported.
- [ ] Decide whether to publish all historical branches/PR context or prepare a clean public mirror. Any history rewrite must preserve attribution and be reviewed; do not silently erase provenance.
- [ ] Confirm issue/discussion settings and public contact details do not expose personal information.
- [ ] Perform a final independent review, then make visibility changes only after the maintainer explicitly approves the final go/no-go.

## Go/no-go rule

**Do not make this repository public until the history/ref/artifact secret scan, legal/provenance review, privacy-policy accuracy review, workflow review, and clean build/test verification are complete.** The present status is not cleared.
