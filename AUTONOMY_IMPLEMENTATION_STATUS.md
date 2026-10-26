# Tor VPN Beta 2 — Autonomous Backend Status

Last updated: 2026-10-27  
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
- [x] One coordinator now exposes trust classification, bounded reconnect planning, circuit-refresh recommendations, and cover-traffic budget decisions as advisory APIs.
- [x] A separate allow-list gates proposed actions by explicit config flags; observation-only mode remains non-executing, and every action without a real executor (including reconnect, bridge failover, circuit refresh, active leak-check triggering, portal bypass, notification delivery, and traffic restoration) remains suppressed regardless of feature flags. Fail-closed recommendations remain the only always-approved policy items.
- [x] The release workflow uses read-only repository permissions for its build job and grants release-write permission only to the main-branch publish job.
- [x] The signed APK artifact upload is ordered after unit tests, lint, and emulator smoke testing, so failed verification does not produce the signed download artifact.
- [x] The reproducible build script runs release unit tests before producing the release APK.

## Deliberately not claimed as complete

The report gate is a contract for a separate verifier; the read-only Android probe checks exposed route/DNS/lockdown settings but does **not** run end-to-end DNS or IPv6 leak probes, firewall enforcement tests, or per-app routing verification. The recovery, circuit-refresh, and cover-traffic planners and the action gate are advisory; they do **not** enqueue reconnection, restart the VPN service, choose a different bridge, rotate circuits, launch leak-check commands, send notifications, allow captive-portal bypass, restore tunneled traffic, or generate packets. Those integrations remain blocked until they can be wired to real measurements and tested without changing the original UI.

The current Android environment monitor reads default-network validation, captive-portal state, and cellular transport. The HMAC utility and Android Keystore provider do not collect identifiers. An isolated storage adapter now persists keyed digests and timestamps only, explicitly excludes its preference file from cloud backup and device transfer, and is not wired to collection or a user-facing consent setting. Public/trusted-network evidence and real DNS/TLS anomaly signals remain unimplemented. The Android Keystore provider has not yet been validated by an Android build or device test. None should be inferred from the current signals.

## Phase checklist from the backend specification

### Phase 1 — Autonomous Safety Core
- [x] Existing foreground VPN service and boot-time start mechanism preserved.
- [x] Additive policy/event/bridge-health model and bounded event journal.
- [x] Conservative network-trust classifier and evidence freshness checks.
- [x] Read-only Android probe records VPN-route/DNS/always-on settings as diagnostic observations only; it supplies no enforcement-verification evidence.
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
- [x] HMAC-SHA-256 fingerprint derivation uses a per-install secret, domain separation, length-prefixed fields, BSSID/security-type normalization, exact SSID preservation, and no raw identifier output.
- [x] Add isolated Android Keystore generation/readback for a per-install non-exportable HMAC key.
- [x] Add isolated private-app storage for keyed digests only, requiring explicit opt-in on every read/write, bounded capacity, bounded retention, corruption filtering, and deletion.
- [ ] Wire consent into a user-controlled setting and connect key management/storage to the live network-learning path only after UI and Android-device verification.
- [ ] Add real DNS anomaly/TLS interception signals with explicit false-positive handling.
- [ ] Implement portal completion without bypassing the platform's captive-portal flow.
- [ ] Validate on real Wi-Fi and cellular networks.

### Phase 4 — Ghost Mesh / traffic-analysis resistance
- [x] Pure cover-traffic budget planner with explicit opt-in, user-controlled target gate, battery/data caps, and conservative cellular limits.
- [ ] Live cover-traffic feasibility review and platform integration; no traffic is generated by the planner.
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

GitHub Actions is triggering for pushes but the latest inspected jobs terminate before any workflow step starts: the job has an empty step list, no runner name, and no log output. Static review corrected invalid Android SDK package identifiers in both the `Containerfile` and `scripts/reproducible_fetch_deps.sh`; API 37 is now requested through `--channel=3`, while API 36 and its Build Tools come from stable. The standalone setup now retries package installation when a partial SDK already exists and explicitly pins the SDK root for the NDK install. The revised installer section passes `bash -n` and five static assertions for package IDs, channels, SDK-root selection, and repair of partial installs; this does not verify downloads or the Android build. These changes still need an actual successful container build. This is an infrastructure/runner-startup blocker, not evidence that the Kotlin source compiles or fails to compile. Isolated local Kotlin smoke harnesses have passed 24 recovery-planning assertions, 7 leak-gate assertions, 14 circuit-refresh assertions, 15 cover-traffic budget assertions, 12 fingerprint-hasher assertions using both byte-array and SecretKey constructors, and 8 action-gate assertions confirming unimplemented commands remain suppressed even with feature flags enabled. A standalone local Kotlin harness passed 14 assertions for the opt-in storage logic, including out-of-order timestamps, non-regressing duplicate records, and opt-out deletion before input validation. These harnesses are not the repository's Gradle test suite; the new JUnit storage tests remain unexecuted by Gradle. These are manually compiled standalone harnesses, not the repository's Gradle test suite.

A separate minimal runner-diagnostic workflow was tried with both `ubuntu-latest` and the explicit `ubuntu-24.04` label. Both failed with zero steps, `runner_id: 0`, an empty runner name, no job logs URL, and no artifacts. This rules out the runner-label alias as the simple cause; the failure is still before workflow commands, and the exact account/repository billing or Actions-policy cause cannot be identified through the available repository APIs.

No successful Android APK build, APK signature verification, or emulator smoke test has been verified yet. Do not publish or merge the branch as build-verified until these checks actually pass.
