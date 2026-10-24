# Tor VPN Beta — additive autonomy roadmap

This branch adds a testable policy layer beside the existing Tor VPN service. It does not
replace the current service, Onionmasq integration, app-routing database, or UI.

## Implemented in this branch

- Platform-independent signal and decision models.
- Deterministic network-trust to protection-profile mapping.
- Fail-closed recommendation until the service independently verifies tunnel readiness.
- Captive-portal state represented separately; it does not authorize ordinary traffic.
- Conservative bridge ranking from existing observations only (no active probing).
- Battery/mobile-data budget gate for any future cover-traffic feature.
- Unit tests for decisions and input validation.

## Not implemented or claimed

- The policy engine is not yet wired into TorVpnService.
- It does not itself enforce Android always-on VPN or lockdown settings.
- It does not inspect other apps' private data, falsify device permissions, spoof identifiers,
  or create synthetic requests to unrelated endpoints.
- It does not prove DNS, IPv6, WebRTC, or tunnel leak resistance on a real device.
- It does not implement circuit splitting, mixnets, post-quantum transport, or ML prediction.
- Bridge failover and circuit refresh remain recommendations until supported by verified
  Onionmasq APIs and integration tests.

## Integration gates

1. Keep existing tunnel and routing architecture intact.
2. Verify exact Onionmasq APIs for readiness, circuit lifecycle, and transport failover.
3. Connect the engine to service lifecycle only after unit tests and build pass.
4. Treat an unknown tunnel state as unverified; never infer readiness from a start request.
5. Add integration tests for network changes, service cancellation, descriptor ownership,
   and failure recovery before enabling autonomous reconnect.
6. Keep all new behavior behind a disabled-by-default feature flag until device tests pass.
7. Do not merge this branch into main until CI passes and a reviewer confirms no bypass path.

## Build/test

Run the unit tests, ktlint, and assembleDebug task in the pinned Android/Rust build container.
CI results are authoritative; source creation alone is not proof that the branch builds.
