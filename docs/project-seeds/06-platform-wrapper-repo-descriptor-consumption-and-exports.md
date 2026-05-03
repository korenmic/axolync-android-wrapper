# Platform Wrapper Repo Descriptor Consumption And Exports

## Summary

Add `axolync.repo.toml` descriptor support to `axolync-platform-wrapper` and expose wrapper-owned topology/exports through the shared `axolync-contract` descriptor model.

This is the wrapper-side split of builder Seed 53. The wrapper repo is both consumed by builder and a consumer of browser/contracts/wrapper-relevant exports.

## Priority

- `P1`

## Status

- `draft`
- `todo`

## Product Context

`axolync-platform-wrapper` now owns wrapper family logic that used to be scattered across builder and android-wrapper. It is a mixed repo:

- builder consumes it as the source of wrapper hosts/templates/runtime code
- it consumes browser and contracts
- it may consume wrapper-relevant addon/native exports when packaging wrapper runtime companions

The descriptor must make that mixed role explicit without moving browser logic or addon-specific truth into the wrapper repo.

## Technical Constraints

- Consume descriptor schema/parser/validator from `axolync-contract`.
- Update old contract repo references to `axolync-contract` where they exist.
- Add an `axolync.repo.toml` descriptor for `axolync-platform-wrapper`.
- Use `repo.roles = ["consumer", "consumable"]`.
- Expose wrapper-owned topology and template/native-service-companion outputs through export blocks.
- Expose wrapper topology through a dedicated optional export block such as `exports.wrapper_topology` rather than only a generic inventory entry.
- Keep the accepted topology under `wrappers/<type>/<wrapper_name>/...`.
- Do not teach browser wrapper topology.
- Do not move addon-specific native payload truth into the wrapper repo as role-bearing repo identity.
- Generated wrapper workspaces/templates/native companion payloads are exports, not consumed repos.
- Preserve artifact generation behavior consumed by builder.

## Completion Criteria

1. `axolync-platform-wrapper` has a valid `axolync.repo.toml`.
2. The descriptor declares the wrapper repo as both consumer and consumable.
3. Wrapper topology exports identify active wrapper families without relying on builder-owned path guesses.
4. Builder can consume wrapper exports through the descriptor-backed aggregate registry.
5. Existing wrapper artifact generation still works.
6. Browser remains wrapper-agnostic.
7. Contract references use `axolync-contract`.

## Open Questions

Recommended answers for spec-making:

1. Wrapper-specific source truth belongs in `axolync-platform-wrapper`; builder only consumes it.
2. Wrapper topology should be exported through a dedicated optional block such as `exports.wrapper_topology`.
3. Generated wrapper outputs should be exports, not consumed repos.
4. Any temporary compatibility path must be visible as cleanup debt.
