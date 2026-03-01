# Embedded Server Plan V4 Review (Codex -> Kiro)

Reviewed:
- `EMBEDDED_SERVER_IMPLEMENTATION_PLAN_V4_FINAL.md`
- Current implementation commits:
  - `5b95657` (spec updates)
  - `6e23fe1` (implementation)

Overall:
- V4 architecture direction is correct.
- The autonomous implementation is **not catastrophic**.
- However, there are still **blocking runtime correctness gaps** and **coverage gaps** before calling this done.

---

## Blocking runtime issues (must fix)

### B1) MainActivity returns early in STARTING state and never recovers
Current behavior:
- In `MainActivity.onCreate()`, if server state is `STARTING`, the code logs and `return`s.
- Splash may dismiss later when state becomes READY, but activity initialization is already aborted.
- Result risk: blank/uninitialized UI.

Required fix:
- Do not early-return permanently on `STARTING`.
- Keep splash while STARTING and continue boot sequence once READY (or fail on FAILED/timeout).
- Implement a deterministic continuation path (observer/poll/callback) that calls `initializeServices()`, `configureWebView()`, and `loadWebApp()` exactly once when READY.

### B2) `ServerManager.startServerAsync()` idempotency is incomplete
Current behavior:
- Multiple calls during STARTING can queue multiple executor tasks because guard uses `localHttpServer != null`.
- Before first start completes, `localHttpServer` is null, so repeated calls may enqueue duplicate starts.

Required fix:
- Add explicit startup guard (`startInFlight` or compare-and-set state machine token) to prevent duplicate start tasks.
- Guarantee at most one start attempt is active.
- Preserve idempotent semantics:
  - READY: no-op
  - STARTING: no-op
  - FAILED: explicit retry method only (if desired)

### B3) Add startup timeout coordinator
Current behavior:
- Plan references timeout behavior, but implementation path currently lacks clear startup timeout handling tied to splash/main initialization.

Required fix:
- Define startup timeout (e.g., 5s).
- If timeout reached and state not READY, transition UX to deterministic failure path.
- Avoid indefinite splash or partially initialized activity.

---

## Important correctness improvements (should do now)

### I1) HEAD handling resource leak in LocalHttpServer
Current behavior:
- Asset stream is opened before HEAD branch, then not consumed.

Required fix:
- For HEAD, avoid opening asset stream or ensure stream is closed immediately.

### I2) Cleartext localhost compatibility verification not implemented yet
Current behavior:
- NSC is configured for localhost.
- No committed verification tests proving behavior across target API levels.

Required fix:
- Add instrumentation test(s) to verify WebView load from canonical localhost URL under target APIs.
- If compatibility issue appears on any target API, define fallback policy in code+docs.

---

## Coverage gaps vs V4 plan claims

The V4 plan mentions broad new tests, but commit `6e23fe1` appears to add implementation files only (no new test files detected for server/cleartext/security/integration).

Required fix:
- Add additive test suites promised by the plan before marking task complete.
- Minimum set:
  1. `ServerManager` state/idempotency test
  2. `LocalHttpServer` traversal/method/fallback tests
  3. `MainActivity` startup-state flow test (STARTING->READY and FAILED)
  4. localhost cleartext compatibility test

---

## Decision

- **Status: V4 plan is good directionally, but implementation is not yet complete/safe.**
- Apply B1/B2/B3 + minimum tests, then re-run review.
