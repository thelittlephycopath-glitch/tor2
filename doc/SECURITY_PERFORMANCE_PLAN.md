# Tor VPN Beta: Security and Performance Work Plan

Status: baseline inspection started; no runtime security claims have been verified yet.

## Scope and rules

- Preserve Tor's anonymity properties; do not weaken encryption or routing to improve benchmark numbers.
- Do not advertise the app as anonymous or leak-proof until tests support the claim.
- Keep changes on a dedicated branch and require a successful build plus regression tests before merge.
- Do not add a second VPN protocol or tunnel stack until the current Tor/Onionmasq path is understood and measured.

## Baseline measurements

Record before/after results on the same device and network:
- cold-start time and time to Tor bootstrap readiness;
- reconnect time after Wi-Fi/mobile switching and temporary network loss;
- sustained throughput, latency, CPU, memory, and battery;
- DNS, IPv4, IPv6, and traffic-during-disconnect leak tests;
- behavior after process death, reboot, VPN permission revocation, and private-DNS settings changes.

## Initial source-review observations

These are review leads, not confirmed vulnerabilities:

1. `TorVpnService.prepareVpnProfile()` installs default IPv4 and IPv6 routes, adds internal DNS addresses, allows both address families, and sets MTU to 1500. Verify the native packet path handles both families and DNS consistently. Do not change MTU or routes without reproducible device tests.
2. `TorVpnService.createObservers()` records connection events including proxy endpoints, destination metadata, app IDs, and relay addresses/identities in the app's log observable. Determine retention, access controls, export behavior, and whether detailed logs are enabled by default. Minimize or redact sensitive metadata where compatible with diagnostics.
3. `TorVpnService.establishVpn()` uses a nullable result from `Builder.establish()` and detaches its file descriptor inside a coroutine. Verify null handling, descriptor ownership/closure on all failure paths, and races between stop/start.
4. `stop()` cancels `startCoroutineScope`; verify a later start in the same service instance cannot reuse a cancelled scope. Add lifecycle tests before changing lifecycle code.
5. The manifest requests `QUERY_ALL_PACKAGES` and boot-related permissions. Review each against app-routing and always-on VPN requirements; retain only justified permissions and document the user-facing need.
6. The build workflow uses an ephemeral debug signing key. Suitable for development artifacts only; not a stable release signing identity.

## Work sequence

### Gate 1 — Establish a trustworthy build
- Capture the latest workflow result and exact failed step/log.
- Make the clean release build reproducible and preserve diagnostic logs.
- Confirm generated APK names, ABI coverage, installability, signature verification, and SHA-256 hashes.

### Gate 2 — VPN lifecycle and routing safety
- Test null TUN descriptor handling and descriptor cleanup.
- Test start/stop/start races, duplicate start intents, service recreation, and VPN permission revocation.
- Test fail-closed behavior and Android always-on/lockdown interactions.
- Test IPv4, IPv6, DNS, private DNS, and network handovers.
- Never claim kill-switch protection based only on UI state.

### Gate 3 — Sensitive data minimization
- Trace log creation, storage, retention, export, and access.
- Redact connection endpoints, relay identity/address details, and app identifiers from routine logs where possible.
- Keep diagnostics opt-in where feasible; never log credentials or browsing history.
- Review backup, exported components, and permissions.

### Gate 4 — Performance
- Profile native packet processing and Kotlin service work before optimizing.
- Measure startup, bootstrap, throughput, latency, CPU, memory, and battery.
- Investigate allocation/copy overhead, timer wakeups, unnecessary repeated initialization, and reconnect loops.
- Change one factor at a time and compare against the baseline.

### Gate 5 — Protocol choices
- Prioritize maintained Tor bridge/pluggable-transport support compatible with Onionmasq.
- Treat VPN-before-Tor or Tor-before-VPN as separate trust models, not automatic privacy upgrades.
- Do not invent cryptography or bundle unmaintained protocol implementations.

### Gate 6 — Release integrity
- Pin third-party workflow actions to verified immutable revisions where practical.
- Produce dependency inventory/SBOM and dependency audit results.
- Keep development signing separate from protected release signing.
- Publish source revision, checksum, signature information, limitations, and test evidence.
- Require a clean build and passing regression suite before release.

## Acceptance criteria

A release is not considered ready until:
- a clean build succeeds and its artifacts are verified;
- routing, DNS, IPv4/IPv6, disconnect, and lifecycle regression tests pass;
- performance changes have measured evidence and no security regression;
- sensitive logging and permissions have been reviewed;
- build provenance, checksum, signing status, and limitations are documented;
- unresolved high-severity findings are explicitly documented and block a production-security claim.
