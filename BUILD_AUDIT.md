# Tor VPN Beta — deep build audit

Audit date: 2026-10-21
Repository: https://github.com/thelittlephycopath-glitch/tor2
Upstream: https://gitlab.torproject.org/tpo/applications/vpn

## Confirmed issues fixed in this commit

1. **Malformed Containerfile package-install chain.** The mkdir line lacked an `&&`, so apt-get clean could be interpreted as extra mkdir arguments. The chain is now explicit and apt metadata is cleaned.
2. **Invalid Android SDK platform package.** The app declares compileSdk 37 while the container requested platforms;android-37.0. It now requests platforms;android-37.
3. **Unstable Android SDK location.** ANDROID_HOME now uses /opt/android-sdk rather than relying on build-time PWD expansion.
4. **Incorrect NDK toolchain PATH.** The old LLVM/PATH values referenced a relative version directory instead of the installed NDK path.
5. **APK output directory not guaranteed.** The reproducible build script copied APKs into build/ after clean without ensuring the directory existed; it now creates it.
6. **local.properties could remain renamed after a failed build.** An EXIT trap restores it after success or failure.
7. **No end-to-end APK delivery workflow.** Added a manual GitHub Actions pipeline to build Onionmasq and Tor VPN, sign APKs with a temporary debug key, verify signatures, upload artifacts, and optionally publish private Release assets.

## Verification status

- Static review of the container recipe and reproducible build scripts completed.
- A full Android build has not yet passed. The workflow must run to expose any remaining compiler, dependency, toolchain, or upstream availability issues.
- Successful runs should publish direct APK assets to the repository Releases page and retain workflow artifacts for 14 days.
- APKs are signed with an ephemeral debug key, not Tor Project's official release key. They will not update over an official Tor-signed installation.

## Remaining audit scope

A static pass cannot certify every Kotlin, Rust, native networking, DNS, or runtime issue. Those require a successful full build, automated tests, emulator/device tests, and review of the published Cure53 audit findings. The workflow deliberately fails rather than publishing an APK if compilation, signing, or signature verification fails.

## Security notes

Tor VPN Beta is experimental and may leak information. These changes focus on build correctness and delivery and do not intentionally alter upstream networking behavior. Do not use these community builds for sensitive activity.
