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

## Current execution blocker — 2026-10-27

The APK workflow is now enabled on `dangerous-experiment` as well as `main`, while release publication remains separately gated. The first verification run for the current branch is:

- Commit: `556a4f6ee30f211f5e7e42ddffaf878aa76b4fcc`
- Workflow run: https://github.com/thelittlephycopath-glitch/tor2/actions/runs/40568144676
- Result: failed before any job step executed.
- GitHub's job record reports `runner_id: 0`, an empty `runner_name`, and `steps: []`; the job completed in roughly three seconds. The cross-platform diagnostic has shown the same pattern on `windows-latest`.

This is evidence of a hosted-runner assignment/service/account-level blocker, not evidence of a Kotlin, Rust, Gradle, SDK, or APK-signing failure. The build has not reached compilation, and no APK artifact was produced. Repeated source edits cannot fix runner allocation. Check repository **Settings → Actions → General** and the account's Actions/billing/usage eligibility, then rerun the workflow. Do not treat a rerun as a code fix; once a runner executes steps, use the preserved build log to diagnose actual compiler or dependency failures.

## Current verified status

- [x] Build workflow push trigger includes `dangerous-experiment`.
- [x] Release publication remains explicitly opt-in and is not enabled by the default input.
- [ ] Hosted runner successfully assigned.
- [ ] Container/toolchain build reaches completion.
- [ ] Onionmasq Rust/Android build completes.
- [ ] Tor VPN Gradle release build completes.
- [ ] APK signing, alignment, and signature verification complete.
- [ ] Unit tests, ktlint, Android lint, staging build, and emulator smoke test pass.
- [ ] APK artifact uploaded and independently checked for package metadata, signature, and SHA-256.
- [ ] Device-level VPN, DNS, IPv4/IPv6, lockdown, reconnect, and traffic-leak tests pass.

Do not describe the APK as built until the remaining blocking checks are evidenced by successful runs.

## Release-publication guard correction — 2026-10-28

Static review found that the publish job condition allowed every push to `main` to publish a GitHub Release, even though the workflow-dispatch input defaults to false. That contradicted the stated opt-in safety gate. The condition now requires all three facts: the ref is `main`, the event is `workflow_dispatch`, and `inputs.publish_release` is explicitly true. A push to either branch can build and upload workflow artifacts but cannot publish a release. This is a static configuration correction; the hosted runner blocker still prevents a successful end-to-end workflow verification.

### Runner retry result — 2026-10-28

Retried the failed build workflow (run 40570108287, attempt 2) after confirming the branch workflow and release guard. It again failed before the first step:

- Job ID: `122421839083`
- `runner_id: 0`
- `runner_name: ""`
- `steps: []`
- Run: https://github.com/thelittlephycopath-glitch/tor2/actions/runs/40570108287

The failure is reproducible across multiple runs and both Ubuntu and Windows runner labels. This rules out a project-specific compile error as the immediate cause of those failures; it does not prove the account-level root cause. The owner should check repository Actions enablement/policies and account billing/usage restrictions. If those are clear, escalate to GitHub Support with the run URL because there is no job log to diagnose in source code. GitHub's workflow troubleshooting guide documents reviewing workflow logs, billing errors, Actions availability, and runner troubleshooting: https://docs.github.com/en/actions/how-tos/troubleshoot-workflows
