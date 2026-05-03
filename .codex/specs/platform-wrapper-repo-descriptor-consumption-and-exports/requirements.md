# Requirements Document

## Introduction

This spec adds descriptor support to `axolync-platform-wrapper` so wrapper-owned topology and exports are visible through `axolync.repo.toml` validated by `axolync-contract`. The wrapper repo is both consumed by builder and a consumer of browser/contracts/wrapper-relevant exports.

## Requirements

### Requirement 1

**User Story:** As builder, I want platform-wrapper to expose its wrapper topology through descriptors, so that builder stops guessing wrapper source paths.

#### Acceptance Criteria

1. WHEN platform-wrapper adds its descriptor THEN it SHALL declare `repo.roles = ["consumer", "consumable"]`.
2. WHEN wrapper topology is exported THEN it SHALL use a dedicated optional export block such as `exports.wrapper_topology`.
3. WHEN topology paths are declared THEN they SHALL point to the canonical `wrappers/<type>/<wrapper_name>/...` shape.
4. IF active wrapper source remains only discoverable through builder-owned path guesses THEN this migration SHALL not be complete.

### Requirement 2

**User Story:** As a wrapper maintainer, I want generated wrapper outputs modeled as exports, so that workspace templates and native companion outputs do not become fake repo identities.

#### Acceptance Criteria

1. WHEN workspace templates are described THEN they SHALL be descriptor exports.
2. WHEN native-service-companion or wrapper-generated payloads are described THEN they SHALL be descriptor exports.
3. WHEN descriptor roles are validated THEN generated wrapper outputs SHALL NOT be modeled as consumed repos.

### Requirement 3

**User Story:** As a wrapper consumer, I want platform-wrapper to consume contracts and browser explicitly, so that required dependencies fail clearly.

#### Acceptance Criteria

1. WHEN platform-wrapper consumes `axolync-contract` THEN it SHALL declare it with an actionable `use` value and `required = true`.
2. WHEN platform-wrapper consumes browser runtime truth THEN it SHALL declare browser with an actionable `use` value and `required = true`.
3. WHEN optional wrapper-related exports are unavailable THEN they SHALL use `required = false` only when missing them should not block unrelated work.
4. WHEN an old `axolync-plugins-contract` reference exists THEN it SHALL be updated to `axolync-contract`.

### Requirement 4

**User Story:** As a browser maintainer, I want wrapper descriptor work to remain outside browser topology, so that browser stays wrapper-agnostic.

#### Acceptance Criteria

1. WHEN wrapper topology is implemented THEN browser SHALL NOT be required to read or understand it.
2. WHEN platform-wrapper consumes browser THEN that relationship SHALL not require browser to know platform-wrapper internals.
3. WHEN tests verify wrapper descriptor consumption THEN they SHALL include a guard that browser is not coupled to wrapper topology.
