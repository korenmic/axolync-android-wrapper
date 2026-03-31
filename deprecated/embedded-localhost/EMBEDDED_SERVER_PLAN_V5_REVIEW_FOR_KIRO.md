# Embedded Server V5 Review (Codex -> Kiro)

Scope reviewed:
- Latest code after V4 implementation commits
- `MainActivity.kt`, `ServerManager.kt`, `LocalHttpServer.kt`, Android manifest/config
- `.kiro/specs/android-apk-wrapper/tasks.md`

Verdict:
- Direction is correct and compile/tests are green at a basic level.
- Not done yet: 4 blocking gaps remain.

---

## Blocking gaps to patch now

### 1) STARTING-state flow in MainActivity can dead-end
Current behavior:
- In `MainActivity.onCreate()`, `STARTING` branch logs and `return`s.
- If `onCreate` enters this branch, initialization never continues in that activity instance.

Required patch:
- Replace early-return logic with deterministic continuation:
  - keep splash gate while `STARTING`
  - if still `STARTING` after content set, schedule short retry loop (`Handler`) with hard timeout (e.g. 5s)
  - when `READY`: run `initializeServices()`, `configureWebView()`, state restore, `loadWebApp()` once
  - when `FAILED` or timeout: show fatal error dialog
- Ensure boot continuation is idempotent (guard flag like `bootstrapped = false`).

### 2) `ServerManager.startServerAsync()` idempotency/concurrency guard is incomplete
Current behavior:
- Guard `serverState == STARTING && localHttpServer != null` is insufficient.
- Multiple calls during early STARTING can enqueue duplicate start tasks.

Required patch:
- Add explicit atomic in-flight guard:
  - `private val startScheduled = AtomicBoolean(false)`
  - on schedule: `if (!startScheduled.compareAndSet(false, true)) return`
  - reset only if you intentionally support retry path
- Keep semantics:
  - READY -> no-op
  - STARTING -> no-op
  - FAILED -> no-op unless explicit `retryServerStart()` is called.

### 3) `LocalHttpServer` HEAD path leaks stream handle
Current behavior:
- Asset stream is opened before HEAD branch and then not used.

Required patch:
- For HEAD requests, do not open asset stream.
- Determine existence and MIME first, return headers-only response.
- For GET requests, open stream and serve body.

### 4) Spec/tasks drift remains (SplashActivity + test claims)
Current behavior:
- `tasks.md` still has completed SplashActivity tasks and broad completion marks not matching real coverage.

Required patch:
- Update `.kiro/specs/android-apk-wrapper/tasks.md`:
  - replace SplashActivity tasks with SplashScreen API tasks
  - add explicit uncompleted subtasks for the missing tests below
  - only mark complete when code + test evidence exists

---

## Required tests (additive, minimum)

1. **ServerManager concurrency/idempotency tests**
- concurrent `startServerAsync()` calls schedule one start
- STARTING->READY and STARTING->FAILED transitions

2. **MainActivity startup-state tests**
- STARTING path eventually continues to READY boot (no dead-end)
- FAILED path shows deterministic fatal UX
- timeout path shows deterministic fatal UX

3. **LocalHttpServer HEAD tests**
- HEAD on existing asset returns 200 + headers, no body
- HEAD on missing asset returns 404

4. **Localhost cleartext verification test**
- instrumentation test proving WebView can load canonical `http://localhost:<port>/index.html` on target API level(s)

---

## Implementation priority order

1. Fix MainActivity STARTING continuation + timeout + one-time bootstrap guard.
2. Fix ServerManager in-flight scheduling guard.
3. Fix LocalHttpServer HEAD stream handling.
4. Patch tasks.md to reflect real status and missing tests.
5. Add minimum tests listed above.
6. Run:
   - `./gradlew :app:compileDebugKotlin`
   - `./gradlew :app:testDebugUnitTest`

---

## Notes for this patch

- Keep canonical URL `http://localhost:<port>/`.
- Keep localhost-only binding and strict origin allowlist.
- Do not reintroduce SplashActivity.
- Do not remove existing test suites; add tests only.
