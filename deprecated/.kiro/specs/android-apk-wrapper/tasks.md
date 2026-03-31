# Implementation Plan: Android APK Wrapper

## Overview

This implementation plan creates a native Android application that hosts the Axolync karaoke companion web experience (axolync-browser v1.0.0) within a WebView. The wrapper follows a thin native wrapper pattern with bundled static assets, providing platform-specific services (audio capture, permissions, lifecycle management) while preserving the complete state machine and UX semantics of the web application.

The implementation uses Kotlin for all native Android components and integrates the axolync-browser submodule as the authoritative source for the web application.

## Tasks

- [x] 1. Set up Android project structure and dependencies
  - Create new Android project with Kotlin support (minSdk 24, targetSdk 34)
  - Add axolync-browser as Git submodule at project root
  - Configure Gradle build to copy built axolync-browser assets to `app/src/main/assets/axolync-browser/`
  - Add required dependencies: AndroidX, Kotlin coroutines, testing libraries (Kotest for property-based testing)
  - Configure manifest with required permissions (RECORD_AUDIO, INTERNET, ACCESS_NETWORK_STATE)
  - Set up basic project structure: activities, services, utils packages
  - _Requirements: 1.1, 1.4_

- [x] 1.5 Implement ServerManager and LocalHttpServer
  - Create ServerManager singleton with async startup on background thread
  - Implement LocalHttpServer with NanoHTTPD (hardened, SPA fallback)
  - Use canonical URL http://localhost:<port>/ (not 127.0.0.1)
  - Add traversal protection, method restrictions
  - Implement /health endpoint
  - Add observability metrics
  - Configure network security config for localhost cleartext
  - Verify cleartext works on API 24-34
  - _Requirements: 6.1, 6.2, 11.3, 11.4, 11.9, 11.10_

- [x] 2. Implement SplashActivity and app initialization flow
  - [x] 2.1 Create SplashActivity with splash screen layout
    - Implement SplashActivity that displays splash screen on launch
    - Add splash screen drawable/layout resources
    - Implement initialization check logic with timeout (5 seconds max)
    - Add navigation to MainActivity when ready signal received
    - _Requirements: 2.1, 2.2_
  
  - [ ] 2.2 (Optional) Write unit tests for SplashActivity
    - Test splash screen displays on startup
    - Test navigation to MainActivity on ready signal
    - Test timeout behavior on slow initialization
    - _Requirements: 2.1, 2.2_

- [x] 3. Implement WebView configuration and asset loading
  - [x] 3.1 Create MainActivity with WebView setup
    - Implement MainActivity as primary activity hosting WebView
    - Configure WebView with security settings (JavaScript enabled, file access disabled, mixed content blocked)
    - Implement strict origin validation with exact scheme+host+port matching
    - Implement origin enforcement for both top-level navigation and subresource requests (e.g., shouldInterceptRequest), blocking all untrusted origins
    - Load web app from ServerManager.getBaseUrl() (http://localhost:<port>/index.html)
    - Use Android SplashScreen API with setKeepOnScreenCondition { !ServerManager.isReady() }
    - MainActivity only handles READY or FAILED states (no recreate loop)
    - Disable remote debugging in production builds
    - _Requirements: 1.2, 1.3, 6.1, 6.3, 6.4, 11.2, 11.6, 11.8_
  
  - [ ] 3.2 (Optional) Write unit tests for WebView configuration
    - Test JavaScript enabled and file access disabled
    - Test origin validation blocks untrusted URLs
    - Test remote debugging disabled in production
    - Test asset loading from bundled path
    - _Requirements: 11.2, 11.6, 11.8_
  
  - [ ] 3.3 (Optional) Write property test for origin validation
    - **Property 17: Untrusted Origin Blocking**
    - **Property 19: Origin Validation Strictness**
    - **Validates: Requirements 11.6, 11.8**

- [x] 4. Implement PermissionManager for microphone access
  - [x] 4.1 Create PermissionManager class
    - Implement checkMicrophonePermission() returning PermissionStatus enum
    - Implement requestMicrophonePermission() using Android runtime permissions API
    - Implement shouldShowRationale() to determine if explanation needed
    - Implement openAppSettings() to navigate to app settings
    - Handle permission result callbacks
    - _Requirements: 3.1, 3.2_
  
  - [ ] 4.2 (Optional) Write unit tests for PermissionManager
    - Test permission status detection (granted, denied, denied permanently)
    - Test rationale display logic
    - Test app settings navigation
    - _Requirements: 3.1, 3.2_
  
  - [ ] 4.3 (Optional) Write property test for permission-gated capture
    - **Property 4: Permission-Gated Capture**
    - **Validates: Requirements 3.1**

- [x] 5. Implement AudioCaptureService for microphone audio
  - [x] 5.1 Create AudioCaptureService class
    - Implement startCapture() using AudioRecord API with 44.1kHz, mono, 16-bit PCM
    - Calculate buffer size using AudioRecord.getMinBufferSize() with 2x multiplier
    - Implement stopCapture() to release audio resources
    - Implement captureLoop() on dedicated background thread
    - Implement PCM 16-bit to Float32Array conversion (normalized to [-1, 1])
    - Add audio callback mechanism for delivering chunks
    - _Requirements: 3.3, 3.4, 8.1, 8.8_
  
  - [ ] 5.2 (Optional) Write unit tests for AudioCaptureService
    - Test capture start/stop lifecycle
    - Test PCM to Float32Array conversion accuracy
    - Test buffer size calculation
    - Test callback delivery mechanism
    - _Requirements: 8.1, 8.8_
  
  - [ ] 5.3 (Optional) Write property test for audio timestamp monotonicity
    - **Property 18: Audio Timestamp Monotonicity**
    - **Validates: Requirements 8.1**

- [x] 6. Checkpoint - Ensure all tests pass
  - Run the full defined test suite for current scope, record pass/fail output, and proceed only on green status.

- [x] 7. Implement NativeBridge JavaScript interface
  - [x] 7.1 Create NativeBridge class with JavaScript interface methods
    - Implement @JavascriptInterface methods: startAudioCapture(), stopAudioCapture()
    - Implement checkMicrophonePermission(), requestMicrophonePermission(), openAppSettings()
    - Implement getNetworkStatus() returning JSON with online status
    - Implement appReady() signal from web app
    - Implement logError() for web-to-native error logging
    - All methods return JSON strings for security
    - _Requirements: 3.3, 3.4, 7.3_
  
  - [x] 7.2 Implement native-to-web communication methods
    - Implement deliverAudioChunk() using evaluateJavascript to call window.AxolyncNative.onAudioChunk()
    - Implement notifyLifecycleEvent() to call window.AxolyncNative.onLifecycle()
    - Implement notifyPermissionResult() to call window.AxolyncNative.onPermissionResult()
    - Ensure Float32Array audio data properly transferred via ArrayBuffer
    - _Requirements: 3.4, 10.1, 10.2_
  
  - [ ] 7.3 (Optional) Write unit tests for NativeBridge
    - Test all JavaScript interface methods return valid JSON
    - Test native-to-web calls execute correctly
    - Test audio chunk delivery with Float32Array format
    - Test input validation on all parameters
    - _Requirements: 3.3, 3.4_
  
  - [ ] 7.4 (Optional) Write property test for capture enablement after permission grant
    - **Property 5: Capture Enablement After Permission Grant**
    - **Validates: Requirements 3.3**

- [x] 8. Implement LifecycleCoordinator for Android lifecycle events
  - [x] 8.1 Create LifecycleCoordinator class
    - Implement onAppPause() to suspend audio capture and notify web app
    - Implement onAppResume() to restore audio capture if previously active
    - Implement onAppBackground() to persist web app state to SharedPreferences
    - Implement onAppForeground() to restore web app state from SharedPreferences
    - Implement onLowMemory() to notify web app to release resources
    - Implement saveState() and restoreState() for state persistence
    - _Requirements: 2.4, 10.1, 10.2, 10.3, 10.4, 10.5_
  
  - [ ] 8.2 (Optional) Write unit tests for LifecycleCoordinator
    - Test pause suspends audio capture
    - Test resume restores audio capture
    - Test state persistence and restoration
    - Test low memory handling
    - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5_
  
  - [ ] 8.3 (Optional) Write property test for pause suspends capture
    - **Property 14: Pause Suspends Capture**
    - **Validates: Requirements 10.1**
  
  - [ ] 8.4 (Optional) Write property test for resume restores capture
    - **Property 15: Resume Restores Capture**
    - **Validates: Requirements 10.2**
  
  - [ ] 8.5 (Optional) Write property test for lifecycle state round-trip
    - **Property 16: Lifecycle State Round-Trip**
    - **Validates: Requirements 10.3, 10.4**

- [x] 9. Implement NetworkMonitor for connectivity detection
  - [x] 9.1 Create NetworkMonitor class
    - Implement isOnline() using ConnectivityManager
    - Implement getConnectionType() returning WIFI, CELLULAR, or NONE
    - Implement registerCallback() using NetworkCallback for state changes
    - Implement unregisterCallback() for cleanup
    - Notify NativeBridge when connectivity changes
    - _Requirements: 7.2, 7.3, 7.4_
  
  - [ ] 9.2 (Optional) Write unit tests for NetworkMonitor
    - Test online/offline detection
    - Test connection type detection
    - Test callback registration and notification
    - _Requirements: 7.2, 7.3, 7.4_
  
  - [ ] 9.3 (Optional) Write property test for network check before operations
    - **Property 9: Network Check Before Network Operations**
    - **Validates: Requirements 7.3**
  
  - [ ] 9.4 (Optional) Write property test for state preservation during connectivity changes
    - **Property 10: State Preservation During Connectivity Changes**
    - **Validates: Requirements 7.5**

- [x] 10. Checkpoint - Ensure all tests pass
  - Run the full defined test suite for current scope, record pass/fail output, and proceed only on green status.

- [x] 11. Implement PluginManager for plugin package management
  - [x] 11.1 Create PluginManager class with storage structure
    - Implement installPlugin() to install plugin to app-private storage
    - Implement updatePlugin() with backup and rollback support
    - Implement validatePlugin() with checksum/signature verification
    - Implement rollbackPlugin() to restore previous version on failure
    - Implement listInstalledPlugins() and getPluginPath()
    - Use storage path: context.filesDir/plugins/{pluginId}/{version}/
    - Maintain plugin registry in SharedPreferences or SQLite
    - _Requirements: 5.5, 5.6, 5.7, 5.8, 5.9, 5.10_
  
  - [ ] 11.2 (Optional) Write unit tests for PluginManager
    - Test plugin installation flow
    - Test plugin update with backup
    - Test checksum validation
    - Test rollback on failure
    - Test plugin registry management
    - _Requirements: 5.5, 5.6, 5.7, 5.8, 5.9_
  
  - [ ] 11.3 (Optional) Write property test for invalid plugin rejection
    - **Property 6: Invalid Plugin Rejection**
    - **Validates: Requirements 5.8, 5.11**
  
  - [ ] 11.4 (Optional) Write property test for plugin update rollback
    - **Property 7: Plugin Update Rollback**
    - **Validates: Requirements 5.9**

- [x] 12. Wire all components together in MainActivity
  - [x] 12.1 Integrate all services in MainActivity
    - Initialize PermissionManager, AudioCaptureService, LifecycleCoordinator, NetworkMonitor, PluginManager
    - Create and register NativeBridge with WebView
    - Wire lifecycle callbacks to LifecycleCoordinator
    - Wire permission results to PermissionManager
    - Connect AudioCaptureService output to NativeBridge for delivery to web app
    - Connect NetworkMonitor changes to NativeBridge notifications
    - Handle SplashActivity ready signal coordination
    - _Requirements: 1.2, 1.3, 2.1, 2.4_
  
  - [ ] 12.2 (Optional) Write integration tests for MainActivity
    - Test component initialization order
    - Test lifecycle event propagation
    - Test audio capture to bridge delivery
    - Test permission flow end-to-end
    - _Requirements: 1.2, 1.3, 2.4_

- [x] 13. Implement error handling and user feedback
  - [x] 13.1 Add error handling for all failure scenarios
    - Implement error dialogs for permission denied, audio hardware errors, capture timeout
    - Implement error handling for plugin installation/update failures
    - Implement connectivity status messages for network errors
    - Implement state restoration failure handling
    - Implement asset load failure error screen
    - Add logging for all error conditions with context
    - _Requirements: 3.2, 5.11, 7.2, 7.5_
  
  - [ ] 13.2 (Optional) Write unit tests for error handling
    - Test error dialog display for various failure scenarios
    - Test error recovery flows
    - Test logging captures error context
    - _Requirements: 3.2, 5.11, 7.2_

- [x] 14. Implement demo and automation support
  - [x] 14.1 Create fake microphone capture for testing
    - Implement FakeAudioCaptureService with pre-recorded audio playback
    - Add configuration flag to switch between real and fake capture
    - Support scripted test scenarios for state machine validation
    - _Requirements: 9.1, 9.2, 9.3_
  
  - [ ] 14.2 (Optional) Write unit tests for fake audio capture
    - Test fake capture delivers expected audio data
    - Test configuration switching between real and fake
    - _Requirements: 9.1, 9.2_

- [x] 15. Checkpoint - Ensure all tests pass
  - Run the full defined test suite for current scope, record pass/fail output, and proceed only on green status.

- [x] 16. Implement property-based tests for state machine parity
  - [x] 16.1 (Optional) Write property test for state machine parity
    - **Property 1: State Machine Parity**
    - Generate random action sequences and verify Android state matches axolync-browser v1.0.0 baseline
    - **Validates: Requirements 1.2, 4.1, 4.3**
  
  - [x] 16.2 (Optional) Write property test for state name consistency
    - **Property 2: State Name Consistency**
    - Verify all state names match exactly with axolync-browser v1.0.0
    - **Validates: Requirements 4.4**
  
  - [x] 16.3 (Optional) Write property test for splash screen persistence
    - **Property 3: Splash Screen Persistence**
    - Test splash screen remains visible until ready signal or timeout
    - **Validates: Requirements 2.2**

- [x] 17. Implement property-based tests for network and offline behavior
  - [x] 17.1 (Optional) Write property test for offline feature degradation
    - **Property 8: Offline Feature Degradation**
    - Test network-dependent operations fail gracefully when offline
    - **Validates: Requirements 7.1**

- [x] 18. Implement property-based tests for audio performance
  - [x] 18.1 (Optional) Write property test for audio capture latency bound
    - **Property 11: Audio Capture Latency Bound**
    - Measure latency from start action to first chunk available (target ≤150ms)
    - **Validates: Requirements 8.2, 8.4**
  
  - [x] 18.2 (Optional) Write property test for cold start performance
    - **Property 12: Cold Start Performance**
    - Measure cold start to first render (target ≤3s on mid-range 2019+ devices with 4GB RAM)
    - **Validates: Requirements 8.3, 10.6**
  
  - [x] 18.3 (Optional) Write property test for audio chunk jitter tolerance
    - **Property 13: Audio Chunk Jitter Tolerance**
    - Test system tolerates up to 2 consecutive missed chunks
    - **Validates: Requirements 8.5**

- [x] 19. Physical device testing and validation
  - [x] 19.1 Set up physical device testing environment
    - Identify target device profile: mid-range Android 2019+ with 4GB RAM
    - Prepare test devices for audio latency and performance measurements
    - Document device specifications used for testing
    - _Requirements: 3.5, 8.6, 8.7, 8.9_
  - [x] 19.2 Execute physical device tests
    - Measure audio capture latency on physical devices (Property 11)
    - Measure cold start performance on physical devices (Property 12)
    - Validate audio routing and quality on physical devices
    - Test lifecycle state preservation under real memory pressure
    - Document test results and any device-specific issues
    - _Requirements: 3.5, 8.2, 8.3, 8.6, 8.7, 8.9_

- [x] 20. Final integration and validation
  - [x] 20.1 Build production APK and validate configuration
    - Build release APK with ProGuard/R8 optimization
    - Verify remote debugging disabled in production build
    - Verify all security settings applied correctly
    - Test APK installation and first-run experience
    - _Requirements: 11.2_
  
  - [x] 20.2 End-to-end validation with axolync-browser integration
    - Verify complete plugin pipeline execution (SongSense, SyncEngine, LyricFlow)
    - Verify Web Workers function correctly
    - Verify state machine transitions match web baseline
    - Test complete user flows: permission grant → audio capture → song identification → lyric sync
    - _Requirements: 1.2, 1.3, 4.1, 4.3, 5.1, 5.2, 5.3, 5.4_

- [x] 21. Final checkpoint - Ensure all tests pass
  - Run the full defined test suite for current scope, record pass/fail output, and proceed only on green status.

- [x] 22. V5 Review Blocking Fixes
  - [x] 22.1 Fix MainActivity STARTING continuation
    - Replace early-return logic with deterministic continuation
    - Add retry loop with Handler and hard timeout (5 seconds)
    - Add bootstrap guard flag to prevent duplicate initialization
    - Handle READY, FAILED, and timeout states deterministically
    - _Requirements: 2.1, 2.2, 11.3_
  
  - [x] 22.2 Fix ServerManager idempotency/concurrency guard
    - Add AtomicBoolean startScheduled for in-flight guard
    - Use compareAndSet to prevent duplicate start tasks
    - Maintain no-op semantics for READY, STARTING, FAILED states
    - _Requirements: 6.1, 11.3_
  
  - [x] 22.3 Fix LocalHttpServer HEAD stream handling
    - Check asset existence without opening stream for HEAD requests
    - Return headers-only response for HEAD
    - Prevent stream handle leaks
    - _Requirements: 6.1, 11.3_
  
  - [x] 22.4 Add required tests for V5 fixes
    - Add ServerManager concurrency/idempotency tests
    - Add MainActivity startup-state tests
    - Add LocalHttpServer HEAD tests
    - Add localhost cleartext verification instrumentation test
    - _Requirements: 11.3_

- [x] 23. Android gesture lock + status-bar adapter bridge foundations
  - [x] 23.1 Disable WebView/browser pinch zoom on Android while preserving fixed text scale
    - Explicitly disable WebView zoom controls/support in `MainActivity` settings
    - Keep text zoom fixed at 100%
    - Ensure pinch gestures do not scale the page
  - [ ] 23.2 Expose status-bar song signal bridge APIs for plugin-side consumption
    - Add native notification-listener service for capturing latest song-like status entries
    - Add `AndroidBridge` JS APIs to check/request status-bar access and fetch latest match payload
    - Add manifest wiring for notification listener service
  - [x] 23.3 Add focused unit tests for new native behavior
    - Add test coverage for notification payload extraction/store behavior
    - Add static guard test that `MainActivity` keeps zoom support disabled
    - _Requirements: 3.1, 3.2, 6.1, 11.3_

- [x] 24. Consume builder server bundle artifacts and preinstalled plugin assets
  - [x] 24.1 Support builder-provided browser bundle paths in Gradle asset packaging
    - Read `AXOLYNC_BUILDER_BROWSER_NORMAL` / `AXOLYNC_BUILDER_BROWSER_DEMO` when present.
    - Prefer builder bundle assets over local submodule build paths for APK packaging.
  - [x] 24.2 Include preinstalled plugin manifest and zip assets in APK package
    - Ensure `plugins/preinstalled/manifest.json` and referenced zips are copied into app assets.
    - Preserve local submodule fallback behavior when builder paths are not provided.
  - [x] 24.3 Add regression test/guard
    - Add a unit/Gradle guard that verifies preinstalled manifest assets are present after copy task.

- [x] 25. Enforce Android touch lock semantics without breaking lyric drag zoom UX
  - [x] 25.1 Disable native page scroll + pinch while preserving WebView JS touch pipeline
    - Keep browser page pan/scroll disabled in wrapped runtime.
    - Keep single-finger lyric drag and double-tap gestures usable by browser-side lyric zoom logic.
  - [x] 25.2 Add regression coverage and CI workflow stability fixes
    - Add/update test proving zoom controls remain disabled and WebView touch lock wiring remains active.
    - Fix Android CI workflow setup-node/cache path reliability for this repository layout.

- [ ] 26. Restore notification-access visibility and disable long-press text selection in wrapped WebView
  - [x] 26.1 Disable Android long-press text selection menu while preserving JS touch gestures
    - Disable WebView long-click interaction and touch callout behavior for wrapped runtime.
    - Keep lyric drag and double-tap gesture pipeline functional.
  - [ ] 26.2 Rewire notification access flow to app-scoped listener settings
    - Mark notification listener service as non-exported with required bind permission for system-only binding.
    - Use `Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` with `Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME` and fallback to full listener settings screen.
  - [x] 26.3 Add regression guards for manifest and native bridge wiring
    - Add static tests for service export/permission wiring and intent fallback coverage markers.

- [x] 27. Support native debug-log archive saves from the wrapped WebView
  - [x] 27.1 Add a NativeBridge save hook for browser-generated debug archives
    - Accept a filename plus archive bytes/base64 from the web app and persist the ZIP into a user-visible downloads location.
    - Return structured success/failure information so the web app can log native save results.
  - [x] 27.2 Add regression coverage
    - Add focused unit/static coverage for the new native bridge method and its save-path contract.

- [x] 28. Make Android back navigation respect in-app overlay hierarchy
  - [x] 28.1 Route system Back to the active web-app overlay instead of exiting immediately
    - If plugin settings are open, Back should return to global settings.
    - If global settings are open, Back should close settings.
    - Exit the app only when the primary lyric view is the sole active surface.
  - [x] 28.2 Add regression coverage
    - Add focused native/browser bridge coverage that proves Back dispatches overlay-close behavior before allowing app exit.

- [x] 29. Restore wrapped-runtime reliability for debug archive saves and bridge workers
  - [x] 29.1 Fix Android debug archive downloads so the browser `Download` button produces a saved file
    - Keep the save path user-visible and return structured success/failure data to the web app.
  - [x] 29.2 Serve packaged bridge-worker assets with a JavaScript MIME type
    - Prevent Android WebView worker bootstrap crashes caused by packaged bridge-worker assets using the `.ts` extension.
    - Add a regression guard for the packaged worker MIME resolution path.
  - [x] 29.3 Add regression coverage
    - Cover native debug-archive save behavior and worker-asset MIME resolution for packaged browser assets.

- [x] 30. Sync checked-in wrapped browser assets to the current bridge-worker packaging contract
  - [x] 30.1 Refresh checked-in `app/src/main/assets/axolync-browser/` from the current browser build output
    - Ensure packaged worker entrypoints are available under `/workers/*.js` alongside the current main bundle.
    - Remove dependence on stale checked-in raw `.ts` bridge-worker assets.
  - [x] 30.2 Add regression coverage
    - Add a static/native guard that the checked-in wrapped browser asset tree contains the expected packaged worker JavaScript entrypoints.

- [x] 31. Materialize stable packaged bridge-worker entrypoints during Android asset sync
  - [x] 31.1 Copy packaged bridge workers into `/workers/*.js` during browser asset sync
    - Derive stable wrapped-runtime worker entrypoints from the built browser bundle instead of relying on raw hashed `.ts` assets.
  - [x] 31.2 Add regression coverage
    - Keep the packaged-worker asset guard green after the stable `/workers/*.js` entrypoints are generated during asset sync.

- [x] 32. Align wrapped-runtime browser asset sync with real browser build outputs
  - [x] 32.1 Reproduce stale/raw worker asset packaging in Android local asset sync
    - Add a regression proving Android asset sync must consume executable JavaScript bridge workers rather than copied raw `.ts` worker assets.
  - [x] 32.2 Run the same browser bundle build steps the platform actually depends on and consume the generated worker assets
    - Keep Android local asset sync aligned with the browser repo's real build outputs instead of relying on stale dist state or hashed TypeScript worker placeholders.
  - [x] 32.3 Add/keep regression coverage
    - Keep the wrapped browser asset tree guard green for executable bridge worker entrypoints after local asset sync.

- [x] 33. Restore JavaScript confirm/alert handling in the wrapped WebView
  - [x] 33.1 Reproduce that Android wrapped runtime does not surface plugin-removal confirmation dialogs
    - Add a focused guard proving MainActivity configures the WebView host to handle browser `alert()` / `confirm()` dialogs needed by plugin removal flows.
  - [x] 33.2 Add wrapped-runtime JavaScript dialog handling
    - Route browser JavaScript confirm/alert dialogs through native Android dialogs so plugin removal and similar settings actions work on Android instead of silently doing nothing.
  - [x] 33.3 Add/keep regression coverage
    - Keep a native/static regression proving the wrapped WebView retains JavaScript dialog support.

- [x] 34. Refresh checked-in wrapped browser assets after the latest browser/runtime fixes
  - [x] 34.1 Re-sync `app/src/main/assets/axolync-browser/` from the current browser build output
    - Keep the checked-in Android asset tree aligned with the latest browser worker/runtime and preinstalled-plugin fixes.
  - [x] 34.2 Re-run the packaged-browser asset guard
    - Confirm the checked-in wrapped asset tree still passes the native/static asset verification after the refresh.

- [x] 35. Add real wrapped-runtime bridge proxy support and keep preinstalled bridge parity visible
  - [x] 35.1 Reproduce that the wrapped local server rejects bridge POST requests and leaves bridge flows stuck
    - Add focused coverage for the wrapped runtime bridge endpoints so LyricFlow/SyncEngine POST traffic cannot silently hit a static-only server and return `405 Method Not Allowed`.
  - [x] 35.2 Add bridge proxy/runtime-config handling to the wrapped local server
    - Serve the runtime bridge-config endpoint and forward wrapped runtime bridge requests to the real localhost backends instead of treating them as static assets.
    - Keep the wrapped runtime behavior aligned with the browser dev stack bridge contract.
  - [x] 35.3 Add/keep regression coverage
    - Keep the wrapped local-server bridge proxy contract green so Android does not regress back to static-only bridge paths.

- [x] 36. Use localhost-safe bridge backend URLs in the wrapped runtime
  - [x] 36.1 Replace Android bridge backend URLs that still point at `127.0.0.1`
    - Keep the wrapped runtime on the canonical `localhost` host for cleartext compatibility when proxying to SongSense, SyncEngine, and LyricFlow backends.
  - [x] 36.2 Add regression coverage
    - Prove the wrapped local server runtime config and proxy target resolution no longer emit `127.0.0.1` backend URLs.

- [x] 37. Keep wrapped preinstalled bridge assets in parity with browser bridge manifests
  - [x] 37.1 Reproduce SyncEngine bridge preinstalled manifest drift in Android assets
    - Cover the case where the wrapped asset manifest version drifts from the bundled bridge ZIP and causes the bridge plugin to disappear from Android plugin lists.
  - [x] 37.2 Fix wrapped preinstalled manifest parity
    - Ensure Android copied assets keep the same bridge manifest versions and names as the current browser preinstalled bundles.
  - [x] 37.3 Add regression coverage
    - Prove the wrapped preinstalled manifest matches the bundled bridge ZIP manifests for SongSense, SyncEngine, and LyricFlow.

- [x] 38. Keep wrapped LyricFlow worker assets compatible with classic Android WebView worker loading
  - [x] 38.1 Reproduce classic-worker import crashes in checked-in wrapped assets
    - Cover the case where the checked-in packaged LyricFlow worker still starts with a module import and Android crashes with `Cannot use import statement outside a module`.
  - [x] 38.2 Refresh wrapped assets to the self-contained worker packaging contract
    - Keep the checked-in wrapped browser asset tree aligned with the packaged classic LyricFlow worker output that inlines its helper code.
  - [x] 38.3 Add regression coverage
    - Prove the checked-in wrapped LyricFlow worker no longer contains the helper import and still contains the inlined helper implementation.

- [x] 39. Keep wrapped LyricFlow worker assets free of CommonJS helper residue
  - [x] 39.1 Reproduce `require is not defined` crashes in checked-in wrapped assets
    - Cover the case where the checked-in packaged LyricFlow worker has already lost the module import but still contains a transpiler-emitted `require("./directLrcLibFallback.js")`.
  - [x] 39.2 Refresh wrapped assets to the plain-script worker packaging contract
    - Keep the checked-in wrapped browser asset tree aligned with packaged classic workers that contain no `require(...)` helper residue.
  - [x] 39.3 Add regression coverage
    - Prove the checked-in wrapped LyricFlow worker contains neither the helper import nor the CommonJS `require(...)` helper call.


- [x] 40. Serve wrapped Android LyricFlow bridge requests from the local server when no packaged backend exists
  - [x] 40.1 Reproduce Android LyricFlow bridge failures after worker bootstrap is fixed
    - Cover the case where the wrapped runtime reaches `lyricflow-bridge`, but the worker still fails with `Failed to fetch` because no packaged localhost backend is actually listening on the LyricFlow bridge port.
  - [x] 40.2 Satisfy wrapped LyricFlow bridge init/process/dispose requests directly from the local server fallback path
    - Keep the wrapped local server honoring the bridge contract for LyricFlow requests by answering init/dispose and translating LRCLIB results into the expected lyric payload when the packaged backend is absent.
  - [x] 40.3 Add regression coverage
    - Prove the wrapped local server contains the LyricFlow fallback bridge path and LRCLIB request handling markers needed by the packaged runtime.


- [x] 41. Refresh checked-in wrapped browser assets after the local-server LyricFlow fallback change
  - [x] 41.1 Re-sync `app/src/main/assets/axolync-browser/` from the current browser build output
    - Keep the checked-in wrapped browser asset tree aligned with the latest browser worker/runtime and preinstalled-plugin outputs after the local-server LyricFlow fallback change.
  - [x] 41.2 Re-run the packaged-browser parity guards
    - Confirm the checked-in wrapped asset tree and preinstalled plugin assets stay in sync with the current browser outputs.

- [x] 42. Re-sync checked-in wrapped browser assets after the latest browser runtime bundle and preinstalled-plugin refresh
  - [x] 42.1 Reproduce wrapped-asset drift after the latest browser build
    - Cover the case where `app/src/main/assets/axolync-browser/` still points at an older `main-*.js` bundle or older preinstalled bridge ZIP metadata after browser-side fixes already landed.
  - [x] 42.2 Refresh the checked-in wrapped asset tree
    - Re-sync the wrapped browser asset tree so Android packages the current browser index, preinstalled bridge ZIPs, and manifest metadata.
  - [x] 42.3 Re-run parity coverage
    - Keep the packaged-browser asset tests green against the refreshed checked-in asset tree.

- [x] 43. Add structured native LyricFlow bridge diagnostics and explicit timeout handling in the wrapped local server
  - [x] 43.1 Reproduce the opaque Android embedded LyricFlow `Failed to fetch` path after Python startup succeeds
    - Cover the case where Android logs already show `launchSucceeded=yes` and `health=ok`, but the WebView only receives a generic fetch failure because the embedded `get-lyrics` bridge call never returns a structured error payload.
  - [x] 43.2 Record native bridge request start/failure/success details and fail timed-out calls explicitly
    - Add a wrapped-runtime native log store that records embedded LyricFlow bridge requests, timing, operation name, and failure details.
    - Expose that native log stream through the wrapped local server for browser debug ZIP export.
    - Add a timeout around embedded LyricFlow bridge invocation so hangs become explicit `504`-style bridge failures instead of vague browser-side `Failed to fetch` errors.
  - [x] 43.3 Add focused regression coverage
    - Prove the wrapped local server exposes the native log endpoint and returns a structured timeout or invocation failure for embedded LyricFlow bridge calls instead of silently hanging.

- [x] 44. Parse Android LyricFlow bridge POST bodies through NanoHTTPD instead of reading the socket to EOF
  - [x] 44.1 Reproduce the real-device gap where the embedded LyricFlow request never reaches native bridge logging
    - Cover the case where Android logs show embedded Python healthy and bridge preflight `ok`, but neither native bridge request start nor failure logs appear for `get-lyrics`, implying the request dies at the local HTTP body-read boundary.
  - [x] 44.2 Use `parseBody`/`postData` for POST payload extraction with a bounded fallback for tests
    - Replace raw socket-to-EOF POST reading in `LocalHttpServer` with NanoHTTPD body parsing so real WebView requests do not hang before embedded LyricFlow invocation.
    - Keep a safe bounded fallback for fake test sessions which do not populate NanoHTTPD `postData`.
    - Add native diagnostics around request-body extraction so the download bundle can show whether the request reached the server and whether body parsing succeeded.
  - [x] 44.3 Add focused regression coverage
    - Prove the wrapped local server source uses NanoHTTPD body parsing for POST payloads and still supports the direct embedded LyricFlow proof-of-life test path.

- [x] 45. Expose embedded LyricFlow status and invocation directly through `AndroidBridge`
  - [x] 45.1 Reproduce the Android gap where embedded Python succeeds natively but WebView fetch still fails
    - Cover the case where the Android wrapper already records successful embedded LyricFlow calls, but the browser path still depends on the local HTTP compatibility route and fails before consuming the native result.
  - [x] 45.2 Add direct native-bridge hooks for embedded LyricFlow status and invocation
    - Expose embedded LyricFlow runtime status as structured JSON through `NativeBridge`.
    - Expose direct embedded LyricFlow invocation through `NativeBridge` with explicit success, timeout, and failure envelopes.
    - Keep the existing local HTTP server available for serving the wrapped app and other wrapper duties, but stop requiring it for Android LyricFlow request execution.
  - [x] 45.3 Add focused regression coverage
    - Prove `NativeBridge` exposes the embedded LyricFlow runtime status and direct invocation hooks and records native diagnostics for start/success/timeout/failure.

## Notes

- Tasks labeled (Optional) may be skipped only for MVP builds; mandatory tasks remain required for production readiness
- Each task references specific requirements for traceability
- Checkpoints ensure incremental validation at key milestones
- Property tests validate universal correctness properties with minimum 100 iterations
- Unit tests validate specific examples, edge cases, and integration points
- Physical device testing is required for audio latency and performance validation (tasks 19.1-19.2)
- The implementation uses Kotlin for all native Android components
- The axolync-browser submodule remains the authoritative source - no modification of web app code
- All property tests must include comment tags: `// Feature: android-apk-wrapper, Property {number}: {property_text}`
