# Changelog

## 0.7.0 — 2026-11-21

- Read and change the saved light style using guarded settings updates and fresh readback.
- Request both diagnostic and native OFF during light cleanup and recovery.
- Clarify saved style, command acknowledgements and physical light-state limitations.
- Extend protocol and research documentation.

The current ON command still produces steady diagnostic light. Native pattern activation from a fully inactive state and physical LED readback remain unresolved.

## 0.6.0 — 2026-11-18

- English and Korean resources, including accessibility labels and notifications.
- Android app-language support.
- Simplified home, settings and choice sheets; operational guidance remains in Help.
- Public architecture, protocol, reverse-engineering and validation documentation.
- Portable Gradle Wrapper, explicit core test tasks and a restricted public-file allowlist.

Lighting control is unchanged: ON produces steady diagnostic output; native blinking and breathing remain unsupported.

## 0.5.0 — Local validation baseline

- Reference-based dual-blade artwork, adaptive launcher icon and light/dark themes.
- Manual ON/OFF, reconnect auto-on and interrupted-command recovery validated on the documented device combination.

This was a local testing version, not a previously published GitHub release.
