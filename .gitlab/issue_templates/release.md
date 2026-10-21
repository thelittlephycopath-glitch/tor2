<!--
* Use this template when creating a new release.
-->

## Changelog

```
For the full changelog, please see here:
https://gitlab.torproject.org/tpo/applications/vpn/-/blob/main/CHANGELOG.md
```

## Steps

### Onionmasq

- [ ] Update version and version code in `android/OnionmasqAndroid/build.gradle`
  - bump `VERSION_CODE` by 1
  - update `VERSION_NAME` according to the `tor-vpn` release, e.g. `1.9.1Beta`
- [ ] Tag with a matching `vpn-<$VERSION_NAME>`

### Vpn

- [ ] Update version and version code in `app/build.gradle`
  - bump `android.defaultConfig.versionCode` by 1
  - update `android.defaultConfig.versionName` by whatever rules we have here?
- [ ] Update deps in `gradle/libs.versions.toml`
  - update `onionmasq-release/version` to match the above `onionmasq` tag
- [ ] Update `scripts/onionmasq.hash`
  - update contents of this file to the `onionmasq` tag
  - NOTE: onionmasq.hash is actually a git reference, not specifically a hash
- [ ] Update built-in bridges and translations with `scripts/pre_release.sh`
  - run `scripts/pre_release.sh`
  - NOTE: pulling down latest translations from weblate fails (as of December)
    - We're using a report from Weblate to identify which of our translations are good
    - the [work around](https://gitlab.torproject.org/tpo/applications/vpn/-/blob/main/scripts/pre_release.sh?ref_type=heads#L25) is to download the json files manually from [weblate](https://hosted.weblate.org/api/components/tor/tor-vpn/translations/) and run `python3 scripts/get-l10n.py deps/translation -f deps/weblate`
- [ ] Update offline docs
    - Download `artifacts.zip` from https://gitlab.torproject.org/tpo/web/marble/support/-/artifacts from the latest `build` job
    - run `scripts/update-offline-help.sh` with the `artifacts.zip` as an argument`
- [ ] Create merge-request with above changes
- [ ] Merge

### Testing

*Currently this will be manual per release so trying to keep it light but capture obvious dire failures, while capturing some nice to haves that would be more feasible as release blockers if we have automation to test them*

- Android
    - Ideally a new version (17 as of 2026.09)  and a version that is closer to the oldest we support(7 (minsdk with api level 24) as of 2026.09).
    - Testers should list their version in the work_item on gitlab to track the coverage
    - Testers should list and perform as many of the following as they can

#### Required Base functionality

- [ ] Tor VPN launches successfully
- [ ] Connects to Tor Network
- [ ] Apps list 
    - [ ] is populated 
    - [ ] lists some exempt "Tor-powered apps" (any of tor browser, briar, cwtch)
    - [ ] toggle turns off all apps and allows manual selection
- [ ] Tor exit test using a not tor-browser browser under tor-vpn coverage (firefox, chrome, etc), https://check.torproject.org reports "Congratulations. This browser is configured to use Tor"
- [ ] Tor VPN does not crash when started while the phone is in airplane mode (requires a restart of the app, to make sure it doesn't crash on app start when internet is inaccessible)

#### Bridge functionality (not required for manual test approval)

- [ ] Can connect using a built in bridge (obfs4 or snowflake, please report)
- [ ] Can connect with a custom bridge line (one is required to be sourced)
- [ ] Use bridge bot to get a custom bridge, test it connects

### Building

- [ ] Tag vpn: `git tag -sa 1.X.0Beta -m "1.x.0Beta release"` and `git push origin tag 1.x.0Beta`
- [ ] Checkout latest `vpn` tag (`1.X.0Beta`)
- [ ] Build VPN on two build machines:
  - Run `scripts/reproducible_build.sh`
  - Note: One of these should be run on a tb-build-0x machine and have the results published for the signing step
- [ ] Ensure matching hashes on reproducible builds from >= 2 builders

### Code Signing
- [ ] Sign
  - [ ] hand off to signer
    - https://gitlab.torproject.org/tpo/applications/tor-browser-build/-/issues with "Release Prep - Tor VPN" template
  - [ ] results published in https://dist.torproject.org/torvpn/

### Publishing
- [ ] Publish to Play Store
  - Create new Open Testing release
  - Upload .aab
  - Add copy from UX team provided in issue
  - dial up Open Testing to 100%
  - Make sure to "Submit changes for review"
- [ ] Update F-Droid
  - Once new tag and build is published, FDroid `check updates` step may pick it up and apply it automatically 
    - *note: will only be possible once signed build is published at https://dist.torproject.org/torvpn*
    - check if there is a new MR at: https://gitlab.com/search?search=bot%3A+Update+Tor+VPN+to&nav_source=navbar&project_id=36528&group_id=28397&scope=merge_requests&sort=created_desc 
  - if not and need manual update:
    - clone https://gitlab.com/fdroid/fdroiddata/
    - [ ] edit metadata/org.torproject.vpn.yml
      - Add 4 arch specific stanzas with the new `commit`, `versionName`, and `versionCode` (*note: the versionCode is unique per arch, `versionCode * 10 + archOffset`*)
      - Update `CurrentVersion` and `CurrentVersionCode` at the bottom
      - example: https://gitlab.com/fdroid/fdroiddata/-/commit/e4ad2e359bfa68e281d7590a9cbb0c1f2abc371b
      - Update `CurrentVersion` and `CurrentVersionCode` at the bottom of the file
    - [ ] Create an MR
