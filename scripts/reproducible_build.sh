#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

ONIONMASQ_HASH="${ONIONMASQ_HASH:-$(cat scripts/onionmasq.hash)}"
echo "Using ONIONMASQ_HASH: $ONIONMASQ_HASH"

IS_CONTAINER=0
if [[ "$(pwd)" == "/build/vpn" ]]; then
  IS_CONTAINER=1
fi

if [[ "$IS_CONTAINER" -eq 0 ]]; then
  . ./scripts/reproducible_fetch_deps.sh
fi

restore_local_properties() {
  if [[ -f local.properties.reproducible.pause && ! -f local.properties ]]; then
    mv local.properties.reproducible.pause local.properties
  fi
}
trap restore_local_properties EXIT

mkdir -p deps
cd deps
if [[ ! -d onionmasq ]]; then
  git clone https://gitlab.torproject.org/tpo/core/onionmasq.git
fi
cd onionmasq
git fetch --tags
git checkout --force "$ONIONMASQ_HASH"

export JAVA_HOME="$ONIONMASQ_JAVA_HOME"

LINE="$(awk '/SOURCE_DATE_EPOCH/ { print NR + 2 "i"; exit }' build-ndk.sh)"
if [[ -z "$LINE" ]]; then
  echo "Could not locate SOURCE_DATE_EPOCH in build-ndk.sh" >&2
  exit 1
fi

if [[ "$IS_CONTAINER" -eq 1 ]]; then
  export CARGO_HOME=/root/.cargo
  sed -i -e "$LINE\\
export RUSTFLAGS=\"\$RUSTFLAGS --remap-path-prefix $CARGO_HOME=/cargo/ \"" build-ndk.sh
else
  sed -i -e "$LINE\\
export RUSTFLAGS=\"\$RUSTFLAGS --remap-path-prefix $CARGO_HOME=/cargo/ --remap-path-prefix $RUSTUP_HOME=/rustup/\"" build-ndk.sh
fi

cargo clean
./build-ndk.sh
cd android/OnionmasqAndroid
./gradlew clean
./gradlew :onionmasq:build
./gradlew :onionmasq:publishLibraryReleasePublicationToMavenLocal
# Return from android/OnionmasqAndroid all the way to the Tor VPN repo root.
cd ../../../..

export JAVA_HOME="$TORVPN_JAVA_HOME"

if [[ -f local.properties ]]; then
  mv local.properties local.properties.reproducible.pause
fi

./gradlew clean
ENFORCE_LOCAL_MAVEN=1 ./gradlew assembleRelease
mkdir -p build
cp app/build/outputs/apk/release/app-*-release-unsigned.apk build/
if [[ "${BUILD_AAB:-1}" == "1" ]]; then
  ENFORCE_LOCAL_MAVEN=1 ./gradlew bundleRelease
  cp app/build/outputs/bundle/release/app-release.aab build/
else
  echo "Skipping Android App Bundle because BUILD_AAB=0"
fi

# Align APKs with the pinned Android SDK build-tools. This is available in
# the reproducible builder and avoids relying on a separately cloned helper
# whose checkout may be incomplete or whose interpreter may be unavailable.
# Align APKs using the pinned Android SDK build-tools. Do not depend on a helper
# script that is not part of this repository checkout.
for apk in build/*.apk; do
  [[ -f "$apk" ]] || continue
  ZIPALIGN=""
  if [[ -n "${ANDROID_BUILD_TOOLS:-}" && -x "$ANDROID_HOME/build-tools/$ANDROID_BUILD_TOOLS/zipalign" ]]; then
    ZIPALIGN="$ANDROID_HOME/build-tools/$ANDROID_BUILD_TOOLS/zipalign"
  else
    # Keep the pinned path first, but tolerate SDK images that expose the
    # installed build-tools directory without exporting ANDROID_BUILD_TOOLS.
    ZIPALIGN="$(find "$ANDROID_HOME/build-tools" -mindepth 2 -maxdepth 2 -type f -name zipalign -perm -u+x 2>/dev/null | sort -V | tail -n 1)"
  fi
  if [[ -z "$ZIPALIGN" || ! -x "$ZIPALIGN" ]]; then
    echo "Android SDK zipalign was not found under $ANDROID_HOME/build-tools" >&2
    find "$ANDROID_HOME/build-tools" -maxdepth 2 -type f -name zipalign -print 2>/dev/null || true
    exit 1
  fi
  mv "$apk" "$apk.unaligned"
  if ! "$ZIPALIGN" -P 16 -f 4 "$apk.unaligned" "$apk"; then
    mv "$apk.unaligned" "$apk"
    echo "APK alignment failed for $apk" >&2
    exit 1
  fi
  rm "$apk.unaligned"
done
