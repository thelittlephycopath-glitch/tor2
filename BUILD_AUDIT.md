# Tor VPN Beta — deep build audit

Audit date: 2026-10-21
Repository: https://github.com/thelittlephycopath-glitch/tor2
Upstream: https://gitlab.torproject.org/tpo/applications/vpn

## Confirmed issues fixed in this workspace

1. **Malformed Containerfile package-install chain.** The mkdir line lacked an `&&`, so apt-get clean could be interpreted as extra mkdir arguments. The chain is now explicit and apt metadata is cleaned.
2. **SDK package IDs, preview channel, and separate app/Onionmasq toolchains.** The app compiles against Android 17/API 37, distributed through the preview SDK channel, while Onionmasq remains pinned to stable API 36. Both `Containerfile` and `scripts/reproducible_fetch_deps.sh` now request `platforms;android-37` and Build Tools 37.0.0 from `sdkmanager --channel=3`, and API 36 plus Build Tools 36.0.0 from `--channel=0`. The platform ID uses no `.0` suffix. The standalone reproducible script also reinstalls required packages on every run instead of skipping a partially created SDK directory, and passes an explicit SDK root for the NDK install. See the [Android 17 SDK setup](https://developer.android.com/about/versions/17/setup-sdk) and [`sdkmanager` package/channel syntax](https://developer.android.com/tools/sdkmanager).
3. **Unstable Android SDK location.** `ANDROID_HOME` now uses `/opt/android-sdk` rather than relying on build-time PWD expansion.
4. **Incorrect NDK toolchain PATH.** The old LLVM/PATH values referenced a relative version directory instead of the installed NDK path.
5. **APK output directory not guaranteed.** The reproducible build script copied APKs into `build/` after clean without ensuring the directory existed; it now creates it.
6. **Wrong return path after the Onionmasq sub-build.** The script was one directory level short of the repository root, which would make the following Gradle command run from the wrong directory. Corrected to return to the project root.
7. **`local.properties` could remain renamed after a failed build.** An EXIT trap now restores it after success or failure.
8. **No end-to-end APK delivery workflow.** Added a GitHub Actions pipeline to build Onionmasq and Tor VPN, sign APKs with a temporary debug key, verify signatures, upload artifacts, and optionally publish Release assets.

## Verification status

- Static review of the container recipe and reproducible build scripts completed.
- Earlier CI evidence showed SDK provisioning failures before app compilation. Static review found invalid API-37 package syntax in both the container and standalone reproducible setup; both now request `platforms;android-37` from the preview channel and API 36 from stable, with matching Build Tools versions. The standalone script retries package installation even if its SDK directory already exists.
- The app compile SDK remains API 37, its target SDK remains API 36, and Onionmasq remains pinned to API 36. A post-fix build is still required to verify SDK provisioning, Rust/Onionmasq compilation, Gradle compilation, signing, and artifact upload in sequence.
- No verified APK exists until a workflow run completes successfully and its artifact/signature are checked.
- APKs are signed with an ephemeral debug key, not Tor Project's official release key. They will not update over an official Tor-signed installation.

## Remaining audit scope

A static pass cannot certify every Kotlin, Rust, native networking, DNS, or runtime issue. Those require a successful full build, automated tests, emulator/device tests, and review of the published Cure53 audit findings. The workflow deliberately fails rather than publishing an APK if compilation, signing, or signature verification fails.

## Security notes

Tor VPN Beta is experimental and may leak information. These changes focus on build correctness and delivery and do not intentionally alter upstream networking behavior. Do not use these community builds for sensitive activity.
