# Setup Instructions

## Prerequisites

1. **Android Studio**: Hedgehog (2023.1.1) or later
2. **JDK**: Version 17
3. **Android SDK**: API level 34 (Android 14)
4. **Git**: For submodule management
5. **Node.js and npm**: For building axolync-browser (if building from source)

## Initial Setup

### 1. Clone the Repository

```bash
git clone <repository-url>
cd axolync-android-wrapper
```

### 2. Initialize axolync-browser Submodule

The project includes axolync-browser as a Git submodule. Initialize and update it:

```bash
git submodule init
git submodule update
```

This will clone the axolync-browser repository at version v1.0.0 into the `axolync-browser/` directory.

### 3. Build axolync-browser

Navigate to the axolync-browser directory and build the web application:

```bash
cd axolync-browser
npm install
npm run build
cd ..
```

This creates the `axolync-browser/dist/` directory with the built web application.

### 4. Open in Android Studio

1. Launch Android Studio
2. Select **File > Open**
3. Navigate to the project root directory and click **OK**
4. Wait for Gradle sync to complete

### 5. Build the Android Project

The Gradle build is configured to automatically copy the built axolync-browser assets to `app/src/main/assets/public/` during the build process.

```bash
# Build the project
./gradlew build

# Or build from Android Studio: Build > Make Project
```

### 6. Run on Device/Emulator

Connect an Android device or start an emulator, then:

```bash
# Install debug build
./gradlew installDebug

# Or run from Android Studio: Run > Run 'app'
```

## Project Structure

```
axolync-android-wrapper/
├── app/                                    # Android application module
│   ├── build.gradle.kts                   # App-level Gradle configuration
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml        # App manifest with permissions
│   │   │   ├── kotlin/com/axolync/android/
│   │   │   │   ├── activities/            # SplashActivity, MainActivity
│   │   │   │   ├── services/              # AudioCaptureService, PermissionManager, etc.
│   │   │   │   ├── bridge/                # NativeBridge (JavaScript interface)
│   │   │   │   └── utils/                 # PluginManager, NetworkMonitor
│   │   │   ├── res/                       # Android resources (layouts, strings, themes)
│   │   │   └── assets/                    # Web app assets (populated by Gradle)
│   │   │       └── axolync-browser/       # Built web application (copied from submodule)
│   │   ├── test/                          # Unit tests
│   │   └── androidTest/                   # Instrumented tests
├── axolync-browser/                       # Git submodule (axolync-browser v1.0.0)
├── build.gradle.kts                       # Root-level Gradle configuration
├── settings.gradle.kts                    # Gradle settings
├── gradle.properties                      # Gradle properties
└── .gitmodules                            # Git submodule configuration
```

## Gradle Tasks

### Copy Assets

Manually copy axolync-browser assets (automatically runs during build):

```bash
./gradlew copyAxolyncBrowserAssets
```

### Build

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
```

### Install

```bash
# Install debug build
./gradlew installDebug

# Install release build
./gradlew installRelease
```

### Test

```bash
# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device/emulator)
./gradlew connectedAndroidTest
```

## Troubleshooting

### axolync-browser dist directory not found

**Error**: `axolync-browser dist directory not found`

**Solution**: Build axolync-browser first:
```bash
cd axolync-browser
npm install
npm run build
cd ..
```

### Gradle sync failed

**Error**: Gradle sync issues

**Solution**: 
1. Ensure JDK 17 is configured in Android Studio
2. File > Invalidate Caches / Restart
3. Delete `.gradle/` and `build/` directories, then sync again

### Submodule not initialized

**Error**: `axolync-browser/` directory is empty

**Solution**: Initialize the submodule:
```bash
git submodule init
git submodule update
```

### Permission denied on gradlew

**Error**: `Permission denied` when running `./gradlew`

**Solution**: Make the script executable:
```bash
chmod +x gradlew
```

## Next Steps

After setup, the project structure is ready for implementation of:
- WebView configuration and security
- Native bridge implementation
- Audio capture service
- Permission management
- Lifecycle coordination
- Plugin management

See `.kiro/specs/android-apk-wrapper/tasks.md` for the implementation roadmap.
