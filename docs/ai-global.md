# AI Global Guide (Planner / Supervisor Scope)

Canonical orchestration guide for this repository.

If you are an AI agent contributing here, read this file first.

## Scope
- Planning, sequencing, validation strategy, commit governance, doc sync.
- For task-level coding details also read `docs/ai-per-task.md`.
- For machine-specific commands and quirks read `docs/ai-local-system.md`.

## Non-Negotiable Task Tracking Policy
1. Task checkbox updates must be in the same commit proving completion.
2. Do not mark tasks complete in unrelated cleanup commits.
3. If historical correction is needed, use dedicated correction commit and explain.
4. Add new work as unchecked task before implementation starts.

## Workflow Guardrails
- Use focused scope per task.
- Keep commit bodies contextual (task/subtask + checks run).
- Prefer `git revert` for undoing prior shared commits.
- Avoid force-push in normal flow.

## Planner-Implementor Flow
1. Select unchecked task scope.
2. Implement code/tests for that scope only.
3. Review and run fast local validations.
4. If CI exists for this repo, use it; otherwise run required local checks.
5. Mark task complete only after validations pass.

## Documentation Sync Rules
- If runtime architecture changes, update README/spec docs in same change set.
- Move one-off handoff/status artifacts under `deprecated/` instead of treating them as current authority.
- Keep AI docs current when workflow changes.
