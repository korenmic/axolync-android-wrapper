# Handoff: Notification Listener Access Fix (Spec 8 / Task 26)

## What Was Fixed

Axolync was not appearing in Android Settings > Notification Access list on Samsung Galaxy S25+. Two root causes identified and resolved.

## Root Causes

**1. `android:exported="false"` on the service (manifest bug)**
Samsung's One UI Settings app enumerates notification listener services via PackageManager. With `exported="false"` it cannot see the service. Changed to `exported="true"`. Security is unaffected — the `android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"` attribute still restricts binding to the system only.

**2. Android 13+ Restricted Settings (user flow gap)**
Apps sideloaded via ADB are blocked from appearing in sensitive settings screens (Notification Access, Accessibility) until the user manually allows them via:
`Settings → Apps → Axolync → ⋮ → Allow Restricted Settings`
The app now detects this state and shows a two-step guide in SongSense settings.

## Files Changed

### `axolync-android-wrapper`

| File | Change |
|------|--------|
| `app/src/main/AndroidManifest.xml` | `android:exported="false"` → `android:exported="true"` on `StatusBarSongSignalService` |
| `app/src/main/kotlin/com/axolync/android/bridge/NativeBridge.kt` | `getStatusBarAccessStatus()` returns structured JSON `{enabled, state, reasonCode, message}`; added `openAppInfoSettings()` bridge method; added `isRestrictedFromSensitiveSettings()` private helper |
| `app/src/test/kotlin/com/axolync/android/bridge/NotificationAccessBridgeWiringTest.kt` | Fixed assertions (was incorrectly asserting `exported="false"`); added 3 new test cases for structured status, `openAppInfoSettings`, and restricted detection |

### `axolync-browser`

| File | Change |
|------|--------|
| `src/ui/PluginManager.ts` | `getStatusBarBridgeState()` returns `{available, enabled, state, restricted}`; new `renderStatusBarAccessSection()` renders single-step or two-step UI; new `openAppInfoSettings()` JS bridge wrapper |

## New Bridge API Surface

```
getStatusBarAccessStatus() → { enabled: bool, state: "granted|not_granted|restricted|error", reasonCode: string, message: string }
openAppInfoSettings() → void   // opens App Info page for Allow Restricted Settings
requestStatusBarAccessPermission() → void   // unchanged behavior
```

## Test Results

- Android unit tests: `./gradlew :app:testNormalDebugUnitTest` — BUILD SUCCESSFUL (26 tests)
- Browser tests: `npm test` — 41 files, 368 tests, all passed

## History Note (Why Codex Failed)

Task 26 (commit `fb3b2f8`) correctly set `exported="true"`. Task 26.2 (commit `9f7cc08`) incorrectly reverted it to `exported="false"` with rationale "system-only binding" — this is wrong. The `android:permission` attribute handles binding security; `exported` controls discoverability. The test was also updated to assert the wrong value, locking in the regression.

Do NOT add `<uses-permission android:name="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"/>` — that would cause Samsung install rejection.

## Pending (Not In Scope Here)

- Demo SongSense settings still show notification access UI (spec says they should not — pre-existing issue)
- Spec tasks 3–10 from `.codex/specs/android-notification-listener-access/tasks.md` remain open (checklist artifact, CI enforcement, integration smoke test, etc.)
