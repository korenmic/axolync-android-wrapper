# Design Document

## Overview

Android gets a separate LRCLIB native operator alongside the existing Vibra operator. The host dispatches by `runtime_operator_kind`, deploys the packaged SQLite DB into app-private storage, serves LRCLIB-compatible loopback HTTP routes, and reports local result truth.

This design intentionally keeps Android transport as raw loopback HTTP. It does not introduce a native-mediated request bridge.

## Architecture

### Operator Dispatch

Native companion registration should map operator kinds:

- `shazam-discovery-loopback-v1`: existing Vibra server
- `lrclib-local-loopback-v1`: new LRCLIB server

Unknown operator kinds return explicit unsupported status.

### DB Deployment

Android deploys the compressed DB into app-private storage. Representative path:

```text
/data/user/0/<applicationId>/files/axolync/native-service-companions/axolync-addon-lrclib/lrclib_local/<payloadVersion>/<assetSha256>/db.sqlite3
```

Deployment state should include:

- compressed asset path/name
- compressed SHA-256
- deployed DB path
- deployed timestamp
- SQLite header/schema validation result

The app-private path is removed by Android on uninstall.

### SQLite Query Serving

For the first version, Android implements query serving in Kotlin rather than embedding ABI-specific LRCLIB binaries.

The implementation should parse:

- `/api/get?artist_name=...&track_name=...`
- `/api/search?track_name=...&artist_name=...`
- `/api/search?track_name=...`

Response shape should be LRCLIB-compatible enough for the existing LRCLIB adapter parser. If exact upstream SQL semantics cannot be perfectly replicated in the first implementation, the gap must be documented in diagnostics and tests.

### Loopback Server

The server binds to `127.0.0.1` on an available port and returns a `loopback-http-base-url` connection.

Response diagnostics should include the local result classification. This can be an HTTP header matching the descriptor or equivalent structured native diagnostics.

### Fallback Policy

Android does not call remote LRCLIB. It returns local hit/miss/plain-only truth. The LRCLIB local-JS adapter decides whether to retry the configured remote base URL.

### Diagnostics

Diagnostics should separate:

- registration missing
- payload missing
- compressed DB missing
- DB deploy failure
- SQLite open failure
- SQLite query failure
- loopback bind failure
- WebView loopback reachability failure
- local hit
- subset miss
- plain-only/no-synced result

## Error Handling

- Missing payload or DB asset: native capability unavailable, setting disabled by browser truth.
- Brotli failure: startup fails with deploy failure diagnostics.
- SQLite failure: startup or request fails with SQLite diagnostics.
- Unsupported route: HTTP 404 with JSON error and diagnostics.
- Loopback bind failure: startup fails with connection unavailable diagnostics.

## Testing Strategy

- JVM tests for descriptor parsing and dispatch by operator kind.
- Fixture tests for Brotli DB deployment and hash reuse.
- Fixture tests for stale/corrupt DB replacement.
- SQLite fixture tests for `/api/get` and `/api/search`.
- Loopback route tests using Android/JVM-compatible HTTP server abstraction.
- APK/package validation proving native assets and registry entries exist for native-capable variants.
- Explicit manual/on-device proof blocker note until emulator/device automation is approved.
