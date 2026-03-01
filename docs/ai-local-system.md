# AI Local System Notes (Current Environment)

## Environment
- Repo: `/home/deck/src/axolync-android-wrapper`
- Shell: `bash`
- Java: JDK 17
- Build system: Gradle Android

## Standard Commands
```bash
./gradlew :app:compileDebugKotlin
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

APK output:
- `app/build/outputs/apk/debug/app-debug.apk`

## Notes
- Embedded server runtime is localhost-based; avoid file:// fallback behavior unless explicitly requested.
- Splash is `SplashActivity`-based full-screen layout.
- If UI appears static/non-interactive, inspect `LocalHttpServer` asset resolution and JS module paths first.
