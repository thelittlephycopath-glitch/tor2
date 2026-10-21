# Tor VPN Beta — tor2 workspace

This repository is a private working copy for inspecting and repairing the Tor VPN Beta Android source before customization.

## Source import

Run the **Import Tor VPN Beta source** workflow from the Actions tab to download the public upstream source from Tor Project GitLab, apply the two checked-in build-script fixes, and commit the resulting source tree to `main`.

## Current status

- Build-script fixes are checked in under `.build-fixes/`.
- The import workflow is manual (`workflow_dispatch`) and only runs when explicitly triggered.
- An APK has **not** yet been built or verified. A full build needs Android SDK/NDK, Java 21, Gradle downloads, and the matching Onionmasq dependency.
- Tor VPN Beta is experimental; do not rely on it for sensitive activity.

Upstream source: https://gitlab.torproject.org/tpo/applications/vpn
