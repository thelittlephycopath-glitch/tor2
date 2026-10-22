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

## Iteration log

### 2026-10-22 — VPN service lifecycle source review

- Added an explicit null check for `Builder.establish()` before detaching the TUN file descriptor (commit `25bf5fe0cd2ad39c1794069ba0d5061d105beb80`).
- Recreated the startup coroutine scope when it is no longer active and made the usage timer recreatable after cancellation (commit `87550e8ff92ad2e9b255d0fe1363614b43ff724a`).
- Source review only. No associated GitHub Actions run or status check was returned for the latest commit. Compilation, start/stop/start behavior, descriptor cleanup, and device-level routing/leak tests remain unverified.
- Next: inspect startup/stop concurrency and TUN descriptor ownership; then establish an actionable build/test signal before making broader routing or performance changes.

- Follow-up source review found `TaskDebouncer.cancel()` permanently marks its instance cancelled, while `TorVpnService` retained that instance as a field. The service now recreates the debouncer after stop so a later start can enqueue work (commit `b73c540b1d3c6a767cc9d33d1e476e68c0d5671c`). Concurrent start/stop stress tests are still required; the field is volatile, but this is not a substitute for runtime race testing.

- Descriptor ownership follow-up: startup now closes the Android `ParcelFileDescriptor` on the non-detached failure path and adds a coroutine-completion cleanup for cancellation before the coroutine body runs (commit `f7ff764a8ab37b5c1c9d10f6982e2b406fbc2f8c` and follow-up `1a1a66fb27f97a64d67436f3a3782b47e73d02df`). This remains uncompiled; verify `ParcelFileDescriptor.isClosed` behavior and native `OnionMasq.start` descriptor ownership before accepting the fix.
