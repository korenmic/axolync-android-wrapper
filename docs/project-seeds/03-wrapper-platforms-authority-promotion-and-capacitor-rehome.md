# Wrapper Platforms Authority Promotion And Capacitor Rehome

## Summary

Decide and implement the Android wrapper repo's role in the planned cross-platform webview wrapper authority.

The preferred path is to promote/refactor `axolync-android-wrapper` into the neutral wrapper-platforms repo, rather than copy-pasting Android/Capacitor code into a new empty repository. If repo rename or GitHub logistics make that unsafe, this repo should become a temporary migration source until parity is proven in the new wrapper authority.

## Priority

- `P0`

## Product Context

The wrapper ownership migration cannot be safely queued from builder alone. Builder can orchestrate artifact production, but it does not own the Android/Capacitor runtime shell, native companion host, app lifecycle, bridge registration, or platform diagnostics.

Today this repo is Android-named, but the product direction needs one wrapper authority that can host:

- Capacitor Android wrapper runtime
- future Capacitor iOS wrapper runtime
- Tauri desktop wrapper runtime
- Electron desktop wrapper runtime
- shared native-service-companion host concepts that wrappers consume without becoming addon-specific

The current Android implementation is valuable because it already contains real Capacitor bridge lessons from Vibra and LRCLIB. Losing history or reimplementing that code from scratch would be unnecessary risk.

## Technical Constraints

- Preserve Android buildability while the migration is in progress.
- Prefer repo rename/refactor if it preserves Git history and keeps builder consumption simple.
- If a new neutral repo is required, keep this repo as the migration source until builder can consume the new repo and Android parity is proven.
- Move Android-specific code under a wrapper-family layout, for example `wrappers/capacitor/android`, instead of leaving Android as the repository's only implied purpose.
- Add future iOS placeholders only as ownership/staging structure. Do not claim working iOS build support unless it is implemented and proven.
- Keep Capacitor native companion host code separate from Vibra or LRCLIB addon-specific payload code.
- Keep wrapper-owned host compatibility metadata complete enough for browser to distinguish unsupported, unavailable, refused, failed, and running native operator states.
- Preserve existing Vibra Android native proxy behavior.
- Preserve or improve LRCLIB Android native local bridge behavior; do not hide known Android crash risks behind migration.
- Ensure builder can consume this repo as a sibling source after the migration, the same way it consumes browser/addon repos.
- Update bootstrap/managed-repo docs wherever this repo is renamed, promoted, or superseded.

## Open Questions

Recommended answers for spec-making:

1. Prefer renaming/refactoring `axolync-android-wrapper` into `axolync-webview-wrapper-platforms` if GitHub rename logistics are acceptable.
2. If rename is not acceptable, create `axolync-webview-wrapper-platforms` as a new sibling repo and treat `axolync-android-wrapper` as a temporary migration source.
3. Rehome Android under `wrappers/capacitor/android` and keep future `wrappers/capacitor/ios` as a documented placeholder only.
4. Keep wrapper shared bridge concepts in a shared wrapper package, but keep addon-owned native payload descriptors and assets in addon repos.
5. Do not enqueue builder's wrapper consolidation tasks until this seed has a spec/tasks list and the browser migration guardrail seed also has a spec/tasks list.
