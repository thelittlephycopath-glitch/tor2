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
    [[ "$line" =~ ^[[:space:]]*(-[[:space:]]+)?uses:[[:space:]]*([^[:space:]]+) ]] || continue
    target="${BASH_REMATCH[2]}"

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

if (( checked == 0 )); then
  echo "No external GitHub Actions references were checked; refusing to pass silently." >&2
  exit 1
fi

# Preserve the explicit release publication safeguard.
if ! grep -Fq "github.event_name == 'workflow_dispatch' && inputs.publish_release" .github/workflows/build-apk.yml; then
  echo "APK release publication is missing its manual-dispatch opt-in guard." >&2
  exit 1
fi
if ! grep -A 7 -F 'publish_release:' .github/workflows/build-apk.yml | grep -Eq '^[[:space:]]*default:[[:space:]]*false[[:space:]]*
# Keep upstream source replacement isolated to the experiment branch and an immutable source revision.
if ! grep -Fq "github.ref == 'refs/heads/dangerous-experiment'" .github/workflows/import-source.yml; then
  echo "Upstream source import must be restricted to dangerous-experiment." >&2
  exit 1
fi
if ! grep -Fq 'UPSTREAM_COMMIT: ${{ inputs.upstream_commit }}' .github/workflows/import-source.yml ||
   ! grep -Fq '[[ ! "$UPSTREAM_COMMIT" =~ ^[0-9a-f]{40}$ ]]' .github/workflows/import-source.yml; then
  echo "Upstream source import must require and validate a full commit SHA." >&2
  exit 1
fi
if ! grep -Fq 'cp -a .github /tmp/tor-vpn-local-github' .github/workflows/import-source.yml ||
   ! grep -Fq 'cp -a /tmp/tor-vpn-local-github .github' .github/workflows/import-source.yml; then
  echo "Upstream source import must preserve local GitHub workflow controls." >&2
  exit 1
fi

for script in scripts/reproducible_build.sh scripts/reproducible_fetch_deps.sh; do
  bash -n "$script"
done

echo "CI security preflight passed: checked $checked immutable external action references, release opt-in guard, and reproducible build-script syntax."
; then
  echo "APK release publication must default to disabled." >&2
  exit 1
fi
if ! grep -A 7 -F 'run_apk_build:' .github/workflows/build-apk.yml | grep -Eq '^[[:space:]]*default:[[:space:]]*false[[:space:]]*
# Keep upstream source replacement isolated to the experiment branch and an immutable source revision.
if ! grep -Fq "github.ref == 'refs/heads/dangerous-experiment'" .github/workflows/import-source.yml; then
  echo "Upstream source import must be restricted to dangerous-experiment." >&2
  exit 1
fi
if ! grep -Fq 'UPSTREAM_COMMIT: ${{ inputs.upstream_commit }}' .github/workflows/import-source.yml ||
   ! grep -Fq '[[ ! "$UPSTREAM_COMMIT" =~ ^[0-9a-f]{40}$ ]]' .github/workflows/import-source.yml; then
  echo "Upstream source import must require and validate a full commit SHA." >&2
  exit 1
fi
if ! grep -Fq 'cp -a .github /tmp/tor-vpn-local-github' .github/workflows/import-source.yml ||
   ! grep -Fq 'cp -a /tmp/tor-vpn-local-github .github' .github/workflows/import-source.yml; then
  echo "Upstream source import must preserve local GitHub workflow controls." >&2
  exit 1
fi

for script in scripts/reproducible_build.sh scripts/reproducible_fetch_deps.sh; do
  bash -n "$script"
done

echo "CI security preflight passed: checked $checked immutable external action references, release opt-in guard, and reproducible build-script syntax."
 ||
   ! grep -Fq 'if: ${{ inputs.run_apk_build }}' .github/workflows/build-apk.yml; then
  echo "APK build execution must require a separate default-off manual opt-in." >&2
  exit 1
fi

# Keep upstream source replacement isolated to the experiment branch and an immutable source revision.
if ! grep -Fq "github.ref == 'refs/heads/dangerous-experiment'" .github/workflows/import-source.yml; then
  echo "Upstream source import must be restricted to dangerous-experiment." >&2
  exit 1
fi
if ! grep -Fq 'UPSTREAM_COMMIT: ${{ inputs.upstream_commit }}' .github/workflows/import-source.yml ||
   ! grep -Fq '[[ ! "$UPSTREAM_COMMIT" =~ ^[0-9a-f]{40}$ ]]' .github/workflows/import-source.yml; then
  echo "Upstream source import must require and validate a full commit SHA." >&2
  exit 1
fi
if ! grep -Fq 'cp -a .github /tmp/tor-vpn-local-github' .github/workflows/import-source.yml ||
   ! grep -Fq 'cp -a /tmp/tor-vpn-local-github .github' .github/workflows/import-source.yml; then
  echo "Upstream source import must preserve local GitHub workflow controls." >&2
  exit 1
fi

for script in scripts/reproducible_build.sh scripts/reproducible_fetch_deps.sh; do
  bash -n "$script"
done

echo "CI security preflight passed: checked $checked immutable external action references, release opt-in guard, and reproducible build-script syntax."
