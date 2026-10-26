# Tor VPN Beta 2 — Autonomous Backend Status

Last updated: 2026-10-26  
Working branch: `dangerous-experiment`  
Integration rule: keep changes additive; do not change the existing UI/UX; do not merge this branch into `main` until build and verification gates pass.

## Verification status

- [x] Existing VPN lifecycle and network/resource observations feed an in-memory, bounded autonomy coordinator.
- [x] Tunnel readiness alone is not considered sufficient for traffic release.
- [x] Bridge outcome learning is bounded and advisory; it does not change the active bridge or route.
- [x] Network/transport changes invalidate prior verification and reset session-specific bridge learning.
- [x] Network trust evidence requires a fresh monotonic timestamp; stale/future evidence is classified as unknown.
- [x] Recovery planning has bounded exponential backoff, single-pending-attempt protection, explicit failure triggers, and a fail-closed outcome.
- [x] Leak-verification reports require fresh evidence for IPv4 routing, IPv6 containment, DNS protection, system lockdown, and app routing.
- [x] Raw `LEAK_CHECK_PASSED` events cannot authorize the coordinator without a validated evidence report.
- [x] The release workflow now uses read-only repository permissions for the build job and grants release-write permission only to a main-branch publish job.
- [x] The reproducible build script runs release unit tests before producing the release APK.

## Deliberately not claimed as complete

The report gate is a contract for a separate verifier; the read-only Android probe checks exposed route/DNS/lockdown settings but does **not** run end-to-end DNS or IPv6 leak probes, firewall enforcement tests, or per-app routing verification. The recovery and circuit-refresh planners are advisory; they do **not** enqueue reconnection, restart the VPN service, choose a different bridge, or rotate circuits. Those integrations remain blocked until they can be wired to real measurements and tested without changing the original UI.

The current Android environment monitor reads default-network validation, captive-portal state, and cellular transport. It does not yet establish that a network is public/trusted, detect DNS hijacking, detect TLS interception, or learn per-network history. These must not be inferred from the current signals.

## Phase checklist from the backend specification

### Phase 1 — Autonomous Safety Core
- [x] Existing foreground VPN service and boot-time start mechanism preserved.
- [x] Additive policy/event/bridge-health model and bounded event journal.
- [x] Conservative network-trust classifier and evidence freshness checks.
- [x] Read-only Android probe for VPN default routes, Tor DNS addresses, and reported always-on/lockdown settings (per-app routing remains unverified).
- [ ] Live end-to-end leak verifier covering IPv4, IPv6, DNS, app routing, and system lockdown.
- [ ] Runtime kill-switch/lockdown confirmation on a supported Android device.
- [ ] Full Android build, unit tests, lint, and emulator smoke test.

### Phase 2 — Autonomous Tor Engine
- [x] Observe outcomes for configured Snowflake/obfs4 startup attempts.
- [x] Rank bridge candidates from bounded recent outcomes.
- [ ] Wire ranked candidate selection into Tor/Onionmasq using supported APIs.
- [ ] Execute failover through a single serialized service lifecycle.
- [x] Pure advisory circuit-refresh planner with fresh telemetry checks, bounded thresholds, and cooldown.
- [ ] Circuit-refresh execution using a supported Tor/Onionmasq control interface.
- [ ] Live routing and no-direct-fallback tests.

### Phase 3 — Autonomous Censorship Intelligence
- [x] Detect OS-reported captive-portal state and validated-network transitions.
- [x] Forget bridge outcome history on network/transport changes.
- [ ] Persist privacy-minimized network fingerprints.
- [ ] Add real DNS anomaly/TLS interception signals with explicit false-positive handling.
- [ ] Implement portal completion without bypassing the platform's captive-portal flow.
- [ ] Validate on real Wi-Fi and cellular networks.

### Phase 4 — Ghost Mesh / traffic-analysis resistance
- [ ] Cover-traffic feasibility review, consent and resource-budget tests.
- [ ] Supported packet-padding/timing-jitter implementation (not an application-layer substitute for Tor transport behavior).
- [ ] Circuit splitting and mixnet deferred pending protocol-level design and feasibility proof.
- [ ] No cover traffic may go to unwilling third-party destinations.

### Phase 5 — Decoy data, poisoning, honeyvault and canaries
- [ ] Not implemented. Android sandboxing limits cross-app data modification; no invasive permission workarounds are planned.
- [ ] Any future canary component must be local, opt-in, isolated, non-destructive, and unable to access the real LAN.
- [ ] No fake identities/accounts or destructive automatic “burn” actions.

### Phase 6 — Advanced research
- [ ] Local ML, cross-device cover traffic, mixnets, post-quantum hybrid modes, and behavioral morphing are not implemented.
- [ ] These remain research proposals until the simpler safety and verification layers are working and measured.

## Build blocker

GitHub Actions is triggering for pushes but the latest inspected jobs terminate before any workflow step starts: the job has an empty step list, no runner name, and no log output. This is an infrastructure/runner-startup blocker, not evidence that the Kotlin source compiles or fails to compile. A local Kotlin smoke harness passed 24 recovery-planning assertions and 7 leak-gate assertions, but those are isolated harnesses, not the repository's Gradle test suite.

No successful Android APK build, APK signature verification, or emulator smoke test has been verified yet. Do not publish or merge the branch as build-verified until these checks actually pass.
