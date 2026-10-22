# Stabilization Work Log

**Branch:** `security/performance-baseline`  
**Status:** active investigation; no merge authorized  
**Last reviewed:** 2026-10-22

## Safety rules for this iteration

- Keep all work on the stabilization branch.
- Do not modify `main` directly.
- Do not create the next experiment branch until the stabilization PR has been verified and merged.
- Do not claim tests, builds, leak tests, or device tests passed unless their actual output is recorded.
- Do not publish or sign a release artifact during validation.
- Preserve the project's experimental-app and possible-leak warnings until evidence supports changing them.

## Confirmed repository facts

- The stabilization branch is 16 commits ahead of `main` and not behind it at the time of review.
- Draft PR #1 currently includes changes to `TaskDebouncer.kt`, `TorVpnService.kt`, `TaskDebouncerTest.kt`, and this security/performance plan.
- The existing GitLab pipeline defines `ktlintCheck`, `lintDebug`, `assembleStaging`, and `:app:testDebug` jobs. Its publish job is separate and must not be triggered for validation.
- `settings.gradle.kts` resolves Onionmasq from the Tor Project Maven endpoint when configured with a token, or from `mavenLocal()` otherwise.
- GitHub status checks and pull-request workflow runs were empty for the reviewed commit. This is not proof that GitLab CI has not run.
- The regression test file exists, but its tests have not yet been compiled or executed in this environment.

## Findings requiring follow-up

### A. TaskDebouncer cancellation/enqueue race

The cancellation flag is checked before queue publication, while cancellation can clear the queue between those operations. A producer that passed the early check may subsequently publish work and call `start()`. `process()` checks the terminal flag, which reduces the risk of executing the block, but the queue/consumer lifecycle can still be left in an inconsistent state.

**Next action:** serialize the enqueue publication sequence with terminal cancellation, then add a regression test that coordinates a producer and cancellation deterministically. Verify that the synchronization design cannot deadlock with consumer startup or cancellation callbacks. Run the focused test and full unit suite before calling it fixed.

### B. Startup readiness is not established by the latch

In `TorVpnService.establishVpn()`, `waitCoroutineStarted.countDown()` occurs at the beginning of the coroutine, before `OnionMasq.start()`. Therefore, the 15-second wait only observes coroutine entry; it does not prove the native tunnel is ready. Do not change readiness state based on assumptions about native startup behavior.

**Next action:** inspect the pinned Onionmasq API and event semantics. Define what proves the native tunnel is ready, then test startup failure, cancellation, and service stop during startup.

### C. TUN descriptor ownership needs native-contract verification

The code detaches the descriptor before calling `OnionMasq.start()`, and completion cleanup attempts to close the Android descriptor if still open. Once detached, the integer file descriptor has a different ownership model. Do not add a speculative close of the detached integer or assume the native API closes it on every failure path.

**Next action:** establish the exact native ownership contract and test pre-start cancellation, detach, native-start exception, and service teardown.

### D. Sensitive connection diagnostics

Connection log events include proxy endpoints, Tor destination metadata, app IDs, and circuit relay information. Before changing log content, trace the logging dependency's storage, export, retention, and access behavior. Prefer data minimization, but retain enough diagnostics for safe troubleshooting.

### E. Stop/start and observers

The service recreates a cancelled TaskDebouncer and startup scope. Repeated stop events, observer removal posted to the main thread, and the interaction between native stop and coroutine cancellation need tests. Avoid broad lifecycle refactors until the test harness can exercise these paths.

## Validation gate

Before merge, collect actual evidence for:

- [ ] Dependency resolution and clean build from a documented environment.
- [ ] Kotlin formatting/lint and Android lint.
- [ ] Focused TaskDebouncer regression tests and the complete unit-test task.
- [ ] Staging build success without running the publish job.
- [ ] Review of source diffs for descriptor ownership, lifecycle, and exception paths.
- [ ] Device evidence for IPv4, IPv6, DNS, handover, disconnect, lockdown, and private-DNS behavior.
- [ ] Log privacy/retention review.
- [ ] Artifact source SHA, ABI, signature, alignment, and checksum if an APK is produced.
- [ ] Explicit list of unverified risks.

If the required Tor Maven access or device testing is unavailable, report the blocker. Do not substitute source review for a passing build or device test.

## Update — cancellation/enqueue serialization patch

- Commit: `b288c5c0862b44e5b9110e5e2158210d37c363a0`
- Added a lifecycle lock around enqueue publication and terminal cancellation cleanup, so a producer cannot publish new queued work after cancellation has cleared the queue.
- Cancellation now clears the producer work signal and remains idempotent.
- This is a source-level race fix, **not yet compiled or tested**. The consumer may already be executing a callback when cancellation is requested; callers must not treat `cancel()` as proof that an already-running synchronous callback has completed.
- Next: add/strengthen a focused regression test, run the unit-test task and lint, then review the full diff for lock ordering and lifecycle semantics.
