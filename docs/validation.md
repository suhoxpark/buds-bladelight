# Validation and limits

[README](../README.md) · [Architecture](architecture.md) · [Reverse engineering](reverse-engineering.md)

## Evidence levels

Three kinds of evidence are kept distinct: source/instruction analysis, protocol exchanges, and direct observation of the earbuds. A successful reply is not a light-state measurement. Historical physical validation applies to the tested control implementation; localization/build changes do not constitute a new hardware compatibility campaign.

## Hardware baseline

Physical control testing on 2026-11-17 used Galaxy Buds3 Pro, user-reported firmware R630XXU0AZG2, and Galaxy S25 FE / Android 17 / One UI 9.0. The firmware analysis used a matching-version archive, not a device readback. Tests were bounded functional observations, not certification or a broad device matrix.

| Behavior | Observed result |
| --- | --- |
| ON and OFF | User confirmed both lights changed |
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

## Automated checks

```sh
./gradlew :app:verifyCore :app:lintDebug :app:assembleDebug
python scripts/check-public-tree.py
```

| Suite | Coverage |
| --- | --- |
| `ProtocolTest` | Exact frames, CRC vector, reply matching, fragmented/concatenated input, malformed-input recovery and historical trial helpers |
| `RecoveryTransactionTest` | Journal before effects, fresh recovery session, mode exit after failed OFF, retained failed recovery, journal-write failures and cancellation |
| `AutoConnectPolicyTest` | Registered/bonded/Classic filtering and stale/already-handled connection rejection |

These tests are standalone Java `main` programs. `verifyCore` explicitly runs them; Android's `testDebugUnitTest` alone does not. Tests exercise policy with controlled transports rather than real Bluetooth radios. Lint and APK builds verify Android resources and packaging, not physical operation.

## Interface checks

The interface supports English/Korean resources and light/dark/system themes. Previous on-device checks covered themed sheets, navigation, large font scale 2.0 and a narrow 320 dp window. Release-specific localization checks should verify both languages, accessibility labels, line wrapping, all choice sheets and absence of diagnostic instrumentation in ordinary builds.

For the 0.6.0 preparation on 2026-11-18, a separate Preview package was used on the documented phone to verify English/Korean home, settings, theme selection and Help in light/dark themes. The Preview package was removed afterward; the installed 0.5.0 application and its preferences were unchanged. No light command or new earbud registration was performed. Large-font/narrow-window testing was not repeated in this pass.

A copy containing only the 52 allowlisted public files passed all three core suites, debug Lint, debug assembly and unsigned release assembly. Public-tree Lint reported 0 errors and 8 warnings, including compatibility/deprecation and recommended-resource notices. Local debug/release Lint also passed. The final debug APK was checked for version 0.6.0 (code 8) and absence of the experimental instrumentation classes. GitHub Actions is configured but was not executed remotely during this preparation; no signed public release is claimed.

## Public-release update

On 2026-11-18, the release build for 0.6.0 was signed with a dedicated release key and the development-to-release signing lineage. `apksigner` verification passed for SDK 31, SDK 32 and SDK 37. The same APK was installed over the existing 0.5.0 app on the test phone without uninstalling. Package identity and first-install timestamps were retained; the registered earbuds, auto-on option and theme were checked in the updated UI. A readback of the installed APK matched the release APK byte-for-byte. This verifies migration on the documented Android 17 phone, not physical behavior on Android 12 devices. No lighting command was issued as part of this update check.

## Negative experiments

Ordinary `0x8E 01` did not turn on the lights. The phone-managed brightness trial produced visible stepping/flicker and was rejected. BESAUD connection-only probes found a channel-mode mismatch; no native lighting payload reached that channel. Their meaning and limitations are documented in [reverse engineering](reverse-engineering.md).

## Unverified

- Other phones, Buds models and firmware versions.
- Long-duration power consumption and low-battery behavior.
- All reboot and OEM power-management combinations.
- A reliable query of physical LED state.
- Native blinking/breathing activation from the app.
- Arbitrary failures outside the selected interruption boundaries.
- Samsung-setting changes exactly during an active diagnostic transaction.

Raw personal device captures and proprietary analysis inputs are not distributed with this repository. Public tables summarize the recorded results and should not be represented as a complete reproduction bundle.
