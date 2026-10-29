# gtor
{:.no_toc}

*WARNING* This is experimental software, do not rely on it for anything other than testing and development. It may leak information and should not be relied on for anything sensitive!

# Download and install a community-built APK

**Experimental software:** this app may leak information. Use it only for testing, not sensitive activity.

## Downloading the APK

1. Open the [gtor APK workflow](https://github.com/thelittlephycopath-glitch/tor2/actions/workflows/build-apk.yml).
2. Open the latest **successful** “Build gtor APK” run.
3. Under **Artifacts**, download and extract `gtor-debug-signed-apks`.
4. On Android, open the extracted APK and approve installation. Choose the universal APK when available; otherwise use the APK matching your device's CPU architecture.

These are community-built APKs signed with a temporary debug key, not official Tor Project releases. Each build uses a new key, so upgrading from an earlier community build may require uninstalling it first. Do not install these over an official Tor-signed version unless you understand the consequences.

## Install with ADB

For computer-based installation, install Android platform tools and enable USB debugging on the phone. The command is `adb install <path-to-downloaded-apk.apk>`. On a phone, you can usually install by opening the APK directly after allowing installation from your chosen file manager.

## Enable Developer mode
On your Android device, you will need to have `Developer Mode` enabled. To do this, you have to follow a little bit of a secret pathway:

1. Open Settings on your phone
2. Then go to `About phone` (near the bottom of the list)
4. Tap `Build number` (bottom of list) *seven* times in a row.
5. When you have finished you will see a message saying it is enabled.
6. Now go back to the main Settings screen and you should see a new Developer options menu. On Google Pixel phones and some other devices, you might need to navigate to Settings > System to find the Developer options menu.
7. Go in there and enable the USB debugging option.

## Connect to your phone
Attach your phone to your computer via a USB cable. 

You should get a pop-up on your phone asking you if you want the device to connect. You will need to accept it (possibly a few times, unless you tell it to remember your device).

Then go to a shell and type `adb devices`, you should see something similar to the following:

```
List of devices attached
1e778e25        device
```

The number on the left will be unique to your device.

## Install the package
While in the directory that you unzipped the archive, install the application with `adb install app-debug.apk`.

Once you have installed the application, you should be able to find it in your normal list of applications.

You are finished! You do not need to follow the next section, if you are just wanting to install the apk from the CI. The following section is only if you wish to build it locally.

### Installation problems

There are a few edge cases where installation will not work, here are a few possibilities:

You may need to enable installation of apps from 'unknown sources' (unsigned apps):
`adb shell settings put secure install_non_market_apps 1`

If you get this error: `adb: failed to install tor-vpn-debug.apk: Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package org.torproject.vpn signatures do not match newer version; ignoring!]`, in that case you will need to remove the existing app, and then after do the install:

```
$ adb uninstall org.torproject.vpn
```

# Build locally

The supported local build path uses the repository's pinned container toolchain. Use Linux with Docker (or a compatible container engine), Git, and network access. The build downloads the pinned Onionmasq source revision and required build dependencies.

```bash
git clone https://github.com/thelittlephycopath-glitch/tor2.git
cd tor2
docker build --pull --file Containerfile --tag tor2-android-builder .
docker run --rm \
  --volume "$PWD:/build/vpn" \
  --workdir /build/vpn \
  tor2-android-builder \
  env BUILD_AAB=0 bash scripts/reproducible_build.sh
```

The unsigned release APK outputs are written to `build/`. They are for local build inspection and are not ready to install until signed. For an installable community test build, use a successful run from the repository's **Build gtor APK** workflow; its artifacts use a temporary debug signing key and are not official Tor Project releases.

Do not place API tokens, signing keys, or other credentials in source files or commits. This repository's build path does not require a personal GitLab access token in `local.properties`. The build still downloads upstream Onionmasq source from Tor Project-hosted infrastructure, so availability of that upstream source is a build-time dependency.

# Instrumentation Testing

To run UI tests, you will need to prepare an emulator (or a physical device). Please make sure that animations are disabled, as they are a core reason for flaky test results.
To disable them, you will need to enable the Developer Mode in your emulator by opening the `Settings` app,
tapping on `About emulated device` and 7 times on `Build number`.

Enter the Developer options by going back to the root of the settings app, tap on `System` -> `Developer Options`, scroll down to
`Window animation scale`, `Transition animation scale` and `Animator animation scale`. Set all of these options to `Animation off`.

It's now time to run the tests. Make sure your emulator is running and enter

```
./gradlew app:connectedDebugAndroidTest
```

into your console.

# In your language

Check this out on how to contribute: https://gitlab.torproject.org/tpo/community/l10n/-/wikis/home 

# Update offline help

To update the bundled offline help pages, download the `artifacts.zip` from the [support portal CI](https://gitlab.torproject.org/tpo/web/marble/support/) and run:

```
./scripts/update-offline-help.sh path/to/artifacts.zip
```

The script extracts the help content, copies it to `app/src/main/assets/help/offline/`, and rewrites asset paths for Android WebView compatibility.

# Upgrading Android SDK

When updating the Android SDK/Build-Tools versions, ensure that the following locations are updated.

1. Tor VPN:
   * `app/build.gradle`: `compileSdk` (and `targetSdk` if upgrading the target version).

2. Container Build Environment:
   * `Containerfile`:
     - `ANDROID_COMPILE_SDK`: SDK platform version for `tor-vpn`.
     - `ANDROID_BUILD_TOOLS`: Build-tools version for `tor-vpn`.
     - `ONIONMASQ_ANDROID_COMPILE_SDK`: SDK platform version for `onionmasq`.
     - `ONIONMASQ_ANDROID_BUILD_TOOLS`: Build-tools version for `onionmasq`.

3. Reproducible Build Script:
   * `scripts/reproducible_fetch_deps.sh`:
     - `./sdkmanager --install "platforms;android-<version>"` for both `tor-vpn` and `onionmasq` platform versions.
     - `./sdkmanager --install "build-tools;<version>"` for required build-tools versions.

4. Onionmasq Repository (if upgrading `onionmasq` SDK):
   * `onionmasq/android/OnionmasqAndroid/build.gradle`: `TARGET_SDK_VERSION` / `COMPILE_SDK_VERSION`.
   * `onionmasq/Containerfile`: `ANDROID_COMPILE_SDK` and `ANDROID_BUILD_TOOLS`.

# Before releasing

For devs: before publishing a new version, run `./scripts/pre_release.sh` to update the translations and the default bridges which tor-vpn pre-ships.

# License

This code is licensed under the [3-clause BSD license](https://opensource.org/license/bsd-3-clause/)

See [doc/OPEN_SOURCE_LICENSES.md](doc/OPEN_SOURCE_LICENSES.md) for third-party license notices.
