# Tor VPN Beta 2 — Master Execution Plan

**Target branch:** `dangerous-experiment`  
**Baseline commit reviewed:** `7251674a52026d32d827f3d158d9f2423a13ac44`  
**Primary rule:** preserve the existing UI/UX. No redesign, navigation changes, or user-facing behavior changes unless explicitly requested.  
**Release rule:** do not merge to `main` or claim an APK is ready until the acceptance gates below pass.

This is an execution plan, not a claim that the work is already complete. Each item must be marked PASS only with reproducible evidence; otherwise mark FAIL, BLOCKED, or NOT TESTED.

## Operating rules

1. Work in small, reviewable commits on `dangerous-experiment`.
2. Read the current source and tests before changing behavior; do not overwrite newer work with a stale snapshot.
3. Prefer minimal fixes with regression tests. Avoid broad refactors while the build is unverified.
4. Never synthesize a successful leak check, trust result, or security status from missing data.
5. Keep experimental autonomy disabled by default and fail closed when critical evidence is absent, stale, inconsistent, or bound to another session.
6. Do not expose hostnames, IP addresses, SSIDs, destinations, DNS queries, credentials, bridge secrets, or other sensitive material in routine logs or persistent diagnostics.
7. Separate static checks, unit tests, integration tests, device tests, and release verification. A passing static script is not proof of a working VPN.
8. If hosted CI cannot allocate a runner, record the exact evidence and work on independent static audits; do not claim compilation or runtime verification.
9. Keep signing keys and credentials out of source control and artifacts. Community/debug APKs must be clearly distinguished from official Tor-signed releases.
10. Preserve upstream licensing, attribution, and security notices.

## Phase 0 — Establish the baseline and evidence ledger

- [ ] Confirm branch head, working tree/commit history, workflow definitions, current specification file path, and all audit documents.
- [ ] Create a requirement-to-code-to-test matrix covering every specification section and acceptance criterion.
- [ ] Classify each requirement: implemented, partial/advisory, missing, or unverified.
- [ ] Record build environment versions, dependency pins, known CI runner failures, and the exact commit under test.
- [ ] Capture baseline behavior and UI screenshots only for regression comparison; do not change UI/UX.
- [ ] Keep an evidence ledger with command/workflow URL, commit SHA, result, timestamp, and known limitations.

**Gate 0:** no implementation claims without a source location and an appropriate test or explicit verification limitation.

## Phase 1 — Build pipeline and developer reproducibility

- [ ] Diagnose why GitHub Actions jobs terminate before the first step; inspect workflow run, job, runner, permissions, billing/quota, and repository-level evidence available to the project.
- [ ] Validate workflow YAML and all shell scripts; run shell syntax checks and CI-security fixture tests.
- [ ] Keep third-party GitHub Actions pinned to immutable commit SHAs and verify the checked-out SHA matches the expected pin.
- [ ] Pin and verify bootstrap inputs: JDK archive, Android command-line tools, SDK/NDK packages, Rust/rustup installer, native dependencies, and helper scripts. Use official checksums/signatures where publishers provide them; document cases where they do not.
- [ ] Ensure dependency fetching is deterministic and fails on missing or changed pins instead of silently updating.
- [ ] Validate API 36/37 SDK channel/package consistency, NDK path, Rust targets, Onionmasq build inputs, and Gradle/JDK compatibility.
- [ ] Ensure temporary files, local.properties changes, signing material, and build directories are cleaned or restored on failure.
- [ ] Add clear build-stage diagnostics without leaking secrets.
- [ ] Run the reproducible dependency setup from a clean environment, then native compilation, Gradle compilation, tests, APK packaging, signing, and verification in that order.

**Gate 1:** clean dependency setup and all build stages complete on a real runner. Until then, mark build status BLOCKED/UNVERIFIED.

## Phase 2 — Android VPN lifecycle and network correctness

- [ ] Audit the complete VPN service lifecycle: start, foreground promotion, permission denial, tunnel ready, disconnect, stop, destroy, process recreation, and repeated start/stop.
- [ ] Close callback shutdown races: mark monitors inactive before unregistering; guard every callback against inactive registration and stale network identity; make unregister idempotent.
- [ ] Add regression tests for callbacks arriving during shutdown, out-of-order network events, rapid Wi-Fi/mobile transitions, and monitor re-registration.
- [ ] Verify all asynchronous jobs, executors, scopes, sockets, file descriptors, and native resources are cancelled/closed exactly once.
- [ ] Review VpnService.Builder configuration: routes, DNS, allowed/disallowed apps, MTU, IPv4/IPv6, underlying network, and session metadata.
- [ ] Verify tunnel startup ordering so the app cannot declare traffic protected before routes, DNS, and tunnel readiness are established.
- [ ] Audit disconnect, crash, native process failure, captive portal, no-network, airplane mode, and tunnel-reconnect behavior.
- [ ] Confirm Android lockdown behavior is observed accurately and never reported as enabled unless actually verified.
- [ ] Check that callbacks and state transitions are serialized or otherwise race-safe.

**Gate 2:** lifecycle tests pass and the service cannot release traffic on stale or incomplete readiness signals.

## Phase 3 — Fail-closed leak verification

- [ ] Define a verifier contract that returns independently measured evidence rather than policy intentions or monitor guesses.
- [ ] Specify separate checks for IPv4 default route, IPv6 containment/blocking, DNS path, system lockdown state, and per-app routing.
- [ ] Bind every verification report to the current tunnel/session generation and network identity; reject null, stale, mismatched, duplicated, or incomplete evidence.
- [ ] Ensure a network transition, tunnel restart, DNS/TLS anomaly, monitor failure, or hostile-trust transition invalidates prior evidence.
- [ ] Keep diagnostic probes explicitly diagnostic; never convert their placeholder false values into a pass.
- [ ] Add unit tests for every missing field, false field, stale timestamp, wrong generation, race, exception, and state invalidation.
- [ ] Add emulator/device integration tests using controlled test endpoints and packet/DNS observations to verify expected routing and leak behavior.
- [ ] Verify the actual packet path, not merely internal booleans. Use controlled test infrastructure; do not log real user traffic.
- [ ] Add a single explicit traffic-release predicate requiring tunnel readiness, current-session verification, acceptable network policy, and no active blocking condition.
- [ ] Make verifier failure, timeout, unsupported platform state, and unknown state block protected-traffic claims and trigger a safe state.

**Gate 3:** independent end-to-end leak tests pass. If no real verifier is wired, the feature remains BLOCKED and must not be described as leak-proof.

## Phase 4 — Network trust and autonomy integration

- [ ] Connect Android observations to the trust classifier through a narrow, typed, privacy-minimized interface.
- [ ] Distinguish observed facts from inferred risk and user-configured policy; include source, timestamp, freshness, and session/network generation.
- [ ] Treat missing or contradictory observations conservatively.
- [ ] Make captive portal handling portal-limited; never interpret portal access as general internet readiness.
- [ ] Ensure legacy trust APIs cannot bypass session binding or silently accept missing generation tokens.
- [ ] Keep decision engine, policy planner, command allow-list, and real action executor separate.
- [ ] For each proposed action, require a registered executor, validated arguments, current state, cooldown/rate limit, cancellation path, and audit event.
- [ ] Do not automatically change bridges, rotate circuits, generate decoy traffic, or react to canaries until the corresponding action is implemented, tested, safe, and explicitly enabled.
- [ ] Add deterministic tests for trust transitions, repeated events, stale events, conflicting events, action suppression, cooldowns, and recovery loops.
- [ ] Keep experimental autonomy disabled by default; ensure disabling it immediately prevents new optional actions.

**Gate 4:** every advertised autonomous capability is classified as implemented and tested, or explicitly labelled advisory/not available.

## Phase 5 — Privacy, data minimization, and security review

- [ ] Review Android manifest permissions, exported components, intent filters, foreground service declarations, network security configuration, and cleartext traffic policy.
- [ ] Review backup/data-extraction rules, local databases, preferences, cache, crash reports, logs, analytics, and diagnostic exports.
- [ ] Confirm opt-in learning storage is genuinely opt-in, bounded, deletable, and excluded from backup unless explicitly justified.
- [ ] Review HMAC/fingerprint handling, key generation, key storage, rotation, and resistance to accidental raw-identifier persistence.
- [ ] Check for credentials, signing keys, tokens, private bridge data, personal data, or secrets in Git history, workflow output, artifacts, and test fixtures.
- [ ] Review native/Rust and Kotlin boundaries for unsafe code, unchecked inputs, error propagation, and resource cleanup.
- [ ] Review dependency advisories and licenses; record accepted risk and remediation priority.
- [ ] Review relevant upstream security audits and ensure local changes do not claim to resolve issues they do not address.
- [ ] Add tests proving sensitive fields are excluded from event records and diagnostic output.
- [ ] Write a threat model: assets, adversaries, trust boundaries, failure modes, mitigations, residual risks, and unsupported guarantees.

**Gate 5:** no known critical/high issue remains unexplained; privacy tests and secret scans pass.

## Phase 6 — Reliability, performance, and resource limits

- [ ] Measure cold startup, warm startup, tunnel-ready time, reconnect time, CPU, memory, battery, wakeups, and network overhead on representative devices.
- [ ] Profile before optimizing; avoid speculative changes to the data path.
- [ ] Check event journal bounds, database bounds, queue limits, retry/backoff, circuit-breaker behavior, and runaway recovery loops.
- [ ] Ensure optional cover traffic and learning features are disabled or suppressed under unknown battery/network state, metered data, low battery, or policy restrictions.
- [ ] Test prolonged sessions, repeated reconnects, screen off/on, Doze, background restrictions, process death, and resource pressure.
- [ ] Add regression thresholds where measurements are stable; record device/OS/build context.
- [ ] Do not promise a speed improvement without before/after measurements and no regression in correctness or privacy.

**Gate 6:** reliability tests pass and measured performance has no unexplained regression.

## Phase 7 — Test pyramid and adversarial validation

- [ ] Unit tests for policies, gates, state machines, parsers, and error handling.
- [ ] Property/fuzz tests for untrusted bridge/configuration input and boundary values.
- [ ] Integration tests for coordinator, service, monitor, native tunnel, and persistence boundaries.
- [ ] Android emulator tests for install, permission flow, start/stop, network churn, and process recreation.
- [ ] Device tests across supported Android versions and IPv4/IPv6/DNS environments.
- [ ] Negative tests: no network, bad DNS, stale evidence, verifier exception, tunnel crash, malformed config, permission denial, storage failure, and CI dependency mismatch.
- [ ] Re-run all relevant tests after each fix and test the final candidate commit from a clean checkout.
- [ ] Track flaky tests separately; do not convert retries into a pass without root-cause analysis.

**Gate 7:** all required tests pass, or remaining blockers are explicitly documented and prevent release.

## Phase 8 — APK packaging and artifact integrity (last)

- [ ] Build from the verified commit using the pinned toolchain.
- [ ] Produce expected ABI-specific and universal APK outputs, if configured.
- [ ] Verify package ID, version, ABI contents, manifest metadata, signing certificate, and APK integrity.
- [ ] Generate SHA-256 checksums and build metadata that includes source commit and toolchain versions.
- [ ] Install the artifact on a clean emulator/device; launch it and test VPN permission, service start/stop, reconnect, and critical failure behavior.
- [ ] Clearly label debug/community signing. Never imply an ephemeral debug key is an official Tor release signature.
- [ ] Upload artifacts only after every preceding check passes; release publication must remain separately gated and opt-in.
- [ ] Download the uploaded artifact and independently verify its checksum and signature.

**Gate 8:** APK exists, installs, launches, passes smoke tests, and matches the recorded commit and checksum.

## Phase 9 — Review, merge, and handoff

- [ ] Update the requirement matrix and all audit documents with evidence and unresolved risks.
- [ ] Review the final diff for accidental UI/UX, dependency, permission, or behavior changes.
- [ ] Ensure no secrets or temporary signing files are committed.
- [ ] Require all CI checks and acceptance gates before opening/merging the final PR.
- [ ] Merge to `main) only after explicit final verification; do not merge a blocked candidate merely to make progress appear complete.
- [ ] Preserve rollback instructions and identify the exact verified artifact, source commit, checksum, and signing identity.

## Definition of done

The project is complete only when:
1. The original UI/UX remains unchanged unless explicitly authorized.
2. Critical tunnel lifecycle and traffic-release paths are tested and fail closed.
3. Real, independent leak verification is implemented and passes controlled end-to-end tests.
4. Trust and autonomy features advertised as active are connected to real observations and safe executors; all others are clearly advisory.
5. Privacy and supply-chain reviews have recorded evidence and addressed critical/high findings.
6. Unit, integration, emulator/device, and reproducible build checks pass.
7. The final APK is installed and smoke-tested, its signature and checksum are verified, and its source commit is recorded.
8. Documentation accurately distinguishes verified behavior from limitations and residual risk.

## Current known blockers at baseline

- GitHub Actions jobs previously failed before any step executed, with no runner allocated and no step logs; root cause still requires confirmation.
- No verified Gradle test suite, full reproducible build, device test, or APK is evidenced by the current audit.
- The leak probe is diagnostic and does not independently verify the five protection claims.
- Network-trust integration from live Android observations is incomplete.
- Monitor callback shutdown-race protection and regression tests remain outstanding unless subsequent commits prove otherwise.

**Status at plan creation:** execution incomplete; no APK or security certification claimed.


## New strategic direction — free, user-owned distributed IP ecosystem

**User decision:** pursue BOTH tracks: (1) continue strengthening GTor's existing Tor/relay capability rather than treating it as a new feature, and (2) investigate a separate, consent-based distributed IP network. The product goal is free access for users; infrastructure still has real costs, so “free” must be supported by a sustainable, transparent operating model rather than hidden data collection, undisclosed bandwidth sharing, or unsafe monetization.

**Important status distinction:** this is a planning decision, not evidence that a new residential IP network has been built. The user says the existing Tor/relay capability is already implemented; audit the current repository to verify exactly what is present before proposing incremental improvements. Do not claim that GTor has a large residential IP pool unless it has been independently measured and verified.

### Track A — Improve what GTor already has

- [ ] Map existing Tor integration, relay-related code, network lifecycle, routing, and current user-visible claims to actual implementation and tests.
- [ ] Preserve Tor protocol compatibility and existing UI/UX; prioritize reliability, safe lifecycle behavior, performance measurement, privacy, and reproducible builds.
- [ ] Identify improvements that add measurable value on top of existing functionality instead of reimplementing an already-present feature.
- [ ] Keep any relay operation transparent and compliant with Tor's architecture and relevant operator/provider requirements.
- [ ] Never route Tor exit traffic through an extra proxy layer by default or make unsupported anonymity/security claims.

### Track B — Research a new, opt-in distributed IP network

- [ ] Evaluate a separate network in which informed participants explicitly contribute a device or connection; participation must be opt-in, revocable, and off by default.
- [ ] Compare designs and threat models without connecting the new network to production traffic during research.
- [ ] Define technical isolation between participant devices, the control plane, and routed traffic; secure enrollment, authentication, updates, node revocation, rate limits, and abuse response.
- [ ] Specify traffic policy, participant controls, bandwidth/data-cap protections, resource limits, complaint handling, and legal/provider obligations before any prototype.
- [ ] Assess whether the intended use is compatible with Tor's security model. Treat residential proxy routing as a separate trust domain; never silently insert it into Tor circuits or claim it provides Tor anonymity.
- [ ] Do not expose participants' private IPs, precise locations, identifiers, browsing destinations, or DNS queries in dashboards or logs.
- [ ] Distinguish node count, distinct public IP count, independent networks/ASNs, geographic coverage, measured uptime, and usable bandwidth. Report only measured values, with freshness and methodology.
- [ ] Design for free end-user access from the start. Compare transparent funding, grants/donations, community-operated infrastructure, and carefully bounded sponsorship; do not assume bandwidth or hosting is costless.
- [ ] Prohibit hidden bandwidth resale, covert enrollment, forced participation, sale of user data, undisclosed ads, or monetization that undermines privacy.
- [ ] Estimate hosting, bandwidth, support, abuse operations, legal review, security monitoring, and replacement/revocation costs before selecting a scale target.
- [ ] Define measurable success criteria and a kill switch; abandon the design if participant safety, abuse control, legal feasibility, or sustainable free operation cannot be demonstrated.

### Learn from paid VPNs — benchmark features, not marketing claims

- [ ] Compare reputable paid VPN offerings for useful capabilities such as kill switch, IPv6/DNS leak protection, modern protocols, auto-reconnect, split tunneling, multi-hop options, independent audits, transparent privacy policies, and clear performance reporting.
- [ ] For every candidate feature, record the user benefit, implementation cost, battery/performance impact, privacy impact, compatibility with Tor, test evidence, and whether it belongs in GTor at all.
- [ ] Prefer features that can be delivered safely without subscriptions or user tracking. Do not copy proprietary code, branding, or unsupported “no logs,” “military-grade,” “unlimited,” or speed claims.
- [ ] Treat multi-hop or proxy chaining as an architecture decision requiring a threat-model review, not an automatic upgrade.
- [ ] Keep existing UI/UX unchanged. This research does not authorize new screens, navigation, or user-facing behavior.

### Free-for-users acceptance gates

- [ ] Document the actual cost model and credible funding/hosting plan before promising a sustainable network at scale.
- [ ] Demonstrate explicit participant consent, simple withdrawal, secure isolation, and a tested emergency disable/revocation path.
- [ ] Complete abuse, privacy, legal/provider-terms, and threat-model reviews.
- [ ] Verify all network and IP counts from live, privacy-safe measurements; never invent scale or availability.
- [ ] Prove that the existing Tor path is not weakened and that failures fail closed where required.
- [ ] Obtain explicit approval before any implementation or UI/UX change.

**Current status:** PLANNING / RESEARCH ONLY. No new distributed IP network has been implemented by this plan update. No UI/UX changes are authorized. “Free for users” is a goal; sustainable zero-cost infrastructure is not assumed.
