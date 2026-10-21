# Security and performance follow-up

## Changes made in this pass

- The CI workflow now persists Gradle and Cargo dependency/build caches between runs.
- Gradle may execute independent tasks in parallel, with the worker count capped at two for the standard GitHub-hosted runner.
- APK signing remains ephemeral and debug-only; the workflow verifies every APK signature before upload.
- The build does not change Tor routing, cryptography, DNS policy, circuit selection, or transport behavior without measurement and protocol review.

## Performance plan

1. Record cold-start and warm-start connection times on the same device/network.
2. Measure DNS lookup latency, time-to-first-byte, sustained throughput, battery use, and reconnect rate across several runs.
3. Compare the same Tor VPN version and network conditions before and after each runtime change.
4. Only tune the measured bottleneck. Tor traffic is still relayed through the Tor network, so it should not be expected to match direct internet speed.

## Security plan

- Review every finding in Tor Project's published VPN audit against the exact upstream revision used here.
- Add regression tests for input validation and DNS edge cases before changing their handling.
- Keep sensitive configuration protected by Android storage controls; do not invent custom cryptography.
- Do not disable VPN lockdown, leak protections, certificate validation, or Tor safeguards for speed.
- Treat a successful build as build verification only, not a security audit or endorsement.

## Release status

This repository's APKs are community builds signed with an ephemeral debug key. They are not signed with Tor Project's official release key and cannot replace/update an official-key installation. Do not use these experimental builds for high-risk or sensitive activity.
