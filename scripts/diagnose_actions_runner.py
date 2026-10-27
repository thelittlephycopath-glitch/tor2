#!/usr/bin/env python3
"""Diagnose GitHub Actions jobs that fail before a runner starts.

Requires Python 3.10+ and GITHUB_TOKEN with Actions: read and Checks: read.
Usage:
  GITHUB_TOKEN=... python3 scripts/diagnose_actions_runner.py 40603725729
"""
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime
from typing import Any

API = "https://api.github.com"
OWNER = "thelittlephycopath-glitch"
REPO = "tor2"


def get(path: str, token: str) -> Any:
    request = urllib.request.Request(
        API + path,
        headers={
            "Accept": "application/vnd.github+json",
            "Authorization": f"Bearer {token}",
            "X-GitHub-Api-Version": "2022-11-28",
            "User-Agent": "gtor-actions-runner-diagnostic",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return json.load(response)
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"GitHub API returned HTTP {exc.code} for {path}: {body[:500]}") from exc


def elapsed(start: str | None, end: str | None) -> float | None:
    if not start or not end:
        return None
    a = datetime.fromisoformat(start.replace("Z", "+00:00"))
    b = datetime.fromisoformat(end.replace("Z", "+00:00"))
    return round((b - a).total_seconds(), 2)


def main() -> int:
    if len(sys.argv) != 2 or not sys.argv[1].isdigit():
        print(f"Usage: GITHUB_TOKEN=... {sys.argv[0]} WORKFLOW_RUN_ID", file=sys.stderr)
        return 2
    token = os.environ.get("GITHUB_TOKEN")
    if not token:
        print("ERROR: GITHUB_TOKEN is not set (token value is never printed).", file=sys.stderr)
        return 2

    run_id = int(sys.argv[1])
    base = f"/repos/{OWNER}/{REPO}"
    run = get(f"{base}/actions/runs/{run_id}", token)
    jobs_payload = get(f"{base}/actions/runs/{run_id}/jobs?filter=all&per_page=100", token)
    jobs = []
    pre_runner_failures = []
    for job in jobs_payload.get("jobs", []):
        steps = job.get("steps") or []
        record = {
            "name": job.get("name"),
            "job_id": job.get("id"),
            "status": job.get("status"),
            "conclusion": job.get("conclusion"),
            "runner_label": job.get("labels", []),
            "runner_id": job.get("runner_id"),
            "runner_name": job.get("runner_name"),
            "steps_count": len(steps),
            "steps": [
                {"name": step.get("name"), "status": step.get("status"),
                 "conclusion": step.get("conclusion")}
                for step in steps
            ],
            "elapsed_seconds": elapsed(job.get("started_at"), job.get("completed_at")),
            "job_url": job.get("html_url"),
        }
        if job.get("conclusion") == "failure" and not steps and not job.get("runner_id"):
            record["classification"] = "FAILED_BEFORE_RUNNER_OR_STEP_START"
            pre_runner_failures.append(record)
        elif job.get("conclusion") == "failure":
            record["classification"] = "FAILED_AFTER_JOB_STARTED_OR_STEPS_RECORDED"
        else:
            record["classification"] = "NOT_A_FAILURE"
        jobs.append(record)

    report = {
        "diagnostic_version": 1,
        "repository": f"{OWNER}/{REPO}",
        "run": {
            "id": run.get("id"),
            "name": run.get("name"),
            "event": run.get("event"),
            "branch": run.get("head_branch"),
            "commit": run.get("head_sha"),
            "status": run.get("status"),
            "conclusion": run.get("conclusion"),
            "attempt": run.get("run_attempt"),
            "created_at": run.get("created_at"),
            "run_started_at": run.get("run_started_at"),
            "updated_at": run.get("updated_at"),
            "url": run.get("html_url"),
        },
        "job_count": len(jobs),
        "pre_runner_failure_count": len(pre_runner_failures),
        "jobs": jobs,
        "interpretation": (
            "One or more jobs failed with no runner ID and no steps. This run contains no "
            "compiler/build evidence; investigate GitHub Actions runner provisioning, account "
            "billing/limits, runner-group policy, or GitHub service status. Do not infer a "
            "Gradle/Kotlin source failure from this result."
            if pre_runner_failures else
            "A runner was assigned or steps were recorded for each failed job. Inspect the "
            "listed step outcomes and workflow logs for the actionable build failure."
        ),
        "pre_runner_failures": pre_runner_failures,
    }
    print(json.dumps(report, indent=2))
    return 1 if pre_runner_failures else 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, TimeoutError, urllib.error.URLError) as exc:
        print(f"DIAGNOSTIC ERROR: {exc}", file=sys.stderr)
        raise SystemExit(2)
