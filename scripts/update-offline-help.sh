#!/bin/bash
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
ZIP_FILE="${1:?Usage: $0 <path-to-artifacts.zip>}"
[[ "$ZIP_FILE" = /* ]] || ZIP_FILE="$PWD/$ZIP_FILE"
cd "$REPO_ROOT"

OFFLINE_DIR="app/src/main/assets/help/offline"
TMPDIR_EXTRACT="$(mktemp -d)"
trap 'rm -rf "$TMPDIR_EXTRACT"' EXIT

# Support both GNU sed (Linux CI/build containers) and BSD sed (macOS).
sed_in_place() {
  if sed --version >/dev/null 2>&1; then
    sed -i "$@"
  else
    sed -i '' "$@"
  fi
}

TORVPN_PATTERNS=$(unzip -l "$ZIP_FILE" | grep '/tor-vpn/' | sed 's/.* public/public/' | sed 's|/tor-vpn/.*|/tor-vpn/*|' | grep -v "Archive: " | sort -u)

echo "Extracting $ZIP_FILE..."
EXTRACT_ARGS=()
while IFS= read -r pattern; do EXTRACT_ARGS+=("$pattern"); done <<< "$TORVPN_PATTERNS"
unzip -q "$ZIP_FILE" "${EXTRACT_ARGS[@]}" 'public/css/*' 'public/fonts/*' 'public/images/*' 'public/static/*' -d "$TMPDIR_EXTRACT"

echo "Replacing tor-vpn..."
rm -rf "$OFFLINE_DIR/tor-vpn"
cp -R "$TMPDIR_EXTRACT/public/tor-vpn" "$OFFLINE_DIR/tor-vpn"

for locale_dir in "$TMPDIR_EXTRACT"/public/*/tor-vpn; do
  [ -d "$locale_dir" ] || continue
  locale=$(basename "$(dirname "$locale_dir")")
  case "$locale" in css|fonts|images|static) continue ;; esac
  echo "Replacing $locale/tor-vpn..."
  mkdir -p "$OFFLINE_DIR/$locale"
  rm -rf "$OFFLINE_DIR/$locale/tor-vpn"
  cp -R "$locale_dir" "$OFFLINE_DIR/$locale/tor-vpn"
done

for dir in css fonts images static; do
  if [ -d "$TMPDIR_EXTRACT/public/$dir" ]; then
    echo "Replacing $dir..."
    rm -rf "$OFFLINE_DIR/$dir"
    cp -R "$TMPDIR_EXTRACT/public/$dir" "$OFFLINE_DIR/$dir"
  fi
done

ASSET_BASE="file:///android_asset/help/offline"
echo "Rewriting asset paths for Android WebView..."
while IFS= read -r -d '' file; do
  sed_in_place \
    -e "s|\.\./\(\.\./\)*css/|${ASSET_BASE}/css/|g" \
    -e "s|\.\./\(\.\./\)*fonts/|${ASSET_BASE}/fonts/|g" \
    -e "s|\.\./\(\.\./\)*images/|${ASSET_BASE}/images/|g" \
    -e "s|\.\./\(\.\./\)*static/|${ASSET_BASE}/static/|g" \
    -e '/<link[^>]*rel="preload"[^>]*as="font"[^>]*>/d' \
    -e 's|?h=[0-9a-f]*"|"|g' "$file"
done < <(find "$OFFLINE_DIR" -name '*.html' -print0)

echo "Replace support link to point to current dir"
while IFS= read -r -d '' file; do
  sed_in_place '/<ol class="breadcrumb">/{N;N;s|href="[^"]*"|href="."|;}' "$file"
done < <(find "$OFFLINE_DIR" -name '*.html' -print0)

echo "Fixing parent-dir navigation links..."
while IFS= read -r -d '' file; do
  sed_in_place 's|href="\.\./\."|href="."|g' "$file"
done < <(find "$OFFLINE_DIR" -name '*.html' -print0)

while IFS= read -r -d '' file; do
  sed_in_place -e "s|url(\.\./\(\.\./\)*fonts/|url(${ASSET_BASE}/fonts/|g" "$file"
done < <(find "$OFFLINE_DIR/css" -name '*.css' -print0)

echo "Done. Updated offline help files in $OFFLINE_DIR"
