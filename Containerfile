FROM containers.torproject.org/tpo/tpa/base-images/debian:trixie

ENV ANDROID_HOME="${PWD}/android-home" \
    ANDROID_COMPILE_SDK="37.0" \
    ANDROID_BUILD_TOOLS="36.0.0" \
    ANDROID_CLT="13114758" \
    ANDROID_CLT_SHA256="7ec965280a073311c339e571cd5de778b9975026cfcbe79f2b1cdcb1e15317ee" \
    ONIONMASQ_ANDROID_COMPILE_SDK="36" \
    ONIONMASQ_ANDROID_BUILD_TOOLS="36.0.0" \
    ANDROID_NDK_VERSION="27.3.13750724" \
    ANDROID_NDK_SHA256="601246087a682d1944e1e16dd85bc6e49560fe8b6d61255be2829178c8ed15d9" \
    ANDROID_NDK_RELNAME="r27d"

RUN install -d $ANDROID_HOME && \
    mkdir -p /usr/share/man/man1 \
    apt-get clean && \
    apt-get --quiet update --yes && \
    DEBIAN_FRONTEND=noninteractive TZ=Etc/UTC apt-get -y install tzdata && \
    apt-get --quiet install --yes apt-utils wget tar unzip lib32stdc++6 lib32z1 \
    build-essential curl git pkg-config libssl-dev openjdk-21-jdk python3 jq \
    rustup

# these envionment variables are used in the reproducible build scripts to set the right JAVA_HOME path when building onionmasq and tor-vpn
ENV TORVPN_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    ONIONMASQ_JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

COPY scripts/msrv.txt /tmp/msrv.txt

RUN export RUST_VERSION=$(cat /tmp/msrv.txt) \
    && rustup install $RUST_VERSION \
    && rustup default $RUST_VERSION \
    && rustup target add armv7-linux-androideabi aarch64-linux-android i686-linux-android x86_64-linux-android \
    && rustup show \
    && rm /tmp/msrv.txt

RUN cargo install bindgen-cli@0.71.1 --locked \
    && cargo install cargo-ndk@3.5.4 --locked

# Get Android ndk
RUN cd $ANDROID_HOME \
    && wget -nv --output-document=ndk.zip https://dl.google.com/android/repository/android-ndk-${ANDROID_NDK_RELNAME}-linux.zip \
    && echo "${ANDROID_NDK_SHA256} ndk.zip" | sha256sum --strict -c - \
    && unzip -qq -d ndk ndk.zip \
    && rm -f ndk.zip \
    && mv ndk/android-ndk-${ANDROID_NDK_RELNAME}/ ndk/${ANDROID_NDK_VERSION}/ \
    && cd ..

# Exporting required ndk paths
ENV ANDROID_NDK_HOME=$ANDROID_HOME/ndk/${ANDROID_NDK_VERSION} \
    ANDROID_NDK=$ANDROID_HOME/ndk/${ANDROID_NDK_VERSION} \
    LLVM_PREBUILD=$ANDROID_NDK_VERSION/toolchains/llvm/prebuilt/linux-x86_64/bin \
    PATH=$ANDROID_NDK_HOME:$PATH \
    PATH=$ANDROID_NDK_VERSION:$PATH \
    PATH=$LLVM_PREBUILD:$PATH

# Get Android sdk
RUN wget -nv --output-document=$ANDROID_HOME/cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_CLT}_latest.zip \
    && cd $ANDROID_HOME \
    && echo "${ANDROID_CLT_SHA256} cmdline-tools.zip" | sha256sum --strict -c - \
    && unzip -qq -d cmdline-tools cmdline-tools.zip \
    && rm -f cmdline-tools.zip \
    && cd ..

# Exporting required sdk paths
ENV PATH=$PATH:${ANDROID_HOME}/cmdline-tools/cmdline-tools/bin/
    # Accepting licences before using sdkmanager
RUN yes | sdkmanager --licenses \
    && sdkmanager --sdk_root=${ANDROID_HOME} "platforms;android-${ANDROID_COMPILE_SDK}" \
    && sdkmanager --sdk_root=${ANDROID_HOME} "platforms;android-${ONIONMASQ_ANDROID_COMPILE_SDK}" \
    && sdkmanager --sdk_root=${ANDROID_HOME} "platform-tools" \
    && sdkmanager --sdk_root=${ANDROID_HOME} "build-tools;${ANDROID_BUILD_TOOLS}" \
    && sdkmanager --sdk_root=${ANDROID_HOME} "build-tools;${ONIONMASQ_ANDROID_BUILD_TOOLS}"
    


