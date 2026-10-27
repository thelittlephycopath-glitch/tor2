#!/usr/bin/env bash
set -euo pipefail

# Lightweight preflight: keep workflow dependencies immutable and build scripts parseable.
# This check needs no Android SDK, Docker image, network access, or secrets.

shopt -s nullglob
workflows=(.github/workflows/*.yml .github/workflows/*.yaml)
if (( ${#workflows[@]} == 0 )); then
  echo "No GitHub Actions workflow files were found." >&2
  exit 1
fi

checked=0
for workflow in "${workflows[@]}"; do
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^[[:space:]]*uses:[[:space:]]*([^[:space:]]+) ]] || continue
    target="${BASH_REMATCH[1]}"

    # Local reusable workflows and local composite actions do not use external refs.
    [[ "$target" == ./* ]] && continue

    if [[ "$target" != *@* ]]; then
      echo "External action has no explicit ref in $workflow: $line" >&2
      exit 1
    fi

    ref="${target##*@}"
    if [[ ! "$ref" =~ ^[0-9a-f]{40}$ ]]; then
      echo "External action must be pinned to a full 40-character commit SHA in $workflow: $line" >&2
      exit 1
    fi

    if [[ ! "$line" =~ \#[[:space:]]v[0-9]+\.[0-9]+\.[0-9]+([[:space:]]|$) ]]; then
      echo "Pinned action must carry a version comment for automated update tracking in $workflow: $line" >&2
      exit 1
    fi

    checked=$((checked + 1))
  done < "$workflow"
done

# Preserve the explicit release publication safeguard.
if ! grep -Fq "github.event_name == 'workflow_dispatch' && inputs.publish_release" .github/workflows/build-apk.yml; then
  echo "APK release publication is missing its manual-dispatch opt-in guard." >&2
  exit 1
fi
if ! grep -A 7 -F 'publish_release:' .github/workflows/build-apk.yml | grep -Eq '^[[:space:]]*default:[[:space:]]*false[[:space:]]*$'; then
  echo "APK release publication must default to disabled." >&2
  exit 1
fi

bash -n scripts/reproducible_build.sh scripts/reproducible_fetch_deps.sh

echo "CI security preflight passed: checked $checked immutable external action references, release opt-in guard, and reproducible build-script syntax."
