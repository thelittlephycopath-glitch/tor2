# GTor execution evidence — build and lifecycle audit

Date: 2026-10-28
Branch reviewed: `dangerous-experiment`
Reviewed commit: `3df013a5c53542077abcc10acd5ace60d7de92ff`

This is an evidence ledger, not a release approval. No build or runtime pass is claimed here.

## Verified from repository source

- `.github/workflows/build-apk.yml` exists and is manually dispatched. The build job is skipped unless `run_apk_build` is explicitly true. Release publication has a separate false-by-default input.
- The workflow pins checkout, cache, and artifact actions to immutable commit SHAs.
- The workflow runs `bash -n` and `scripts/test_ci_security.sh` / `scripts/verify_ci_security.sh` before building.
- The reproducible build script runs Onionmasq native/Android builds, then `testReleaseUnitTest`, then `assembleRelease`; it optionally builds an AAB and runs APK alignment verification.
- `LeakVerificationGate` checks timestamp freshness and five caller-supplied booleans, but explicitly does not measure the network itself. Its comments require a separate independent verifier.
- `AndroidEnvironmentMonitor` now serializes callback side effects with `unregister()` using `callbackDispatchLock` and rechecks registration/network identity before dispatch. This is a source-level fix; compilation and Android callback-scheduling tests remain outstanding.
- `AutonomyLifecycleOrderingTest` adds three stale-evidence ordering tests. The repository status endpoint returned no CI status entries for the reviewed commit, and the commit-workflow-runs query returned no runs. Therefore these tests are not verified as compiled or passing.

## Blockers and next evidence required

1. **Lifecycle race — OPEN / HIGH PRIORITY.** Serialize callback dispatch with shutdown or use an equivalent generation/active-token design, then add deterministic regression tests for callback-vs-unregister, stale capabilities after network replacement, and monitor re-registration.
2. **Independent leak verifier — BLOCKED.** The gate accepts a report; it does not independently establish IPv4, IPv6, DNS, lockdown, or per-app routing. Keep any leak-proof claim blocked until real measurements and controlled packet-path tests exist.
3. **Build execution — NOT VERIFIED.** No successful workflow run, Gradle test output, native build output, APK, signature check, or device smoke-test evidence was found in the queried commit status/workflow-run endpoints.
4. **Bootstrap integrity — NEEDS REVIEW.** The dependency-fetch script downloads a JDK archive and the rustup installer without verifying a pinned checksum/signature in that script. The container workflow has separate pinned inputs, but the standalone bootstrap path must be reconciled with the reproducibility requirements before it is treated as trusted.
5. **Acceptance matrix — INCOMPLETE.** Privacy, secret scanning, manifest/backup review, device tests, performance measurements, and final artifact provenance still need explicit evidence.

## Gate decision

- Build gate: **BLOCKED / NOT VERIFIED**
- Lifecycle gate: **BLOCKED**
- Leak-verification gate: **BLOCKED**
- APK/release gate: **NOT RUN**
- UI/UX preservation: **no UI files were changed in this audit**
