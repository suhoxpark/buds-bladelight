# Validation and limits

[README](../README.md) · [Architecture](architecture.md) · [Reverse engineering](reverse-engineering.md)

## Evidence levels

Three kinds of evidence are kept distinct: source/instruction analysis, protocol exchanges, and direct observation of the earbuds. A successful reply is not a light-state measurement. Historical physical validation applies to the tested control implementation; localization/build changes do not constitute a new hardware compatibility campaign.

## Hardware baseline

Physical control testing on 2026-11-17 used Galaxy Buds3 Pro, firmware R630XXU0AZG2, and Galaxy S25 FE / Android 17 / One UI 9.0. Both earbuds reported this version in version information; the firmware analysis used a matching-version archive, not a bytewise device readback. Tests were bounded functional observations, not certification or a broad device matrix.

| Behavior | Observed result |
| --- | --- |
| ON and OFF | Visual inspection showed both lights changed |
| After diagnostic exit and socket close | Both remained lit after 30 seconds |
| Samsung companion coexistence | Separate channels operated concurrently; EQ changed/restored while lights remained on |
| Wear, music, remove | Playback worked; lights observed after removal, not continuously while worn |
| Close case 15 seconds, then remove | Both off afterward; inside the closed case was not observed |
| Physical gesture | First gesture maintained output; second turned both off |
| Phone Bluetooth cycle after completed ON | Both remained lit after reconnection |
| App absence, then later OFF | Lights persisted; a fresh app process turned both off |
| Socket loss after mode/LED reply | Fresh-session recovery; journal cleared; both-off observation for the LED-stage case |
| Bluetooth loss during transaction | Pending record survived; reconnect recovered even with auto disabled |
| Force-stop during transaction | Pending record survived; reopening recovered |
| Ordinary process death followed by reconnect | Background auto-on ran and both lights came on |
| Auto enabled, manual OFF | Both stayed off for more than 30 seconds |
| Auto disabled, reconnect | No ON command; both stayed off |

These observations do not establish every possible interruption timing or persistent role-state behavior. ON and OFF reply text is identical, and the propagation mechanism responsible for both earbuds reacting to one responding endpoint remains unresolved.

## Later bounded research

On the documented hardware, guarded style changes were read back after reconnecting. Visual inspection showed that an active native breathing pattern changed to blinking and back, and that the same change could relight it after diagnostic OFF. After a closed-case reconnect or native OFF, the same style changes did not relight the earbuds. These are bounded trials, not a compatibility or reliability estimate.

A separate native OFF request produced valid responses and both lights were observed off; the Samsung native-light notification also disappeared. Diagnostic OFF had left that notification active after the lights were observed off. Neither notification state nor a protocol response is an optical LED measurement. The integrated v0.7 OFF sequence was subsequently tested as described below.

A diagnostic log snapshot captured peer-message parsing and named handler entry for two ordinary synchronization messages. It did not capture the initial handshake, establish host command admission or provide LED-state readback. The session's close path requests a restart; normal reconnection was checked separately. See [reverse engineering](reverse-engineering.md).

## 0.7.0 integration checks — 2026-11-21

The application was tested in a separate Preview package on the hardware above. Fresh settings reads confirmed the initial breathing style, followed by blinking, steady and restored breathing. Visual inspection confirmed both earbuds followed each selected style. The transaction verified the saved style and preservation of mapped touch settings on a separate connection after each write.

OFF was then tested while the native breathing pattern was active. Diagnostic mode entry, diagnostic LED OFF, mode exit and native OFF each received matching responses; both lights were visually confirmed off and the recovery record was cleared. A separate app ON→OFF test confirmed steady output followed by both lights off. The saved style remained breathing.

The five standalone core suites, release Lint and release assembly passed. Regression coverage includes an unavailable settings read after pending lighting cleanup and an explicit OFF that bypasses the settings prerequisite. Failure injection and automatic reconnect behavior were checked with controlled transports; the earlier full interruption/auto-on hardware matrix was not repeated for 0.7.0.

A separate build containing only the 56 allowlisted public files also passed all five core suites, release Lint and release assembly. Its Lint report contained 0 errors and 8 compatibility/deprecation/resource warnings. The installed signed candidate was built from this public-source copy, excluding historical local resource files.

Korean/dark and English/light home and style sheets were inspected on the phone. The current style was read from the connected earbuds rather than a cached display value. The main screen labels the last command as a request, not an actual light-state reading. Large-font and narrow-window checks were not repeated in this pass.

The signed 0.7.0 APK (code 9) passed signature verification for SDK 31, 32 and 37 and updated 0.6.0 in place. The first-install timestamp, registered earbuds, auto-on option and theme were retained. The installed APK matched the signed file byte-for-byte. Saved-style reading also succeeded from the updated release app. The Preview package was removed. These checks describe local build and installation validation; hosted CI is recorded separately.

## Automated checks

```sh
./gradlew :app:verifyCore :app:lintDebug :app:assembleDebug
python scripts/check-public-tree.py
```

| Suite | Coverage |
| --- | --- |
| `ProtocolTest` | Exact frames, CRC vector, reply matching, fragmented/concatenated input, malformed-input recovery and historical trial helpers |
| `RecoveryTransactionTest` | Journal before effects, fresh recovery session, both OFF paths, retained failed recovery, journal-write failures and cancellation |
| `PatternSettingsTest` / `PatternTransactionTest` | Snapshot validation, preservation guards, write journaling and readback reconciliation |
| `AutoConnectPolicyTest` | Registered/bonded/Classic filtering and stale/already-handled connection rejection |

These tests are standalone Java `main` programs. `verifyCore` explicitly runs them; Android's `testDebugUnitTest` alone does not. Tests exercise policy with controlled transports rather than real Bluetooth radios. Lint and APK builds verify Android resources and packaging, not physical operation.

## Interface checks

The interface supports English/Korean resources and light/dark/system themes. Previous on-device checks covered themed sheets, navigation, large font scale 2.0 and a narrow 320 dp window. Release-specific localization checks should verify both languages, accessibility labels, line wrapping, all choice sheets and absence of diagnostic instrumentation in ordinary builds.

For the 0.6.0 preparation on 2026-11-18, a separate Preview package was used on the documented phone to verify English/Korean home, settings, theme selection and Help in light/dark themes. The Preview package was removed afterward; the installed 0.5.0 application and its preferences were unchanged. No light command or new earbud registration was performed. Large-font/narrow-window testing was not repeated in this pass.

A copy containing only the 52 allowlisted public files passed all three core suites, debug Lint, debug assembly and unsigned release assembly. Public-tree Lint reported 0 errors and 8 warnings, including compatibility/deprecation and recommended-resource notices. Local debug/release Lint also passed. The final debug APK was checked for version 0.6.0 (code 8) and absence of the experimental instrumentation classes.

## Hosted CI

GitHub Actions completed successfully on **2026-11-20** for commit [`b8ed479`](https://github.com/suhoxpark/buds-bladelight/commit/b8ed479cb647234a1d8ef6dcc4f6bd35a136856a). The [verified run](https://github.com/suhoxpark/buds-bladelight/actions/runs/44642028785) passed Android SDK setup, the public-tree audit, all three core Java suites, `lintDebug`, `assembleDebug` and upload of the `BladeLight-development-apk` artifact. The uploaded artifact was also downloaded and checked.

Hosted CI was unavailable when 0.6.0 was first published on 2026-11-18. This later run verifies the updated workflow on `main`; it builds a development APK and does not sign or replace the published release APK. Release signing and the physical in-place update were verified separately as described below.

## Public-release update

On 2026-11-18, the release build for 0.6.0 was signed with a dedicated release key and the development-to-release signing lineage. `apksigner` verification passed for SDK 31, SDK 32 and SDK 37. The same APK was installed over the existing 0.5.0 app on the test phone without uninstalling. Package identity and first-install timestamps were retained; the registered earbuds, auto-on option and theme were checked in the updated UI. A readback of the installed APK matched the release APK byte-for-byte. This verifies migration on the documented Android 17 phone, not physical behavior on Android 12 devices. No lighting command was issued as part of this update check.

## Negative experiments

Ordinary `0x8E 01` did not turn on the lights. The phone-managed brightness trial produced visible stepping/flicker and was rejected. Phone BESAUD probes found a channel-mode mismatch; a later Linux Basic-mode connection succeeded under a different one-ear setup without sending an application payload. Their meaning and limitations are documented in [reverse engineering](reverse-engineering.md).

## Unverified

- Other phones, Buds models and firmware versions.
- Long-duration power consumption and low-battery behavior.
- All reboot and OEM power-management combinations.
- A reliable query of physical LED state.
- Starting native blinking/breathing from a fully inactive state through the app.
- Arbitrary failures outside the selected interruption boundaries.
- Samsung-setting changes exactly during an active diagnostic transaction.

Raw personal device captures and proprietary analysis inputs are not distributed with this repository. Public tables summarize the recorded results and should not be represented as a complete reproduction bundle.
