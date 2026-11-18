# Bluetooth protocol

[README](../README.md) · [Architecture](architecture.md) · [Reverse engineering](reverse-engineering.md)

This reference describes the implementation tested against Galaxy Buds3 Pro firmware R630XXU0AZG2. It is not a Samsung-supported API or a promise of compatibility with other firmware.

## Transport

| Service | UUID | Observed RFCOMM channel |
| --- | --- | --- |
| Factory Serial Port, used by BladeLight | `00001101-0000-1000-8000-00805f9b34fb` | 20 |
| Samsung management | `2e73a4ad-332d-41fc-90e2-16bef06523f2` | 27 |

BladeLight opens an insecure RFCOMM socket to the factory UUID using Android's service discovery. The observed channel numbers are not hardcoded. The registered device must already be bonded. This avoids competing for the management socket used by Samsung's app; coexistence was physically tested on the documented setup.

## Frame format

```text
FD | length_flags:u16le | message_id:u8 | payload | crc:u16le | DD
```

The lower ten bits of `length_flags` encode payload length plus 3. A frame has payload length plus 7 bytes in total. Higher bits carry message type and metadata flags. CRC-16/XMODEM uses initial value 0 and polynomial `0x1021`, covers the ID and payload, and is stored little-endian. The standard ASCII test vector `123456789` yields `0x31C3`.

The streaming decoder accepts fragmented or concatenated input, searches past noise, and rejects invalid length, delimiter or CRC. Its length field bounds buffering. These checks should not be described as comprehensive adversarial parser hardening.

## Implemented light transaction

| Step | Bytes |
| --- | --- |
| Enter diagnostic mode | `FD 04 00 12 01 30 75 DD` |
| LED ON | `FD 0A 00 13 46 46 46 45 22 31 22 EF BA DD` |
| LED OFF | `FD 0A 00 13 46 46 46 45 22 30 22 DE 89 DD` |
| Leave diagnostic mode | `FD 04 00 12 00 11 65 DD` |

Only one of the two LED commands is sent in an ordinary transaction. Message `0x13` contains ASCII `FFFE"1"` or `FFFE"0"` inside the binary frame; these are not unframed modem AT strings. The sequence is mode 1 → selected LED command → mode 0 → close. Successful ON leaves the tested diagnostic output lit after mode exit and socket closure.

Factory mode has effects beyond the light command: analysis found changes to message filtering and temporary automatic role-switch behavior. Explicit exit and interrupted-command recovery are therefore part of the implementation, not optional cleanup.

## Response handling

Mode replies must carry the requested mode byte. The LED response gate accepts bounded `LEDTEST:…,OK` text, optionally preceded by an L/R prefix, on the current session. Metadata frames flagged `0x2000` are not accepted as these replies. Only one exchange is outstanding at a time.

ON and OFF share the same reply text. There is no transaction sequence number, so a reply cannot independently establish the physical LED state. Recovery uses a new connection to avoid a previous socket's delayed response. A single L-prefixed reply also does not prove that only the left light changed: both earbuds changed in the observed trials.

## Ordinary lighting messages

Incoming notifications and outgoing requests are not interchangeable. Sending message `0x8E` with payload `01` failed to turn on the lights. In the examined request handler, the payload is not read for the lighting action; the handler invokes OFF. The frame `FD 04 00 8E 00 97 38 DD` was observed to turn off normal lighting, but this is not the production diagnostic OFF transaction.

Pattern selection is part of compound touch settings in message `0x90`. It is not a standalone power command and must not be rewritten without preserving the other settings. Its examined handler changes playback only when the normal light mode is already active. Diagnostic LEDTEST does not establish that normal state.

## Source and scope

[Protocol.java](../app/src/main/java/dev/bladelight/app/Protocol.java) implements frames and reply matching; [RecoveryTransaction.java](../app/src/main/java/dev/bladelight/app/RecoveryTransaction.java) defines production sequencing.

[GalaxyBudsClient's hidden command encoder](https://github.com/timschneeb/GalaxyBudsClient/blob/master/GalaxyBudsClient/Message/Encoder/HiddenCmdDataEncoder.cs) and [hidden-mode controller](https://github.com/timschneeb/GalaxyBudsClient/blob/master/GalaxyBudsClient/Interface/ViewModels/Pages/HiddenModePageViewModel.cs) provide independent public context. Its generic delimiter encoding differs from the quoted arguments tested here; the exact target parser and physical experiment establish this implementation's format.
