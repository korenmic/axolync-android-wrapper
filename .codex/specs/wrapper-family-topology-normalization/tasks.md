# Implementation Plan

- [x] 1. Add failing topology and identity guardrail tests.
  - Add or extend wrapper-local tests that parse wrapper layout metadata and assert active roots live only under `wrappers/mobile/*` or `wrappers/desktop/*`.
  - Assert active Tauri and Electron source cannot be canonical under top-level `templates/desktop/*`.
  - Assert `wrappers/mobile/capacitor/android`, `wrappers/desktop/tauri`, and `wrappers/desktop/electron` exist with real source markers.
  - Assert repo identity docs/config name `axolync-platform-wrapper` as final authority and treat `axolync-android-wrapper` only as transitional.
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 4.1, 4.3, 4.4, 5.5, 6.1, 6.2, 6.3_

- [x] 2. Move Capacitor source into the typed mobile wrapper topology.
  - Move active `wrappers/capacitor/android` source to `wrappers/mobile/capacitor/android`.
  - Move or create shared and iOS placeholder paths under `wrappers/mobile/capacitor/shared` and `wrappers/mobile/capacitor/ios`.
  - Convert any old `wrappers/capacitor/*` path to a temporary compatibility shim or remove it if builder no longer needs it.
  - Update tests and metadata so only the typed mobile path is canonical.
  - _Requirements: 1.1, 2.1, 2.2, 2.3, 2.4, 4.1, 4.2, 6.1_

- [x] 3. Move Tauri source into the typed desktop wrapper topology.
  - Move active Tauri source from top-level `templates/desktop/tauri` into `wrappers/desktop/tauri/workspace-template`.
  - Preserve native service companion host integration points under the Tauri wrapper root or a clearly named nested role folder.
  - Remove or quarantine the old top-level Tauri template path so it cannot pass as active source.
  - Update tests and metadata so only `wrappers/desktop/tauri/...` is canonical.
  - _Requirements: 1.2, 1.3, 1.4, 3.1, 3.3, 3.4, 4.1, 4.3, 6.1, 6.2_

- [x] 4. Move Electron source into the typed desktop wrapper topology.
  - Move active Electron source from top-level `templates/desktop/electron` into `wrappers/desktop/electron/workspace-template`.
  - Preserve Electron native companion host integration points under the Electron wrapper root or a clearly named nested role folder.
  - Remove or quarantine the old top-level Electron template path so it cannot pass as active source.
  - Update tests and metadata so only `wrappers/desktop/electron/...` is canonical.
  - _Requirements: 1.2, 1.3, 1.4, 3.2, 3.3, 3.4, 4.1, 4.3, 6.1, 6.2_

- [x] 5. Update wrapper layout metadata and docs for typed topology.
  - Rewrite `config/wrapper-layout.json` to describe typed mobile and desktop wrapper families as canonical active source.
  - Clearly separate compatibility aliases from canonical source if any old paths remain for one migration window.
  - Document that `workspace-template` is tracked wrapper-owned source copied into generated workspaces, not an untracked output folder.
  - Document that top-level `templates/` is not an active wrapper ownership domain.
  - _Requirements: 3.3, 4.1, 4.2, 4.3, 4.4, 6.4, 8.1, 8.2, 8.3_

- [ ] 6. Perform and verify repo identity cutover to `axolync-platform-wrapper`.
  - Update wrapper repo docs and bootstrap references owned by this repo to use `axolync-platform-wrapper` as the final repo identity.
  - Add a small verification script or documented command that checks the expected final Git remote URL and local folder identity after rename.
  - Rename the GitHub repository from `axolync-android-wrapper` to `axolync-platform-wrapper` when credentials/tooling allow it, preserving history instead of creating a new unrelated repo.
  - Rename the local checkout folder from `axolync-android-wrapper` to `axolync-platform-wrapper` and update local `origin` to the renamed GitHub URL.
  - If the executing environment cannot perform the GitHub rename directly, leave the exact required command/remediation in the task output and keep the task unchecked.
  - Track any remaining `axolync-android-wrapper` references as compatibility aliases only.
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 6.3_

- [ ] 7. Preserve defaults override behavior across topology migration.
  - Update any wrapper default-loading paths affected by the move without changing priority order.
  - Add or update tests proving builder overrides stay above wrapper TOML, wrapper constants, browser config defaults, and browser runtime defaults.
  - Ensure wrapper topology metadata does not force browser or addons to learn typed wrapper paths.
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 8.1, 8.2, 8.3, 8.4_

## Self-Review Notes

- The tasks are intentionally stricter than the previous seed: active source under top-level `templates/desktop/*` cannot pass.
- The repo rename/local folder/origin scope is included here because repo identity is part of completing wrapper authority.
- Browser and addon repos are explicitly excluded except for existing generic contract bugs, preventing topology leakage.
