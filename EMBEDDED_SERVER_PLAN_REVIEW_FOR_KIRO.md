# Embedded Server Plan Review (Codex -> Kiro)

This review applies to:
- `EMBEDDED_SERVER_IMPLEMENTATION_PLAN.md`

Overall assessment:
- The plan direction is correct (embedded localhost server is the right fix).
- The plan is implementation-ready only after the blocking items below are corrected.

---

## Blocking Corrections (must fix before implementation)

### 1) Activity lifecycle architecture is brittle
Problem:
- Plan starts `MainActivity` from `SplashActivity` and then polls `MainActivity.isServerReady()` via static state.
- This creates race/back-stack/lifecycle fragility and couples activities through process-static flags.

Required fix:
- Use a single owner for server lifecycle (`Application`-scoped `ServerManager`) and expose readiness state.
- Keep splash visible until ready using one of these patterns:
  1. Preferred: Android SplashScreen API in `MainActivity` with `setKeepOnScreenCondition { !serverReady }`.
  2. If keeping `SplashActivity`, start server in `SplashActivity` (or Application), then launch `MainActivity` only after ready.
- Do **not** use `MainActivity` companion object flags for readiness signaling.

### 2) Server lifecycle tied to Activity is incorrect
Problem:
- Stopping server in `MainActivity.onDestroy()` will break on configuration changes / activity recreation.

Required fix:
- Move server lifecycle to app/process scope (`Application` + manager).
- Stop server only when app process terminates or explicitly during controlled shutdown.

### 3) Spec/document source of truth mismatch
Problem:
- Plan references spec edits generally, but implementation must align to the **Kiro Android wrapper spec**.

Required fix:
- Edit these files first:
  - `.kiro/specs/android-apk-wrapper/requirements.md`
  - `.kiro/specs/android-apk-wrapper/design.md`
  - `.kiro/specs/android-apk-wrapper/tasks.md`
- Ensure wording is explicit that v1 uses embedded localhost server, not `file:///` direct loading.

### 4) Local HTTP server hardening is incomplete
Problem:
- Current sample server serves asset paths with minimal checks.

Required fix:
- Add strict URI normalization and traversal protection:
  - Reject `..`, encoded traversal, and unsupported methods.
  - Map `/` -> `/index.html` only.
- Add explicit MIME mapping at minimum for `.html`, `.js`, `.css`, `.json`, `.map`, `.svg`, `.png`, `.jpg`, `.woff`, `.woff2`, `.ttf`, `.wasm`.
- Return deterministic 404 and 405 responses.

### 5) WebView allowlist/origin policy needs deterministic origin source
Problem:
- Origin allowlist logic depends on Activity-local server object and runtime checks.

Required fix:
- Derive allowed origin from `ServerManager.baseUrl` (single source).
- Strictly allow only that origin (and explicitly documented external API origins if required).
- Keep mixed content off and file access restrictions as already defined.

---

## Important Non-Blocking Improvements (high value)

1. Readiness gate should include both:
- server health ready (`/health` 200), and
- first WebView page success signal.

2. Add startup timeout states:
- `STARTING`, `READY`, `FAILED(timeout/exception)` with user-facing message.

3. Keep a feature flag fallback only for emergency rollback (optional), but default path must be embedded server.

4. Add structured logs:
- startup start/end timestamps,
- chosen port,
- failure reason category.

---

## Test Plan Adjustments (minimum required)

Additive tests only (do not remove existing suites):

1. Unit: `LocalHttpServer`
- serves `/health` 200,
- serves `index.html`,
- rejects traversal,
- returns 404 for unknown path.

2. Unit/instrumentation: server lifecycle
- server survives Activity recreation,
- readiness state transitions are correct.

3. Instrumentation: WebView boot
- initial loaded URL is `http://127.0.0.1:<port>/...` (not `file:///`).

4. Failure path
- server startup failure produces deterministic UX and does not hang splash.

---

## Recommended Revised Implementation Order

1. Update `.kiro/specs/android-apk-wrapper/*` (requirements/design/tasks).
2. Implement `ServerManager` (Application scope).
3. Implement hardened `LocalHttpServer`.
4. Wire `MainActivity` to load `ServerManager.baseUrl`.
5. Implement splash/readiness gate without static activity flags.
6. Add/adjust tests (additive).
7. Validate on device and run existing + new test suites.

---

## Decision

- The current plan is a good foundation.
- Proceed only after applying the blocking corrections above.
