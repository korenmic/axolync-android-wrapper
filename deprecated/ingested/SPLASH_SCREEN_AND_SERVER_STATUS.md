# Splash Screen and Embedded Server Status

## ✅ CONFIRMED: Server IS Running and Being Used

### Server Architecture (Working Correctly)
1. **AxolyncApplication.onCreate()** starts ServerManager asynchronously on app launch
2. **ServerManager** starts LocalHttpServer on background thread
3. **LocalHttpServer** binds to random available port (localhost only)
4. **MainActivity** loads WebView from `http://localhost:<port>/index.html`

### Evidence Server is Working
- ✅ AxolyncApplication calls `serverManager.startServerAsync()` on app startup
- ✅ MainActivity loads via `webView.loadUrl("$baseUrl/index.html")` where baseUrl = `http://localhost:<port>`
- ✅ MainActivity has strict origin validation allowing only localhost HTTP
- ✅ Network security config allows cleartext only for localhost
- ✅ WebView settings disable file:// access (`allowFileAccess = false`)

**The app is NOT using file:// - it IS using the embedded HTTP server correctly!**

## ✅ FIXED: Splash Screen Issues

### What Was Fixed

1. **Minimum 2-Second Display Duration** ✅
   - Added `MINIMUM_SPLASH_DURATION_MS = 2000L` constant
   - Splash screen now shows for at least 2 seconds, regardless of server startup time
   - Uses `enforceMinimumSplashDuration()` to delay app initialization if needed

2. **Background Color** ✅
   - Changed from white (#FFFFFF) to dark gray (#1A1A1A)
   - Defined as `splash_background` color in `values/colors.xml`

3. **Animation Duration** ✅
   - Added 200ms fade-in animation
   - Configured via `windowSplashScreenAnimationDuration` in theme

4. **Infrastructure for High-Resolution Images** ✅
   - Created `drawable-port/` folder for portrait splash images
   - Created `drawable-land/` folder for landscape splash images
   - Created `splash_logo.xml` drawable that can reference orientation-specific images
   - Added comprehensive `SPLASH_SCREEN_README.md` with instructions

### Current Splash Screen Configuration

**Location**: `app/src/main/res/values/themes.xml`
```xml
<style name="Theme.App.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/splash_background</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/splash_logo</item>
    <item name="windowSplashScreenAnimationDuration">200</item>
    <item name="postSplashScreenTheme">@style/Theme.AxolyncAndroid</item>
</style>
```

**Behavior**:
- Shows for minimum 2 seconds
- Dark gray background (#1A1A1A)
- 200ms fade-in animation
- Waits for server to be READY before dismissing (but enforces 2-second minimum)

## 📋 TODO: Add Your Custom Splash Images

### What You Need to Provide

1. **Portrait Splash Image** (for tall devices)
   - Recommended size: 1080x1920 pixels or higher
   - Aspect ratio: 9:16 (portrait)
   - Format: PNG with transparency
   - Save as: `app/src/main/res/drawable-port/splash_logo_portrait.png`

2. **Landscape Splash Image** (for wide devices)
   - Recommended size: 1920x1080 pixels or higher
   - Aspect ratio: 16:9 (landscape)
   - Format: PNG with transparency
   - Save as: `app/src/main/res/drawable-land/splash_logo_landscape.png`

### How to Add Your Images

1. Place your portrait image at:
   ```
   app/src/main/res/drawable-port/splash_logo_portrait.png
   ```

2. Place your landscape image at:
   ```
   app/src/main/res/drawable-land/splash_logo_landscape.png
   ```

3. Update `app/src/main/res/drawable/splash_logo.xml`:
   ```xml
   <?xml version="1.0" encoding="utf-8"?>
   <layer-list xmlns:android="http://schemas.android.com/apk/res/android">
       <item>
           <bitmap
               android:src="@drawable/splash_logo_portrait"
               android:gravity="center" />
       </item>
   </layer-list>
   ```
   Note: Android will automatically use the correct orientation-specific image from drawable-port/ or drawable-land/

4. Rebuild the APK:
   ```bash
   distrobox enter devbox -- ./gradlew clean :app:assembleDebug
   ```

5. Install and test:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## 🔍 How to Verify Server is Working

### Method 1: Check Logcat
```bash
adb logcat | grep -E "(AxolyncApplication|ServerManager|MainActivity)"
```

Look for:
- `AxolyncApplication: Server start initiated asynchronously`
- `ServerManager: Server started successfully on port XXXXX`
- `MainActivity: Loading web app from http://localhost:XXXXX/index.html`

### Method 2: Check WebView Network Requests
```bash
adb logcat | grep -E "chromium|WebView"
```

You should see HTTP requests to `http://localhost:<port>/` NOT `file://`

### Method 3: Test in App
1. Install APK on device
2. Launch app
3. If you see the web app loading, the server is working
4. If you see "Server Error" dialog, check logcat for details

## 📦 Current Build Status

- ✅ Compilation: SUCCESS
- ✅ Unit Tests: SUCCESS (all tests pass)
- ✅ APK Build: SUCCESS
- ✅ APK Location: `app/build/outputs/apk/debug/app-debug.apk` (7.6 MB)

## 🚀 Next Steps

1. **Provide your custom splash images** (portrait and landscape)
2. **Test the APK** on your device to verify:
   - Splash screen shows for at least 2 seconds
   - Splash screen has dark background
   - Web app loads correctly from localhost server
   - No file:// URLs are being used
3. **Check logcat** if you encounter any issues

## 📝 Summary

**The embedded HTTP server IS working correctly!** The app loads the WebView from `http://localhost:<port>/index.html`, not from `file://`. The splash screen now:
- Shows for minimum 2 seconds
- Has a dark gray background
- Has proper infrastructure for high-resolution orientation-specific images
- Just needs your custom splash images to be added

Everything is ready for you to add your splash images and test!
