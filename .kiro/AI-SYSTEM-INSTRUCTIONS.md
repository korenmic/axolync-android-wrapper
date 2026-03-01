# AI System Instructions - Development Environment

## System Architecture

This project runs on a **Steam Deck** (Arch Linux host) with development work isolated in an **Ubuntu container** called `devbox` using **distrobox**.

## Command Execution Rules

### Host System (Arch Linux - Steam Deck)
**ALLOWED:**
- File read/write operations
- Git commands (commit, status, add, diff, log, etc.)
- Simple file system operations (ls, cat, find, etc.)
- Non-interactive commands only

**FORBIDDEN:**
- Build commands (gradlew, npm, cargo, etc.) - use container instead
- Test execution - use container instead
- Package installation (pacman, yay, etc.)
- Any critical/heavy compilation or execution tasks
- Interactive commands that require user input (unless auto-answered with pipes/flags)

### Dev Container (Ubuntu - distrobox)
**REQUIRED FOR:**
- All Gradle/Android build commands
- Test execution
- Any compilation or build tasks

**ALLOWED:**
- Using pre-installed tools (Java, Gradle, Android SDK, etc.)
- Running builds and tests with already-installed dependencies

**FORBIDDEN:**
- Installing new packages (apt, snap, etc.) - tools must already be installed
- Interactive commands that block without user input (unless auto-answered)

**IMPORTANT:** You can USE tools that are already installed in the container, but you cannot INSTALL new tools.

## Interactive Commands

**CRITICAL RULE:** Never run interactive commands that require user input unless you can auto-answer them with pipes, flags, or input redirection.

**Examples of FORBIDDEN interactive commands:**
```bash
# These will block and wait for user input - DO NOT USE
apt install package-name          # Asks for confirmation
npm init                          # Asks questions
git add -p                        # Interactive staging - FORBIDDEN
git add --patch                   # Interactive staging - FORBIDDEN
git rebase -i                     # Interactive rebase - FORBIDDEN
```

**How to handle interactive commands:**
```bash
# Use flags to auto-answer
apt install -y package-name       # -y auto-confirms
npm init -y                       # -y uses defaults
git add file.txt                  # Non-interactive add

# Or check if already configured
git config user.name || echo "Not set"  # Check first, don't prompt
```

**CRITICAL - Git Interactive Commands:**
- **NEVER use `git add -p` or `git add --patch`** - these are interactive
- **NEVER use `git rebase -i`** - this is interactive
- **ALWAYS use `git add <file>` or `git add -A`** - these are non-interactive
- **If you need to stage specific changes, use `git add <specific-files>`**

**If a command becomes interactive unexpectedly:**
1. **Timeout detection:** If a command runs for >10 minutes with no output, assume it's waiting for input
2. **Self-diagnose:** Check if the command has interactive flags
3. **Abort and retry:** Use non-interactive alternatives
4. **Document the issue:** Note what went wrong for future reference

**Example of proper timeout handling:**
```bash
# If this hangs for >10 minutes, it's likely interactive
git add -p file.txt

# Abort mentally and use:
git add file.txt  # Non-interactive alternative
```

## How to Execute Commands in Container

Use the `distrobox enter` command to run one-liners in the dev container:

```bash
distrobox enter dev -- <command>
```

### Examples:

```bash
# Build Android debug APK
distrobox enter devbox -- ./gradlew assembleDebug

# Run tests
distrobox enter devbox -- ./gradlew test

# Run specific test
distrobox enter devbox -- ./gradlew test --tests "com.axolync.android.ExampleTest"

# Clean build
distrobox enter devbox -- ./gradlew clean build
```

**Note:** Container name is `devbox`, not `dev`.

## Git Configuration Rules

### CRITICAL - DO NOT MODIFY GLOBAL GIT CONFIG

**NEVER** run these commands:
```bash
git config --global user.name "..."    # FORBIDDEN
git config --global user.email "..."   # FORBIDDEN
```

**WHY:** Global git config affects the user's entire system and all repositories. This is the user's personal computer.

**WHAT TO USE:** The repository already has local git configuration set up. Just use git commands normally - they will use the existing config.

### Allowed Git Operations

```bash
# These are fine - they use existing config
git add <files>
git commit -m "message"
git status
git log
git diff
# etc.
```

## Git Commit Policy for Spec Tasks

**CRITICAL WORKFLOW:** When implementing tasks from a spec (tasks.md), each task completion MUST be committed separately with the following rules:

### Per-Task Commit Structure

1. **One commit per task** (or sub-task if specified)
2. **Each commit MUST include:**
   - The task checkbox update in tasks.md (marking that specific task as `[x]`)
   - All implementation files related to that task
   - Any test files for that task

3. **Commit message format:**
   ```
   feat: task X.Y - brief description
   
   - Detailed point 1
   - Detailed point 2
   - Requirements: X.Y, Z.A
   ```

### Example Workflow

For task 7.1 "Create NativeBridge class":
```bash
# 1. Implement the code
# 2. Update tasks.md to mark ONLY task 7.1 as [x]
# 3. Stage both files
git add app/src/main/kotlin/.../NativeBridge.kt
git add .kiro/specs/feature-name/tasks.md
# 4. Commit with descriptive message
git commit -m "feat: task 7.1 - create NativeBridge class

- Implement @JavascriptInterface methods
- Add JSON response handling
- Requirements: 3.3, 3.4"
```

### Why This Matters

- **Traceability:** `git log --follow tasks.md` shows exactly which tasks were completed in which commits
- **Atomic changes:** Each commit represents one complete task
- **Easy rollback:** Can revert individual tasks without affecting others
- **Clear history:** Each commit tells a story of one specific feature/task

### What NOT To Do

❌ **DON'T** commit multiple tasks together
❌ **DON'T** commit implementation without updating tasks.md
❌ **DON'T** update tasks.md without committing the implementation
❌ **DON'T** mark tasks as complete in tasks.md before implementing them

### Checkpoint Tasks

Checkpoint tasks (e.g., "Ensure all tests pass") should be committed after running tests successfully, including:
- The tasks.md update marking the checkpoint complete
- Any test output or test file changes
- Any fixes made to pass the tests

## Repository Boundaries

### This Repository (axolync-android-wrapper)
- **Full access:** Read, write, commit, modify
- **Purpose:** Active development workspace

### Other Repositories
- **Read-only access:** Can read files for reference
- **NO modifications:** Do not change, commit, or execute anything
- **NO git operations:** Do not commit, push, or modify git state

## Java/JDK

Java is installed in the `devbox` container, not on the host. Always use the container for Java-dependent operations.

## Android SDK

**Status:** ✅ Installed in the `devbox` container

**Location:** `/opt/android-sdk`

**Configuration:**
- `ANDROID_SDK_ROOT=/opt/android-sdk` (set in `/etc/profile.d/android-sdk.sh`)
- `PATH` includes `cmdline-tools/latest/bin` and `platform-tools`
- Project `local.properties` contains `sdk.dir=/opt/android-sdk`
- All required packages installed and licenses accepted

**Ready for:**
- Building Android applications
- Running Android unit tests
- Running Android instrumented tests

## Summary

| Operation Type | Host | Container |
|----------------|------|-----------|
| File I/O | ✅ | ✅ |
| Git commands | ✅ | ✅ |
| Gradle/builds | ❌ | ✅ (if installed) |
| Tests | ❌ | ✅ (if installed) |
| Package install | ❌ | ❌ |
| Global git config | ❌ | ❌ |
| Interactive commands | ❌ | ❌ |

**Remember:** 
- When in doubt, use the container for anything that compiles, builds, or executes code
- Always use non-interactive flags for commands
- Never install packages - only use what's already installed
- Each task gets its own commit with tasks.md update included
