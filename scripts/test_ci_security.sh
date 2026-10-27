#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_PREFLIGHT="$ROOT/scripts/verify_ci_security.sh"
test -f "$SOURCE_PREFLIGHT"

fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT
mkdir -p "$fixture/scripts" "$fixture/.github/workflows"
cp "$SOURCE_PREFLIGHT" "$fixture/scripts/verify_ci_security.sh"
printf '#!/usr/bin/env bash\nset -euo pipefail\necho build\n' > "$fixture/scripts/reproducible_build.sh"
printf '#!/usr/bin/env bash\nset -euo pipefail\necho dependencies\n' > "$fixture/scripts/reproducible_fetch_deps.sh"

write_valid_workflows() {
  cat > "$fixture/.github/workflows/build-apk.yml" <<'YAML'
name: fixture build
on:
  workflow_dispatch:
    inputs:
      publish_release:
        default: false
jobs:
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
}

expect_failure() {
  local label="$1"
  if bash "$fixture/scripts/verify_ci_security.sh" > "$fixture/output.log" 2>&1; then
    echo "FAIL: expected preflight to reject $label" >&2
    cat "$fixture/output.log" >&2
    exit 1
  fi
  echo "PASS: rejected $label"
}

write_valid_workflows
bash "$fixture/scripts/verify_ci_security.sh"
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

# The same preflight must also reject shell syntax regressions.
write_valid_workflows
printf 'if then\n' > "$fixture/scripts/reproducible_build.sh"
expect_failure "invalid reproducible build-script syntax"

echo "All CI security preflight fixtures passed."
