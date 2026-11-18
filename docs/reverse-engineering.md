# Reverse-engineering findings

[README](../README.md) · [Protocol](protocol.md) · [Validation](validation.md)

## Question and current result

The investigation asked how a phone can start the Galaxy Buds3 Pro blade lights without holding the earbuds' stems, and whether it can retain Samsung's selected blinking or breathing style.

The diagnostic LEDTEST route successfully turns both lights on and off. It produces steady output independently of the saved native pattern. The normal pattern engine and its peer synchronization path were located, but **native-pattern activation from the application remains unresolved**. No experimental breathing substitute or peer packet is included in the production controller.

## Evidence and provenance

Static analysis used [archived FOTA_R630XXU0AZG2.bin](https://github.com/timschneeb/galaxy-buds-firmware-archive/blob/main/R630/FOTA_R630XXU0AZG2.bin), not a firmware readback from the tested earbuds.

| Item | Value |
| --- | --- |
| Full container size | 8,683,104 bytes |
| Full container SHA-256 | `2e1bd0151459c21daf2d669c6ff6b548821140a6247817fce9534a12299622ce` |
| Extracted segment 6 SHA-256 | `7bc510b4bbaef21ec4ba191871113086a611cd2c53a183d3379472fd3d72e47e` |
| Container magic | `0xCAFECAFE` |
| Segment 6 file offset / size | `0x1C` / `0x847E40` |
| Segment CRC32 | `0x0985CC89`, matching the container record |
| Code mapping | ARM Thumb at `0x2C020000`, alias `0x0C020000` |

Embedded strings matched the user-reported firmware version. The archive is third-party provenance, not a Samsung authenticity verification. Its bytes were not compared with an installed readback or a fresh official CDN download.

Official companion-app message builders were examined locally with JADX. Firmware work used Capstone instruction inspection and targeted Ghidra 12.2 decompilation in read-only mode. Virtual addresses were checked against literal strings and call targets. A container offset maps to `offset - 0x1C + 0x2C020000` for this segment.

Decompiled output is not recovered original source. Incorrect function boundaries and unrecognized non-returning stack-guard paths can merge adjacent routines. Important branches were checked against instructions. Function names below are descriptive analysis labels or embedded names, not a complete symbol table. Addresses apply only to this image.

Only original explanatory text, factual identifiers and independently implemented application code are published. Vendor binaries, extracted code, raw device captures and local analysis tooling remain outside the public tree. The observations below are reported results, not a bundled independently reproducible hardware dataset.

## Diagnostic output

Message `0x12` selects diagnostic mode 1 or mode 0. Mode1 enables the factory Idle dispatcher, which passes message `0x13` to the four-character hexadecimal command parser. The quoted argument of `FFFE` is converted to an integer: zero selects OFF; every nonzero value selects the same no-argument ON backend.

| Address | Finding |
| --- | --- |
| `0x2C25E6DC` | Factory mode handler |
| `0x2C266D18` | Idle AT-data dispatch |
| `0x2C1AD194` | AT command parser |
| `0x2C264184` | Factory hardware test dispatch |
| `0x2C2DEEBC` / `0x2C2DEF68` | Diagnostic LED ON / OFF |

The ON backend configures output `0x60` with a fixed-output tuple and RAM state; OFF disables that output. No nonvolatile write was identified in these LED backends. This does not make the enclosing diagnostic mode side-effect-free: it changes filtering and temporary role-switch behavior. The application explicitly exits mode and retains recovery state after failure.

Sending `FFFE"2"` or `FFFE"3"` cannot select blinking or breathing through this branch. It reaches the same output as `FFFE"1"`.

## Native pattern engine

The examined style mapping is steady 1, blinking 2, breathing 3. `TwfLed_PlayAtWill` adds 5, producing normal scenes 6,7,8 respectively.

```text
Coordinated stem hold
  → touch mapping produces internal event 0x2E0
  → paired-side / placement / timestamp / battery checks
  → saved style from configuration offset +0x3EA
  → TwfLed_PlayAtWill
  → normal scene 6, 7 or 8
```

| Address | Role |
| --- | --- |
| `0x2C2ABEA8` | Maps raw hold input to the internal event under touch-state conditions |
| `0x2C2AC14C` | Publishes the six-byte gesture |
| `0x2C276094` | AtWill dispatcher |
| `0x2C274C28` | Coordinated physical-gesture handler |
| `0x2C274200` | Plays the selected AtWill pattern |
| `0x2C2750BC` | Applies changed `0x90` style when normal mode is active |
| `0x2C275870` | Handles peer state synchronization `0x2D5` |
| `0x2C274FF0` | Handles state update `0x2D6` without playback |

The gesture handler checks placement, both sides' input flags, a timestamp interval below `0x2BD`, and battery thresholds of 30. These normal-engine conditions must not be assumed to protect the separate diagnostic output.

Peer synchronization carries `[side, scene, savedStyle, timestamp32LE]`. Crucially, the handler writes the received scene before checking for scenes 6–8. Its predicate therefore does **not** prove that local output had to be active beforehand. A contrary early interpretation was rejected after instruction-level reinspection. Event `0x2D6` alone updates state and does not play a pattern.

## Phone-to-peer entry points

The external SPP services register mobile Format 1/2/3 parsers. Format 1 and Format 3 read 8-bit IDs. Format 2 maps fixed inputs to IDs `0x50`–`0x54`. The metadata flag does not expand the command ID to 16 bits. The internal bridge parser at `0x2C1B7A20` uses 16-bit IDs and is registered separately.

| Candidate | Finding |
| --- | --- |
| Ordinary `0x8E 01` | Physical ON attempt failed; request handler invokes OFF without using this payload |
| Compound `0x90` | No verified start from inactive normal mode |
| `DEBUG_TOUCH` | Recording/debug telemetry, not a verified gesture injector |
| ACT `0x4B` (decimal75) | Open/close, charging/hall events and factory operations; no arbitrary event wrapper |
| Property writes | Examined tags0301–030C and registered callbacks exposed no native-light start |
| Custom GATT | Same mobile Format 1 parser |
| LE credit L2CAP | Same Format 1/2/3 bindings as SPP |
| `BRDG_FORWARD` | Preserves parsed ID/payload and changes route |
| Socket relay | Transfers queued messages on role change; no payload-to-internal-ID substitution |

The parser binding and callback searches narrow the verified routes. They do not mathematically exclude every dynamically computed pointer or every future firmware implementation. Provisional filenames describing recording functions as “input simulation” were corrected and are not evidence of an injection API.

## Rejected phone-managed breathing

Diagnostic `00E1` LEDCTRLT exposes levels 1–9 and restarts output between changes. Values 10 or greater use a fixed maximum descriptor, not a breathing selector. Calibration and nonvolatile brightness commands were excluded from testing.

A short phone-managed trial sent 37 commands and received 37 replies over 13.101 seconds. Both earbuds changed brightness, but the user observed visible steps and flicker. Both were observed off after cleanup. An earlier trial stopped after one response because it incorrectly expected replies from both sides. A single L-prefixed reply corresponded to both earbuds changing, so reply prefixes cannot substitute for physical observations.

This alternative was rejected. Increasing the send rate alone does not establish smooth output, and no permanent brightness loop or background keepalive was added.

## BESAUD transport and connection probes

The bridge is carried by IBRT command `0x8701` over BESAUD **Classic L2CAP PSM `0x0033`**. Registration at `0x2C16D0AC` and outbound connection code both use that PSM. The lower command table contains the bridge receive callback. Static packet construction is:

```text
80 | command:u16le | sequence:u16le | payload
```

Command `0x8701` carries the internal bridge frame. No such application payload was sent in the probes. The examined BESAUD callback selects an available channel context; an existing peer connection may occupy it. No explicit remote-address allowlist was found in that callback, but lower-level security and context occupancy remained separate unknowns.

A disposable phone diagnostic package tested connection only, with an 8-second socket deadline and no output stream writes:

| Probe | Result |
| --- | --- |
| Ordinary app API | Classic socket type rejected; hidden-method reflection blocked before connecting |
| Secure Classic L2CAP, instrumentation-only hidden-API exemption | Timeout after 8.018 seconds |
| Existing factory RFCOMM control | Connected in 318 ms, then closed |
| Insecure Classic L2CAP, same PSM | Timeout after 8.020 seconds |

The phone stack reported “Peer does not support our desired channel types” after the channel security-complete event. The inspected [AOSP Classic socket implementation](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/refs/heads/main/system/btif/src/btif_sock_l2cap.cc) supplies ERTM options; the checked flags/settings provided no basic-mode selector. This corroborates the mode mismatch but is not analysis of the exact Samsung native Bluetooth binary. The [Android device implementation](https://android.googlesource.com/platform/packages/modules/Bluetooth/+/5b0a0284d21c4cfa9e2a99afeca348c601a95f6c/framework/java/android/bluetooth/BluetoothDevice.java) also distinguishes hidden Classic L2CAP methods from the public LE channel API.

The probe was removed, the ordinary app and preferences were preserved, and Bluetooth remained connected. No global hidden-API policy was changed, no pairing was replaced, and no LED/style/firmware payload was sent. A compatible BESAUD channel and native-pattern ON remain unverified. A timeout at channel setup must not be described as rejection of a lighting command.
