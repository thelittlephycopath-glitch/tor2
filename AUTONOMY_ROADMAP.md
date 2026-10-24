# Tor VPN Beta 2 — additive autonomous backend architecture

**Branch:** `dangerous-experiment`  
**Baseline:** existing Tor VPN Beta service, Onionmasq integration, app-routing database, preferences, and current UI  
**Principle:** augment the current architecture; do not replace it. No UI/UX changes are part of this work.

This document expands the attached *TOR VPN BETA 2 — FULLY AUTONOMOUS BACKEND SPECIFICATION* into an implementation plan with explicit runtime boundaries, state invariants, test gates, and a capability ledger. A specification item is not treated as implemented merely because it appears here.

## Executive status

The first autonomy foundation now lives beside the existing VPN code:

- Deterministic policy evaluation and network-profile recommendations.
- A bounded, privacy-minimised, in-memory event journal.
- A stateful coordinator that receives VPN lifecycle observations.
- The coordinator is wired to selected lifecycle events in `TorVpnService`.
- An Android default-network callback observes validated connectivity, cellular transport, and captive-portal capability without reading SSIDs or changing routes.
- Network loss/return invalidates previous tunnel/leak verification. Monitor registration failure is recorded instead of crashing the VPN service or failing open.
- Tunnel bootstrap does **not** count as an independent leak-check pass.
- The policy planner emits recommendations only; it does not bypass Android VPN behavior or call unverified Onionmasq operations.
- All automation flags default off. The current UI is untouched.

This is a foundation, not a claim that every item in the attached 17-section spec is finished. Battery/data monitoring, network-trust and anomaly detection, actual recovery actions, leak probes, encrypted persistence, decoy files, cover traffic, ML, and device-level guarantees remain unimplemented and gated.

## 1. Compatibility contract: preserve the existing architecture

Existing subsystems remain the source of truth:

| Existing subsystem | Role kept intact | New relationship |
|---|---|---|
| `TorVpnService` | Owns Android `VpnService` lifecycle, TUN setup, observers, and Onionmasq calls | Emits privacy-minimised lifecycle events to `AutonomyCoordinator` |
| Onionmasq | Owns Tor networking, current transport/bridge behavior, and native TUN handling | Candidate source for future verified APIs; no speculative calls added |
| `VpnStatusObservable` | Existing connection state and data usage | Not replaced with a second UI status model |
| `AppManager` and app-routing database | Existing per-app route selection | Not bypassed or silently rewritten by the policy engine |
| `PreferenceHelper` | Existing settings and migration behavior | No preference migration or default setting changed here |
| Current UI and Quick Settings tile | User-facing workflows and affordances | No UI/UX changes |
| Existing CI/build container | Pinned Android/Rust toolchain and APK packaging | Used to validate additive files on this branch |

The autonomy subsystem is intentionally a sidecar module in package `org.torproject.vpn.autonomy`. The service sends observations into it; it does not hand the TUN descriptor, app database, or route policy to this module.

## 2. Layered backend architecture

### 2.1 Signal ingestion

Proposed sources are separated by responsibility:

- **VPN lifecycle adapter:** service started/stopped, tunnel start requested, Onionmasq bootstrap completion, connection failures, and private-DNS configuration conflicts.
- **Android network monitor — future:** network available/lost, validated connectivity, metered state, and captive-portal state through supported Android APIs.
- **Leak-check adapter — future:** explicit test outcomes for DNS, IPv4/IPv6 routing, tunnel state, and reconnect isolation. No currently wired tester is allowed to emit a pass.
- **Resource monitor — future:** battery percentage, charging state, and data-budget inputs.
- **Bridge observation source — future:** recent outcomes already observed by the app, not unbounded active scans.

Each source emits an enumerated `AutonomyEventType` and `AutonomyEventSource`. Event payloads do not accept free-form strings. Do not add raw SSIDs, destination hosts, IP addresses, DNS queries, app package names, account identifiers, browsing content, or credentials to the event record.

### 2.2 Event journal

`AutonomyEventJournal` is a fixed-size, thread-safe ring buffer, capped at 4,096 entries (default 128). Each event has a type, source, monotonic elapsed-time timestamp, and in-process sequence number. Oldest entries are evicted when capacity is reached. Clearing the journal does not reset its sequence counter.

The journal is **in-memory only**. No unencrypted event log or ad hoc cryptography is introduced. Durable learning requires a separately reviewed Android storage design, encryption using an established library/platform mechanism, bounded retention, clear/reset semantics, and tests proving secrets never enter the record.

### 2.3 Policy engine

`AutonomousPolicyEngine` is pure, deterministic Kotlin. It maps provided signals to a profile, reason codes, and proposed actions. It performs no network calls, no bridge probes, no filesystem writes, no device-identifier changes, and no route changes. The result is advisory until a separately reviewed executor exists.

Profiles currently map as follows:

| Input | Suggested profile | Important qualifier |
|---|---|---|
| Confirmed/user-supplied hostile state or suspected DNS/TLS interference | Maximum | Suspicion raises protection; it does not prove active interception |
| Public network | Maximum | Public classification must come from a real source, not an SSID substring |
| Unknown network or no connectivity | High | Unknown is never silently promoted to trusted |
| Explicitly trusted network with no risk signal | Balanced | A saved SSID alone must not establish trust |

The present service does not provide authoritative Wi-Fi trust, hostile-network, battery, data-budget, or leak-check results. Until the respective monitors exist, those inputs remain unknown/defaulted and no automatic behavior is inferred from missing data.

### 2.4 Stateful coordinator

`AutonomyCoordinator` combines the policy engine with service-observed state. On tunnel start, network changes, connection failures, DNS-policy conflict, stop, or leak-check events, it invalidates previous readiness as appropriate and recalculates the decision.

Critical invariant:

> Traffic is considered eligible for release by the planner only when the network is available, the tunnel is ready, an independent leak-check pass has been observed, no leak-check failure is active, and no unresolved DNS configuration conflict exists.

A bootstrap event only sets tunnel readiness. It does **not** set `leakChecksPassed`. Network loss/restoration, tunnel failure, and a DNS conflict invalidate prior verification. A leak-check pass received before a ready tunnel is ignored.

This invariant is a **planner invariant**, not a device-enforced kill switch. The new coordinator does not itself block packets. Real lockdown depends on Android VPN/Always-on/lockdown behavior and must be separately validated on device.

### 2.5 Action executor — deliberately not wired yet

The planner can recommend:

- Keep traffic blocked pending verification.
- Run an independent leak check.
- Request tunnel reconnect or bridge failover.
- Request circuit refresh.
- Suppress optional cover traffic when resource budgets are low.
- Surface a critical warning.
- Restore verified tunneled traffic.

None of these actions is executed by the policy module. Before execution is enabled, the team must verify the actual Android/Onionmasq APIs, idempotency, cancellation rules, retry limits, and failure behavior. Reconnection must never be used as an excuse to switch to direct internet. No unrecognized action may default to permissive behavior.

## 3. Network and threat policy

### 3.1 Trust evidence

Trust is a policy input, not learned from a name alone. Future classifier inputs may include Android's validated-network/captive-portal signals and repeatable checks. A TLS anomaly or DNS mismatch is a signal to investigate, not definitive attribution. Do not store raw network identifiers as a side effect of classification.

A future optional history can use a local, rotating, app-scoped keyed digest for network grouping, but must be opt-in, bounded, and reviewed first. No raw SSID or carrier label should enter diagnostic events.

### 3.2 Captive portals

The planner exposes `allowCaptivePortalOnly` as a **future policy recommendation**. It does not create a direct-network exception today. A future implementation must use a tiny, explicit portal-only allowlist and ensure all unrelated app traffic remains contained; if the platform cannot enforce that boundary, remain blocked and explain the limitation rather than falling back to the open internet.

### 3.3 DNS and IPv6

The existing tunnel setup retains its current DNS and route construction. The new policy can represent an unresolved private-DNS conflict and reject traffic-release eligibility, but it does not claim to detect DNS poisoning or prove IPv6 leak resistance. Do not change DNS fallback, permit native IPv6, or weaken route coverage until test fixtures and on-device leak tests exist.

### 3.4 Per-app routing

Existing `AppManager` and `TorVpnService.applyAppFilter` remain authoritative. Future autonomous policy may propose app-routing changes, but must not silently broaden exclusions or change a user-configured app list. Default unknown-app behavior needs to be implemented only after a safe migration/default policy and backward-compatibility tests are designed.

### 3.5 Bridges and circuits

The current bridge selection is based on existing settings/assets. The policy's `rankBridges` only orders supplied observations, excludes candidates explicitly marked blocked, and uses deterministic tie-breaking. It performs no probes and selects no bridge on its own.

Before automatic bridge rotation/circuit refresh can be enabled, inspect the exact Onionmasq revision and expose a narrow adapter with verified contracts for: capability support, cancellation, state observation, cooldown, repeated-failure handling, and an upper bound on retries. If the adapter cannot verify a safe result, retain the current route policy or fail closed; never silently drop to direct internet.

## 4. State machine and transition gates

Conceptual state is derived from events, not guessed from a start request:

| Event | Internal update | Required follow-up |
|---|---|---|
| Tunnel start requested / bootstrapping | Clear previous readiness and leak pass | Wait for actual bootstrap and then independent checks |
| Tunnel ready | Mark tunnel ready; keep leak pass false | Run independent checks before any future release |
| Leak check passed | Accept only if tunnel is ready, network available, and DNS policy clean | Planner may mark release eligible |
| Leak check failed | Invalidate pass; require warning/recovery | Block recommendation remains active |
| Network lost or restored | Invalidate previous tunnel/leak verification | Re-establish and re-check |
| Connection/tunnel failed | Increment bounded failure count; invalidate verification | Recommend reviewed recovery after threshold |
| DNS-policy conflict | Mark conflict and invalidate verification | Stop/resolve conflict; do not route around the policy |
| Tunnel/service stopped | Invalidate verification | No protected state may survive a stop |

The implemented thresholds (three consecutive connection failures, battery at or below 20%, and remaining data at or below 50 MB) are policy defaults used for recommendations only. They are not experimentally tuned values, and resource signals are not yet wired to Android monitors.

## 5. Resource and performance policy

The attached spec's cover traffic, packet padding, timing jitter, circuit splitting, and mixnet ideas have substantial performance and protocol risks. The current implementation only models a budget gate:

- Cover traffic remains disabled by default.
- Budget permission is not the same as an enabled feature.
- Suppress cover traffic on mobile data, low battery, or low remaining data budget.
- No third-party synthetic requests are generated.
- No packet manipulation, circuit splitting, or protocol changes are applied.

Any later experiment must quantify cold/warm start, time to bootstrap, DNS resolution latency, throughput, reconnect rate, battery use, and data use against the same device/network baseline. Test one change at a time and keep a known-good fallback. No tuning may disable certificate validation, weaken Tor routing, or increase leak risk.

## 6. Decoy data, honeyvault, and “burn” concepts

The attached specification includes decoy personas, poisoned permission data, synthetic activity, honeyvault files, and automatic burn mode. These have not been implemented and are intentionally not connected to the service.

Guardrails for any later research:

- No automatic account creation, impersonation, fabricated real-person personas, or deceptive content sent to other people.
- No synthetic requests to unrelated third-party services; traffic-generation research must use a user-controlled test endpoint, opt-in, and explicit rate/data budgets.
- No fake location for navigation, banking, emergency, accessibility, or safety-critical functions.
- No reading or modifying other apps' private storage; Android's app sandbox must be respected.
- No creation of fake credential/seed-phrase documents in shared storage.
- No automatic destructive wipe, evidence destruction, or silent deletion of user data.
- Any future canary experiment must be opt-in, use harmless markers in an isolated app-owned test area, avoid actual credentials or private documents, and document false positives.
- A compromised device or an adversary observing both communication ends cannot be made safe by decoys alone.

## 7. Local learning and AI/ML

No local ML runtime is added in this phase. The future feature boundary is deliberately narrow:

1. Start with transparent counters and simple deterministic heuristics.
2. Record only aggregate outcomes required for bridge-quality comparison; define sample minimums and expiration.
3. Compare heuristic quality against held-out observations and a no-learning baseline.
4. Keep model inputs local and avoid payload/content data.
5. Expose reasons and allow reset/disable.
6. Only consider a local model if it demonstrably improves reliability without increasing battery/data cost or reducing privacy.

Never upload browsing history, messages, contacts, credentials, personal files, raw DNS queries, or raw network identity for model training.

## 8. Android lifecycle requirements for future monitors

- Use supported `ConnectivityManager.NetworkCallback` events; unregister the callback on service destruction.
- Do not create a competing foreground service; the existing `TorVpnService` remains the owner of tunnel lifecycle.
- Respect Android background-start restrictions, user VPN consent, and Always-on/lockdown semantics.
- Bound retries with exponential backoff and jitter; add a maximum-attempt and cooldown policy.
- Treat `onRevoke`, process death, cancellation, and Android network changes as explicit state transitions.
- Do not keep raw `ParcelFileDescriptor` handles in the coordinator. TUN ownership stays inside the existing service/native boundary.
- Use monotonic time for intervals; wall time can change and must not drive retry logic.
- Separate “service started,” “native backend running,” “Tor bootstrapped,” and “leak tests passed” states; one is not proof of the next.

## 9. Test matrix required before enabling actuation

### Pure unit tests (added here)
- Unknown network defaults high and remains blocked.
- Public/hostile signals yield the intended profile.
- Tunnel readiness without an independent leak pass cannot release traffic.
- A pass received before tunnel readiness is ignored.
- Network loss, connection failure, and DNS-policy conflict invalidate verification.
- Captive portal recommendation does not permit general traffic.
- Cover-traffic budgets disable optional traffic on mobile/low resource budgets.
- Bridge ranking is deterministic and excludes known-blocked candidates.
- Invalid signal ranges and journal capacity are rejected.
- The journal stays bounded and event sequence remains monotonic across clear.

### Integration tests still required
- Existing service lifecycle: start, stop, revoke, restart, and cancellation races.
- Network change while the native backend is bootstrapping or active.
- Failed start and TUN file-descriptor ownership.
- Bridge failure and bounded recovery using actual Onionmasq APIs.
- DNS/private-DNS conflict recovery.
- App routing and allowed/excluded application behavior.
- No route is left outside the intended VPN policy after failure.
- Service restart does not duplicate observers, timers, or jobs.

### Device/emulator checks still required
- Android API 24 minimum through current API levels supported by the project.
- Wi-Fi ↔ mobile handover, captive portal, offline mode, airplane mode, battery saver.
- Always-on VPN and OS lockdown behavior.
- DNS and IPv4/IPv6 leak tests against controlled endpoints.
- Reconnect time, network traffic, CPU, memory, battery, and sustained stability.
- Install, upgrade, uninstall, and coexistence with an existing official-key build.

A successful Gradle build proves compilation only. A successful APK signature check proves the artifact is structurally signed by the workflow key only. Neither certifies Tor anonymity or on-device leak protection.

## 10. Implementation phases mapped to the supplied spec

| Phase | Included scope | Gate to proceed |
|---|---|---|
| **0 — Additive policy foundation (current)** | Models, deterministic policy, in-memory journal, coordinator, service lifecycle events, unit tests | PR CI build/tests/lint must pass |
| **1 — Network/resource observations** | Android connectivity/captive portal callbacks, metered-network state, battery and budget input | No raw SSID or sensitive telemetry; lifecycle/unregister tests |
| **2 — Independent verification** | Safe probe framework, leak-check state and controlled endpoint tests | On-device evidence and false-negative analysis |
| **3 — Guarded recovery** | Adapter for verified reconnect/bridge/circuit APIs; cooldowns, retry budget, idempotency | Integration tests prove no direct fallback or stale readiness |
| **4 — Trust and local learning** | Explainable rules and aggregate bridge outcomes with retention/reset controls | Measurement shows benefit; privacy review approves persistence |
| **5 — Optional traffic-analysis research** | Budgeted, controlled-endpoint cover traffic; explicit opt-in | Lab-only validation first; speed/data/battery/privacy review |
| **6 — Isolated decoy/canary research** | Harmless app-owned canaries only, no impersonation or dangerous fake files | Explicit opt-in, threat model, isolation and false-positive tests |
| **7 — Advanced research** | Mixnets, multi-path/circuit experiments, post-quantum transports, or ML | Separate design review; no default enablement |

Speculative capabilities are intentionally not bundled into Phase 0. A capability can move forward only when its real runtime implementation, tests, and failure contract exist.

## 11. Release and merge gate

This branch is an experiment. Do not merge it into `main` until:

1. CI build, unit tests, ktlint, Android lint, and staging APK assembly pass.
2. The exact failed/passed workflow logs are examined, not inferred from a started job.
3. No UI or UX files changed.
4. No network action is executed merely because the policy planner recommended it.
5. Any change to tunnel, route, DNS, bridge, or descriptor code has specific regression coverage.
6. On-device leak and network-transition checks are completed before enabling any automation.
7. The generated community APK is identified as debug-key signed unless an official release process supplies a production key.

The project must remain honest about its guarantees: Tor VPN can reduce exposure, but no client can fully defeat a compromised device, a malicious endpoint, or all traffic-correlation adversaries.
