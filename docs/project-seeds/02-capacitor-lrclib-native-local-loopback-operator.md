# Capacitor LRCLIB Native Local Loopback Operator

## Summary

Add real Android/Capacitor support for LRCLIB's `lrclib-local-loopback-v1` native operator instead of treating the existing Vibra/Shazam loopback server as generic enough.

This seed owns Android wrapper implementation and proof surfaces only. LRCLIB addon metadata and DB asset authority stay in the LRCLIB repo, and browser UI policy stays generic.

## Priority

- `P0`

## Product Context

The current Android native companion host can load descriptors and run a native loopback server, but the implemented server is Shazam/Vibra-specific: it expects `uri` and `samplems` and proxies to Shazam-like upstreams.

LRCLIB native mode needs a different server:

- deploy/read a packaged Brotli-compressed SQLite DB asset
- expose LRCLIB-compatible `/api/get` and `/api/search`
- classify local hit, local miss, plain-only/no-synced result, deployment failure, SQLite failure, and remote fallback
- return a connection the Android webview can actually use

Without this, Android can display a native LRCLIB checkbox while having no real native LRCLIB implementation.

## Technical Constraints

- Android-specific code stays in `axolync-android-wrapper`.
- The wrapper host must dispatch by `runtime_operator_kind`.
- Existing Vibra `shazam-discovery-loopback-v1` behavior must keep working and remain separate.
- Unknown operator kinds must fail with explicit unsupported truth.
- LRCLIB-specific route and fallback semantics should be driven by LRCLIB-owned descriptors/package data as much as practical, not browser hardcoding.
- The Android implementation must surface diagnostics for:
  - registration loaded / missing
  - payload missing
  - compressed DB missing
  - DB deployment/decompression failure
  - SQLite open/query failure
  - local subset hit/miss
  - remote fallback hit/miss/failure
  - webview connection failure
- Because emulator/device automation is not yet approved, the first implementation must use best-effort JVM/unit/staging tests and learned guardrails from the Vibra Capacitor work.
- The seed must explicitly state that final on-device proof remains blocked until emulator/device automation exists.
- Android should implement LRCLIB native serving directly in Kotlin using the LRCLIB-owned descriptor and packaged SQLite DB asset. Do not require a packaged native server binary per ABI for the first version.
- Android must use raw loopback HTTP for LRCLIB, matching the other wrapper behavior. Do not introduce a native-mediated request bridge unless a future seed explicitly changes that policy.
- The deployed DB must live in app-private storage under a versioned native companion path keyed by addon id, companion id, payload version, and compressed asset hash. A representative target is `/data/user/0/<applicationId>/files/axolync/native-service-companions/axolync-addon-lrclib/lrclib_local/<payloadVersion>/<assetSha256>/db.sqlite3`.
- Android must decompress the Brotli DB only once per matching hash. It must reuse the decompressed DB if present and valid, and replace it when stale, corrupt, or hash-mismatched.
- App-private DB deployment means Android uninstall removes the decompressed DB with the app's private storage. The implementation must not store the decompressed DB in shared external storage.
- Remote fallback remains LRCLIB local-JS adapter policy, not Android wrapper policy. Android native loopback exposes local result truth and diagnostics; it does not own cross-platform fallback orchestration.

## Open Questions

Resolved for spec-making:

1. Android implements SQLite query serving directly in Kotlin for the first native LRCLIB path.

2. Android webview uses raw loopback HTTP. If loopback proves broken, diagnostics should expose that truth rather than silently switching transport shape.

3. The deployed SQLite DB lives in app-private versioned native companion storage and is reused per compressed asset hash.

4. Minimum non-device proof includes JVM/unit/package tests for descriptor parsing, asset staging, DB deploy/decompress, SQLite query fixture, route behavior, diagnostics, and APK asset/registry proof. Final on-device proof remains blocked until emulator/device automation exists.
