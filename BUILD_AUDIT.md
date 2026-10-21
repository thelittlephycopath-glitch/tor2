# Tor VPN Beta source build audit

Audit date: 2026-10-21
Source archive inspected: `vpn-main.zip` (root commit listed by archive: `57e304ba8c219ec48652c4bdd3b6f1da87a5cb9f`)

## Confirmed build-tooling defects fixed in this workspace

1. `scripts/reproducible_build.sh` attempted to edit and restore `build.gradle`, but this source tree uses `build.gradle.kts` and `settings.gradle.kts`. The stale `sed` and `git checkout build.gradle` steps were removed. The settings already support `ENFORCE_LOCAL_MAVEN=1` for the local Onionmasq artifact.
2. `scripts/update-offline-help.sh` passed the shell function `sed_in_place` to `find -exec`, which cannot resolve a shell function as an executable. The rewrite steps now use null-delimited Bash loops.
3. The offline-help updater supports both GNU sed on Linux and BSD sed on macOS.

## Checks and limitations

- ZIP integrity check passed.
- `bash -n scripts/reproducible_build.sh scripts/update-offline-help.sh` passed after changes.
- A Gradle build attempt stopped before Gradle configuration because this environment could not resolve `services.gradle.org` (`UnknownHostException`). This is an environment/network failure, not evidence of a Kotlin/Android compiler error.
- No APK has been produced or verified yet.

## Remaining requirements

- Gradle wrapper download/network access.
- Android SDK platform 37, Android build tools, and the NDK/toolchain required by Onionmasq.
- The matching `org.torproject:onionmasq` dependency, via the Tor Project Maven package with suitable access or a matching local Maven build.

## Safety

Tor VPN Beta is experimental and may leak information. These changes do not intentionally alter networking behavior or privacy protections.
