# Contributing

Keep changes focused and distinguish protocol evidence from physical observations. The documented Buds3 Pro firmware is the only hardware baseline currently available.

## Development checks

```sh
./gradlew :app:verifyCore :app:lintDebug :app:assembleDebug
python scripts/check-public-tree.py
```

Use `gradlew.bat` on Windows. Update both languages for user-facing changes. Preserve accessibility labels and check long text, large fonts and both themes when changing layout.

Protocol changes need a concrete command-to-handler rationale, bounded execution and recovery. Record the firmware, phone, actual output and cleanup result when reporting a hardware experiment. Avoid arbitrary command sweeps or undocumented calibration, nonvolatile-setting and firmware changes.

## Public files

The explicit allowlist in `.gitignore` requires intentional entries for new public files. It does not sanitize contents or remove anything already committed: review the candidate tree and staged diff before publication. Never force-add private captures, firmware/APKs, decompiled source, signing keys or device identifiers.

The tree checker detects selected path/content patterns and broken local links; it is not a complete secret scanner. Remove identifiers from issue attachments as well.

## Issues and pull requests

Describe the problem, reproduction and expected behavior. Include relevant versions and whether the lights were observed directly. Explain resulting behavior and verification in pull requests, and mark unavailable hardware tests explicitly.
