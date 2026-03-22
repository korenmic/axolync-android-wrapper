# Axolync Android Wrapper

## Migration Status

This README describes the embedded localhost + `WebView` Android wrapper that is being displaced by Migration 05.

- Active migration target: Capacitor-based Android host
- Legacy reference doc: [docs/legacy/embedded-wrapper-reference.md](docs/legacy/embedded-wrapper-reference.md)
- Legacy state label: `archived-reference`

Until the Capacitor reset is fully landed, this repository may temporarily contain transitional code. Treat the embedded-server architecture documented below as the preserved legacy reference, not as the intended long-term default.

Native Android shell for the Axolync web app.

The wrapper starts an embedded localhost HTTP server inside the app process, then loads the web UI in `WebView` from `http://localhost:<port>/index.html`.

## What This Project Does

- Packages `axolync-browser` web assets into an APK.
- Runs a local in-app HTTP server (`NanoHTTPD`) to serve those assets.
- Uses `WebView` for rendering + native bridge for Android integrations (permissions, audio, lifecycle).
- Preserves Axolync state-machine/UI behavior from the web app baseline.

## Runtime Flow

1. `AxolyncApplication` starts `ServerManager` asynchronously.
2. `SplashActivity` shows full-screen splash art while server reaches READY (with timeout guard).
3. `MainActivity` loads WebView from localhost URL.
4. Web app JS runs from served assets (not `file://`).

## Project Structure

- `app/src/main/kotlin/com/axolync/android/`
  - `AxolyncApplication.kt` - app entry, server bootstrap
  - `activities/`
    - `SplashActivity.kt` - fullscreen splash + server readiness gate
    - `MainActivity.kt` - WebView host + bridge wiring
  - `server/`
    - `ServerManager.kt` - app-scope server lifecycle/state
    - `LocalHttpServer.kt` - localhost asset server
  - `bridge/` - JS bridge between Android and web app
  - `services/` - audio capture, permission handling, lifecycle coordination
  - `utils/` - plugin/network helpers
- `app/src/main/res/`
  - `layout/activity_splash.xml` - fullscreen splash layout
  - `drawable-port/` + `drawable-land/` - orientation-specific splash art
  - `xml/network_security_config.xml` - cleartext policy (localhost-only)
- `app/src/main/assets/axolync-browser/` - copied web assets used by server
- `.kiro/specs/android-apk-wrapper/` - requirements/design/tasks

## Build and Test

```bash
# Compile Kotlin
./gradlew :app:compileDebugKotlin

# Unit tests
./gradlew :app:testDebugUnitTest

# Build both normal + demo debug APKs
./gradlew :app:assembleNormalDebug :app:assembleDemoDebug
```

APK output:
- `app/build/outputs/apk/normal/debug/app-normal-debug.apk`
- `app/build/outputs/apk/demo/debug/app-demo-debug.apk`

## Local Parity Server (same serving style as embedded Android server)

To debug the same static asset behavior on desktop (without Vite), run:

```bash
node scripts/serve-android-assets.mjs --port=4173
```

Then open:

```text
http://127.0.0.1:4173/index.html
```

## Asset Sync From axolync-browser

`preBuild` depends on `copyAxolyncBrowserAssets`.
It copies:
- `axolync-browser/dist/**`
- `axolync-browser/index.html` (rewritten from `/src/main.ts` to `/main.js` for static runtime)
into `app/src/main/assets/axolync-browser/`.

## Permissions

- `RECORD_AUDIO`
- `INTERNET`
- `ACCESS_NETWORK_STATE`

## Important Notes

- Wrapper runtime is `http://localhost:<port>/...` by design.
- If UI looks like static HTML (buttons not behaving), check JS module path serving in `LocalHttpServer` first.
- Splash rendering is custom activity-based (full-screen image), not Android 12 icon-style splash.

## AI Contributor Docs

- `docs/ai-global.md`
- `docs/ai-per-task.md`
- `docs/ai-local-system.md`
