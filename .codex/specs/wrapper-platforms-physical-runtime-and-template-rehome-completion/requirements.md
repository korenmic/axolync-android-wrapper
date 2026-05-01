# Requirements Document

## Introduction

This spec finishes the wrapper repo side of the ownership migration that the earlier wrapper authority spec only prepared. The target identity remains `axolync-platform-wrapper`, and the intended migration path is a physical rename/refactor of the same authority rather than a permanent new sibling repo.

The previous work added authority metadata, compatibility descriptors, placeholder folders, and native companion scaffolding. That is not enough. Completion here requires the wrapper authority repo to physically own the active Capacitor Android runtime source and the active Tauri/Electron desktop wrapper template source that builder will consume.

## Resolved Questions

1. Keep the target identity as `axolync-platform-wrapper`.
2. Treat this as a physical rename/refactor of the same wrapper authority, not a new permanent sibling repo.
3. Move active Android runtime source under `wrappers/capacitor/android`; allow only thin root shims if tooling requires them.
4. Publish desktop templates in builder-consumable canonical paths, including `templates/desktop/tauri` and `templates/desktop/electron`.
5. Keep iOS placeholder-only unless a separate iOS implementation seed proves runnable support.
6. Require tests that distinguish real source rehome from placeholder/quarantine-only completion.

## Requirements

### Requirement 1

**User Story:** As a wrapper maintainer, I want the Android/Capacitor runtime physically rehomed under the neutral wrapper layout, so Android is no longer the repo's implied top-level identity.

#### Acceptance Criteria

1. WHEN this migration is complete THEN `wrappers/capacitor/android` SHALL contain the canonical Android/Capacitor runtime project source, not only README placeholders.
2. IF Android tooling requires root-level files THEN those files SHALL be thin documented shims that delegate to the canonical `wrappers/capacitor/android` source.
3. WHEN Android runtime files are moved THEN Gradle, Capacitor, asset staging, signing, and build scripts SHALL resolve through the canonical wrapper-family layout.
4. WHEN an implementation only adds folders, config aliases, or documentation THEN it SHALL NOT satisfy this requirement.

### Requirement 2

**User Story:** As a desktop wrapper maintainer, I want Tauri and Electron templates physically owned by the wrapper authority, so builder no longer owns desktop wrapper runtime source.

#### Acceptance Criteria

1. WHEN this migration is complete THEN the wrapper repo SHALL contain canonical Tauri template/source files at a builder-consumable path.
2. WHEN this migration is complete THEN the wrapper repo SHALL contain canonical Electron template/source files at a builder-consumable path.
3. WHEN desktop templates are added THEN they SHALL include the native service companion host glue required by generated desktop artifacts.
4. WHEN a template exists only in builder THEN that SHALL be treated as an unfinished migration state.

### Requirement 3

**User Story:** As a native companion owner, I want generic host glue to live with wrapper templates while addon payloads stay in addon repos, so ownership is not muddled by the rehome.

#### Acceptance Criteria

1. WHEN wrapper templates need native companion host glue THEN that generic host code SHALL be present in wrapper-owned source.
2. WHEN addon-specific payloads or descriptors are needed THEN their source truth SHALL remain in addon repos.
3. WHEN Vibra or LRCLIB operator support is represented THEN wrapper source SHALL only own host loading, deployment, status, and diagnostics behavior.
4. WHEN native companion source is moved THEN compressed addon payloads SHALL NOT be duplicated into unrelated wrapper roots.

### Requirement 4

**User Story:** As a future iOS owner, I want iOS represented only as a placeholder until real support exists, so the repo shape is ready without pretending support is complete.

#### Acceptance Criteria

1. WHEN `wrappers/capacitor/ios` exists THEN it SHALL be documented as placeholder-only unless runnable iOS support is implemented.
2. WHEN tests validate wrapper layout THEN iOS placeholder files SHALL NOT be counted as working iOS artifact proof.
3. WHEN future iOS work starts THEN it SHALL be able to build on the canonical Capacitor family layout.

### Requirement 5

**User Story:** As a regression owner, I want tests that fail on placeholder-only completion, so agents cannot mark the migration complete without moving real source.

#### Acceptance Criteria

1. WHEN `wrappers/capacitor/android` contains only README/config placeholders THEN tests SHALL fail.
2. WHEN wrapper-owned Tauri templates are missing THEN tests SHALL fail.
3. WHEN wrapper-owned Electron templates are missing THEN tests SHALL fail.
4. WHEN native companion host glue needed by templates is missing THEN tests SHALL fail.
5. WHEN root Android files contain active runtime source instead of thin shims after migration THEN tests SHALL fail or flag the gap.

### Requirement 6

**User Story:** As a builder integrator, I want the wrapper repo to publish deterministic source paths, so builder can consume wrapper-owned source without heuristics or fallback.

#### Acceptance Criteria

1. WHEN builder resolves desktop templates THEN the wrapper repo SHALL expose stable canonical paths for Tauri and Electron.
2. WHEN builder resolves Android wrapper source THEN the wrapper repo SHALL expose a stable canonical Capacitor Android path.
3. WHEN wrapper source metadata is published THEN it SHALL distinguish canonical active source from shims, placeholders, and historical compatibility paths.
4. WHEN compatibility paths remain THEN they SHALL include removal criteria and SHALL NOT be described as final runtime ownership.

## Self-Review Notes

- Requirements explicitly reject placeholder-only, alias-only, quarantine-only, and documentation-only completion.
- Requirements keep the target identity already hardened as `axolync-platform-wrapper`.
- Requirements are wrapper-repo scoped and do not make builder own wrapper runtime source.
- Requirements preserve addon payload ownership and browser neutrality.
