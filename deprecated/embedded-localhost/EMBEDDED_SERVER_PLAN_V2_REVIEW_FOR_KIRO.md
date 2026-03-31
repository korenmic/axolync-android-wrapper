# Embedded Server Plan V2 Review (Codex -> Kiro)

Reviewed file:
- `EMBEDDED_SERVER_IMPLEMENTATION_PLAN_V2.md`

Overall assessment:
- V2 is significantly better than V1 and directionally correct.
- It is close to implementation-ready, but still has a few blocking issues that should be fixed before coding.

---

## What V2 got right

1. Correctly moves server ownership to app scope via `ServerManager`.
2. Removes Activity-static readiness coupling from V1.
3. Adds URI traversal/method hardening in server plan.
4. Defines additive test strategy (does not replace existing suites).
5. Keeps explicit spec-first update phase.

---

## Blocking fixes required before implementation

### B1) Splash strategy must be singular (pick one)
Problem:
- V2 keeps two alternatives (SplashActivity polling and SplashScreen API) mixed in plan/tasks.
- Mixed strategy will cause drift and duplicate work.

Required fix:
- Choose one approach as final for this project.
- Recommended: **MainActivity + Android SplashScreen API** only.
- If using SplashScreen API, remove SplashActivity-specific signaling/polling references from the plan.

### B2) MainActivity must not fail fast while server is still STARTING
Problem:
- Planned `MainActivity.onCreate()` checks `!serverManager.isReady()` and immediately shows error.
- This conflicts with startup where state may still be `STARTING`.

Required fix:
- Only show fatal error if state is `FAILED` or startup timeout reached.
- While `STARTING`, keep splash condition active and continue waiting.

### B3) Cleartext localhost policy must be explicitly addressed
Problem:
- Plan uses `http://127.0.0.1:<port>` but does not explicitly define cleartext policy.
- On modern Android, cleartext policy/network security config can block HTTP requests.

Required fix:
- Add one explicit item in plan/spec:
  - either `android:usesCleartextTraffic="true"` (broad), or
  - preferred: network security config allowing cleartext only for localhost.
- Add a test/check that WebView localhost load works on target API levels.

### B4) Server start should be resilient to concurrent calls
Problem:
- `ServerManager.startServer()` sample is not clearly concurrency-safe for multiple callers.

Required fix:
- Make `startServer()` synchronized/serialized and idempotent:
  - if `READY` -> no-op success
  - if `STARTING` -> return in-progress/await outcome
  - if `FAILED` -> explicit retry path

### B5) Server shutdown assumptions are inaccurate
Problem:
- V2 mentions cleanup in `Application.onTerminate()` risk handling; `onTerminate()` is not reliable on production devices.

Required fix:
- Do not rely on `onTerminate()` for correctness.
- Document that server lifetime equals process lifetime; explicit stop is optional best-effort only.

---

## Strongly recommended (non-blocking)

1. **Add SPA fallback behavior**:
- unknown non-asset route -> serve `index.html` (if router requires it).

2. **Tighten traversal guard**:
- normalize then reject suspicious paths before asset lookup.
- keep deterministic `403/404/405` behavior.

3. **Feature flag policy**:
- keep emergency fallback optional, but ensure default path is embedded server and tested.

4. **Observability**:
- include startup metrics fields: `state`, `port`, `durationMs`, `failureCategory`.

---

## Test-plan corrections required

Add or clarify these tests:

1. Startup state tests:
- `STARTING -> READY`
- `STARTING -> FAILED`
- timeout handling without premature error.

2. Cleartext/localhost compatibility test:
- verify WebView can load `http://127.0.0.1:<port>/index.html` on target API levels.

3. Concurrency/idempotency tests for `startServer()`.

4. Rotation/config-change test proving no server restart loop and consistent base URL availability.

---

## Required plan edits summary (concise)

1. Remove dual splash alternatives; pick one final approach.
2. Replace `MainActivity` fail-fast check with state-aware handling (`FAILED` only) + timeout path.
3. Add explicit localhost cleartext policy + validation step.
4. Add `ServerManager` concurrency/idempotency behavior to design + tests.
5. Remove reliance on `Application.onTerminate()` for correctness.

---

## Decision

- **Status: Needs one more revision before implementation.**
- After the five blocking items are fixed in V3, I expect this to be implementation-ready.
