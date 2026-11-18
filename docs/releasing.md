# Building and releasing

[README](../README.md)

## Portable build

Install JDK 21 (17 minimum), Android SDK 36 and Build Tools 35.0.0. Set `ANDROID_HOME` or configure `sdk.dir` in a local, ignored `local.properties`. Run:

```sh
./gradlew :app:verifyCore :app:lintDebug :app:assembleDebug
python scripts/check-public-tree.py
```

Windows uses `gradlew.bat `. The wrapper downloads checksum-pinned Gradle 8.13. No developer-specific tool directory or private research input is required. The first build needs network access for Gradle and Android build dependencies; this is separate from the application's lack of Internet permission.

## Release signing

Use a durable private release key stored outside this repository. Never publish a debug-signed APK as an official release, and retain the same signing identity for future updates. Changing signing keys prevents an ordinary in-place update; uninstalling removes app settings.

The release configuration reads these environment variables:

| Variable | Value |
| --- | --- |
| `BLADELIGHT_KEYSTORE` | Absolute path to the private keystore |
| `BLADELIGHT_STORE_PASSWORD` | Keystore password |
| `BLADELIGHT_KEY_ALIAS` | Signing alias |
| `BLADELIGHT_KEY_PASSWORD` | Key password |

Supply all four using your local secret-management method. Partial configuration fails the build. If all are absent, Gradle produces an **unsigned** release APK; this is useful for compilation checks but is not an installable release artifact.

```sh
./gradlew :app:verifyCore :app:lintRelease :app:assembleRelease
```

Signed output: `app/build/outputs/apk/release/app-release.apk`. With no key configured, expect `app-release-unsigned.apk`. Verify the final signature using the Android Build Tools `apksigner verify --verbose --print-certs`, and record a SHA-256 checksum of the exact APK uploaded. Keep the key and passwords out of logs and command history.

### Official signing identity and migration

The first public release uses a dedicated release key, with a proof-of-rotation from the private development signing identity used for earlier local installations. Preserve the release key, its credentials and the complete signing lineage outside Git. This allows the existing tested phone installation to update without uninstalling.

For this release lineage, build the unsigned release first, then use `apksigner sign` with the release key and `--lineage`, explicitly setting `--rotation-min-sdk-version 28`, `--v1-signing-enabled false`, `--v2-signing-enabled false`, `--v3-signing-enabled true` and `--v4-signing-enabled false`. All supported OS versions are above the API28 introduction of v3. Keep these rotation settings for subsequent official builds; the ordinary Gradle signing example above does not embed this lineage by itself.

Verify the actual APK across the supported SDK range and test an in-place update before publishing. Static signature verification is not a substitute for an Android12 device migration test: AOSP documents older-platform limitations around key rotation. See [apksigner](https://developer.android.com/tools/apksigner) and [v3 signing compatibility](https://source.android.com/docs/security/features/apksigning/v3). The tested migration device runs Android17.

## Publication

1. Review the complete public candidate tree and run `scripts/check-public-tree.py`. `.gitignore` is an allowlist, not a replacement for content review or history inspection.
2. Check both languages and themes on a device. Confirm ordinary builds contain no experimental instrumentation. For a control-path change, rerun the affected bounded hardware checks and record physical observations.
3. Set the intended version name/code in `app/build.gradle`, update `CHANGELOG.md`, and record the actual validation scope. A version listed as Unreleased is not yet a published release.
4. Build and verify the signed APK, then create the GitHub repository/tag/release when publication is authorized. Include the APK, its checksum and the steady-only limitation in the release notes.
5. Check the uploaded artifact and signing certificate against the local result. Keep signed APKs as release assets rather than source-tree files.

The checked-in workflow builds a development APK and runs verification on GitHub. It does not sign or publish releases and requires no signing secrets. Its artifacts must remain labelled as development builds.

## Source inclusion

The public tree includes the production app, three core test suites, portable build tools, curated English documentation and Korean README. Experimental packages, local scripts, caches, signing material, device records and the entire `design` directory are excluded. Only the active signature in application resources is published; design references, duplicate masters and previous iterations remain local.

Generated Gradle Wrapper files retain their Apache 2.0 licensing. Project code and documentation use MIT; vendor analysis inputs are not redistributed.
