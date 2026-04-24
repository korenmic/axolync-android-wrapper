# Requirements Document

## Introduction

This feature adds real Android/Capacitor support for LRCLIB's `lrclib-local-loopback-v1` native operator.

The existing Android native companion host can run Vibra/Shazam loopback behavior, but LRCLIB needs a different operator: deploy a compressed SQLite DB, serve LRCLIB-compatible `/api/get` and `/api/search`, and expose local hit/miss truth over loopback HTTP. Remote fallback remains LRCLIB local-JS adapter policy.

## Requirements

### Requirement 1

**User Story:** As an Android wrapper maintainer, I want native companion dispatch by operator kind, so that LRCLIB does not accidentally reuse Vibra's Shazam-specific server.

#### Acceptance Criteria

1. WHEN Android loads a native companion descriptor with `runtime_operator_kind = "lrclib-local-loopback-v1"` THEN it SHALL dispatch to LRCLIB-specific Android support.
2. WHEN Android loads `shazam-discovery-loopback-v1` THEN existing Vibra behavior SHALL remain unchanged.
3. WHEN Android sees an unknown operator kind THEN it SHALL fail with explicit unsupported truth and diagnostics.
4. WHEN LRCLIB support is unavailable for the installed package THEN Android SHALL report unavailable capability rather than pretending startup succeeded.

### Requirement 2

**User Story:** As a listener, I want Android LRCLIB native mode to expose a real loopback LRCLIB-compatible API, so that local lyrics queries can run from the WebView using the same URL shape as other platforms.

#### Acceptance Criteria

1. WHEN LRCLIB native starts on Android THEN it SHALL bind a local loopback HTTP server on `127.0.0.1`.
2. WHEN the WebView calls `/api/get` or `/api/search` THEN Android SHALL return LRCLIB-compatible JSON response shapes.
3. WHEN a local result is found THEN Android SHALL signal local-hit truth through response diagnostics.
4. WHEN the local subset cannot satisfy a query THEN Android SHALL signal subset-miss or no-usable-lyrics truth without retrying remote itself.
5. WHEN the WebView cannot reach loopback THEN Android SHALL report loopback failure truthfully.

### Requirement 3

**User Story:** As an Android user, I want the compressed LRCLIB DB deployed once into app-private storage, so that startup is not repeatedly slowed by decompressing a large database.

#### Acceptance Criteria

1. WHEN Android receives a packaged `db.sqlite3.br` asset THEN it SHALL deploy it into app-private storage under a versioned native companion path keyed by addon id, companion id, payload version, and compressed asset hash.
2. WHEN a valid deployed `db.sqlite3` already exists for the same hash THEN Android SHALL reuse it without decompressing again.
3. WHEN the deployed DB is missing, corrupt, stale, or hash-mismatched THEN Android SHALL replace it from the packaged Brotli asset.
4. WHEN the app is uninstalled THEN the deployed DB SHALL be removed with app-private storage because it is not stored in shared external storage.

### Requirement 4

**User Story:** As a maintainer, I want Android LRCLIB query serving implemented in Kotlin for the first version, so that we avoid ABI-specific server binary complexity.

#### Acceptance Criteria

1. WHEN Android serves LRCLIB native requests THEN it SHALL use Kotlin-owned SQLite query logic driven by LRCLIB-owned descriptor/package metadata.
2. WHEN `/api/get` is requested THEN Android SHALL query the deployed SQLite DB using LRCLIB-compatible parameters and return compatible response semantics.
3. WHEN `/api/search` is requested THEN Android SHALL query the deployed SQLite DB using LRCLIB-compatible search parameters and return compatible response semantics.
4. WHEN SQLite open/query fails THEN Android SHALL return structured diagnostics and SHALL NOT crash the app.

### Requirement 5

**User Story:** As an operator diagnosing Android native failures, I want detailed diagnostics, so that missing payloads, DB failures, and loopback failures can be distinguished without guessing.

#### Acceptance Criteria

1. WHEN Android registers LRCLIB native support THEN diagnostics SHALL record registration loaded or missing.
2. WHEN startup runs THEN diagnostics SHALL record payload presence, compressed DB presence, deployment/reuse/replacement state, SQLite open result, loopback bind result, and connection base URL.
3. WHEN requests run THEN diagnostics SHALL record local hit, subset miss, plain-only/no-synced result, SQLite query failure, and unsupported route.
4. WHEN debug archives are exported THEN Android LRCLIB diagnostics SHALL be available to browser/reporting layers.

### Requirement 6

**User Story:** As a release maintainer, I want non-device tests before the first Android artifact trial, so that obvious LRCLIB native packaging and server errors are caught without waiting for manual device testing.

#### Acceptance Criteria

1. WHEN JVM/unit tests run THEN they SHALL cover descriptor parsing and operator dispatch.
2. WHEN tests run THEN they SHALL cover Brotli asset deployment, hash reuse, stale/corrupt replacement, and SQLite open/query fixtures.
3. WHEN tests run THEN they SHALL cover loopback route behavior for `/api/get` and `/api/search`.
4. WHEN APK/package validation runs THEN it SHALL prove the LRCLIB native asset and registry entries are present for native-capable variants.
5. WHEN final on-device proof is required THEN the task SHALL state it remains blocked until emulator/device automation is available.
