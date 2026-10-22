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

- The latest comparison on 2026-10-22 reports `security/performance-baseline` 24 commits ahead of `main` and 0 behind. Re-check immediately before any merge because the branch can advance.
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

## Update — focused cancellation regression coverage

- Commit: `1f45bc16678a67d9fa0595045cb9507fc14c0160`
- Added `cancelWhileConsumerIsBlockedDropsPendingWork()`: it holds the consumer in a blocking task, queues additional work, cancels, releases the blocker, and asserts queued callbacks do not execute.
- The existing cancellation test still verifies that enqueueing after cancellation is ignored.
- Source and test files were fetched again from GitHub after the writes; the lifecycle lock appears in both enqueue and cancel paths, and four TaskDebouncer test methods are present.
- The new tests remain **unexecuted**. The latest GitHub status and workflow-run queries still return no checks/runs, so no passing result can be claimed.


## Update — latest review pass (2026-10-22)

- Re-fetched the current branch versions of `TaskDebouncer.kt`, `TaskDebouncerTest.kt`, `TorVpnService.kt`, `settings.gradle.kts`, and `.gitlab-ci.yml`.
- GitHub combined status for commit `cc8d2269a26fd6ca98a21dee90bff9eb601e4a8c` returned no status entries; the GitHub workflow-run query returned no runs. This does not tell us whether the external GitLab pipeline ran.
- Confirmed the repository's existing GitLab validation jobs are `lintDebug`, `assembleStaging`, and `debugTests`; `publish` is a separate job. Do not run publishing as part of validation.
- Confirmed dependency resolution requires either the Tor Project Onionmasq Maven token configuration or an existing local Maven artifact. A generic GitHub runner cannot be assumed to have this dependency.
- Reviewed the new lifecycle-lock code and cancellation regression test at source level. The code remains uncompiled and tests remain unexecuted; source inspection is not a substitute for Gradle output.
- No source-code change was made during this pass because a compile/test environment is still unavailable and unverified edits would increase risk.
- Next gate: obtain a permitted CI run or a documented local environment with the required Onionmasq dependency; run focused tests, full `:app:testDebug`, ktlint, Android lint, and a staging build while ensuring the `publish` job does not run.


## Update — atomic claim review and stronger concurrent-producer assertion (2026-10-22)

- Commit `9257e1c1af8934121301d2471321e70023cece65` updates `TaskDebouncer` so the consumer validates that a polled item is still the newest and claims the running slot while holding `lifecycleLock`, the same lock used by producers. This closes the source-level window where a producer could replace the pending item between the consumer's stale check and its slot claim.
- Commit `d0603517bd3785d2110ae0d45df6d517d1f06d18` strengthens `concurrentProducersKeepOnlyNewestPendingTaskPerType`: the blocker keeps all candidate tasks pending, enqueue calls are serialized in the test, and the assertion checks the final accepted task ID rather than merely checking that one task ran.
- These are **source-level changes only; the Android Gradle tests have not been executed**. GitHub combined status and GitHub Actions workflow-run results are empty for the updated head. This does not prove whether external GitLab CI ran.
- The local container cannot resolve GitHub to clone this private repository, and no Gradle executable or Android SDK is configured in this environment. A complete repository build and the actual project test task therefore remain unavailable here. Do not represent a synthetic or source-only check as a passing project test.
- The repository's GitLab `publish` job has rules for tags and commits to the default branch. A merge into `main` can therefore cause the configured package-upload job to run in the target project's pipeline. Ensure the validation/merge plan accounts for that side effect.
- The merge gate remains closed pending real CI output for dependency resolution, `ktlintCheck`, `lintDebug`, `:app:testDebug`, and `assembleStaging`, plus the documented descriptor ownership/startup/device review. Do not run or enable publishing merely to validate the changes.


## Update — deterministic newest-pending regression setup (2026-10-22)

- Commit `1dc1bd726bac6a5236e7f4e81cd10cdbb12b5c6a` changes `newestPendingTaskOfSameTypeWins()` to block the consumer with latches while all 40 same-type tasks are enqueued, then release it and assert that only task 39 executes.
- This removes reliance on the initial debounce delay and a fixed post-execution sleep for that test. It improves determinism but is **not an executed test result**.
- Branch comparison, CI status, and GitHub Actions workflow queries still need to be refreshed after this commit. Merge remains blocked until real project CI and native/device checks produce evidence.


## Update — service teardown and startup-job ownership (2026-10-22)

- Commit `b4cc77a3efabbbaa150dda7939dd439df921aaa1` adds explicit service-destruction cleanup for the independent TaskDebouncer consumer, startup scope, periodic timer, and observers/collection job. This is source-reviewed but not exercised in an Android service test.
- Commit `bf6746437112d2fef6e417267e01a8e7c2622947` corrects startup-job ownership: the startup scope now uses a child `SupervisorJob(job)`, both on initial construction and recreation. Previously, the scope used the service root `job` directly, so `startCoroutineScope.cancel()` could cancel the root job and make a recreated scope inherit an already-cancelled parent.
- A small local Kotlin harness passed the isolated parent/child `SupervisorJob` cancellation semantics check: cancelling the child left the parent active, a fresh child was active under that parent, and cancelling the parent cancelled its child. This is **not** a build or test of the Android application or its actual Gradle test suite.
- Repeated VPN stop/start and service destruction still require runtime/device verification. Native descriptor ownership, Onionmasq startup readiness, and log retention remain open.
- The GitLab connection has been initiated but is not active yet. Do not call GitLab tools or claim pipeline results until authentication is completed and the connection is confirmed active.
