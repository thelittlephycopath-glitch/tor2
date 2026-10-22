# Tor VPN Beta — Security, Stability, and Performance Plan

> **Status:** Source review in progress. The working branch has not been compiled or device-tested in this review session. Do not treat it as production-ready.

## Goals

- Improve lifecycle correctness and reliability without weakening Tor's privacy properties.
- Reduce unnecessary sensitive diagnostics while retaining useful troubleshooting.
- Establish repeatable build, test, and performance evidence before release.
- Keep all work on `security/performance-baseline`; do not modify `main` directly.
- Never claim that a VPN makes a user untraceable or guarantees protection from a state-level adversary.

## Current evidence and limits

- The repository contains a GitLab CI pipeline in `.gitlab-ci.yml`. It defines lint, staging assembly, unit-test, integration-test, and conditional publish jobs.
- GitHub status lookup for the latest descriptor-cleanup commit returned no status checks. This does **not** establish whether GitLab CI has run; the GitLab pipeline must be checked separately.
- `settings.gradle.kts` requires access to the Tor Project Onionmasq Maven artifact or a locally published equivalent. A clean build needs the documented dependency setup.
- The reproducible-build script builds Onionmasq from a pinned revision, then builds an unsigned release APK (and, by default, an AAB). Do not run a publishing or signing workflow as a substitute for a safe validation build.
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
