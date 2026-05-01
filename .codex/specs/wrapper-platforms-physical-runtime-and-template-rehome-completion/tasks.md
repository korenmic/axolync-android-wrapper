# Implementation Plan

- [ ] 1. Add failing structural tests for canonical wrapper source ownership.
  - Test that `wrappers/capacitor/android` contains real Android/Capacitor project source and not only README/config placeholders.
  - Test that wrapper-owned Tauri and Electron template roots exist with required project files.
  - Test that native service companion host glue exists in wrapper-owned paths.
  - Test that placeholder-only iOS cannot count as runnable support.
  - _Requirements: 1.1, 2.1, 2.2, 2.3, 4.1, 4.2, 5.1, 5.2, 5.3, 5.4_

- [ ] 2. Rehome active Android/Capacitor runtime source under the canonical wrapper family path.
  - Move or restructure the active Android app/project source so `wrappers/capacitor/android` is canonical.
  - Preserve Android buildability by converting any required root-level Gradle/Capacitor/script entrypoints into thin shims.
  - Update wrapper layout metadata to identify canonical source paths and shim paths separately.
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 5.5, 6.2, 6.3_

- [ ] 3. Publish canonical desktop Tauri template source in the wrapper repo.
  - Add the active Tauri desktop template/source currently required for generated desktop artifacts under a wrapper-owned canonical path.
  - Include required Tauri package/config/source files and native companion host integration points.
  - Add tests that fail if the Tauri template is missing or placeholder-only.
  - _Requirements: 2.1, 2.3, 2.4, 3.1, 5.2, 6.1_

- [ ] 4. Publish canonical desktop Electron template source in the wrapper repo.
  - Add the active Electron desktop template/source currently required for generated desktop artifacts under a wrapper-owned canonical path.
  - Include required Electron package/main/preload/native companion host files.
  - Add tests that fail if the Electron template is missing or placeholder-only.
  - _Requirements: 2.2, 2.3, 2.4, 3.1, 5.3, 6.1_

- [ ] 5. Harden generic native service companion host ownership.
  - Ensure wrapper-owned native companion host protocol, deployment, diagnostics, and capability-state files support the desktop and Capacitor templates.
  - Keep Vibra, LRCLIB, and other addon-specific payload files sourced from addon repos only.
  - Add tests or fixtures proving wrapper source owns host glue without duplicating addon payloads.
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 5.4_

- [ ] 6. Update wrapper authority metadata and documentation to reject soft completion.
  - Update `config/wrapper-layout.json`, authority docs, and wrapper README content to identify canonical active source, shims, placeholders, and historical compatibility names.
  - Document that README-only folders, config aliases, and quarantine ledgers are not valid completion for physical source ownership.
  - Add tests that parse metadata and fail when active canonical paths point to placeholder-only folders.
  - _Requirements: 4.1, 4.2, 6.1, 6.2, 6.3, 6.4_

- [ ] 7. Add parity-oriented wrapper source proof for builder consumption.
  - Add a wrapper-local command or test fixture that proves canonical Android, Tauri, Electron, and native companion source roots are present for builder consumption.
  - Ensure the proof can be called by builder tests without requiring a device/emulator.
  - Ensure the proof fails if the repo regresses to the seed 03 placeholder-only state.
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 6.1, 6.2_

## Self-Review Notes

- Tasks are unchecked because the physical migration is not done.
- Each task requires code/tests or source movement, not only docs/config.
- The plan blocks placeholder-only completion by adding tests before movement.
- Builder cutover remains in the matching builder spec, while this spec publishes wrapper-owned source.
