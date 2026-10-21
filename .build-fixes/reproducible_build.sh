#!/bin/bash

set -e

# Ensure executing in ...../vpn/
cd $(dirname "$0")
cd ..

ONIONMASQ_HASH=${ONIONMASQ_HASH:-$(cat scripts/onionmasq.hash)}
echo "Using ONIONMASQ_HASH: $ONIONMASQ_HASH"

IS_CONTAINER=0
if [ $(pwd) == "/build/vpn" ]; then
  IS_CONTAINER=1
fi

if [ $IS_CONTAINER -eq 0 ]; then
  # leading . to absorb env vars defined inside like CARGO_HOME
  . ./scripts/reproducible_fetch_deps.sh
fi

# Get and build onionmasq
mkdir -p deps
cd deps
if [ ! -d "onionmasq" ]; then
  git clone https://gitlab.torproject.org/tpo/core/onionmasq.git
fi

cd onionmasq
git fetch
git checkout -f $ONIONMASQ_HASH

# The container and the reproducible_fetch_deps.sh set the exported environment variable ONIONMASQ_JAVA_HOME
export JAVA_HOME="$ONIONMASQ_JAVA_HOME"

# Since we have overridden $CARGO_HOME and $RUSTUP_DIR we need to inject additional remaps for them
# Find EPOCH line, add 2, append 'i' while at it for sed
LINE=$(grep -n SOURCE_DATE_EPOCH build-ndk.sh | cut -f1 -d':' | xargs -n 1 expr 2 + | xargs printf -- '%si' | xargs echo)

if [ $IS_CONTAINER -eq 1 ]; then
  # mirroring container state so replacement lines work
  export CARGO_HOME=/root/.cargo
  sed -i -e "$LINE\\
export RUSTFLAGS=\"\$RUSTFLAGS --remap-path-prefix $CARGO_HOME=/cargo/  \"" build-ndk.sh
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
cd ../..
cd ../../

# Build VPN. Java 21 is required by the project.
export JAVA_HOME="$TORVPN_JAVA_HOME"

# Temporarily disable local.properties so local SDK paths and tokens do not affect the reproducible build.
if [ -f local.properties ]; then
  mv local.properties local.properties.reproducible.pause
fi

# settings.gradle.kts honors ENFORCE_LOCAL_MAVEN=1 and routes Onionmasq to mavenLocal(),
# so no source-file rewrite is needed here. The old script edited build.gradle (Groovy DSL),
# which no longer exists in this Kotlin-DSL project.
./gradlew clean
ENFORCE_LOCAL_MAVEN=1 ./gradlew assembleRelease
cp app/build/outputs/apk/release/app-*-release-unsigned.apk build
ENFORCE_LOCAL_MAVEN=1 ./gradlew bundleRelease
cp app/build/outputs/bundle/release/app-release.aab build

# Restore local.properties if moved.
if [ -f local.properties.reproducible.pause ]; then
  mv local.properties.reproducible.pause local.properties
fi

for apk in build/*.apk; do
  mv "$apk" "$apk.unaligned"
  ./bin/reproducible-apk-tools/zipalign.py --page-size 16 --pad-like-apksigner --replace "$apk.unaligned" "$apk"
  rm "$apk.unaligned"
done
