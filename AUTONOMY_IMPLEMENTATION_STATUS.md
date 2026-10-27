# Tor VPN Beta 2 — Autonomous Backend Status

Last updated: 2026-10-27  
Working branch: `dangerous-experiment`  
Integration rule: keep changes additive; do not change the existing UI/UX; do not merge this branch into `main` until build and verification gates pass.

## Verification status

- [x] Existing VPN lifecycle and network/resource observations feed an in-memory, bounded autonomy coordinator.
- [x] Android resource monitoring now records battery percentage plus charging-state availability; monitor loss clears both readings so stale charging telemetry cannot be reused.
- [x] Tunnel readiness alone is not considered sufficient for traffic release.
- [x] Bridge outcome learning is bounded and advisory; it does not change the active bridge or route.
- [x] Network/transport changes invalidate prior verification and reset session-specific bridge learning.
- [x] Network trust evidence requires a fresh monotonic timestamp; stale/future evidence is classified as unknown.
- [x] Recovery planning has bounded exponential backoff, single-pending-attempt protection, explicit failure triggers, and a fail-closed outcome.
- [x] Leak-verification reports require fresh evidence for IPv4 routing, IPv6 containment, DNS protection, system lockdown, and app routing.
- [x] Raw `LEAK_CHECK_PASSED` events cannot authorize the coordinator without a validated evidence report.
- [x] Starting a new leak check invalidates any prior pass but preserves an active failure until a fresh, complete, safe report passes; a regression test was added, but the repository Gradle suite remains unexecuted.
- [x] A failed leak report now invalidates tunnel readiness, keeps the failure latched through reconnect/network events, requests bounded recovery while the tunnel is down, and only clears after a fresh safe report; regression tests were added, but Gradle execution is pending.
- [x] The policy continues recommending a new leak check after tunnel readiness even when a prior check failed; a regression test was added, but Gradle execution is pending.
- [x] A prior verified session invalidated by network/hostile-state changes retains a recovery trigger until successful verification, while a newly observed idle network does not trigger reconnect by itself; regression coverage was added, but Gradle execution is pending.
- [x] Opt-out deletion now runs before validating an unused read-clock value, so an invalid clock cannot prevent a requested erase; a regression test was added, but Gradle execution remains pending.
- [x] Corrected a malformed literal `\\n` sequence in the existing service's coordinator declaration that would prevent Kotlin parsing; nearby autonomy/service source was scanned for the same escaped-newline pattern. Full compiler verification is still pending.
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
- [x] Charging-related cover-traffic budget boosts now require both a positive charging reading and `charging=true`; unknown/stale status cannot receive the boost. Regression coverage was added; Gradle execution remains pending.
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

GitHub Actions is triggering for pushes but the latest inspected jobs terminate before any workflow step starts: the job has an empty step list, no runner name, and no log output. Static review corrected invalid Android SDK package identifiers in both the `Containerfile` and `scripts/reproducible_fetch_deps.sh`; API 37 is now requested through `--channel=3`, while API 36 and its Build Tools come from stable. The standalone setup now retries package installation when a partial SDK already exists and explicitly pins the SDK root for the NDK install. The revised installer section passes `bash -n` and five static assertions for package IDs, channels, SDK-root selection, and repair of partial installs; this does not verify downloads or the Android build. These changes still need an actual successful container build. This is an infrastructure/runner-startup blocker, not evidence that the Kotlin source compiles or fails to compile. Isolated local Kotlin smoke harnesses have passed 24 recovery-planning assertions, 7 leak-gate assertions, 14 circuit-refresh assertions, 15 cover-traffic budget assertions, 12 fingerprint-hasher assertions using both byte-array and SecretKey constructors, and 8 action-gate assertions confirming unimplemented commands remain suppressed even with feature flags enabled. A standalone local Kotlin harness passed 18 assertions for the opt-in storage logic, including out-of-order timestamps, non-regressing duplicate records, opt-out deletion before input validation, expiry/future/malformed-record cleanup, and oversized-payload rejection. These harnesses are not the repository's Gradle test suite; the new JUnit storage tests remain unexecuted by Gradle. These are manually compiled standalone harnesses, not the repository's Gradle test suite.

A separate minimal runner-diagnostic workflow was tried with `ubuntu-latest`, explicit `ubuntu-24.04`, and then `ubuntu-22.04`; the APK workflow also failed on `ubuntu-latest` during the same push. The newest runs again failed in about two seconds with zero steps, `runner_id: 0`, an empty runner name, no job logs URL, and no artifacts. Changing the hosted image therefore did not fix runner assignment. This rules out a single runner-label/image alias as the simple cause; the failure is still before workflow commands, and the exact account/repository billing or Actions-policy cause cannot be identified through the available repository APIs. The next required check is in GitHub's repository/account Actions settings and usage/billing controls; changing app source or the Dockerfile cannot repair a job that never receives a runner.

No successful Android APK build, APK signature verification, or emulator smoke test has been verified yet. Do not publish or merge the branch as build-verified until these checks actually pass.

## Latest build-blocker investigation — 2026-10-27

- [x] Corrected the Kotlin Gradle DSL placement of `jvmToolchain(21)`: it now sits directly inside `kotlin { }`, not inside `compilerOptions { }`. Commit: `757f07b23f41101186ee4e3e76cd1dacafac0718`.
- [x] Re-ran the APK workflow after that correction. It still failed before executing any step.
- [x] Checked a separate runner-diagnostic workflow using a Windows hosted runner as a cross-platform control. That also failed with zero steps; the job summaries expose no runner assignment or logs. This makes a Linux-only image issue less likely, but does not by itself prove whether the cause is account/repository policy or GitHub's hosted-runner service.
- [ ] Restore hosted-runner execution before any Gradle compile/test, signing, or emulator result can be obtained.
- [ ] Do not mark an APK as produced until a real artifact exists and signature/build checks pass.

## Source-level safety audit — 2026-10-27

- [x] The coordinator now retains a recovery request when a previously ready/verified session is invalidated by network or hostile-state changes. Once connectivity returns, it can recommend bounded recovery; a first-time idle network does not create a retry loop.
- [x] A leak-check failure now invalidates tunnel readiness as well as traffic-release eligibility. The prior failure remains latched across tunnel start/ready, network changes, and monitor events; only a new complete, safe leak report clears it.
- [x] Recovery planning waits while tunnel startup/bootstrap is in progress and resumes only after a ready/failed/network-transition result, preventing concurrent retry recommendations. Regression coverage was added; Gradle execution remains pending.
- [x] The policy recommends another leak check after a tunnel is ready even when the previous check failed. The recovery plan avoids asking for another reconnect while a ready tunnel is waiting for its next check.
- [x] The evidence submission path rejects the coordinator's explicit `HOSTILE` trust classification, even if a caller supplies a set of otherwise-positive leak-check fields.
- [x] The coordinator stores the monotonic timestamp of a successful leak report and invalidates traffic-release eligibility when the report becomes stale or future-dated; it requests a fresh check without treating staleness alone as proof of a leak. Regression coverage was added; Gradle execution remains pending.
- [x] The coordinator uses the injected leak-verification gate's configured maximum report age, not a separate hard-coded default, for expiry. Regression coverage was added; Gradle execution remains pending.
- [x] Network-trust evidence is retained only in memory and reclassified against its monotonic timestamp on reads/decision refresh; trusted evidence ages to `UNKNOWN` rather than remaining trusted indefinitely. Regression coverage was added; Gradle execution remains pending.
- [x] Stale/future network-trust evidence is not cached; a future-dated item cannot become trusted merely because the clock advances. Regression coverage was added; Gradle execution remains pending.
- [x] Cached network-trust evidence is discarded on network loss/restoration, transport changes, and monitor failure so a previous network's trusted label cannot transfer to a new path. Regression coverage was added; Gradle execution remains pending.
- [x] The Android monitor now invalidates state on every active default-network identity transition, including transitions while validation is pending, even if its network/cellular booleans would otherwise remain unchanged. This callback path still needs Android-device verification.
- [x] The opt-in learning store now prunes expired/malformed records when a sample is rejected as stale/future-dated or has an invalid digest/observation timestamp, rather than leaving expired records untouched until the next successful write. Regression coverage was added; Gradle execution remains pending.
- [x] The `dangerous-experiment` push trigger was removed from the APK workflow so backend-only work does not launch APK builds while APK work is paused; the `main` trigger and manual dispatch remain unchanged.
- [x] The runner-diagnostic workflow was changed to manual dispatch only so experimental source commits do not repeatedly queue known-failing runner diagnostics.
- [x] Added 13 regression tests across `AutonomyCoordinatorTest.kt`, `AutonomousPolicyEngineTest.kt`, `OptInNetworkLearningStoreTest.kt`, and `CoverTrafficPolicyPlannerTest.kt` covering recovery after network changes, startup retry serialization, hostile/future/stale trust evidence, leak-report expiry and retry, custom freshness limits, retention cleanup, and unknown charging state.
- [ ] Execute the repository Gradle test suite and Android compilation. The hosted runner has previously failed before any steps begin; these newly added tests have not yet been executed.
- [ ] Recheck the final workflow behavior through GitHub Actions after runner access is restored; the workflow-trigger edit is statically verified from the file contents only.
- [ ] Perform real Android device/emulator validation of VPN lock-down, DNS/IPv6, per-app routing, and actual no-direct-fallback behavior. The current read-only probe is not a substitute for these tests.


No UI/UX changes were made. No verified APK artifact is available from these failed runs.


## Follow-up work — 2026-10-27

- [x] Fixed a recovery lifecycle edge case: explicit tunnel/service stop now clears the recovery planner's pending attempt and retry budget, so stale scheduled work cannot block a fresh service lifecycle. Added a focused regression test. Gradle execution remains pending.
- [x] Refined the bridge settings information surface with a black-to-charcoal gradient, restrained white outline, and more consistent internal spacing. The palette remains strictly monochrome; no navigation or bridge-selection behavior was changed. UI rendering and accessibility checks remain pending.
- [ ] Bridge strategy proposal: investigate a **layered resilience policy** before attempting transport chaining. The first layer is the configured active bridge/transport; the second is a ranked, separately validated standby candidate; a third layer is a conservative recovery policy that keeps traffic blocked when neither path is verified. This avoids assuming that multiple bridge transports can be chained inside Onionmasq when the supported API may not provide that capability.
- [ ] Do not claim simultaneous bridge stacking or activate multiple bridges until Onionmasq/Tor control APIs and the resulting routing semantics are verified. A standby strategy is not equivalent to stacking transport tunnels, and no bridge switch or traffic path is activated by the current advisory planner.
- [ ] Visual follow-up: review the connection home screen and bridge settings together on narrow Android screens, then refine typography, card rhythm, touch targets, and motion without adding non-monochrome colors or changing connection semantics.
