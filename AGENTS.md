# Agent Rules for Release Updates

## Required When Updating Version
- If `pluginVersion` in `gradle.properties` changes, add a matching section in `CHANGELOG.md` (for example, `## [0.1.1]`).
- Keep README install instructions aligned with the current process (`build/distributions` for source-based manual installs).

## Required Build Verification
- Rebuild plugin artifacts after version updates:
  - `./gradlew clean buildPlugin --offline`
- Verify expected distribution output exists:
  - `build/distributions/unify-file-viewer-<version>.zip`

## Repository Hygiene
- Do not commit ignored build artifacts (for example `build/` and `releases/`) unless explicitly requested.
- Stage and commit only intended source/config/docs files related to the release update.
- Include version bump and changelog update in the same change set when possible.

## Pre-Push Checklist
- `pluginVersion` in `gradle.properties` matches release notes in `CHANGELOG.md`.
- Distribution ZIP name reflects the same version.
- `README.md` manual install instructions are still accurate.
- `git status --short` shows only intended changes.

