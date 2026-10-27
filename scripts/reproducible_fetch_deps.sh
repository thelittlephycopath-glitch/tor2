#!/bin/bash

set -e

# Ensure executing in ...../vpn/
cd $(dirname "$0")
cd ..

echo "Fetching Environment..."

# Detect OS and architecture
OS=$(uname -s)
ARCH=$(uname -m)

if ! command -v unzip &>/dev/null; then
  echo "Error: unzip is required, please install"
  exit 1
fi

if ! command -v curl &>/dev/null; then
  echo "Error: curl is required, please install"
  exit 1
fi

# tor-vpn requires Java 21
# Check for system Java 21
SYSTEM_JAVA21=""
if [ "$OS" = "Darwin" ]; then
  JAVA21_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
  if [ -n "$JAVA21_HOME" ]; then
    SYSTEM_JAVA21="$JAVA21_HOME"
  fi
else
  if [ -d /usr/lib/jvm/java-21-openjdk-amd64 ]; then
    SYSTEM_JAVA21="/usr/lib/jvm/java-21-openjdk-amd64"
  fi
fi

if [ -n "$SYSTEM_JAVA21" ]; then
  echo "  System Java 21 found, using!"
  export PATH="$SYSTEM_JAVA21/bin":$PATH
  export ONIONMASQ_JAVA_HOME="$SYSTEM_JAVA21"
  export TORVPN_JAVA_HOME="$SYSTEM_JAVA21"
else
  if [ "$OS" = "Darwin" ]; then
    if [ "$ARCH" = "arm64" ]; then
      JDK_DIR="jdk-21.0.1"
      JDK_URL="https://download.java.net/java/GA/jdk21.0.1/415e3f918a1f4062a0074a2794853d0d/12/GPL/openjdk-21.0.1_macos-aarch64_bin.tar.gz"
      JDK_ARCHIVE="openjdk-21.0.1_macos-aarch64_bin.tar.gz"
    else
      JDK_DIR="jdk-21.0.1"
      JDK_URL="https://download.java.net/java/GA/jdk21.0.1/415e3f918a1f4062a0074a2794853d0d/12/GPL/openjdk-21.0.1_macos-x64_bin.tar.gz"
      JDK_ARCHIVE="openjdk-21.0.1_macos-x64_bin.tar.gz"
    fi
    JDK_INNER="${JDK_DIR}.jdk/Contents/Home"
  else
    if [ "$ARCH" = "aarch64" ]; then
      JDK_DIR="jdk-21.0.1"
      JDK_URL="https://download.java.net/java/GA/jdk21.0.1/415e3f918a1f4062a0074a2794853d0d/12/GPL/openjdk-21.0.1_linux-aarch64_bin.tar.gz"
      JDK_ARCHIVE="openjdk-21.0.1_linux-aarch64_bin.tar.gz"
    else
      JDK_DIR="jdk-21.0.2"
      JDK_URL="https://download.java.net/java/GA/jdk21.0.2/f2283984656d49d69e91c558476027ac/13/GPL/openjdk-21.0.2_linux-x64_bin.tar.gz"
      JDK_ARCHIVE="openjdk-21.0.2_linux-x64_bin.tar.gz"
    fi
    JDK_INNER="${JDK_DIR}"
  fi

  if [ ! -d "bin/${JDK_INNER}/bin/" ]; then
    echo "  System Java 21 not found, stop script and install via your package manager if you'd prefer to use now, else will download 3rd party JDK..."
    sleep 3
    echo "  Java 21 not found, attempting to download..."
    mkdir -p bin
    cd bin
    curl "$JDK_URL" -o "$JDK_ARCHIVE"
    tar -xzf "$JDK_ARCHIVE"
    rm "$JDK_ARCHIVE"
    cd ..
  fi
  export PATH="$PWD/bin/${JDK_INNER}/bin/":$PATH
  export ONIONMASQ_JAVA_HOME="$PWD/bin/${JDK_INNER}"
  export TORVPN_JAVA_HOME="$PWD/bin/${JDK_INNER}"
fi

echo "  Java 21 OK!"

# set a default
export JAVA_HOME="$TORVPN_JAVA_HOME"


export RUSTUP_HOME=$PWD/bin/rustup
export CARGO_HOME=$PWD/bin/cargo

if [ ! -d $RUSTUP_HOME ]; then

  echo "  rustup not installed, fetching..."
  echo ""

  mkdir -p bin
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs -o bin/rustup-init.sh
  chmod a+x bin/rustup-init.sh
  ./bin/rustup-init.sh -y
fi

. "$CARGO_HOME/env"

MSRV="$(cat scripts/msrv.txt)"
RUST_VER="$(rustup default | cut -b 1-6)"
if [ "$RUST_VER" != "$MSRV" ]; then
  echo "  Selecting pinned Rust toolchain $MSRV..."
  rustup default "$MSRV"
fi

# Target installation must be idempotent and independent of the active toolchain.
# A partially provisioned SDK/toolchain can already have the pinned Rust version but
# still be missing one or more Android targets after an interrupted setup.
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android

echo "  Rust $MSRV and Android targets OK!"

SDK_ROOT="$PWD/bin/Android/Sdk"
SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

# Bootstrap command-line tools independently of the SDK directory. A prior failed install may
# have left a partial SDK tree; package installation below always runs to repair missing packages.
if [ ! -x "$SDKMANAGER" ]; then
  echo "  Android SDK command-line tools not found. Installing..."
  if [ "$OS" = "Darwin" ]; then
    CMDLINE_TOOLS_PLATFORM="mac"
  else
    CMDLINE_TOOLS_PLATFORM="linux"
  fi
  CMDLINE_TOOLS_ZIP="commandlinetools-${CMDLINE_TOOLS_PLATFORM}-13114758_latest.zip"

  mkdir -p "$SDK_ROOT/cmdline-tools"
  pushd "$SDK_ROOT/cmdline-tools"
  rm -rf .latest-staging
  mkdir -p .latest-staging
  curl --fail --location --silent --show-error \
    "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}" \
    -o "$CMDLINE_TOOLS_ZIP"
  unzip -qq "$CMDLINE_TOOLS_ZIP" -d .latest-staging
  rm -f "$CMDLINE_TOOLS_ZIP"
  rm -rf latest
  mv .latest-staging/cmdline-tools latest
  rmdir .latest-staging
  popd
fi

SDKMANAGER="$SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  echo "Error: Android SDK command-line tools were not installed successfully" >&2
  exit 1
fi

export ANDROID_HOME="$SDK_ROOT"
yes | "$SDKMANAGER" --sdk_root="$ANDROID_HOME" --licenses

# Android 17/API 37 is in the preview channel; Onionmasq remains on stable API 36.
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" --channel=0 --install \
  "platforms;android-36" "build-tools;36.0.0"
"$SDKMANAGER" --sdk_root="$ANDROID_HOME" --channel=3 --install \
  "platforms;android-37" "build-tools;37.0.0"


if [ ! -d $ANDROID_HOME/ndk/27.3.13750724 ]; then
  echo "  Android NDK not found. Installing..."
  echo ""
  pushd $ANDROID_HOME/cmdline-tools/latest/bin
  "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" --channel=0 --install "ndk;27.3.13750724"
  popd
fi
export ANDROID_NDK_ROOT=$ANDROID_HOME/ndk/27.3.13750724
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.3.13750724



echo "  Android NDK 27.3.13750724 OK!"

# zipalign.py recommended by f-droid and incorporated there in https://github.com/f-droid/fdroiddata/blob/master/srclibs/reproducible-apk-tools.yml
# so we'll use here. It's been archived (and not mainteined) since 2025.05.12, so we'll always get the right / latest version
# Pin the archived helper repository to an immutable commit. Cloning its default branch
# would make identical source revisions depend on whichever commit happens to be current.
REPRODUCIBLE_APK_TOOLS_COMMIT="9401d827a2404827ec3de6cb90a879d9fbd3f60d"
TOOLS_DIR="$PWD/bin/reproducible-apk-tools"
if [ -e "$TOOLS_DIR" ] && [ ! -d "$TOOLS_DIR/.git" ]; then
  echo "Refusing to overwrite non-Git path: $TOOLS_DIR" >&2
  exit 1
fi
if [ ! -d "$TOOLS_DIR/.git" ]; then
  echo "Fetching reproducible-apk-tools at pinned commit $REPRODUCIBLE_APK_TOOLS_COMMIT..."
  mkdir -p bin
  git clone --no-checkout https://github.com/obfusk/reproducible-apk-tools "$TOOLS_DIR"
fi
git -C "$TOOLS_DIR" fetch --depth=1 origin "$REPRODUCIBLE_APK_TOOLS_COMMIT"
git -C "$TOOLS_DIR" checkout --detach --force "$REPRODUCIBLE_APK_TOOLS_COMMIT"
ACTUAL_TOOLS_COMMIT="$(git -C "$TOOLS_DIR" rev-parse HEAD)"
if [ "$ACTUAL_TOOLS_COMMIT" != "$REPRODUCIBLE_APK_TOOLS_COMMIT" ]; then
  echo "reproducible-apk-tools commit mismatch: expected $REPRODUCIBLE_APK_TOOLS_COMMIT, got $ACTUAL_TOOLS_COMMIT" >&2
  exit 1
fi

./scripts/reproducible_fetch_rust_deps.sh

echo "Env OK for reproducible build!"
echo ""