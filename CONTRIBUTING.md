# Contributing to TIDE Fit

Thanks for helping. A few guidelines keep the project consistent.

## Ground rules
- **Privacy first.** No analytics, no trackers, no network calls. The app has no INTERNET permission and a change that needs one will not be accepted.
- **The user approves changes.** Anything that alters user data or targets must be previewed and confirmed.
- **Honest data.** Missing data is shown as missing, never as zero. Estimates are labelled as estimates.

## Getting started
1. Fork and clone the repo.
2. Use JDK 17 and a recent Android Studio.
3. Run `./gradlew testDebugUnitTest` and `./gradlew assembleDebug`.

Read [DEVELOPMENT.md](DEVELOPMENT.md) for the architecture: UI in `feature/`, pure logic in `domain/calc`, repositories behind interfaces in `domain/repository`, Room implementations in `data/`.

## Making a change
- Open an issue first for anything bigger than a small fix.
- Keep logic out of composables; put it in `domain/calc` and add a unit test.
- Database changes need a Room migration (never destructive) and the updated schema JSON in `app/schemas`.
- Match the existing style and design tokens in `core/designsystem`.

## Commits and pull requests
- Commit messages start with a prefix: `feat:`, `fix:`, `chore:`, `docs:`, `refactor:` or `test:`.
- Keep pull requests focused, describe what you changed and how you tested it, and include a screenshot for UI changes.
- Tests must pass.

## Reporting bugs
Use the issue templates. For widget problems include your launcher, Android version and a screenshot. For security issues, see [SECURITY.md](SECURITY.md) and do not open a public issue.
