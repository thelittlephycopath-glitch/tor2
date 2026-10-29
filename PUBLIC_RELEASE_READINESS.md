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


### Branch inventory snapshot

The branch comparison API reported these branches as diverged from `main` at review time; these are not safe to delete without inspecting the full commit diffs:

- `ci/claude-android-autofix`: 5 commits ahead and 3 behind; includes the AutoFix workflow. PR #8 is still draft/open.
- `codespaces-build-test`: 3 commits ahead and 4 behind; includes a devcontainer file and a Containerfile change. Preserve until unique build configuration is reviewed.
- `feat/subtle-cute-connect-polish`: 7 commits ahead and 3 behind; modifies the connect fragment/layout and adds a drawable. This is a UI/UX change and must remain untouched unless explicitly approved. PR #7 is still draft/open.
- `fix/android-sdk-package`: 1 commit ahead and 4 behind; includes a Containerfile change. Review whether the fix is already present on `main` before deciding whether to retain it.

The API compare summary showed one changed-file summary per branch and is not a substitute for reviewing each commit/diff. Branch names, commit counts, and PR states can change; refresh before any public-visibility decision.

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


## Deep-audit progress update (2026-10-30)

This is an evidence update from the second review pass. It does not clear the repository for public visibility.

### Changes made only on the preparation branch

- [x] Replaced stale README local-build instructions that directed readers to clone the upstream GitLab repository and create a personal GitLab token. The README now documents this repository's Containerfile/reproducible-build path and distinguishes unsigned local outputs from debug-signed community test builds.
- [x] Replaced mutable action tags in `.github/workflows/build-apk.yml` and `.github/workflows/diagnostic-build.yml` with commit-SHA pins and version comments:
  - `actions/checkout` `3d3c42e5aac5ba805825da76410c181273ba90b1` (`v7.0.1`)
  - `actions/cache` `55cc8345863c7cc4c66a329aec7e433d2d1c52a9` (`v6.1.0`)
  - `actions/upload-artifact` `cf430e030ddbb5b0abf93d22962f4752f3646cd9` (`v7.0.2`)
- [x] Re-ran a read-only static inventory of the six workflow files on this branch: 17 external `uses:` references found; all 17 use 40-character commit SHAs and version comments; zero unpinned references in that inventory. This was a script-level inspection, not a GitHub Actions execution, and the workflow validator itself has not yet been observed passing in a new run.

### Newly confirmed blockers and risk findings

- [ ] **Packaged privacy policy identifies the wrong developer and scope.** Both `doc/PRIVACY_POLICY.md` and the bundled `app/src/main/assets/help/PRIVACY_POLICY.md.html` say the developer is The Tor Project, Inc. and describe Tor Browser for desktop and Android as well as TorVPN. They make categorical no-collection/no-telemetry/no-third-party-transmission claims. That text is not an accurate project-specific policy for this community fork without a policy-owner review. It has not been changed because it is user-facing content and must be reconciled with actual behavior before making claims.
- [ ] **Network trust and leak-protection claims remain unverified.** The existing `PRIVACY_IMPLEMENTATION_AUDIT.md` records that there is no verified runtime call path from Android network callbacks to the network-learning persistence, that trust evidence is not yet bound to session generation end-to-end, and that the Android probe does not prove IPv4/IPv6/DNS containment, system lockdown, or app-routing enforcement. Do not advertise those protections as proven.
- [ ] **Dependency pin is not immutable.** `scripts/onionmasq.hash` contains `vpn-1.9.1Beta`, a tag name rather than a full commit SHA. The reproducible build script fetches upstream tags and checks out this tag; the exact source commit behind the tag was not independently recorded as a locked SHA during this pass.
- [ ] **Container base image is tag-pinned only.** `Containerfile` starts from `containers.torproject.org/tpo/tpa/base-images/debian:trixie`, not an immutable image digest. The build pins hashes for downloaded Android command-line tools and the NDK, but its base-image identity can move.
- [ ] **Repository protection is absent.** The repository metadata and branch listing report `protected: false` for `main` and every visible branch. There are no required-check/approval safeguards evidenced by this review.
- [ ] **Public releases already exist on the private repository.** The Releases API lists multiple published build releases, including `tor2-build-88` (published 2026-10-24). The latest release assets are debug-signed APKs and include a universal APK of about 281 MB. Release notes correctly say these are unofficial and experimental, but all historical release assets and their contents have not been independently scanned. Do not assume the manual publish gate means no releases exist.
- [ ] **CI evidence still missing.** The recent Android validation and security-preflight runs failed within seconds, but the GitHub connector returns empty job-step lists and `BlobNotFound` when retrieving those jobs' logs. A mutable-action-ref mismatch is now established in the old `main` workflows and is a deterministic reason the workflow guard would fail; the precise recorded failing step is not available, and the Android compile failure root cause remains unknown. The audit-branch changes have not yet been validated by a successful live CI run.
- [ ] **A full history scan is still not complete.** The GitHub code-search connector returned no useful code matches for secret keywords and its indexed search results are not a trustworthy history-wide scan. The visible-branch list, current tree, releases inventory sample, and commit-history page do not prove all commits, refs, historical releases, CI artifacts, or uploaded objects are clean. Do not interpret “no search results” as “no secrets.”

### Source-level data handling observed (not runtime certification)

- The manifest requests `QUERY_ALL_PACKAGES`, internet and network-state access, boot-completed, wake-lock, foreground-service, notification and vibration permissions. `allowBackup=false` and cleartext traffic disabled are explicit in the manifest; backup/device-transfer rules separately exclude the network-learning preferences file.
- The circumvention API client makes HTTPS requests to `bridges.torproject.org/moat/circumvention/` and uses a certificate pin. These remote requests do not, by themselves, establish personal-data collection, but the privacy policy should explain actual app network operations rather than make unqualified blanket claims.
- The license file is BSD 3-Clause and carries upstream Tor Project copyright. This pass has not completed a file-by-file provenance, notice, or trademark review of all imported source, bundled offline help, screenshots, logos, icons, fonts, translations, and binaries.

### Verification status

- **Repository visibility:** still private.
- **Main branch:** unchanged by this audit pass.
- **UI/UX:** unchanged by this audit pass.
- **Audit branch:** only build-workflow pinning, README build-instruction correction, and this audit document are intended to change.
- **Build/tests:** no successful new CI run or device/emulator regression pass has been established.
- **Release decision:** **NO-GO** until history and all ref/artifact surfaces are scanned, privacy/legal attribution is corrected and independently checked, reproducible dependency/image identities are reviewed, workflow guard and Android build/tests pass, and VPN leak/routing behavior is tested.
