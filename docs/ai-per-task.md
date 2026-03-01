# AI Per-Task Guide (Implementor Scope)

Use this file when implementing a specific task/subtask.

## Read First
1. `.kiro/specs/<spec>/requirements.md`
2. `.kiro/specs/<spec>/design.md`
3. `.kiro/specs/<spec>/tasks.md`
4. `docs/ai-local-system.md`

## Rules
1. Implement only assigned task scope.
2. Do not mark tasks done before implementation + validation are complete.
3. Add/adjust tests with behavior changes.
4. Minimize blast radius; avoid unrelated refactors.
5. Report commands run and outcomes.

## Validation
- Run relevant compile/tests for changed files.
- If blocked, report exactly what and why.
