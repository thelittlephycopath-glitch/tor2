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

## Artifact integrity gate — 2026-10-28

The APK workflow now performs a post-signing integrity pass before artifact upload:

- Re-runs APK signature verification for every signed APK.
- Reads the APK package ID from the APK manifest and fails if it is not `org.torproject.vpn.tor2beta.dev`.
- Records each APK's filename, package ID, and byte size in `dist/BUILD-METADATA.txt`.
- Generates `dist/SHA256SUMS` for the signed APK files and uploads both verification files alongside the APKs.

This makes a successful future artifact independently checkable after download. It does not substitute for a successful workflow run or runtime VPN security tests.

- Workflow change: [`f073559`](https://github.com/thelittlephycopath-glitch/tor2/commit/f0735594c87c062d9d603c50c376b2f76292f866)
- Triggered run: https://github.com/thelittlephycopath-glitch/tor2/actions/runs/40571377421
- Result: runner job failed before any steps/logs were exposed; this run does not validate the new metadata gate.

## CI supply-chain hardening — 2026-10-28

The branch's GitHub Actions dependencies are now pinned to full commit SHAs instead of movable major-version tags. The inline version comments are retained so Dependabot can propose reviewed upgrades. Current pins:

- `actions/checkout` `3d3c42e5aac5ba805825da76410c181273ba90b1` (`v7.0.1`)
- `actions/cache` `55cc8345863c7cc4c66a329aec7e433d2d1c52a9` (`v6.1.0`)
- `actions/upload-artifact` `cf430e030ddbb5b0abf93d22962f4752f3646cd9` (`v7.0.2`)
- `actions/download-artifact` `9000827ccba6bdab643e8b6fd33ac0654aef8333` (`v8.0.2`)
- `actions/setup-java` `b843ebf9b913146b38da2eaa7aeddf76074ae1ad` (`v6.1.0`)
- `android-actions/setup-android` `be39fa834029ff78f1a44aa3bb0819b8fc2bd8fd` (`v4.0.4`)
- `reactivecircus/android-emulator-runner` `a421e43855164a8197daf9d8d40fe71c6996bb0d` (`v2.38.0`)
- `softprops/action-gh-release` `efb35369e0ad2afab669f228072c1b0d510eae64` (`v3.0.3`)

Added `.github/dependabot.yml` to request weekly GitHub Actions dependency updates. Version updates remain reviewable PRs rather than silently moving action refs.

Added `scripts/verify_ci_security.sh` as a lightweight, offline preflight. It checks external action references for 40-character SHA pins and version comments, checks the manual release-publication guard/default-off input, and syntax-checks the reproducible build scripts. The preflight is invoked before expensive toolchain builds in the APK, diagnostic-build, and PR-validation workflows. A local fixture test passed for valid pins and correctly rejected a mutable `@v7` reference. This fixture test is not a substitute for running the script in GitHub Actions against the full checkout.

The action gate test suite now also includes an exhaustive scenario where every automation flag is enabled and every action is recommended: only the two fail-closed actions remain approved; all side-effecting actions remain suppressed as `ACTION_NOT_IMPLEMENTED`. The source test is committed but has not run under Gradle.

### Post-hardening workflow evidence

- Latest branch-triggered build run: https://github.com/thelittlephycopath-glitch/tor2/actions/runs/40574107343
- Result: failed before any step logs were exposed (`steps: null`, no job log URL).
- The workflow run therefore does not verify action execution, preflight execution, compilation, tests, APK signing, or artifact integrity.
- No APK is confirmed as built. The runner/Actions environment blocker remains open.

## APK build deferral aligned with plan execution — 2026-10-28

The APK workflow now uses manual dispatch only. Automatic push triggers were removed from `dangerous-experiment` so backend and privacy work can proceed without repeatedly launching a known-failing hosted-runner job while APK work is intentionally deferred. The existing explicit manual release-publication gate remains default-off and restricted to manual dispatch on `main`.

- Workflow change: [`f8b7a4b`](https://github.com/thelittlephycopath-glitch/tor2/commit/f8b7a4b112426fc93d82492dde209944a8662bae)
- This does not repair the runner assignment problem or validate source compilation. The APK workflow can be manually resumed after the backend plan's acceptance gates are ready.

## Post-change static workflow verification — 2026-10-28

A fresh repository-tree read of `dangerous-experiment` checked all five workflow YAML files after deferring APK work:

- 16 external `uses:` references checked; all 16 are pinned to 40-character commit SHAs and include version comments.
- No unpinned external action reference was found.
- APK build workflow has no `push` trigger and is manual-dispatch only.
- Release publication still requires `refs/heads/main`, `workflow_dispatch`, and explicit `inputs.publish_release`; the input default remains `false`.
- This was a source-content inspection through the GitHub API, not execution of the repository's shell preflight or a CI job. No build/test pass is implied.

