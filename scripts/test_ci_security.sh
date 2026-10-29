#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_PREFLIGHT="$ROOT/scripts/verify_ci_security.sh"
test -f "$SOURCE_PREFLIGHT"

fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/scripts" "$fixture/.github/workflows" "$fixture/app/src/main/res/xml"
cp "$SOURCE_PREFLIGHT" "$fixture/scripts/verify_ci_security.sh"
cat > "$fixture/app/src/main/AndroidManifest.xml" <<'XML'
<manifest>
  <application
    android:allowBackup="false"
    android:fullBackupContent="@xml/backup_rules"
    android:dataExtractionRules="@xml/data_extraction_rules" />
</manifest>
XML
cat > "$fixture/app/src/main/res/xml/backup_rules.xml" <<'XML'
<full-backup-content>
  <exclude domain="sharedpref" path="autonomy_network_learning_v1.xml" />
</full-backup-content>
XML
cat > "$fixture/app/src/main/res/xml/data_extraction_rules.xml" <<'XML'
<data-extraction-rules>
  <cloud-backup>
    <exclude domain="sharedpref" path="autonomy_network_learning_v1.xml" />
  </cloud-backup>
  <device-transfer>
    <exclude domain="sharedpref" path="autonomy_network_learning_v1.xml" />
  </device-transfer>
</data-extraction-rules>
XML
printf '#!/usr/bin/env bash\nset -euo pipefail\necho build\n' > "$fixture/scripts/reproducible_build.sh"
printf '#!/usr/bin/env bash\nset -euo pipefail\necho dependencies\n' > "$fixture/scripts/reproducible_fetch_deps.sh"

write_valid_workflows() {
  cat > "$fixture/.github/workflows/build-apk.yml" <<'YAML'
name: fixture build
on:
  workflow_dispatch:
    inputs:
      run_apk_build:
        default: false
      publish_release:
        default: false
jobs:
  build:
    if: ${{ inputs.run_apk_build }}
  publish:
    if: ${{ github.ref == 'refs/heads/main' && github.event_name == 'workflow_dispatch' && inputs.publish_release }}
YAML
  cat > "$fixture/.github/workflows/test.yml" <<'YAML'
name: fixture
jobs:
  check:
    steps:
      - name: checkout
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1 # v7.0.1
YAML
  cat > "$fixture/.github/workflows/import-source.yml" <<'YAML'
on:
  workflow_dispatch:
    inputs:
      upstream_commit:
        required: true
jobs:
  import-source:
    if: ${{ github.ref == 'refs/heads/dangerous-experiment' }}
    steps:
      - name: validate source revision
        env:
          UPSTREAM_COMMIT: ${{ inputs.upstream_commit }}
        run: |
          if [[ ! "$UPSTREAM_COMMIT" =~ ^[0-9a-f]{40}$ ]]; then exit 1; fi
          cp -a .github /tmp/tor-vpn-local-github
          cp -a /tmp/tor-vpn-local-github .github
YAML
}

expect_failure() {
  local label="$1"
  if (cd "$fixture" && bash scripts/verify_ci_security.sh) > "$fixture/output.log" 2>&1; then
    echo "FAIL: expected preflight to reject $label" >&2
    cat "$fixture/output.log" >&2
    exit 1
  fi
  echo "PASS: rejected $label"
}

write_valid_workflows
(cd "$fixture" && bash scripts/verify_ci_security.sh)
echo "PASS: accepts pinned action and guarded release"

# Mutable major tags must be rejected.
cat > "$fixture/.github/workflows/test.yml" <<'YAML'
name: fixture
jobs:
  check:
    steps:
      - name: checkout
        uses: actions/checkout@v7 # v7.0.1
YAML
expect_failure "mutable action tags"

# Version comments are required so dependency updates remain reviewable.
write_valid_workflows
cat > "$fixture/.github/workflows/test.yml" <<'YAML'
name: fixture
jobs:
  check:
    steps:
      - name: checkout
        uses: actions/checkout@3d3c42e5aac5ba805825da76410c181273ba90b1
YAML
expect_failure "missing pinned-version comments"

# Release publication must stay behind a manual, default-off opt-in.
write_valid_workflows
cat > "$fixture/.github/workflows/build-apk.yml" <<'YAML'
name: fixture build
on:
  workflow_dispatch:
    inputs:
      publish_release:
        default: false
jobs:
  publish:
    if: false
YAML
expect_failure "missing release opt-in guard"

# APK work must remain explicitly disabled by default and gated at the build job.
write_valid_workflows
cat > "$fixture/.github/workflows/build-apk.yml" <<'YAML'
name: fixture build
on:
  workflow_dispatch:
    inputs:
      run_apk_build:
        default: false
      publish_release:
        default: false
jobs:
  build:
    if: false
  publish:
    if: ${{ github.ref == 'refs/heads/main' && github.event_name == 'workflow_dispatch' && inputs.publish_release }}
YAML
expect_failure "missing APK build opt-in gate"

# The same preflight must also reject shell syntax regressions.
write_valid_workflows
printf 'if then\n' > "$fixture/scripts/reproducible_build.sh"
expect_failure "invalid reproducible build-script syntax"

# Source import must stay branch-restricted and use a validated immutable revision.
write_valid_workflows
cat > "$fixture/.github/workflows/import-source.yml" <<'YAML'
on:
  workflow_dispatch:
jobs:
  import-source:
    steps:
      - run: echo "unsafe"
YAML
expect_failure "unrestricted or unpinned source import"

# Opt-in network-learning records must remain excluded from backup and device transfer.
write_valid_workflows
cat > "$fixture/app/src/main/res/xml/data_extraction_rules.xml" <<'XML'
<data-extraction-rules>
  <cloud-backup />
  <device-transfer />
</data-extraction-rules>
XML
expect_failure "network-learning backup exclusion"

echo "All CI security preflight fixtures passed."
