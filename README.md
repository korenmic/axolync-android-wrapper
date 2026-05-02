# Axolync Platform Wrapper Migration Source

Target wrapper authority: `axolync-platform-wrapper`.

This checkout is still named `axolync-android-wrapper` during rename/refactor migration. Treat the old name as a compatibility alias only. See [docs/wrapper-platform-authority.md](docs/wrapper-platform-authority.md) and [config/wrapper-authority.json](config/wrapper-authority.json).

Soft completion is not accepted for this migration. The repo must physically own the active Capacitor Android source, desktop Tauri template, desktop Electron template, and generic native service companion host sources before builder can treat the migration as complete.

## Migration Status

This README describes the active Capacitor-based Android host introduced by Migration 05.

- Active migration target: Capacitor-based Android host
- Legacy reference doc: [docs/legacy/embedded-wrapper-reference.md](docs/legacy/embedded-wrapper-reference.md)
- Legacy state label: `archived-reference`

Treat the embedded-server architecture as archived legacy behavior. The active host path in this repository is now the thin Capacitor shell described below.

Android is one child target under the Capacitor wrapper family. Desktop Tauri and Electron templates are sibling wrapper sources under `templates/desktop/`. Repo-level docs should describe the neutral platform-wrapper authority; Android-specific runtime notes belong under `wrappers/mobile/capacitor/android/`.

## What This Project Does

- Packages `axolync-browser` web assets into an APK.
- Stages browser build artifacts into Capacitor public assets.
- Uses a thin Capacitor `BridgeActivity` host instead of a custom localhost server or Kotlin-owned product runtime.
- Defers Android-native integrations that need custom native plugins until later bridge-plugin work lands.
- Preserves Axolync state-machine/UI behavior from the web app baseline.

## Runtime Flow

1. Builder/browser outputs are staged into `wrappers/mobile/capacitor/android/app/src/main/assets/public`.
2. `MainActivity` launches as a Capacitor `BridgeActivity`.
3. Capacitor loads the staged web app assets directly from the packaged host shell.
4. Browser JS runs in the mobile host baseline without requiring `AndroidBridge`, a localhost server, or embedded Python in the active path.

## Project Structure

- `config/wrapper-authority.json` - target wrapper authority identity and compatibility alias policy
- `docs/wrapper-platform-authority.md` - promotion/rename policy for `axolync-platform-wrapper`
- `wrappers/mobile/capacitor/android/app/src/main/kotlin/com/axolync/android/`
  - `activities/MainActivity.kt` - thin Capacitor host activity
- `wrappers/mobile/capacitor/android/app/src/main/res/`
  - `xml/config.xml` - Capacitor host config
  - `xml/file_paths.xml` - file-provider compatibility config
- `wrappers/mobile/capacitor/android/app/src/main/assets/public/` - staged browser build used by the Capacitor host
- `wrappers/mobile/capacitor/android/app/src/main/assets/capacitor/` - checked-in Capacitor asset metadata
- `wrappers/desktop/tauri/workspace-template/` - builder-consumable Tauri desktop wrapper source
- `templates/desktop/electron/` - builder-consumable Electron desktop wrapper source
- `native-service-companions/` - generic companion host protocol, deployment, and diagnostics ownership
- `wrappers/mobile/capacitor/android/scripts/stage-browser-assets.mjs` - stages browser outputs into Capacitor assets
- `deprecated/.kiro/specs/android-apk-wrapper/` - archived pre-Capacitor localhost-era requirements/design/tasks

## Build and Test

```bash
# Install Capacitor dependencies
npm ci --no-fund --no-audit

# Unit tests
cd wrappers/mobile/capacitor/android
./gradlew :app:testNormalDebugUnitTest

# Build both normal + demo debug APKs
./gradlew :app:assembleNormalDebug :app:assembleDemoDebug
```

APK output:
- `wrappers/mobile/capacitor/android/app/build/outputs/apk/normal/debug/app-normal-debug.apk`
- `wrappers/mobile/capacitor/android/app/build/outputs/apk/demo/debug/app-demo-debug.apk`

## Debug Signing Stability

- Debug builds use the tracked keystore at `signing/axolync-debug.keystore`.
- This keeps debug APK upgrade lineage stable across machines instead of falling back to each host's local `~/.android/debug.keystore`.
- Release builds remain outside this debug-key path.

## Local Parity Debugging

To debug the staged browser output on desktop, use the browser repo or the builder report artifacts directly. The active Android host no longer depends on a localhost asset server inside the APK.

## Asset Sync From axolync-browser

`preBuild` depends on `stageCapacitorBrowserAssets`.
It copies:
- builder/browser normal output into `app/src/main/assets/public/`
- builder/browser demo assets into `app/src/main/assets/public/demo/assets/`
- Capacitor compatibility stubs (`cordova.js`, `cordova_plugins.js`) when missing

## Permissions

- `RECORD_AUDIO`
- `MODIFY_AUDIO_SETTINGS`
- `INTERNET`
- `ACCESS_NETWORK_STATE`

## Important Notes

- Phase 1 intentionally drops native-only capabilities that require custom bridge plugins.
- SongSense notification-reader behavior is deferred in the active Capacitor baseline.
- If mobile behavior differs from the browser baseline, treat that as a host capability gap to be reintroduced explicitly later, not as a reason to restore hidden localhost/native-bridge assumptions.

## AI Contributor Docs

- `docs/ai-global.md`
- `docs/ai-per-task.md`
- `docs/ai-local-system.md`
