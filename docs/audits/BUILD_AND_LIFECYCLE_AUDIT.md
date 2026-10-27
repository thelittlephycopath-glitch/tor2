# GTor execution evidence — build and lifecycle audit

Date: 2026-10-28
Branch reviewed: `dangerous-experiment`
Latest reviewed source fix: `f3223ae686cfa9148529f48273717234ba689ec0`

This is an evidence ledger, not a release approval. No build or runtime pass is claimed here.

## Verified from repository source

- `.github/workflows/build-apk.yml` exists and is manually dispatched. The build job is skipped unless `run_apk_build` is explicitly true. Release publication has a separate false-by-default input.
- The workflow pins checkout, cache, and artifact actions to immutable commit SHAs.
- The workflow runs `bash -n` and `scripts/test_ci_security.sh` / `scripts/verify_ci_security.sh` before building.
- The reproducible build script runs Onionmasq native/Android builds, then `testReleaseUnitTest`, then `assembleRelease`; it optionally builds an AAB and runs APK alignment verification.
- `LeakVerificationGate` checks timestamp freshness and five caller-supplied booleans, but explicitly does not measure the network itself. Its comments require a separate independent verifier.
- `AndroidEnvironmentMonitor` now serializes callback side effects with `unregister()` using `callbackDispatchLock` and rechecks registration/network identity before dispatch. Callback transitions and initial environment publication are now serialized with shutdown. This is a source-level fix; compilation and Android callback-scheduling tests remain outstanding.
- `AutonomyLifecycleOrderingTest` adds three stale-evidence ordering tests. The repository status endpoint returned no CI status entries for the reviewed commit, and the commit-workflow-runs query returned no runs. Therefore these tests are not verified as compiled or passing.

- A follow-up source inspection found an invalid named argument in `AutonomyLifecycleOrderingTest`: one call supplied `coordinator` to a helper that accepts only `checkedAt` and `generation`. An earlier recorded fix was not present in the fetched branch file; the exact invalid argument has now been removed and committed as `b79781917134488bfa8cc55e18d1c80e34cedfd4`. This is a source-level correction only; no test run or compilation result is available yet.
- Added `docs/research/FREE_NETWORK_AND_VPN_FEATURE_RESEARCH.md` to capture official Tor relay-safety/capacity guidance and a feature benchmark for paid VPN capabilities. This is research only; no new IP network is implemented.
- A second test audit found `NetworkTrustSessionGenerationTest` contradicted the coordinator's fail-closed contract: a test expected tokenless trust evidence to be accepted even though `updateNetworkTrust` requires the current network-generation token. Updated the test to expect `UNKNOWN` for tokenless evidence and to capture the network-generation token in the stale-evidence case (`05225fb77aace26ebdd2d6a2716efa7d28c9692e`). This aligns assertions with the existing security contract; the test has not been executed yet.

- A follow-up bootstrap hardening commit (`f3223ae686cfa9148529f48273717234ba689ec0`) enables strict Bash error handling, resolves the repository root safely for paths containing spaces, quotes Android SDK/NDK paths, uses fail-on-HTTP-error HTTPS downloads, and checks for the actual rustup executable rather than only a directory. This improves failure behavior and shell robustness; it does **not** resolve missing pinned integrity verification for the standalone JDK/rustup downloads, and no shell or build execution has yet been recorded for this commit.

- A second bootstrap follow-up (`2f43f6ba22daf0cef590f99d0489844714856143`) now rejects unsupported host OS/architectures and verifies that the selected Java 21 executable exists before proceeding. This closes a misleading-success path in which the script could print “Java 21 OK!” without a usable executable. It remains source-reviewed only; execution is still unverified.

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
