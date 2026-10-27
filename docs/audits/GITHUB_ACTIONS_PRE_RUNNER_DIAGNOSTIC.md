# GitHub Actions pre-runner failure diagnosis

Date: 2026-10-28  
Repository: `thelittlephycopath-glitch/tor2`  
Branch: `dangerous-experiment`

## What the evidence says

For diagnostic workflow run [40603725729](https://github.com/thelittlephycopath-glitch/tor2/actions/runs/40603725729), GitHub's job API reported:

- Requested runner label: `ubuntu-24.04`
- Runner ID: `0`
- Runner name: empty
- Steps recorded: `0`
- Attempt 1 and attempt 2 both ended in failure before a runner was assigned.
- The separate APK workflow showed the same pre-step failure pattern.

The repository has no registered self-hosted runners. The Actions usage endpoint returned an empty billable-usage object for the two workflows. The account's October Actions usage summary showed 2,000 Linux minutes discounted and net Actions usage of $0; this is not, by itself, proof of remaining minute allowance or account eligibility.

**Conclusion:** the failed runs contain no Gradle, Kotlin, Rust, Docker, or APK compiler output. The observed failure is before the job's first step. A source-code change cannot fix a hosted runner that never starts, and the exact platform-side reason is not exposed in the job metadata available to this diagnostic.

## Repeatable API diagnostic

Use `scripts/diagnose_actions_runner.py` to capture the run metadata and classify each job. It requires Python 3.10+ and a GitHub token with Actions read access for this repository. Keep the token in an environment variable; never commit or paste it into the repository.

```sh
export GITHUB_TOKEN='YOUR_TOKEN'
python3 scripts/diagnose_actions_runner.py 40603725729
```

The script prints JSON containing the run commit, status, runner labels/IDs, whether any steps ran, elapsed time, and an explicit classification. Exit status is:
- `0`: no failed-before-runner job detected
- `1`: at least one failed job has no runner ID and no steps
- `2`: usage, authentication, or API error

## Next diagnostic branch

If this reports `FAILED_BEFORE_RUNNER_OR_STEP_START`, check the repository/account Actions page for runner provisioning, account spending/usage limits, and service-side notices. If a runner is assigned and steps appear, use those step results and logs to diagnose the actual build. Do not change app source or merge to `main` based only on a pre-runner failure.

## Release gate

No APK has been produced or verified from these runs. This report does not certify the app as buildable, secure, or release-ready.


## Follow-up retry — 2026-10-28

After the diagnostic script and this note were committed, both workflows were dispatched again against commit `b84e8108ccb40cd69ffd74c4fac26199adb26c38`.

- APK run [40604376977](https://github.com/thelittlephycopath-glitch/tor2/actions/runs/40604376977): build job `122540098305` failed in ~2 seconds, runner label `ubuntu-24.04`, `runner_id=0`, empty runner name, zero steps. Release job was correctly skipped.
- Diagnostic run [40604376709](https://github.com/thelittlephycopath-glitch/tor2/actions/runs/40604376709): job `122540096540` failed in ~2 seconds with the same runner metadata and zero steps.
- Repository Actions permission API says Actions are enabled and all actions are allowed.
- Repository runner listing reports zero self-hosted runners.
- Workflow usage endpoint returns an empty `billable` object. This endpoint response does not establish the account's remaining included minutes.
- Changing the runner label from `ubuntu-latest` to `ubuntu-24.04` did not change the failure pattern.

### Current root-cause boundary

The reproducible fault is **job startup / hosted-runner assignment before step execution**, not an observed Gradle/Kotlin/native compiler failure. Available job/check metadata contains no actionable platform message, and the logs endpoint has not supplied runner logs. Do not claim the exact cause is quota, billing, or a GitHub outage without account/service evidence.

### Next action

Run `scripts/diagnose_actions_runner.py <run-id>` with a token kept locally in `GITHUB_TOKEN`, then inspect the repository owner's Actions billing/usage and spending-limit settings and GitHub's Actions service status. If the account settings look normal and jobs continue to fail before runner assignment, report the run URLs and timestamps to GitHub Support. Once a runner executes steps, fix the concrete failure shown in logs. No APK, successful build, or merge to `main` is evidenced by these attempts.
