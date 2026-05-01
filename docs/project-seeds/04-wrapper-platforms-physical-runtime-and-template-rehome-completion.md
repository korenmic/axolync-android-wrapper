# Wrapper Platforms Physical Runtime And Template Rehome Completion

## Summary

Finish the wrapper ownership migration that seed 03 only prepared.

Seed 03 established wrapper authority metadata, `wrappers/capacitor/...` placeholder structure, compatibility layout descriptors, and native companion host scaffolding. That was useful prerequisite work, but it did not physically complete the ownership migration. Android/Capacitor runtime source still remains rooted in the Android-shaped repo layout, and desktop Tauri/Electron wrapper templates still live in builder.

This continuation seed must complete the real migration: the wrapper authority repo must physically own the active Capacitor Android runtime layout and the active desktop Tauri/Electron wrapper templates that builder consumes. Metadata, placeholders, aliases, and quarantine ledgers are not sufficient completion criteria.

The already-hardened target repo identity remains `axolync-platform-wrapper`.

## Priority

- `P0`

## Status

- `draft`
- `todo`
- `needs-spec-making`

## Product Context

The intended end state is one neutral wrapper authority repo:

- `axolync-platform-wrapper` owns webview wrapper runtime source.
- Capacitor Android is one child wrapper family, not the repo identity.
- Capacitor iOS is represented as ownership structure only until working iOS support exists.
- Tauri and Electron desktop wrapper templates/source live in the wrapper authority, not builder.
- Builder consumes wrapper source; it does not invent or own wrapper runtime source.
- Browser remains platform-neutral and should not need wrapper repo identity knowledge.
- Addon repos continue to own addon-specific native payloads and descriptors.

The failure mode this seed must prevent is "soft completion": marking a migration done because docs, config, fallback aliases, or placeholders exist while active runtime source still lives in the old owner.

## Technical Constraints

- This seed continues seed 03. It must not repeat only the metadata/scaffold work already completed.
- Do not mark this seed done until active source authority has physically moved.
- Root-level repo infrastructure may remain at repo root, but active Android runtime/app source must become canonical under `wrappers/capacitor/android`.
- If Gradle, Capacitor, or Android tooling requires root-level entrypoints, root files may remain only as thin forwarding shims. They must not contain duplicated runtime logic or become a second source of truth.
- Existing Android buildability must be preserved during and after the move.
- Desktop wrapper templates must be added to the wrapper repo as active source, including at minimum:
  - Tauri desktop template/source currently consumed from builder
  - Electron desktop template/source currently consumed from builder
  - native service companion host glue required by those templates
- The wrapper repo must publish template paths that builder can consume deterministically.
- Builder fallback/quarantine is not completion for this seed. It is only a temporary bridge until builder is changed by the matching builder continuation seed.
- Do not move addon-specific Vibra, LRCLIB, or other addon payload truth into the wrapper repo.
- Do not claim iOS/macOS runnable support merely because placeholder folders exist.
- Tests must prove canonical paths contain real source and old placeholder-only completion cannot pass.
- Documentation must clearly distinguish:
  - canonical active source
  - thin tool shims, if any
  - placeholder-only future platform folders
  - obsolete compatibility aliases that the builder continuation seed must stop consuming

## Completion Criteria

This seed is not complete unless all of the following are true:

1. `wrappers/capacitor/android` contains the canonical Android/Capacitor runtime project source, not only README placeholders.
2. Any remaining root Android/Gradle/Capacitor files are documented and tested as thin shims only.
3. The wrapper repo contains canonical desktop Tauri and Electron template/source roots.
4. Native service companion host glue used by wrapper templates is wrapper-owned and not copied from builder at build time.
5. Structural tests fail if the repo regresses to placeholder-only `wrappers/capacitor/android`.
6. Structural tests fail if Tauri/Electron templates are missing from the wrapper authority.
7. No task can be checked off solely because a config alias, quarantine ledger, or README was added.

## Open Questions

Recommended answers for spec-making:

1. Keep the target identity as `axolync-platform-wrapper`.
2. Treat this as a physical rename/refactor of the same wrapper authority, not a new permanent sibling repo.
3. Move active Android runtime source under `wrappers/capacitor/android`; allow only thin root shims if tooling requires them.
4. Publish desktop templates in a builder-consumable canonical path such as `templates/desktop/tauri` and `templates/desktop/electron`, while documenting their wrapper-family ownership.
5. Keep iOS placeholder-only unless a separate iOS implementation seed proves runnable support.
6. Require tests that distinguish real source rehome from placeholder/quarantine-only completion.
