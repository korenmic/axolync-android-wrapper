# Embedded Server Plan V3 Review (Codex -> Kiro)

Reviewed file:
- `EMBEDDED_SERVER_IMPLEMENTATION_PLAN_V3.md`

Overall assessment:
- V3 is strong and much closer to execution than V2.
- Not yet implementation-ready: 3 blocking fixes remain.

---

## What V3 did well

1. Correctly converged to one splash strategy (SplashScreen API).
2. Correctly moved server ownership to Application-scope manager.
3. Added explicit security hardening and additive tests.
4. Added clear state model and metrics.

---

## Blocking fixes before implementation

### B1) Avoid blocking startup thread in `Application.onCreate()`
Problem:
- V3 starts server directly in `Application.onCreate()` (`serverManager.startServer()`), which may perform I/O and block app startup.
- This can degrade launch and risks ANR on slow devices.

Required fix:
- Start server asynchronously from app scope (background dispatcher/executor).
- `ServerManager` should expose immediate `STARTING` state and eventual `READY/FAILED` transition.
- Splash keep condition should continue to gate UI while state is `STARTING`.

### B2) Remove `showLoadingState() + recreate()` fallback loop in MainActivity
Problem:
- V3 proposes a safety path that shows dialog + calls `recreate()` repeatedly when state is STARTING.
- This can create UI churn/loop risk and race with splash behavior.

Required fix:
- Delete this fallback loop entirely.
- MainActivity should only render either:
  - normal flow when `READY`,
  - deterministic fatal UX when `FAILED` or timeout reached by startup coordinator.
- STARTING should be handled by splash gate, not post-splash dialog+recreate logic.

### B3) Cleartext localhost policy must be implementation-safe across API levels
Problem:
- V3 proposes network-security config domains for both `localhost` and `127.0.0.1`.
- In practice, NSC `domain` handling is hostname-oriented and behavior with literal IPs can vary.

Required fix:
- Keep `localhost` in NSC domain-config.
- Add explicit compatibility verification task/tests for WebView loading both:
  - `http://127.0.0.1:<port>/...`
  - `http://localhost:<port>/...`
- Define deterministic fallback in plan:
  - If `127.0.0.1` cleartext is blocked on target API/device, use `localhost` URL as canonical base URL.
- Document final canonical base URL in spec/design (pick one after verification).

---

## Non-blocking improvements (recommended)

1. For HEAD requests, ensure response semantics are HEAD-compatible (headers only if needed).
2. For SPA fallback, include a small note that API-like paths (if introduced later) should not fallback to `index.html`.
3. Keep feature-flag fallback optional and clearly marked as emergency-only in docs.

---

## Test plan addendum required

Add these explicit tests:

1. Startup async behavior:
- App launch does not block main thread while server boots.
- Splash remains visible until server state changes from STARTING.

2. URL compatibility tests:
- Localhost URL load success on supported API levels.
- If 127 URL is intended, verify it too; otherwise standardize on localhost.

3. No-loop guarantee:
- No repeated Activity recreation while waiting for server readiness.

---

## Decision

- **Status: V3 needs one more revision (V4) for execution safety.**
- After B1/B2/B3 are integrated, this plan is implementation-ready.
