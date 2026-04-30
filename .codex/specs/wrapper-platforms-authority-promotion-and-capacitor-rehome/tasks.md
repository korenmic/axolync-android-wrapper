# Implementation Plan

- [x] 1. Encode the target wrapper authority identity in repo docs and config.
  - Add or update repo-local docs to describe `axolync-platform-wrapper` as the target identity.
  - Document whether the active path is rename/refactor or temporary migration-source mode.
  - Add compatibility notes for any remaining `axolync-android-wrapper` naming.
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 5.1, 5.4_

- [x] 2. Rehome Android/Capacitor structure under a wrapper-family layout.
  - Move or wrap existing Android runtime files under `wrappers/capacitor/android` or equivalent.
  - Add shared Capacitor and iOS-placeholder structure without claiming iOS build support.
  - Update path references in scripts/config so Android builds still resolve.
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 4.1_

- [x] 3. Preserve browser asset staging through the new layout.
  - Update staging scripts and Gradle/Capacitor hooks to find browser output through the rehomed paths.
  - Add tests or structural checks for the staged asset destination.
  - Keep compatibility path handling while builder still consumes old repo/path assumptions.
  - _Requirements: 4.1, 4.2, 4.4, 6.4_

- [x] 4. Move generic native service companion host concepts into wrapper-owned shared areas.
  - Organize shared host-protocol, deployment, diagnostics, and capability-state code away from Android-only naming.
  - Keep addon-specific Vibra and LRCLIB payload descriptors/assets sourced from addon repos.
  - Add tests for unsupported, unavailable, refused, startup-failed, and running capability states.
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 6.2_

- [ ] 5. Harden native payload deployment and duplicate prevention after rehome.
  - Add or update path normalization for app-private deployment locations.
  - Verify compressed native payloads are not copied into both addon zip payloads and unrelated APK root locations.
  - Add structural tests around Vibra and LRCLIB native payload staging.
  - _Requirements: 4.3, 6.2, 6.3_

- [ ] 6. Add Android build and compatibility proof for the promoted layout.
  - Add structural tests proving required Android/Capacitor files still exist after rehome.
  - Add compatibility tests for old builder-facing path assumptions while transition mode is active.
  - Ensure diagnostics expose whether compatibility mode or target authority mode was used.
  - _Requirements: 4.1, 4.4, 5.2, 6.1, 6.4_

- [ ] 7. Scope or remove stale Android-only repo identity surfaces.
  - Update Android-specific docs so they describe `wrappers/capacitor/android`, not the whole repo identity.
  - Mark temporary compatibility aliases with removal criteria.
  - Add guardrails that prevent new shared wrapper code from being added under Android-only paths.
  - _Requirements: 1.4, 5.1, 5.3, 5.4_

## Self-Review Notes

- Tasks are repo-local and do not implement builder source resolution.
- Tasks preserve Android buildability before cleanup.
- Tasks move generic bridge concepts without moving addon-owned payload truth.
- Tasks keep iOS placeholder-only until a future iOS implementation seed proves more.
