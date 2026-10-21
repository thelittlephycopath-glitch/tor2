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
  echo "Could not locate SOURCE_DATE_EPOCH in deps/onionmasq/build-ndk.sh" >&2
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
cd ../../..

export JAVA_HOME="$TORVPN_JAVA_HOME"

if [[ -f local.properties ]]; then
  mv local.properties local.properties.reproducible.pause
fi

./gradlew clean
ENFORCE_LOCAL_MAVEN=1 ./gradlew assembleRelease
mkdir -p build
cp app/build/outputs/apk/release/app-*-release-unsigned.apk build/
ENFORCE_LOCAL_MAVEN=1 ./gradlew bundleRelease
cp app/build/outputs/bundle/release/app-release.aab build/

for apk in build/*.apk; do
  [[ -f "$apk" ]] || continue
  mv "$apk" "$apk.unaligned"
  ./bin/reproducible-apk-tools/zipalign.py --page-size 16 --pad-like-apksigner --replace "$apk.unaligned" "$apk"
  rm "$apk.unaligned"
done
