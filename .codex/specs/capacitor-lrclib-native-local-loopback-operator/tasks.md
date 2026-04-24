# Implementation Plan

- [x] 1. Add Android operator-kind dispatch for LRCLIB.
  - Register `lrclib-local-loopback-v1` separately from `shazam-discovery-loopback-v1`.
  - Preserve existing Vibra/Shazam behavior unchanged.
  - Return explicit unsupported diagnostics for unknown operator kinds.

- [x] 2. Implement app-private LRCLIB DB deployment.
  - Locate packaged `db.sqlite3.br` assets from the installed native payload.
  - Compute compressed asset hash and deploy to app-private versioned native companion storage.
  - Reuse valid deployed DBs for matching hashes.
  - Replace missing, stale, corrupt, or hash-mismatched deployments.

- [x] 3. Add Kotlin SQLite query support for LRCLIB routes.
  - Open deployed SQLite DB safely and read-only where possible.
  - Implement `/api/get` parameter handling and compatible JSON response behavior.
  - Implement `/api/search` parameter handling and compatible JSON response behavior.
  - Normalize local hit, subset miss, plain-only/no-synced, and query error results.

- [x] 4. Serve LRCLIB over Android loopback HTTP.
  - Bind a local server on `127.0.0.1` with an available port.
  - Route `/api/get` and `/api/search` to the SQLite query layer.
  - Return `loopback-http-base-url` connection data to the WebView bridge.
  - Add CORS/headers needed for WebView fetch compatibility.

- [x] 5. Add Android LRCLIB native diagnostics.
  - Emit lifecycle diagnostics for registration, payload, DB deploy, SQLite open, loopback bind, connection resolution, route request, and failure source.
  - Include local hit/miss/plain-only result classification.
  - Surface diagnostics to the browser/debug archive path used by native companion logs.

- [x] 6. Add non-device regression coverage.
  - Test descriptor parsing and dispatch.
  - Test DB deploy/reuse/replacement with fixture assets.
  - Test SQLite query fixtures for `/api/get` and `/api/search`.
  - Test loopback route behavior where JVM-compatible.
  - Add package/APK validation for LRCLIB native assets and registry entries.

- [ ] 7. Document final Android proof limits.
  - State that first implementation is best-effort without approved emulator/device automation.
  - Add expected manual/device proof steps and required diagnostics for the first native-capable Android artifact trial.
