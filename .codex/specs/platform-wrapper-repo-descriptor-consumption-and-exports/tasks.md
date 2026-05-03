# Implementation Plan

- [x] 1. Add platform-wrapper descriptor with mixed roles and required dependencies.
  - Create `axolync.repo.toml` with `repo.roles = ["consumer", "consumable"]`.
  - Declare required consumption of `axolync-contract` and `axolync-browser`.
  - Update stale `axolync-plugins-contract` references.
  - Add descriptor validation tests.
  - _Requirements: 1.1, 3.1, 3.2, 3.4_

- [ ] 2. Implement dedicated wrapper topology export.
  - Add `exports.wrapper_topology` or descriptor-referenced topology config.
  - Include desktop Tauri, desktop Electron, and mobile Capacitor canonical paths.
  - Add tests proving paths follow `wrappers/<type>/<wrapper_name>/...`.
  - _Requirements: 1.2, 1.3, 1.4_

- [ ] 3. Model generated wrapper outputs as descriptor exports.
  - Declare workspace templates, native-service-companion outputs, and wrapper-generated payloads as exports.
  - Add tests proving generated outputs are not consumed repos.
  - _Requirements: 2.1, 2.2, 2.3_

- [ ] 4. Add browser-agnostic guardrails for wrapper descriptor work.
  - Add tests or scans proving browser does not read `exports.wrapper_topology` or wrapper-specific platform fields.
  - Ensure platform-wrapper consumption of browser does not require browser to know wrapper internals.
  - _Requirements: 4.1, 4.2, 4.3_
