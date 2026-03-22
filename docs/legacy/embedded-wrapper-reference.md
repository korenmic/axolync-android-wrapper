# Embedded Wrapper Legacy Reference

## Status

- archived state: `archived-reference`
- active migration: `Migration 05: Android Wrapper To Capacitor Reset`
- purpose: preserve a reproducible reference for the displaced embedded localhost Android wrapper before the active path is reset to Capacitor

## Legacy Commit Reference

- legacy repo: `axolync-android-wrapper`
- legacy commit sha: `0f9cc98b1a035c4f0cf801b71ca7dce9c1aacfea`
- legacy short sha: `0f9cc98`
- recorded at: `2026-03-22 03:54:40 +0200`
- legacy commit subject: `5. Close the migration with regression coverage and cleanup of remaining hidden wrapper baseline assumptions.`

This commit is the authoritative reproducible reference for the embedded-server Android wrapper path that Migration 05 replaces.

## What The Legacy Wrapper Did

The displaced wrapper architecture was based on:

1. Kotlin-owned Android host bootstrap
2. embedded localhost HTTP serving through NanoHTTPD
3. `WebView` loading `http://localhost:<port>/index.html`
4. custom `window.AndroidBridge` integration for Android-specific capabilities
5. active-path native/runtime assumptions such as Chaquopy-backed embedded Python and localhost bridge routes

## Reproduction Commands

Run these commands at the recorded legacy commit if the displaced wrapper needs to be reproduced intentionally:

```bash
git checkout 0f9cc98b1a035c4f0cf801b71ca7dce9c1aacfea

# Compile Kotlin
./gradlew :app:compileDebugKotlin

# Unit tests
./gradlew :app:testDebugUnitTest

# Build both normal + demo debug APKs
./gradlew :app:assembleNormalDebug :app:assembleDemoDebug
```

Expected legacy APK locations:

- `app/build/outputs/apk/normal/debug/app-normal-debug.apk`
- `app/build/outputs/apk/demo/debug/app-demo-debug.apk`

## Archived Runtime Summary

Legacy runtime flow:

1. `AxolyncApplication` started `ServerManager`
2. `SplashActivity` waited for the embedded localhost server to become ready
3. `MainActivity` loaded the web app from the localhost origin
4. the browser app relied on embedded-wrapper-specific bridge and local-server affordances

## Archive Boundary

This document preserves the old wrapper as a reproducible reference. It does **not** mean the embedded localhost architecture should remain an implicit active dependency.

During Migration 05:

- the active path moves to a Capacitor host
- the legacy path remains reproducible through this document plus the recorded git commit
- any remaining embedded-wrapper code in the workspace should be treated as transitional migration residue until the active path replacement is complete
