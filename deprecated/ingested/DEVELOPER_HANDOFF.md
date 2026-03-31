# Developer Handoff Documentation

## Project Overview
Android APK wrapper for Axolync karaoke companion web app. The app hosts a WebView that loads from an embedded HTTP server (localhost).

## Critical Context

### Current Status
- ✅ **WORKING**: App launches successfully (v0.1.0-alpha tagged)
- **Latest Commit**: 58a437c
- **Known Issues**: 
  - Buttons not clickable (web app may not be fully loading)
  - Splash screen shows small centered icon (Android 12+ API limitation)
  - Need to verify server is actually serving content

### Key Architecture
1. **AxolyncApplication** starts ServerManager on app launch (async, background thread)
2. **ServerManager** starts LocalHttpServer (binds to localhost:<random_port>)
3. **MainActivity** waits for server to be READY, then loads WebView from `http://localhost:<port>/index.html` (NOT file://)
4. **Splash Screen** shows for minimum 2 seconds while server starts
5. **WebView** only loads after server state is READY (prevents loading before server is up)

## Development Environment

### System Requirements
- **OS**: Linux (SteamDeck)
- **Container**: distrobox with devbox container
- **Shell**: bash
- **Java**: JDK 17
- **Android SDK**: API 24-34

### Build Commands

#### All commands MUST be run through distrobox:
```bash
# Pattern: distrobox enter devbox -- <gradle_command>
```

#### Clean Build
```bash
distrobox enter devbox -- ./gradlew clean
```

#### Compile Kotlin (Fast Check)
```bash
distrobox enter devbox -- ./gradlew :app:compileDebugKotlin
```

#### Run Unit Tests (Full Suite - Slow)
```bash
distrobox enter devbox -- ./gradlew :app:testDebugUnitTest
```

#### Run Smoke Tests (Fast - TODO: Not yet implemented)
```bash
# TODO: Create smoke test suite for faster validation
distrobox enter devbox -- ./gradlew :app:testDebugUnitTest --tests "*SmokeTest"
```

#### Build Debug APK
```bash
distrobox enter devbox -- ./gradlew :app:assembleDebug
```

#### Build Release APK
```bash
distrobox enter devbox -- ./gradlew :app:assembleRelease
```

### APK Output Location
```
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release-unsigned.apk
```

### Install APK to Device
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Testing Strategy

### Test Types

#### 1. Unit Tests (Current - Slow)
- Location: `app/src/test/kotlin/`
- Run: `distrobox enter devbox -- ./gradlew :app:testDebugUnitTest`
- Duration: ~2-3 seconds
- Coverage: Structural tests, resource validation

#### 2. Instrumentation Tests
- Location: `app/src/androidTest/kotlin/`
- Run: `distrobox enter devbox -- ./gradlew :app:connectedDebugAndroidTest`
- Requires: Physical device or emulator connected
- Coverage: WebView localhost loading, cleartext verification

#### 3. Smoke Tests (TODO - Not Yet Implemented)
- **NEEDED**: Fast subset of critical tests
- **Purpose**: Quick validation before delivering APK
- **Target Duration**: <1 second
- **Should Test**:
  - Resources exist (splash_logo, themes, colors)
  - MainActivity compiles
  - ServerManager compiles
  - No obvious syntax errors

### Resource Validation Tests
Critical tests in `SplashScreenResourceTest.kt`:
- Validates splash_logo drawable exists
- Validates splash_background color exists
- Validates Theme.App.Starting exists
- **Purpose**: Prevent crashes due to missing resources

## Git Workflow

### Branch Strategy
- **master**: Main development branch
- **origin/master**: Last pushed state (currently 7 commits behind)
- **Feature branches**: Use for investigation/fixes

### Important Commits
```
v0.1.0-alpha (759b388) - First working version - app launches!
58a437c - Current HEAD - Added 2-second splash + wait for server ready
759b388 - Fixed crash by reverting V5 complex logic
bfe51c9 - Last simple working version (before V5 fixes)
be588a2 - Added minimum splash duration (broke it with complex logic)
```

### Creating Investigation Branches
```bash
# Save current work
git checkout -b fix-<issue-name>
git add -A
git commit -m "WIP: <description>"

# Go back to investigate
git checkout master
git checkout <commit-hash>  # Detached HEAD for testing
```

### Never Use git stash
- **Reason**: Not reliable, doesn't survive conflicts
- **Instead**: Create a branch and commit

## Debugging Crashes

### Get Crash Logs from Device
```bash
# Connect device via USB
adb logcat | grep -E "(AndroidRuntime|FATAL|MainActivity|ServerManager|AxolyncApplication)"
```

### Common Crash Causes
1. **Missing Resources**: splash_logo, themes, colors
   - Check: `app/src/main/res/drawable/splash_logo.png` exists
   - Check: `app/src/main/res/values/themes.xml` has Theme.App.Starting
   
2. **Context Issues**: Using `this` in lambda before super.onCreate()
   - Check: MainActivity.onCreate() splash screen setup
   
3. **Server Startup**: ServerManager fails to start
   - Check: AxolyncApplication.onCreate() calls startServerAsync()
   - Check: LocalHttpServer can bind to port

### Bisecting to Find Breaking Commit
```bash
git bisect start
git bisect bad HEAD
git bisect good bfe51c9

# For each commit git checks out:
distrobox enter devbox -- ./gradlew clean :app:assembleDebug
# Test APK on device
# If works: git bisect good
# If crashes: git bisect bad
```

## Key Files to Know

### MainActivity.kt
- **Path**: `app/src/main/kotlin/com/axolync/android/activities/MainActivity.kt`
- **Purpose**: Hosts WebView, coordinates services
- **Critical**: 
  - onCreate() installs splash screen BEFORE super.onCreate()
  - Uses `applicationContext` in splash condition (not `this`)
  - Calls `waitForServerReady()` if server is STARTING
  - Only loads WebView when server state is READY
- **Key Methods**:
  - `waitForServerReady()`: Polls server state every 100ms until READY
  - `loadWebApp()`: Loads `http://localhost:<port>/index.html` in WebView
  - `initializeServices()`: Sets up all native services
  - `configureWebView()`: Configures security settings and JavaScript bridge

### ServerManager.kt
- **Path**: `app/src/main/kotlin/com/axolync/android/server/ServerManager.kt`
- **Purpose**: Manages LocalHttpServer lifecycle
- **Startup**: Async on background thread
- **States**: STARTING, READY, FAILED

### LocalHttpServer.kt
- **Path**: `app/src/main/kotlin/com/axolync/android/server/LocalHttpServer.kt`
- **Purpose**: Embedded HTTP server using NanoHTTPD
- **Binding**: localhost only (security)
- **Assets**: Serves from `app/src/main/assets/public/`

### AxolyncApplication.kt
- **Path**: `app/src/main/kotlin/com/axolync/android/AxolyncApplication.kt`
- **Purpose**: App entry point, starts ServerManager
- **Critical**: Must call serverManager.startServerAsync() in onCreate()

### Splash Screen Resources
- **Theme**: `app/src/main/res/values/themes.xml` (Theme.App.Starting)
- **Theme Android 12+**: `app/src/main/res/values-v31/themes.xml` (Android 12+ specific config)
- **Logo Default**: `app/src/main/res/drawable/splash_logo.png` (fallback)
- **Logo Portrait Standard**: `app/src/main/res/drawable-port-xhdpi/splash_logo.png` (1080x1920)
- **Logo Portrait Tall**: `app/src/main/res/drawable-port-xxhdpi/splash_logo.png` (1080x2400)
- **Logo Landscape Standard**: `app/src/main/res/drawable-land-xhdpi/splash_logo.png` (1920x1080)
- **Logo Landscape Wide**: `app/src/main/res/drawable-land-xxhdpi/splash_logo.png` (2400x1080)
- **Background Color**: `app/src/main/res/values/colors.xml` (splash_background = #1A1A1A)
- **Limitation**: Android 12+ SplashScreen API shows centered icon with size constraints, not full-screen image

## Known Issues

### Current Issues (v0.1.0-alpha)
- **Buttons Not Clickable**: Web app loads but buttons don't respond
  - **Possible Causes**:
    - JavaScript not executing properly
    - NativeBridge not connected
    - Web app expecting different environment
    - Assets not loading correctly from server
  - **Debug**: Check logcat for JavaScript errors, verify server is serving files
  
- **Splash Screen Small Icon**: Android 12+ API shows centered icon, not full-screen
  - **Limitation**: Android 12+ SplashScreen API design
  - **Workaround**: Icon background matches splash background for seamless look
  - **Future**: Consider custom splash activity for full-screen image

### Fixed Issues
- ✅ App crash on startup (fixed in 759b388)
- ✅ Missing default splash_logo.png (fixed in 95326b0)
- ✅ White background (fixed - now dark gray #1A1A1A)
- ✅ Splash duration too short (fixed - now 2 seconds minimum)
- ✅ WebView loading before server ready (fixed - now waits for READY state)

## Version Tagging

### When to Tag
- **v0.1.0-alpha**: ✅ DONE - First version that launches without crashing
- **v0.2.0-alpha**: First version with working WebView + clickable buttons
- **v0.3.0-alpha**: First version with all features working
- **v1.0.0**: Production ready

### How to Tag
```bash
git tag -a v0.2.0-alpha -m "Working version with clickable UI"
git push origin v0.2.0-alpha
```

### Existing Tags
- `v0.1.0-alpha` (759b388): First working version - app launches successfully

## Future Plans (See FUTURE_PLANS.md)
1. **CI/CD**: Set up budtmo/docker-android for automated testing
2. **Smoke Tests**: Create fast test suite (<1 second)
3. **Crash Prevention**: Add more resource validation tests
4. **Automated APK Testing**: Test APK launches before delivery

## Quick Reference

### Fast Validation Workflow
```bash
# 1. Compile check (fastest)
distrobox enter devbox -- ./gradlew :app:compileDebugKotlin

# 2. Run smoke tests (when implemented)
# distrobox enter devbox -- ./gradlew :app:testDebugUnitTest --tests "*SmokeTest"

# 3. Build APK
distrobox enter devbox -- ./gradlew :app:assembleDebug

# 4. Install and test
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 5. Watch logs
adb logcat | grep -E "(AndroidRuntime|FATAL|MainActivity)"
```

### Emergency Rollback
```bash
# Go back to v0.1.0-alpha (last working version)
git checkout v0.1.0-alpha
distrobox enter devbox -- ./gradlew clean :app:assembleDebug
# APK at: app/build/outputs/apk/debug/app-debug.apk
```

## Debugging Web App Issues

### Check if Server is Serving Files
```bash
# Get server port from logcat
adb logcat | grep "ServerManager"
# Look for: "Server started successfully on port XXXXX"

# Test server from device
adb shell
curl http://localhost:<port>/index.html
# Should return HTML content

# Or from computer (if port forwarding set up)
adb forward tcp:8080 tcp:<port>
curl http://localhost:8080/index.html
```

### Check JavaScript Console
```bash
# Enable WebView debugging in MainActivity (if not already)
# WebView.setWebContentsDebuggingEnabled(true) for debug builds

# Then use Chrome DevTools:
# chrome://inspect in Chrome browser
# Find your device and click "inspect"
```

### Check WebView Loading
```bash
adb logcat | grep -E "(MainActivity|WebView|chromium)"
# Look for:
# - "Loading web app from http://localhost:XXXXX/index.html"
# - Any JavaScript errors
# - Network request failures
```

### Verify NativeBridge Connection
```bash
adb logcat | grep "NativeBridge"
# Check if bridge is registered and methods are being called
```

## Contact Points
- **Spec Files**: `.kiro/specs/android-apk-wrapper/`
- **Design Doc**: `.kiro/specs/android-apk-wrapper/design.md`
- **Requirements**: `.kiro/specs/android-apk-wrapper/requirements.md`
- **Tasks**: `.kiro/specs/android-apk-wrapper/tasks.md`

## Critical Reminders
1. ⚠️ **ALWAYS** run gradle through distrobox
2. ⚠️ **NEVER** use `git stash` - use branches instead
3. ⚠️ **ALWAYS** test APK on device before delivering
4. ⚠️ **ALWAYS** check logcat for crashes and errors
5. ⚠️ When working version achieved, tag it immediately
6. ⚠️ WebView must wait for server READY state before loading
7. ⚠️ Use `applicationContext` not `this` in splash screen lambda
8. ⚠️ Splash screen setup must be BEFORE super.onCreate()

## Common Pitfalls

### 1. Loading WebView Before Server Ready
**Problem**: WebView loads before server is up, nothing works
**Solution**: Use `waitForServerReady()` to poll server state
**Check**: Look for "Server became ready, loading web app" in logcat

### 2. Context Issues in Splash Screen
**Problem**: Using `this` in splash screen lambda causes crashes
**Solution**: Use `applicationContext` instead
**Location**: MainActivity.onCreate() splash screen setup

### 3. Missing Default Drawable
**Problem**: App crashes if base drawable/ folder missing splash_logo.png
**Solution**: Always have default in drawable/ plus orientation-specific versions
**Test**: Run SplashScreenResourceTest to validate

### 4. Complex Retry Logic
**Problem**: Complex retry loops and timing logic cause crashes
**Solution**: Keep it simple - poll with Handler.postDelayed()
**Example**: See waitForServerReady() method

### 5. Not Testing on Device
**Problem**: Emulator behaves differently than real device
**Solution**: Always test on physical device before delivering
**Command**: `adb install -r app/build/outputs/apk/debug/app-debug.apk`


## Troubleshooting Guide

### App Crashes on Startup
1. Check logcat for crash stack trace
2. Verify all splash resources exist (run SplashScreenResourceTest)
3. Check MainActivity.onCreate() - splash setup before super.onCreate()?
4. Check AxolyncApplication - is startServerAsync() called?
5. Try emergency rollback to v0.1.0-alpha

### Buttons Not Clickable
1. Check if server is READY: `adb logcat | grep ServerManager`
2. Verify WebView loaded: `adb logcat | grep "Loading web app"`
3. Check JavaScript console: chrome://inspect
4. Verify assets exist: `ls app/src/main/assets/public/`
5. Test server directly: `adb shell curl http://localhost:<port>/index.html`

### Splash Screen Issues
1. Too short: Check MINIMUM_SPLASH_DURATION_MS constant
2. Wrong image: Check drawable/ has default, drawable-port/land have specific
3. White background: Check splash_background color in colors.xml
4. Crashes: Check Theme.App.Starting exists in themes.xml

### Server Won't Start
1. Check logcat: `adb logcat | grep ServerManager`
2. Look for port binding errors
3. Check assets exist: `ls app/src/main/assets/public/`
4. Verify AxolyncApplication.onCreate() calls startServerAsync()
5. Check ServerManager.startServerInternal() for exceptions

### Build Failures
1. Clean build: `distrobox enter devbox -- ./gradlew clean`
2. Check Java version: Should be JDK 17
3. Check Android SDK: Should have API 24-34
4. Verify distrobox is running: `distrobox list`
5. Check for syntax errors: `./gradlew :app:compileDebugKotlin`

## Quick Fixes

### Fix: App Crashes Immediately
```bash
# Rollback to working version
git checkout v0.1.0-alpha
distrobox enter devbox -- ./gradlew clean :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Fix: Buttons Not Working
```bash
# Check if it's a server timing issue
# Add more delay in waitForServerReady()
# Or increase MINIMUM_SPLASH_DURATION_MS
```

### Fix: Splash Screen Too Small
```bash
# Android 12+ limitation - icon is always centered
# Can only adjust icon size, not make it full-screen
# Consider custom splash activity for full control
```

### Fix: Missing Resources
```bash
# Run resource validation test
distrobox enter devbox -- ./gradlew :app:testDebugUnitTest --tests "SplashScreenResourceTest"

# If fails, check:
ls app/src/main/res/drawable/splash_logo.png
ls app/src/main/res/values/themes.xml
ls app/src/main/res/values/colors.xml
```

## Performance Tips

### Faster Builds
1. Use `compileDebugKotlin` instead of full build for syntax checks
2. Don't run full test suite every time - use smoke tests when available
3. Keep Gradle daemon running (it does by default)
4. Use `--parallel` flag for multi-module builds

### Faster Testing
1. Use `adb install -r` to replace existing APK (faster than uninstall/install)
2. Keep device connected and unlocked
3. Use `adb logcat -c` to clear logs before testing
4. Filter logcat to relevant tags only

### Faster Debugging
1. Enable WebView debugging in debug builds
2. Use chrome://inspect for JavaScript debugging
3. Use `adb shell am start` to launch app from command line
4. Use `adb shell dumpsys activity` to check activity state

## Resources

### Documentation
- Android SplashScreen API: https://developer.android.com/develop/ui/views/launch/splash-screen
- NanoHTTPD: https://github.com/NanoHttpd/nanohttpd
- WebView: https://developer.android.com/develop/ui/views/layout/webapps/webview

### Tools
- budtmo/docker-android: https://github.com/budtmo/docker-android
- Android Debug Bridge (adb): https://developer.android.com/tools/adb
- Chrome DevTools: chrome://inspect

### Project Files
- Spec: `.kiro/specs/android-apk-wrapper/`
- Handoff: `DEVELOPER_HANDOFF.md` (this file)
- Future Plans: `FUTURE_PLANS.md`
- APK Status: `APK_STATUS.md`
