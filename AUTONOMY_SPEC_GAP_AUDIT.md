# Tor VPN Beta 2 — Full Specification Gap Audit

**Audit date:** 2026-10-27  
**Branch:** `dangerous-experiment`  
**Source specification:** `tor_vpn_beta_2_fully_autonomous_backend_spec.md` (802 lines, sections 1–17)  
**Scope:** Source-level review against the supplied specification and the current repository tree. This is not a build, runtime, or security certification.

## Status legend

- **Implemented in source** — the code path exists, although runtime verification may still be pending.
- **Partial / advisory** — a model, policy, monitor, API seam, or planner exists, but the live service does not yet execute or enforce the promised behavior.
- **Not implemented** — there is no implementation evidenced by the reviewed source.
- **Unverified** — the code or tests exist, but Android compilation, Gradle tests, emulator tests, or device tests have not passed in this session.

## Executive summary

The repository has a working base Android VPN application and several useful additive autonomy components: a bounded event journal, freshness-aware network trust classification, a recovery planner, bridge-outcome scoring, circuit-refresh and cover-traffic policy planners, an action allow-list, read-only Android environment/resource observations, HMAC fingerprint helpers, and an opt-in digest-storage adapter.

The critical distinction is that **many of the autonomy components remain advisory**. The full specification promises an autonomous privacy agent that performs recovery, route selection, circuit refresh, active leak tests, censorship response, traffic-analysis resistance, decoy activity, and canary response. Those actions must not be described as complete merely because a planner can recommend them.

Recent source changes on this branch:
- The recovery planner now exposes a service-stop reset, and the coordinator calls it on explicit tunnel/service stop. A regression test was added; Gradle execution is pending.
- The bridge-information surface and connection artwork were changed to a black/white/charcoal palette. The bridge selection logic and navigation were not intentionally changed by those artwork edits.
- The Bridge Bot flow now offers an **obfs4 / WebTunnel** choice, requests only the chosen transport from the bridge-distribution API, filters mixed responses to the requested transport, and saves returned lines through the existing manual-bridge configuration path. Tests were added but have not run under Gradle. This is **manual transport selection**, not automated transport failover, and a live WebTunnel connection has not been verified.

No verified APK is available. The current GitHub Actions runner-assignment failure still prevents a trustworthy claim that the full Android project compiles or that its Gradle test suite passes.

## Full specification mapping

### 1. Autonomous system architecture

**Partial / unverified.**

- The existing Android VPN foreground service and Onionmasq startup path exist.
- Existing network and resource observations include default-network availability/validation, cellular transport, captive-portal state, battery readings, and charging-state availability.
- The autonomy event journal is bounded and in-memory.
- The autonomy coordinator joins observations and policy decisions.
- The service-stop recovery reset is now wired into the coordinator.
- The autonomy layer does not yet own or execute every responsibility listed in section 1.1. In particular, active leak verification, automatic bridge choice/failover, automatic circuit rotation, threat response, tracker deception, and canary response are not end-to-end features.
- Boot/foreground lifecycle mechanisms are present in the base application, but their reliability under reboot, force-stop, Doze, screen lock, and network churn has not been device-verified in this audit.

**Improve next:** keep lifecycle, tunnel enforcement, and advisory intelligence separated. Make the service expose a small serialized command interface, then add one executor at a time behind explicit safety gates and tests.

### 2. Decision engine, policy engine, event bus, and local knowledge base

**Partial / advisory.**

- Freshness-aware trust classification and conservative state handling exist.
- Recovery, circuit-refresh, and cover-traffic planners exist, but planners do not execute the actions.
- Proposed actions pass through an allow-list. Commands without real executors remain suppressed.
- Bridge outcomes are bounded in memory and are not a live transport-selection mechanism.
- The fingerprint helper and Android Keystore key provider exist. The opt-in storage adapter stores keyed digests and timestamps, but actual observation collection, consent UI, and live storage wiring remain incomplete.
- The event journal is not a persistent, encrypted knowledge base covering every event in the specification.
- Local ML models for circuit quality, censorship prediction, app risk, anomaly detection, or policy learning are not implemented.

**Improve next:** define a versioned, typed event schema with explicit source, timestamp, confidence, and expiry; persist only the minimum useful metadata; introduce user-controlled consent before collecting network-history samples; and avoid ML until measured, labeled observations exist.

### 3. Automatic connection management and network trust automation

**Partial / advisory.**

- Existing connect-on-launch/boot-related facilities are part of the base app; their full lifecycle behavior has not been verified on a device.
- Network transitions invalidate previously cached trust evidence.
- Trust evidence is freshness-checked; stale or future-dated evidence does not remain trusted indefinitely.
- Recovery planning has bounded backoff and single-pending-attempt protection.
- The planner waits while tunnel startup is in progress and keeps traffic blocked in unverified states.
- The recovery executor is not wired to actually restart the service, select a new transport, wait for bootstrap, trigger a live leak test, or restore traffic after validated success.
- The specified automatic profiles for trusted home Wi-Fi, unknown/public Wi-Fi, mobile data, censorship, battery constraints, and sensitive apps are not implemented end-to-end.
- Current observations do not prove that the system can distinguish a genuinely trusted home network from an arbitrary network simply because Android reports it as validated.

**Improve next:** implement a single serialized service lifecycle with cancellation-safe transitions. Make every reconnect attempt progress through observable states, and require fresh independent verification before traffic is released.

### 4. Automatic leak prevention

**Partial; the most important safety gap.**

- A read-only Android diagnostic probe records exposed VPN route/DNS/always-on settings. This is diagnostic evidence only.
- The leak-report gate requires current evidence for IPv4 routing, IPv6 containment, DNS protection, system lockdown, and app routing.
- A raw “passed” event cannot authorize traffic without a validated report.
- Failure remains latched until a fresh complete safe report passes.
- Stale or future-dated reports lose eligibility.
- **There is no live end-to-end verifier yet** for actual IPv4, IPv6, DNS, WebRTC, per-app routing, system lockdown, or direct-fallback behavior. The read-only probe does not prove those protections are effective.
- No evidence supports a claim that all the leak-prevention requirements in section 3 are complete.

**Improve next:** implement and test an independent verifier against instrumented endpoints in a controlled device/emulator environment. Verify route and firewall behavior during startup, tunnel loss, service stop, network switch, cancellation, and failed bridge recovery. Keep traffic blocked when verification is unavailable or ambiguous.

### 5. Automatic Tor management

**Partial.**

- The base service passes a selected bridge configuration to Onionmasq.
- Existing built-in modes use obfs4 and Snowflake assets; manually supplied bridge lines use the existing line-parser/configuration path.
- Existing source references the bridge parser's WebTunnel type and can label WebTunnel lines.
- The Bridge Bot screen now has a transport choice for requesting obfs4 or WebTunnel lines. The WebTunnel request is transport-specific, and mixed API responses are filtered rather than silently returning another transport.
- Saved WebTunnel lines use the existing manual-bridge storage path; this preserves the bridge line, including its URL/path, rather than inventing a public default bridge.
- This branch does **not** yet automatically rank and activate bridge candidates, rotate transports after a failure, run bridge tests in parallel, or perform actual circuit refresh through a verified Tor/Onionmasq control API.
- The newly added WebTunnel request/save path has not been built or tested against the live bridge API or a real connection.

**Improve next:** validate the WebTunnel request endpoint and bridge-line parser in Gradle tests; then test a user-provided or distributor-provided WebTunnel line on an Android device. Only after that should WebTunnel join an automated fallback policy.

### 6. Automatic censorship resistance

**Partial / mostly not implemented.**

- The Android environment monitor can observe default-network changes, validation, captive-portal indication, and cellular transport.
- Bridge health observations and HMAC fingerprint/storage helpers exist.
- Digest storage requires explicit opt-in on each storage operation, is bounded, expires entries, and supports erasure; it is not wired to a consent surface or live learning path.
- Real DNS-hijack/TLS-interception signals, network-specific transport success learning, time-of-day predictions, predictive bridge selection, automatic bridge rotation, and controlled captive-portal completion are not implemented.
- A hostile-network classification alone cannot be reliable without trustworthy live indicators and false-positive handling.

**Improve next:** start with observable, low-risk measurements (connection success, bootstrap duration, and transport result), explicitly label uncertainty, avoid storing raw network identifiers, and do not automate portal bypass. Re-run the trust decision after every network identity change.

### 7. Ghost Mesh / traffic-analysis resistance

**Partial policy scaffold only.**

- A pure cover-traffic budget planner exists, including opt-in, user-controlled target gating, battery/data caps, conservative cellular limits, and a charging-state safety check.
- Unknown/stale charging telemetry no longer qualifies for the charging boost.
- The planner does not generate packets.
- Packet padding/normalization, timing jitter, application-level mixnets, circuit splitting, and cross-device cover traffic are not implemented.
- No protocol feasibility or performance proof has been completed.

**Improve next:** keep this phase disabled until the core tunnel is verified. Define a measurable budget and performance experiment; use only protocol-supported behavior, explicit opt-in, and destinations that permit automated traffic. Never present generic background traffic as equivalent to Tor protocol-level traffic-analysis resistance.

### 8. Decoy personas, data poisoning, and synthetic activity

**Not implemented.**

- No decoy persona engine, tracker-facing interest noise, synthetic activity generator, permission data poisoning vault, or automatic identifier rotation was found in the reviewed autonomy implementation.
- A normal Android VPN app cannot generally rewrite another app's private files or arbitrarily replace Android permission API results through the VPN interface. Those ideas need a separate platform-feasibility and safety review rather than being treated as ordinary VPN features.
- The specification's exclusions for navigation, emergency services, banking, accessibility, impersonation, and automatic account creation should remain hard constraints.

**Improve next:** begin with transparent, local-only tracking of the VPN's own decisions and network observations. Defer cross-app data manipulation unless there is a supported Android API and a safe, narrowly scoped design.

### 9. Honeyvault, canaries, and tracker tripwires

**Not implemented.**

- There is no honeyvault/canary monitoring or automatic response executor in the reviewed branch.
- A future app-owned canary file can only observe events that the app is permitted to observe; it cannot assume universal visibility into other applications' private storage or all file accesses.
- A remote fake-admin surface is not part of the Android client and would require a separately isolated self-hosted component.

**Improve next:** if pursued, start with non-sensitive decoy markers inside app-owned storage and no destructive action. Prove isolation and false-positive behavior before adding notifications or blocking rules.

### 10. Autonomous device protection

**Not implemented / partly outside ordinary app permissions.**

- No duress profile, private/decoy profile manager, automated burn mode, or general app-permission abuse detector is implemented by the autonomy layer.
- Android sandboxing and permission boundaries prevent the VPN from observing or manipulating every behavior described in the specification.
- Automatic destructive wipe or evidence-destruction behavior should not be added as a default reaction to heuristic signals.

**Improve next:** offer reversible, user-triggered lockdown controls and safe clearing of explicitly scoped volatile session data. Do not auto-delete real user data or claim a duress feature until it has a reviewed threat model and platform-supported implementation.

### 11. Self-hosted components

**Not implemented.**

- No personal relay, exit gateway, peer bridge service, zero-knowledge configuration sync, or reproducible installed-build verifier has been completed by this autonomy work.
- These are optional system components, not prerequisites for proving the Android client's core safety.

**Improve next:** defer them until the client-side tunnel enforcement and bridge recovery are reliable. Treat any relay/exit component as a separate deployable product with its own authentication, abuse, update, and operational threat model.

### 12. AI / ML

**Not implemented.**

- Current logic is deterministic policy code and bounded statistics, not a trained local model.
- There is no validated model for circuit scoring, censorship prediction, app risk, anomaly detection, or automatic user-safe policy learning.

**Improve next:** collect only consented, privacy-minimized operational outcomes first. Establish baselines and false-positive/false-negative measurements before introducing a model. Keep policy decisions explainable and preserve hard safety limits around model outputs.

### 13. Automatic UI behavior

**Partial.**

- The base application already has a connection screen, bridge configuration, app routing, logs, and a Quick Settings tile.
- The bridge-information surface and connection artwork now use a monochrome black/white/charcoal palette.
- The Bridge Bot screen now offers an obfs4/WebTunnel selector with monochrome selected/unselected states and a brief WebTunnel explanation.
- A passive dashboard that accurately reports active profile, current route, active bridge, circuit age, independent leak status, battery/data costs, and recent automatic actions is not wired to the autonomy coordinator.
- Automatic notifications for verified failover, persistent leak risk, hostile networks, canary events, and app bypass attempts are not wired to corresponding real events.
- The new UI code and resource edits have not been compiled or checked on narrow-screen devices, TalkBack, or reduced-motion settings.

**Improve next:** keep the user-facing design monochrome, clear, and low-clutter. Display only measurements that the backend actually knows; distinguish “not checked” from “passed.” Use restrained transitions and test touch targets, text scaling, accessibility, and one-handed use.

### 14. Safety rules

**Partial / policy gate present, enforcement not certified.**

- Unimplemented action types remain suppressed by the allow-list.
- The autonomy layer prefers blocked traffic when evidence is missing, stale, or failed.
- No successful device test proves no direct traffic escapes when the tunnel fails.
- No automation should bypass platform controls, fake safety-critical data, impersonate people, access unwilling third-party destinations, or destroy user data.

**Improve next:** treat fail-closed behavior as an invariant across service lifecycle and all network transitions. Validate it independently on-device, not solely through unit tests of policy decisions.

## Phase checklist

| Spec phase | Current assessment | Critical unfinished work |
|---|---|---|
| Phase 1 — Autonomous Safety Core | Partial | Live leak verifier; verified lockdown and no-direct-fallback; successful Gradle, lint, emulator, and device tests |
| Phase 2 — Autonomous Tor Engine | Partial | Supported bridge candidate activation/failover; serialized recovery executor; real circuit refresh; live routing tests |
| Phase 3 — Censorship Intelligence | Partial | User consent; wired learning path; reliable DNS/TLS indicators; safe captive-portal handling; Wi-Fi/cellular validation |
| Phase 4 — Ghost Mesh | Planner only | Protocol feasibility, real measurements, controlled implementation, battery/data/performance evaluation |
| Phase 5 — Decoy data / honeyvault / canaries | Not implemented | Platform feasibility, isolation, non-destructive behavior, false-positive handling |
| Phase 6 — Advanced research | Not implemented | Research only; defer until lower phases are measured and stable |

## Top-10 feature list from section 15

1. **Always-on kill switch:** Android facilities and policy gates exist, but end-to-end enforcement is not device-verified.
2. **Automatic per-app routing:** the base app has per-app routing; autonomy-driven selection and leak/bypass verification remain unfinished.
3. **DNS/IPv6/WebRTC leak prevention:** evidence contract and a read-only probe exist; live verification and enforcement testing are missing.
4. **Automatic bridge selection/failover:** bridge health scoring and bridge-line configuration exist; automatic activation/failover is missing. WebTunnel retrieval is now available as a user-selected transport, but not as automatic fallback.
5. **Adaptive censorship fingerprint learning:** HMAC helper and opt-in storage adapter exist; collection, consent UI, and live learning integration are missing.
6. **Automatic circuit rotation:** advisory planner only; live control API and verification are missing.
7. **Adaptive Ghost Mesh cover traffic:** budget planner only; no traffic is generated.
8. **Decoy Persona Engine:** not implemented.
9. **Data Poisoning Vault:** not implemented and requires a separate Android platform-feasibility review.
10. **Honeyvault and canary monitoring:** not implemented.

## Recommended implementation order

### P0 — Build and test truthfully
1. Restore GitHub Actions runner assignment or use a separately verified build environment. The observed CI failure occurs before workflow steps run, so it cannot be explained as a Kotlin compilation failure.
2. Run the complete Gradle unit-test suite and Android compilation; resolve all failures rather than treating manually compiled harness assertions as a substitute.
3. Run lint, emulator smoke tests, and a signed-artifact verification only after the relevant build and test gates pass. APK work is currently paused.

### P1 — Finish the minimum safe VPN
1. Verify Android lockdown/no-direct-fallback under startup, failure, stop, network switch, and reconnect.
2. Implement real independent IPv4, IPv6, DNS, and per-app route tests; connect their results to the existing report gate.
3. Wire one serialized recovery executor and prove that stale pending attempts cannot suppress recovery or trigger parallel service starts.
4. Test the newly added WebTunnel retrieval, bridge-line validation, and real Android connection before allowing it into automated selection.

### P2 — Make bridge recovery actually autonomous
1. Normalize observations for obfs4, Snowflake, WebTunnel, and supported manual bridge lines.
2. Rank only candidates with fresh measured outcomes; treat unknown candidates as unknown rather than fabricated successes.
3. On failure, try only explicitly configured/supported alternatives through the service's single lifecycle executor.
4. Keep traffic blocked if all candidates fail. Do not fall back directly to the internet.
5. Add cooldowns, bounded attempts, cancellation handling, and user-visible truthful status.

### P3 — Complete consented learning and circuit management
1. Add explicit, understandable consent before persisting network-history digests.
2. Wire storage into verified observation collection with expiry, deletion, and backup exclusions.
3. Use supported Tor/Onionmasq controls for circuit refresh and verify outcomes; keep the circuit planner advisory until those controls are available.

### P4 — UI and advanced features
1. Complete the monochrome design system with a passive status view backed by real states, not guessed values.
2. Keep notifications sparse and tied to actionable, validated events.
3. Revisit Ghost Mesh only after the tunnel, leak checks, and bridge recovery pass device testing.
4. Defer poisoning, honeyvault, duress/burn, mixnets, ML, and self-hosted relays until each has a platform-feasibility review, isolation test, and measurable benefit.

## Verification limits

- Regression tests have been added for the autonomy components and the WebTunnel request/filter path, but **the repository Gradle test suite has not run successfully**.
- The GitHub hosted-runner jobs previously terminate before running any steps; this is an infrastructure blocker and does not establish whether the source compiles.
- No Android build, lint run, emulator smoke test, real Wi-Fi/cellular test, end-to-end leak test, signature check, or verified APK artifact is available.
- All UI edits in this branch are experimental and uncompiled until the build gate is restored.
- The original UI and main branch must remain untouched unless the user explicitly approves a tested merge.
