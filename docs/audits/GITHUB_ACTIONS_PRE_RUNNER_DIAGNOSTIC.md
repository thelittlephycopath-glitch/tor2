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
