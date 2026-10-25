# Tor VPN Beta — Security, Stability, and Performance Plan

> **Status (updated 2026-10-25):** Stabilization and CI recovery are in progress on GitHub. The current main branch has recent automated validation failures/cancellations; no verified installable APK or device-test evidence is recorded here. Do not treat the app as production-ready.

## Goals

- Improve lifecycle correctness and reliability without weakening Tor's privacy properties.
- Reduce unnecessary sensitive diagnostics while retaining useful troubleshooting.
- Establish repeatable build, test, and performance evidence before release.
- Keep all work on `security/performance-baseline`; do not modify `main` directly.
- Never claim that a VPN makes a user untraceable or guarantees protection from a state-level adversary.

## Current evidence and limits

- The active repository is `thelittlephycopath-glitch/tor2`; GitHub Actions workflows are present under `.github/workflows/`, including `build-apk.yml`, `diagnostic-build.yml`, and `validate-pr.yml`.
- Recent GitHub Actions validation attempts on 2026-10-25 include failures and cancellations. The latest observed main commit during this plan update is `c4bf29deefb56a46c8c57a40de1b547f710cefd2` (`style: sort AppListItem imports for ktlint`). Job-log retrieval returned no stored log payload for the sampled failed run, so the exact current failure must be recovered from a fresh run or GitHub Actions UI before changing code.
- `settings.gradle.kts` and the build workflow must be treated as the source of truth for Onionmasq dependency setup and the pinned Android/Rust toolchain. Confirm these in CI rather than assuming a local cache makes a clean build reproducible.
- Build, validation, and diagnostic workflows can overlap. Prefer one authoritative validation run per commit; avoid rapid speculative commits while a run is queued or building, because superseded runs can be cancelled and obscure the first real failure.
- Keep validation builds separate from release publishing/signing. Development signing is not a production signing key.
- Existing README documentation explicitly describes the app as experimental and warns that it may leak information. Keep that warning until tests justify a change.

## Findings under review

### 1. VPN startup and descriptor ownership

**Observed:** `TorVpnService.establishVpn()` calls `Builder.establish()`, then detaches the TUN descriptor inside a coroutine. A completion handler attempts to close the Android `ParcelFileDescriptor` when it is not already closed.

**Important distinction:** The `CountDownLatch` is decremented at the beginning of the coroutine, before `OnionMasq.start()` is called. The 15-second wait therefore confirms that the coroutine began, not that the native tunnel is ready.

**Required verification:**
- Confirm the exact native `OnionMasq.start()` blocking/return contract and ownership of the detached file descriptor on success, exception, and cancellation.
- Test cancellation before coroutine execution, cancellation after descriptor detach, native-start failure, and service stop during startup.
- Confirm a failed or revoked VPN cannot leave a stale interface or falsely report `CONNECTED`.
- Prefer the existing bootstrap/status event as readiness evidence if its semantics support that; avoid blocking the Android main thread.

**Do not change descriptor handling again based only on assumptions.** These changes remain uncompiled and require targeted tests.

### 2. Start/stop lifecycle

Prior source-review commits recreated the startup coroutine scope and `TaskDebouncer` after stop, and made the usage timer recreatable. These are plausible fixes, not verified lifecycle guarantees.

Required tests:
- start → stop → start on the same service instance;
- duplicate and rapidly alternating start/stop intents;
- stop during startup and native-start failure;
- process death, service recreation, reboot, and VPN permission revocation;
- observer and timer cleanup without duplicate registrations or background work.

### 3. Sensitive connection logs

**Observed:** `createObservers()` formats connection events containing proxy endpoints, Tor destination metadata, app IDs, and circuit relay addresses/identities. Some events are added as verbose logs.

Before modifying logging, trace `LogObservable` and `LogHelper` in the Onionmasq dependency to establish storage location, retention, export behavior, and access controls.

Targets:
- avoid logging browsing destinations, app identifiers, endpoints, or relay identifiers in routine diagnostics unless demonstrably necessary;
- make detailed diagnostics opt-in where feasible;
- do not log credentials or secret bridge configuration;
- verify logs are not unintentionally backed up, exported, or retained beyond the intended lifecycle.

### 4. Routing, DNS, and network changes

**Observed:** the VPN profile installs default IPv4 and IPv6 routes, adds internal DNS addresses `169.254.42.53` and `fe80::53`, allows both address families, and sets MTU 1500.

Do not change routes, DNS, MTU, or fallback behavior without repeatable device tests. Verify:
- DNS and private-DNS handling;
- IPv4 and IPv6 traffic, including IPv6-only and dual-stack networks;
- Wi-Fi ↔ mobile handover and temporary network loss;
- traffic behavior during disconnect, restart, and permission revocation;
- Android always-on VPN and lockdown behavior.

### 5. Permissions and platform configuration

The manifest declares `QUERY_ALL_PACKAGES`, boot reception, network, foreground-service, notification, and wake-lock permissions. Review each against the app-routing, always-on VPN, and current Android platform requirements. Remove nothing until feature dependencies and Android behavior are confirmed.

### 6. Build and release integrity

- Run the repository's existing lint and unit-test jobs.
- Establish a clean staging/debug build with the correct Onionmasq dependency.
- Inspect dependency versions and workflow action pinning.
- Keep development signing separate from release signing.
- Verify APK alignment, package signature, supported ABIs, installability, and SHA-256 checksum.
- Record the exact source commit and test evidence for every artifact.
- Do not publish an APK or mark a release ready when tests fail or have not run.

## Performance baseline

Measure on the same device and network before and after each optimization:
- time to service start and time to Tor bootstrap readiness;
- reconnect time after network handover or temporary loss;
- sustained throughput and latency;
- CPU, memory, battery, and wakeup activity;
- DNS, IPv4, IPv6, and disconnect leak-test results.

Optimize measured bottlenecks one at a time. Do not weaken encryption, route traffic outside the intended tunnel, or trade privacy for benchmark gains.

## Release acceptance checklist

- [ ] Clean build succeeds from a documented environment.
- [ ] Lint and unit tests pass; integration/device tests are recorded.
- [ ] Descriptor ownership and start/stop races are verified.
- [ ] DNS, IPv4, IPv6, handover, disconnect, and lockdown tests pass.
- [ ] Sensitive logging, storage, backup, export, and permissions are reviewed.
- [ ] Performance results are measured and compared with baseline.
- [ ] Artifact ABI, signature, alignment, checksum, and source revision are documented.
- [ ] Remaining high-severity issues and limitations are disclosed.
- [ ] No claim of anonymity, leak-proof behavior, or untraceability exceeds the test evidence.

## Iteration log

### 2026-10-22 — Lifecycle source-review changes

- Added an explicit null check for the result of `Builder.establish()` (commit `25bf5fe0cd2ad39c1794069ba0d5061d105beb80`).
- Recreated the startup coroutine scope and usage timer after stop (commit `87550e8ff92ad2e9b255d0fe1363614b43ff724a`).
- Recreated `TaskDebouncer` after stop because its cancellation is terminal (commit `b73c540b1d3c6a767cc9d33d1e476e68c0d5671c`).
- Added non-detached descriptor cleanup and coroutine-completion cleanup attempts (commits `f7ff764a8ab37b5c1c9d10f6982e2b406fbc2f8c` and `1a1a66fb27f97a64d67436f3a3782b47e73d02df`).
- Documented that the startup latch reports coroutine scheduling, not native tunnel readiness (commit `b67143164ba62ad9ed22bf5a4fc428784c510bc2`).

**Verification status:** source review only. These commits have not been compiled or validated on a device in this session. GitHub returned no status checks for the descriptor-cleanup commit; GitLab CI status still needs separate confirmation. Descriptor ownership, native startup behavior, lifecycle races, routing, leaks, and logging retention remain open verification items.

### 2026-10-22 — Task queue concurrency review

- Fixed a newest-per-type race in `TaskDebouncer.enqueueTask()`: concurrent producers now replace the map entry atomically, and a producer only queues its item if its sequence is still current.
- Changed the consumer's ordinary-failure handler to catch and log `Exception` rather than swallowing every `Throwable`. Fatal JVM errors are no longer deliberately consumed by this handler.
- These are source-level fixes only. The regression test file is now present, but the tests have not been compiled or executed in this session. Run the existing GitLab lint/test jobs before treating the fix as verified.
- Added `app/src/test/java/org/torproject/vpn/utils/TaskDebouncerTest.kt` with regression coverage for newest-pending-task behavior, concurrent producers while the consumer is occupied, and cancellation/rejection after cancellation.
- The concurrency test uses a blocking task to hold the consumer while producers enqueue, making the newest-per-type assertion less dependent on machine timing. The tests are committed but have **not yet been executed** because this environment has no project checkout, Gradle wrapper runtime, Android SDK dependency cache, or Onionmasq artifact configured.



## Upgraded execution roadmap — build first, then harden

This roadmap extends the existing stabilization work; it is **not** a restart or redesign. Preserve the current app architecture and existing UI/UX exactly unless the user explicitly authorizes a UI/UX change. Do not add features or perform broad refactors while the build is red.

### Phase 0 — Establish one trustworthy baseline

1. Record the current `main` SHA and inspect the exact workflow/job/step that failed.
2. Trigger or use one validation run for that SHA; do not make unrelated commits while it is running.
3. Capture the first actionable error (not just the final Gradle failure summary), toolchain versions, dependency resolution output, and failing task.
4. Classify the failure as dependency/toolchain, Kotlin/Compose compilation, Rust/Onionmasq compilation, unit test, lint, packaging, signing, or workflow infrastructure.
5. Fix the earliest root cause only, commit a narrowly scoped change, and rerun the same validation workflow.

**Exit gate:** a fresh run reaches the actual compile tasks and reports a deterministic result. A cancelled run or missing log is not a pass.

### Phase 1 — Produce a verifiable debug/staging APK

1. Build from a clean runner using the repository's pinned Android SDK, JDK, Gradle, Rust, and Onionmasq revisions.
2. Confirm the correct app variant and supported ABI set; avoid changing SDK/dependency versions speculatively.
3. Run Android packaging, then verify the APK exists, is non-empty, has the expected package/application ID, and passes `apksigner verify` (or the equivalent workflow check).
4. Record SHA-256, commit SHA, variant, ABI, signing type, and artifact expiry. Clearly label debug/staging signing; never imply it is a production release key.
5. Download the actual workflow artifact and confirm it can be accessed. A successful workflow without a published APK artifact does not satisfy this phase.

**Exit gate:** one downloadable, signature-verified APK artifact tied to a known commit.

### Phase 2 — Restore automated quality gates

Run and fix in this order, stopping at the first failure:
1. Kotlin/Compose compilation and Rust/Onionmasq compilation.
2. Unit tests, including `TaskDebouncerTest` concurrency and cancellation regressions.
3. ktlint and Android lint.
4. Instrumented/UI tests where the configured runner supports them.
5. Rebuild the staging APK after all fixes.

Do not suppress tests, weaken lint rules, or delete failing assertions simply to obtain green CI. Add a regression test when a defect is reproducible and the existing test framework supports it.

**Exit gate:** a single commit has green build, unit-test, and lint results, plus a verified staging artifact.

### Phase 3 — VPN lifecycle and privacy verification

Prioritize the high-risk boundaries already identified above:
- Establish the real native Onionmasq startup/readiness contract; replace any “coroutine started” inference with a verified readiness signal.
- Prove TUN descriptor ownership and cleanup on success, exception, cancellation, and service stop.
- Test start → stop → start, duplicate commands, stop-during-start, native-start failure, service recreation, permission revocation, and process death.
- Validate DNS, IPv4/IPv6, handover, temporary network loss, disconnect, always-on VPN, and lockdown on physical devices or a documented emulator/network test setup.
- Trace diagnostic logging through the actual Onionmasq implementation and confirm retention, export, and backup behavior before reducing or changing logs.
- Review manifest permissions against actual features; remove permissions only when behavior and tests prove they are unnecessary.

**Exit gate:** recorded test results and no unresolved critical/high-severity lifecycle or traffic-leak issue. If device testing is unavailable, explicitly mark the corresponding items unverified.

### Phase 4 — Measure performance before optimizing

Record baseline device model, Android version, network type, Tor bootstrap time, reconnect time, throughput, latency, CPU, memory, battery, and wakeups. Repeat measurements under the same conditions after each isolated change. Prefer profiling evidence over guessed optimizations. Never bypass Tor, weaken encryption, leak traffic outside the tunnel, or change routing merely to improve a benchmark.

**Exit gate:** measurable comparison against baseline with no regression in privacy, correctness, or reliability.

### Phase 5 — Release readiness

- [ ] Build/test/lint pass on one exact commit.
- [ ] APK signature and checksum verified; package ID, version, and ABI confirmed.
- [ ] Artifact download link works for the user.
- [ ] Device-level lifecycle and network checks are recorded.
- [ ] Known limitations, especially any untested leak/lockdown scenarios, are disclosed.
- [ ] Release signing credentials are managed separately and never committed.
- [ ] Existing UI/UX remains unchanged unless explicitly authorized.
- [ ] Experimental status and privacy claims match the available evidence.

### Operating rules for each loop

1. Inspect → identify the first root cause → patch the smallest surface → commit → wait for that commit's CI → inspect results.
2. Do not stack dependency upgrades on top of unresolved dependency or compilation errors without confirming the API/version mapping.
3. Do not treat “queued,” “in progress,” “cancelled,” a missing log, or source inspection as a successful test.
4. Do not merge additional experimental changes while the current validation run is still the only evidence.
5. Keep a dated iteration note with commit SHA, failing task, exact cause, patch, and post-fix result.
6. If CI fails before compilation due to runner/dependency infrastructure, fix or isolate that infrastructure issue before editing app source.
