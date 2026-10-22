FROM containers.torproject.org/tpo/tpa/base-images/debian:trixie

# Use a stable absolute SDK path; PWD is not guaranteed during ENV expansion.
# Android 17 is currently published as the Cinnamon Bun preview platform package.
ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_COMPILE_SDK=37 \
    ANDROID_BUILD_TOOLS=37.0.0 \
    ANDROID_CLT=13114758 \
    ANDROID_CLT_SHA256=7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee \
    ONIONMASQ_ANDROID_COMPILE_SDK=37 \
    ONIONMASQ_ANDROID_BUILD_TOOLS=37.0.0 \
    ANDROID_NDK_VERSION=27.3.13750724 \
    ANDROID_NDK_SHA256=601246087a682d1944e1e16dd85bc6e49560fe8b6d61255be2829178c8ed15d9 \
    ANDROID_NDK_RELNAME=r27d

RUN install -d "$ANDROID_HOME" /usr/share/man/man1 && \
    apt-get clean && \
    apt-get update --quiet && \
    DEBIAN_FRONTEND=noninteractive TZ=Etc/UTC apt-get install --yes --no-install-recommends tzdata && \
    DEBIAN_FRONTEND=noninteractive apt-get install --quiet --yes --no-install-recommends \
      apt-utils wget tar unzip lib32stdc++6 lib32z1 build-essential curl git \
      pkg-config libssl-dev openjdk-21-jdk python3 jq rustup ca-certificates && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

ENV TORVPN_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    ONIONMASQ_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

COPY scripts/msrv.txt /tmp/msrv.txt

RUN export RUST_VERSION="$(cat /tmp/msrv.txt)" && \
    rustup install "$RUST_VERSION" && \
    rustup default "$RUST_VERSION" && \
    rustup target add armv7-linux-androideabi aarch64-linux-android i686-linux-android x86_64-linux-android && \
    rustup show && rm /tmp/msrv.txt

RUN cargo install --root /usr/local bindgen-cli@0.71.1 --locked && \
    cargo install --root /usr/local cargo-ndk@3.5.4 --locked

RUN cd "$ANDROID_HOME" && \
    wget --no-verbose --output-document=ndk.zip "https://dl.google.com/android/repository/android-ndk-${ANDROID_NDK_RELNAME}-linux.zip" && \
    echo "${ANDROID_NDK_SHA256}  ndk.zip" | sha256sum --strict --check - && \
    unzip -qq -d ndk ndk.zip && rm -f ndk.zip && \
    mv "ndk/android-ndk-${ANDROID_NDK_RELNAME}" "ndk/${ANDROID_NDK_VERSION}"

ENV ANDROID_NDK_HOME=/opt/android-sdk/ndk/27.3.13750724 \
    ANDROID_NDK=/opt/android-sdk/ndk/27.3.13750724 \
    LLVM_PREBUILT=/opt/android-sdk/ndk/27.3.13750724/toolchains/llvm/prebuilt/linux-x86_64/bin
ENV PATH="/root/.cargo/bin:/opt/android-sdk/cmdline-tools/cmdline-tools/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/ndk/27.3.13750724:/opt/android-sdk/ndk/27.3.13750724/toolchains/llvm/prebuilt/linux-x86_64/bin:${PATH}"

RUN wget --no-verbose --output-document="$ANDROID_HOME/cmdline-tools.zip" "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CLT}_latest.zip" && \
    echo "${ANDROID_CLT_SHA256}  $ANDROID_HOME/cmdline-tools.zip" | sha256sum --strict --check - && \
    unzip -qq -d "$ANDROID_HOME/cmdline-tools" "$ANDROID_HOME/cmdline-tools.zip" && \
    rm -f "$ANDROID_HOME/cmdline-tools.zip"

# Preview SDK packages are published under their codename, not android-37.
RUN yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses >/dev/null && \
    sdkmanager --channel=3 --sdk_root="$ANDROID_HOME" "platforms;android-CinnamonBun" \
      "platform-tools" \
      "build-tools;${ANDROID_BUILD_TOOLS}"
