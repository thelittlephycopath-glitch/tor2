# Privacy and implementation-claim audit

Date: 2026-10-28
Branch: `dangerous-experiment`

## Scope and constraints

This is a source-level review, not a runtime privacy certification. The original UI/UX and the displayed privacy-policy text were not changed. No packet capture, rooted-device inspection, emulator test, or Gradle test was available in this review.

## Findings

### 1. The displayed policy contains claims that need app-specific verification

The checked-in `doc/PRIVACY_POLICY.md` and packaged `app/src/main/assets/help/PRIVACY_POLICY.md.html` contain broad claims such as no collection of IP addresses, browsing history, device IDs, or location; no tracking/telemetry; no syncing or cloud backups; and statements describing Tor Browser for desktop and Android. This repository is an experimental VPN app with its own VPN lifecycle, Android network diagnostics, and autonomy scaffolding. A policy inherited from another app or project should not be treated as proof that this app's behavior matches every claim.

This audit deliberately does not rewrite the policy because it is user-facing text and the UI/UX preservation requirement remains in force. Before any public release, the policy owner should reconcile the claims against the actual app, dependencies, crash reporting/build configuration, update path, VPN routing behavior, and any enabled network-learning feature.

### 2. Network-learning persistence is privacy-minimized but not evidence of active collection

The source includes:
- A per-install Android Keystore HMAC key provider.
- A hasher that accepts caller-supplied network identity fields and emits a keyed digest.
- A bounded, retention-limited store that deletes records when explicit opt-in is false.
- Backup/device-transfer exclusions for the store's shared-preference file.
- Unit tests for opt-out deletion, retention, malformed data, capacity limits, and duplicate handling.

These components alone do not prove that the feature is integrated into the application, that consent is presented to users, or that any real network identifiers are collected. Repository-wide references found in this pass point to the implementation and tests, but no verified runtime call path from Android network callbacks to this persistence was established. Do not claim the feature is fully integrated or active until the call path, consent control, and deletion path are reviewed and tested.

### 3. Backup posture is conservative

The application manifest sets `android:allowBackup="false"` and explicitly references both legacy backup rules and Android 12+ data-extraction rules. Both rule files exclude `autonomy_network_learning_v1.xml` from backup/device transfer. The CI preflight now checks these invariants. This is source evidence only; platform behavior still requires device-level verification on supported Android versions.

### 4. Network trust evidence still lacks enforced session binding

The leak-evidence coordinator rejects stale leak evidence from an earlier session generation, with a regression test added. The network-trust evidence model has a session-generation field, but the coordinator's trust-classification path has not yet been migrated to enforce it, and the current coordinator tests have not been updated to establish that contract. Treat network-trust session binding as **not implemented** until the model, caller, coordinator, and regression tests are updated together.

### 5. No user-facing claims should imply verified leak protection yet

The Android path probe reads public Android route, DNS configuration, VPN transport, and lockdown-setting information. Its evidence-producing method intentionally returns false for IPv4 protection, IPv6 containment, DNS protection, system lockdown verification, and app-routing verification. Configuration snapshots are not proof of packet enforcement. The actual independent leak verifier, active DNS/IPv6 checks, per-app routing proof, and fail-closed enforcement tests remain outstanding.

## Release gates

Before release, require:
1. A line-by-line policy-to-implementation review by the policy owner.
2. A documented data inventory covering app preferences, Tor state, logs, dependencies, network-learning data, crash reports, and update traffic.
3. An integration test proving opt-in is explicit, off-by-default, and revocation deletes persisted records.
4. Device tests confirming backup/transfer exclusions and actual VPN behavior.
5. A real leak-verification implementation whose measured evidence is bound to the active network/tunnel generation.
6. No public claim of anonymity, leak prevention, or hostile-network detection beyond what has been independently tested.
