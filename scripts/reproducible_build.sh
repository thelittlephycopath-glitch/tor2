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

# Prefer the reproducible alignment helper. Container builds skip
# reproducible_fetch_deps.sh, so try to fetch it here; a network/tooling
# failure must not discard an otherwise valid APK when Android's pinned
# zipalign is available as a fallback.
if [[ ! -f bin/reproducible-apk-tools/zipalign.py ]]; then
  echo "Fetching reproducible-apk-tools for reproducible APK alignment..."
  mkdir -p bin
  if command -v git >/dev/null 2>&1; then
    git clone --depth 1 https://github.com/obfusk/reproducible-apk-tools.git bin/reproducible-apk-tools || \
      echo "Could not fetch reproducible-apk-tools; falling back to Android SDK zipalign."
  else
    echo "Git is unavailable in the builder; falling back to Android SDK zipalign."
  fi
fi

for apk in build/*.apk; do
  [[ -f "$apk" ]] || continue
  mv "$apk" "$apk.unaligned"
  if [[ -f bin/reproducible-apk-tools/zipalign.py ]]; then
    ./bin/reproducible-apk-tools/zipalign.py --page-size 16 --pad-like-apksigner --replace "$apk.unaligned" "$apk"
  else
    ZIPALIGN="$ANDROID_HOME/build-tools/$ANDROID_BUILD_TOOLS/zipalign"
    if [[ ! -x "$ZIPALIGN" ]]; then
      echo "Neither reproducible zipalign.py nor Android SDK zipalign is available." >&2
      mv "$apk.unaligned" "$apk"
      exit 1
    fi
    "$ZIPALIGN" -P 16 -f 4 "$apk.unaligned" "$apk"
  fi
  rm "$apk.unaligned"
done
