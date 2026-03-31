# Seed 01: Android Doc And Script Cleanup Audit

## Summary

Audit the Android wrapper repository for stale localhost-era docs, one-off handoff artifacts, and orphaned helper scripts so future bootstrap and implementation work starts from the active Capacitor host model instead of archived embedded-server assumptions.

This seed exists to park the remaining cleanup questions after the first archive pass, not to force immediate deletion of everything old.

Priority:
- `P2`

## Why This Exists

The active Android runtime is now a thin Capacitor shell that stages browser assets into the APK and loads them directly from packaged assets.

Several neighboring docs and scripts still describe an older localhost / embedded-server architecture as if it were current. That creates bootstrap risk for future agents and humans:
- stale docs get mistaken for live authority
- one-time handoff notes get treated like durable onboarding docs
- orphaned scripts survive after their references were removed
- suspended features can get misclassified as deprecated if we do not record the distinction clearly

## Current Authority And Guardrails

- Treat the Capacitor host described in `README.md` as the active Android runtime.
- Treat `scripts/stage-browser-assets.mjs` as active infrastructure. It is still wired into Gradle `preBuild` and therefore into builder-driven Android builds.
- Treat notification-listener / status-bar signal work as suspended during migration, not deprecated. Cleanup should not delete runtime code only because a handoff doc became stale.
- Treat `docs/legacy/embedded-wrapper-reference.md` as an intentional archive unless a later pass decides to strengthen its archive banner.

## Already Archived In This Seed Kickoff

These changes were already made before this seed was written:

- moved top-level one-off status / handoff docs under `deprecated/ingested/`
- moved top-level localhost / submodule planning docs under `deprecated/embedded-localhost/`
- moved the old Kiro Android localhost spec trio under `deprecated/.kiro/specs/android-apk-wrapper/`
- removed `scripts/serve-android-assets.mjs` after verifying it had no remaining references anywhere in the current workspace
- updated active docs to point at archived material as history instead of current authority

## Evidence Collected So Far

- The active Capacitor host landed in commit `0310642` on `2026-03-22`.
- `app/src/main/kotlin/com/axolync/android/activities/MainActivity.kt` is the thin `BridgeActivity` host.
- `app/build.gradle.kts` still runs `scripts/stage-browser-assets.mjs` through `stageCapacitorBrowserAssets`, and `preBuild` depends on that task.
- `scripts/serve-android-assets.mjs` had no remaining call sites across the full workspace before removal.
- `docs/ai-local-system.md` still describes `/home/deck`, `bash`, `SplashActivity`, `LocalHttpServer`, and localhost runtime assumptions.
- `docs/physical-device-validation.md` and `scripts/run-physical-device-validation.sh` still need a joint decision: keep-and-rewrite together or archive together.
- `docs/handoff-notification-listener-fix.md` reads like an ingestion artifact even though the underlying notification-listener feature is only suspended.

## Open Questions For The Next Cleanup Pass

### 1. Rewrite Or Archive `docs/ai-local-system.md`?

It still presents the embedded localhost runtime as current. The next pass should decide whether to:
- rewrite it to match the Capacitor host
- or archive it under `deprecated/` if `README.md` and the other AI docs already cover the live path well enough

### 2. Keep Or Archive Physical Device Validation?

`docs/physical-device-validation.md` and `scripts/run-physical-device-validation.sh` should be evaluated together.

Current known state:
- the doc previously overstated localhost readiness checks and was partially corrected
- the script still runs a real connected Android test command
- the present instrumentation coverage is thin, so the workflow may now be too weak to justify staying as an active top-level validation path

### 3. Archive The Notification Listener Handoff Doc Without Deprecating The Feature?

`docs/handoff-notification-listener-fix.md` looks like a one-time ingestion note, not durable authority.

The next pass should likely move that doc under `deprecated/ingested/` while explicitly preserving the distinction:
- doc may be deprecated as onboarding material
- feature is suspended, not deprecated

### 4. Are More Localhost-Era References Still Posing As Current?

Run another targeted scan for active-looking mentions of:
- `LocalHttpServer`
- `SplashActivity`
- `ServerManager`
- `NanoHTTPD`
- embedded localhost runtime assumptions
- machine-specific environment assumptions presented as generic instructions

### 5. Does `docs/legacy/embedded-wrapper-reference.md` Need Stronger Scoping?

It is intentionally archived already. The remaining question is whether its current labeling is strong enough to prevent accidental misuse by future bootstrap readers.

## Non-Goals

- Do not use this seed to remove suspended notification-listener runtime code.
- Do not treat every old document as deletion-worthy just because it is old; some history is worth keeping when it is clearly labeled.
- Do not expand this seed into browser/builder policy cleanup. Keep this one focused on the Android wrapper repo.
