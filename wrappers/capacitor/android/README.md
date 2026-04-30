# Capacitor Android Host

Target authority path for the Android Capacitor host.

During compatibility mode, active Gradle files and Android sources remain at the repository root:

- Gradle app module: `app/`
- Kotlin host source: `app/src/main/kotlin/com/axolync/android/`
- staged browser assets: `app/src/main/assets/public/`

Do not add new shared wrapper concepts directly under Android-only paths. Shared bridge, diagnostics, deployment, and defaults belong in `wrappers/capacitor/shared/` or `native-service-companions/`.
