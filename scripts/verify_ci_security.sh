#!/usr/bin/env bash
set -euo pipefail

# Offline guard for workflow supply-chain and release safety. No SDK, Docker, network, or secrets.
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
      echo "Pinned action must carry a semantic version comment in $workflow: $line" >&2
      exit 1
    fi
    checked=$((checked + 1))
  done < "$workflow"
done

if (( checked == 0 )); then
  echo "No external action references were checked; refusing to pass silently." >&2
  exit 1
fi

build_workflow=.github/workflows/build-apk.yml
if ! grep -Fq 'if: ${{ inputs.run_apk_build }}' "$build_workflow"; then
  echo "APK build must require the explicit manual opt-in." >&2
  exit 1
fi
if ! grep -A 7 -F 'run_apk_build:' "$build_workflow" |
  grep -Eq '^[[:space:]]*default:[[:space:]]*false[[:space:]]*$'; then
  echo "APK build opt-in must default to disabled." >&2
  exit 1
fi

release_guard="github.ref == 'refs/heads/main' && github.event_name == 'workflow_dispatch' && inputs.publish_release"
if ! grep -Fq "$release_guard" "$build_workflow"; then
  echo "Release publication must require main + manual dispatch + explicit opt-in." >&2
  exit 1
fi
if ! grep -A 7 -F 'publish_release:' "$build_workflow" |
  grep -Eq '^[[:space:]]*default:[[:space:]]*false[[:space:]]*$'; then
  echo "Release publication opt-in must default to disabled." >&2
  exit 1
fi

# Upstream source replacement must remain branch-restricted and preserve local workflow controls.
import_workflow=.github/workflows/import-source.yml
if ! grep -Fq "github.ref == 'refs/heads/dangerous-experiment'" "$import_workflow"; then
  echo "Upstream source import must be restricted to dangerous-experiment." >&2
  exit 1
fi
if ! grep -Fq 'UPSTREAM_COMMIT: ${{ inputs.upstream_commit }}' "$import_workflow" ||
   ! grep -Fq '[[ ! "$UPSTREAM_COMMIT" =~ ^[0-9a-f]{40}$ ]]' "$import_workflow"; then
  echo "Upstream source import must require and validate a full commit SHA." >&2
  exit 1
fi
if ! grep -Fq 'cp -a .github /tmp/tor-vpn-local-github' "$import_workflow" ||
   ! grep -Fq 'cp -a /tmp/tor-vpn-local-github .github' "$import_workflow"; then
  echo "Upstream source import must preserve local GitHub workflow controls." >&2
  exit 1
fi

# Network-learning digests are opt-in and must remain local to this installation.
manifest=app/src/main/AndroidManifest.xml
backup_rules=app/src/main/res/xml/backup_rules.xml
extraction_rules=app/src/main/res/xml/data_extraction_rules.xml
if ! grep -Fq 'android:allowBackup="false"' "$manifest"; then
  echo "App backup must remain disabled by default." >&2
  exit 1
fi
if ! grep -Fq 'android:fullBackupContent="@xml/backup_rules"' "$manifest" ||
   ! grep -Fq 'android:dataExtractionRules="@xml/data_extraction_rules"' "$manifest"; then
  echo "Explicit backup and device-transfer rules must remain wired in the manifest." >&2
  exit 1
fi
if ! grep -Fq 'path="autonomy_network_learning_v1.xml"' "$backup_rules" ||
   ! grep -Fq 'path="autonomy_network_learning_v1.xml"' "$extraction_rules"; then
  echo "Opt-in network-learning records must be excluded from backup and device transfer." >&2
  exit 1
fi

for script in scripts/reproducible_build.sh scripts/reproducible_fetch_deps.sh; do
  bash -n "$script"
done

echo "CI security preflight passed: checked $checked immutable action references,"
echo "plus opt-in build/release gates, source-import isolation, and build-script syntax."
