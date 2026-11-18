<p align="center"><img src="app/src/main/res/drawable-nodpi/bladelight_reference.png" width="112" alt="BladeLight — two luminous blades"></p>

# BladeLight

Control the blade lights on your **Galaxy Buds3 Pro** from Android. Turn them on or off, or let them turn on when your earbuds reconnect.

**English** · [한국어](README.ko.md)

## Features

- One-tap light controls and optional auto-on when your earbuds reconnect.
- Light, dark and system themes.
- Interrupted-command recovery with no permanent Bluetooth connection.
- No accounts, ads, analytics or Internet permission.

**Current limitation:** ON produces a steady light. Samsung's blinking and breathing styles are not applied, but their saved settings are not overwritten. The signature artwork is not a live indicator of the earbuds' light state.

## Compatibility

Requires **Android 12 or later**. Hardware testing has covered Galaxy Buds3 Pro with firmware **R630XXU0AZG2**, on a Galaxy S25 FE running Android 17 / One UI 9.0. Other firmware and phones are unverified; the minimum OS version is a build requirement, not an assertion of testing across every supported version.

BladeLight is an independent project, not affiliated with Samsung, and uses a reverse-engineered diagnostic interface. Long-duration battery consumption and low-battery behavior have not been characterized. See the [validation record](docs/validation.md).

## Getting started

1. Install a signed BladeLight APK, or [build the app](#build-from-source).
2. Pair your Galaxy Buds3 Pro in Android Bluetooth settings.
3. Open BladeLight, allow Nearby devices access, and select your earbuds.
4. Use **On** and **Off**. Samsung's companion app can remain running.
5. Enable **Turn on when connected** if desired. It applies on the next connection; disabling it does not turn off lights already on.

If you renamed your earbuds, keep **Buds3 Pro** in their Bluetooth name so they appear in the selector. This filter is a selection aid, not hardware authentication.

After force-stopping BladeLight in Android settings, open it again to resume automatic operation. If a command is interrupted, reconnect the earbuds and reopen the app to let recovery finish.

## Build from source

Use JDK **21** (17 minimum), Android SDK **36**, and Build Tools **35.0.0**. The wrapper pins Gradle **8.13** with a distribution checksum; Android Gradle Plugin is **8.13.2**.

Set `ANDROID_HOME` to your SDK, or configure the SDK in Android Studio. From the repository root:

```sh
./gradlew :app:verifyCore :app:lintDebug :app:assembleDebug
```

On Windows, use `gradlew.bat`. Output: `app/build/outputs/apk/debug/app-debug.apk`. Debug builds use a development key and are not official release artifacts.

`verifyCore` explicitly executes three standalone Java test suites. Android's `testDebugUnitTest` alone does not run them. See [releasing](docs/releasing.md) for signing and publication instructions.

## Technical documentation

| Document | Contents |
| --- | --- |
| [Architecture](docs/architecture.md) | Transactions, recovery, automatic operation and local data |
| [Protocol](docs/protocol.md) | Transport, exact frames, CRC and response handling |
| [Reverse engineering](docs/reverse-engineering.md) | Firmware provenance, native patterns, failed alternatives and BESAUD investigation |
| [Validation](docs/validation.md) | Physical observations, automated checks and remaining uncertainty |
| [Releasing](docs/releasing.md) | Builds, signing and publication workflow |

## Contributing

Include the phone, Android and Buds firmware versions and reproduction steps in issue reports. Distinguish protocol replies from what the lights actually did, and remove addresses, serials and unrelated logs before sharing evidence. See [CONTRIBUTING.md](CONTRIBUTING.md).

The public tree contains application source, required artwork, tests, build configuration and curated documentation. Firmware, Samsung APKs, decompiled source, private captures, signing keys and experimental tools are excluded.

## License and acknowledgements

[MIT License](LICENSE). Tooling retains its own licenses; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Thanks to [GalaxyBudsClient](https://github.com/timschneeb/GalaxyBudsClient) for public protocol research and the [Galaxy Buds firmware archive](https://github.com/timschneeb/galaxy-buds-firmware-archive) for the analysis reference. Samsung application code and firmware are not redistributed here.
