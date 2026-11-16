# TIDE Fit â€“ Development Log

Native Android (Kotlin, Jetpack Compose) fitness and body-recomposition app.
Principle: **the app owns and calculates the data; ChatGPT analyses and recommends; the user approves changes.**

## 1. Status

| Phase | Scope | Status |
|---|---|---|
| 1 | Project setup, design system, navigation, database, onboarding, profile | **Done** (builds, 25 unit tests pass; launched on an API 36 emulator; onboarding, Home, Workouts placeholder and Profile checked visually (screenshots in docs/)) |
| 2 | Dashboard, food DB, nutrition logging, recipes, water | **Done, with gaps listed in section 7** (builds, unit tests pass incl. FoodForm; food logging, Home dashboard and water checked on the emulator, screenshots `docs/p2_*.png`; recipe editor, targets editor and food editor screens compile but were not exercised on the emulator) |
| 3 | Exercises, routines, active workout, history, training analytics | **Done, with gaps listed in section 7** (Room v3 with auto-migration from v2 that kept existing data on the emulator; 25 new tests for training maths and workout repositories pass; on the emulator I started an empty workout, picked an exercise, logged a set with the rest timer, finished it and checked the summary and stats. Screenshots `docs/p3_*.png`. Routine editor, exercise library/editor, history list and workout deletion compile but were not exercised on the emulator) |
| 4 | Weight, measurements, sleep, recovery, supplements, analytics | **Done, with gaps listed in section 7** (Room v4 with auto-migration from v3 that kept existing data on the emulator; 96 unit tests pass, 16 new for body maths and the five repositories. On the emulator I opened the Progress tab, logged a weight (saved, goal progress and trend updated) and opened Supplements. Screenshots `docs/p4_*.png`. Measurements, sleep, recovery and the supplement checklist/dialogs compile but were not exercised on the emulator) |
| 5 | ChatGPT export, prompt, import, validation, preview, rollback, history | **Done for targets; suggestions are read-only (see section 5 and 7)** (Room v5; 115 unit tests pass, 19 new for the parser/validator and the coaching repository. On the emulator: Progress â†’ ChatGPT coaching opens, Copy puts the prompt + JSON export on the clipboard, Paste reads it back, and an invalid reply is rejected with a clear message and no changes. The valid-reply preview, apply and rollback screens were **not** exercised on the emulator because `adb input text` garbled the long JSON; that path is covered by repository tests only. Screenshots `docs/p5_*.png`) |
| 6 | Health Connect, notifications, backup/restore, profiling, accessibility, polish | **Built; device verification partial (see section 7)** (132 unit tests pass, 17 new. Room stays at v5. Data screen, reminder toggle with permission prompt and scheduled job checked on the emulator, screenshots `docs/p6_*.png`. backup creation, restore and an empty Health Connect import were run on the emulator; an import with real data and a firing notification were not; startup was measured but not profiled or optimised) |

### Phase 1 â€“ what actually exists
- Gradle (Kotlin DSL) + version catalog, AGP 9.5.1, Kotlin 2.4.21, KSP, Hilt, Room 2.8.5, DataStore, Compose BOM 2026.09.00.
- Design system (`core/designsystem`): colour tokens (dark-first + light + optional dynamic colour), typography, spacing/radii/sizes/motion tokens, reusable components (FitCard, ProgressRing, FitLinearProgress, MacroIndicator, StatCard, ChipGroup, OptionCard, EmptyState, LoadingState, ErrorState, EstimateBadge, buttons, text field).
- Room v1: `user_profile`, `fitness_goal`, `daily_targets` (append-only history), `weight_entry`. Schema JSON exported to `app/schemas`.
- 8-step onboarding (basics, body+units, goal, training, diet, equipment, review). The review step shows transparent estimates; **targets are only stored if the user explicitly accepts them**.
- Profile screen + full profile editor (shared form code with onboarding), theme (system/dark/light), dynamic colour toggle.
- Home shows only real data (weight, targets). Nutrition / Workouts / Progress tabs are honest "arrives in Phase N" screens; nothing is simulated.
- Type-safe Navigation Compose routes, bottom navigation with saved state, back handling in onboarding.
- Privacy: no INTERNET permission, `allowBackup=false`, data-extraction rules exclude everything from cloud backup/transfer.

## 2. Architecture

Single `:app` module, layered by package (modules would cost build time for no benefit at this size; boundaries are enforced by package + interfaces and can be split later).

```
com.artishorizon.tidefit
â”œâ”€ core/            designsystem, database (Room), datastore, di (Hilt)
â”œâ”€ domain/          model, calc (pure Kotlin: units, target estimation, later nutrition/volume/averages),
â”‚                   repository (interfaces)
â”œâ”€ data/            repository implementations (Room-backed)
â”œâ”€ feature/<name>/  ViewModel (StateFlow<UiState>) + Composables per feature
â””â”€ navigation/      routes + bottom-bar host
```
- MVVM, unidirectional data flow, immutable UI state, `collectAsStateWithLifecycle()`.
- Repositories are interfaces in `domain`, bound in `RepositoryModule` (dependency inversion; ViewModels are tested with fakes).
- Business logic is pure and lives in `domain/calc`; composables only render state.
- Multi-step writes use `withTransaction`. `Clock` is injected for testable time.
- Time: `Instant` (epoch millis) for moments; `LocalDate` (ISO text, sortable) for calendar days. Body values stored in metric; units affect only display/entry.
- Charts: planned as custom Compose `Canvas` (no third-party chart library: smaller APK, full control over honest gaps in data).

## 3. Database (Room)

Phase 1 tables are implemented. Later phases add tables with explicit migrations (never destructive).

Planned (per phase): Food, FoodEntry (with **nutrition snapshot**), Recipe, RecipeIngredient, WaterEntry (P2); Exercise, WorkoutRoutine, RoutineExercise, WorkoutSession/Exercise/Set (P3); BodyMeasurement, ProgressPhoto, SleepEntry, RecoveryEntry, Supplement(Entry), Habit(Completion) (P4); CoachingSession, CoachingRecommendation, CoachingTargetHistory, ImportedJson, ExportHistory (P5). Every table gets indices on date/foreign-key columns; imported (Health Connect) rows keep a `source` column.

## 4. Navigation
Top-level: Home, Nutrition, Workouts, Progress, Profile (bottom bar). Secondary screens (profile editor now; add-food, active workout, import preview later) push on top and hide the bar. Onboarding is shown instead of the nav host until `onboarding_done` is set.

## 5. JSON contract (implemented in `domain/coaching`)
**What is actually built.** Export (`fitness_data_export`, schema 1.0): profile, current targets, weigh-ins with 7-day average and weekly rate, per-day nutrition totals (only days with food logged) with water, finished workouts, sleep, recovery, supplement adherence and a `data_quality` block that says missing days are not zero. Import (`fitness_coaching_update`): `analysis`, nullable `updated_targets` (any subset; missing values keep the current target), `recommendations`, `workout_suggestions`, `habit_suggestions`, `coach_notes`. The prompt that tells ChatGPT the exact reply shape is `CoachingExport.PROMPT`.
**Validation:** outermost `{...}` is extracted (handles code fences and chatter); unknown fields ignored; `type` and major `schema_version` required; targets range-checked (kcal 800â€“6000, protein 20â€“400, carbs 0â€“800, fat 10â€“300, fibre 0â€“150, water 500â€“8000 ml) and rejected outside it; warnings (not errors) for macros that don't add up to the calories (>15% apart), calories < 1200, and a >25% calorie change. Text is data only: it is displayed, never interpreted. `requires_user_confirmation` is ignored because confirmation is always required.
**Flow:** paste â†’ preview (nothing written) â†’ user ticks "Apply these targets" â†’ one transaction stores the response (raw JSON + SHA-256 hash, unique, so the same response can't be imported twice) and appends new targets (source `COACHING_IMPORT`). Rollback appends the previous targets again (source recorded as `USER`) and marks the session rolled back; nothing is deleted.
**Only targets can be applied.** Recommendations, workout suggestions and habit suggestions are shown as read-only text; there is no routine/habit auto-creation.

### Original design intent (kept for reference)
`schema_version "1.0"`, `type: fitness_coaching_update`; analysis block, nullable `updated_targets`, `recommendations`, `workout_suggestions`, `habit_suggestions`, `coach_notes`, `requires_user_confirmation`. Import is strict on required fields/versions, tolerant of unknown fields, previewed, per-item accepted, applied transactionally, duplicate-detected by content hash, audited, and rollbackable. Coaching text is never interpreted as instructions. Full field documentation will be written in Phase 5.

## 6. Architectural decisions
- AGP 9 built-in Kotlin: no `kotlin-android` plugin; KSP 2.3.x and Hilt 2.60 work with it.
- `compileSdk 37` (required by current AndroidX), `targetSdk 36`, `minSdk 29`.
- Robolectric runs on API 35 (`src/test/resources/robolectric.properties`) because API 36 sandboxes need JDK 21 and the project builds on JDK 17.
- Targets are an append-only history; "current" = latest `effectiveFrom`, then id. This makes Phase 5 rollback a matter of appending/reverting rows with an audit trail.
- Calorie/macro estimate: Mifflinâ€“St Jeor Ã— activity factor, goal adjustment (âˆ’20% fat loss, âˆ’10% recomp, +10% muscle), protein 1.4â€“2.0 g/kg, fat 25% (â‰¥0.6 g/kg), 1200 kcal floor, water 35 ml/kg. Always labelled an estimate with the steps explained.

## 7. Known issues / TODO
- Walked through on the emulator (AVD `fitos_api36`): onboarding, Home, tab switching, Profile. Not yet exercised: profile editor save, lb/ft-in entry, process death. UI/instrumented tests and profiling still to do.
- Scrolling content passes behind the transparent status bar (clock overlaps text); add a scrim or top inset handling.
- Fixed: light system theme previously produced dark-on-dark text; theme now paints its own background and defaults to Dark.
- `gradlew` could not download the Gradle distribution on this machine (Java HTTPS to the CDN timed out); builds were run with a locally extracted Gradle 9.9.1. If `gradlew` fails for you, check network/proxy or use a local Gradle.
- Launcher icon is a simple placeholder vector mark.
- Dynamic colour is off by default.
- Release build has minification enabled but is untested.
- Phase 2 gaps: no Room 1â†’2 migration test (needs schema assets for MigrationTestHelper); no ViewModel tests for the new nutrition screens; recipe/food/targets editors not yet exercised on a device; the day does not roll over live at midnight (Home/Water use the day at ViewModel creation); seed foods are all labelled Estimated and the seed has no version tracking; serving label shows "28 g (28 g)" for gram-based foods; `hiltViewModel` import is deprecated (moved to `androidx.hilt.lifecycle.viewmodel.compose`).
- Phase 2 design: food nutrition is stored per 100 g; each log entry keeps an immutable snapshot, so editing/deleting a food never changes history. Water entries of kind "Other beverage" are not counted toward the water target. Water week chart shows dots (not zero bars) for days with nothing logged.
- Phase 3 design: weights are stored in kg and shown in the profile's unit. An in-progress workout is a database row (`endedAt` null), so it survives app restarts; only one can be active. Finishing drops unticked sets; a workout with no completed sets is discarded. New sets are prefilled from the last time the exercise was done. Volume and records ignore warm-ups and unfinished sets; the first time you do a lift is a baseline, not a record. Estimated 1RM (Epley) is shown only for sets of 12 reps or fewer.
- Phase 3 gaps: no migration test for v2â†’v3 (same reason as v1â†’v2); no ViewModel or UI tests for the workout screens; the rest timer is screen-bound (no notification, stops when the app is closed); "records before this workout" uses workout id order; no supersets, exercise-history charts or routine progression suggestions; rest timer and sets rows not checked with TalkBack; seed exercise library is a short generic list.
- Phase 4 design: the Progress tab is a hub of cards (weight, measurements, sleep, recovery, supplements, training) that show "No data yet" instead of zeros. The profile's current weight always mirrors the most recent weigh-in (synced in the same transaction on add/edit/delete). Weight trend is a 7-day mean; the weekly rate is a 4-week least-squares slope and is shown only with at least 4 weigh-in days spanning 10+ days. Sleep and recovery are one row per day (saving the same date replaces it); recovery is three 1â€“5 self-ratings with no composite score. Supplement adherence counts only active supplements, from the day each was added. Measurements are stored in cm (or %) and shown in inches when the height unit is ft/in.
- Phase 4 gaps: no v3â†’v4 migration test (same reason as earlier); no ViewModel/UI tests for the body screens; Home does not yet show sleep/supplement completion or a weight shortcut; progress photos, habits and combined multi-metric charts are not built; charts have no axis labels or touch inspection (only a date range and low/high caption); the date picker, sleep/recovery forms and measurement dialogs were not exercised on a device.
- Phase 5 gaps: preview/apply/rollback UI not exercised on a device (only the repository, parser and rejection path are verified); no per-item accept for recommendations (they cannot be applied); no UI/ViewModel tests; hash is over the parsed JSON text so a reply with reordered keys counts as different; rollback restores targets with source `USER` (the original source is not stored); rollback after later manual target edits still overwrites those edits (the dialog states what it will restore); export includes the profile (age, sex, height, weight) so check what you paste into ChatGPT; no JSON schema file is published; no export history (only imports are stored).
- Phase 6 design: **Reminders** (water, meals, weigh-in) are off by default and are self-renewing one-time WorkManager jobs; each run reschedules itself and only notifies if still useful (`ReminderRules`: water behind target, nothing logged, not weighed). `TideFitApp` re-applies enabled reminders on start. POST_NOTIFICATIONS is requested only when a reminder is switched on. **Health Connect** is an on-demand, read-only import of weight and sleep (last 30 days); manual entries are never overwritten (weights per date with source `HEALTH_CONNECT`, sleep only on empty dates). **Backup** is a copy of the single SQLite file (WAL checkpointed) via the system file picker; restore validates the file (SQLite header, schema not newer than the app, required tables, integrity check), keeps `pre_restore_backup.db` in private storage, then restarts the process. The app still has no INTERNET permission. Line charts expose a text summary as a content description.
- Phase 6 verified: 132 unit tests pass (new: reminder timing incl. DST, reminder rules, Health Connect mapping, backup validation, weight/sleep import rules). On the emulator the Data screen renders, enabling a reminder shows the notification permission prompt and, once allowed, a `ReminderWorker` job appears in JobScheduler; switching it off removes it. Health Connect shows its "Allow access" state. Cold start of the debug build on the emulator was about 4.3-4.8 s (logcat also reported skipped frames at launch); warm start about 0.3 s. Not profiled and not optimised.
- Phase 6 follow-up verification on the emulator: **Create backup** wrote a valid SQLite file (245 KB, correct header) to Downloads via the system picker; **Restore** of a junk file was rejected ("That file is too small to be a TIDE Fit backup.") with data untouched; **Restore** of the real backup replaced the database, kept `pre_restore_backup.db`, restarted the process and Home showed the same profile and today's nutrition/water. **Health Connect**: the permission is granted (READ_WEIGHT/READ_SLEEP) and "Import last 30 days" ran and reported 0 weigh-ins / 0 nights, because the emulator's Health Connect has no data. Screenshots `docs/p6_*.png`. The restore was a same-device round trip, so it did not prove that differing data gets replaced (unit tests cover the validator only).
- Phase 6 gaps / **not verified**: no reminder notification has actually fired (needs waiting for the set time or a time change); a Health Connect import that actually finds data was not seen (no data on the emulator, so mapping is unit-tested only); restoring a backup from a *different* data set was not run on a device; backups are not encrypted; no background Health Connect sync and only weight and sleep are read (no steps); no migration, UI or ViewModel tests; no TalkBack pass (only content descriptions on switches, time buttons and charts); no Baseline Profile, no release/minified build run, no Macrobenchmark; Home still lacks sleep/supplement shortcuts (Phase 4 gap); `hiltViewModel` import is deprecated.
- Redesign (TIDE look): near-black canvas with hairline-bordered gradient cards, lime accent, large radii (24-32 dp), 34 sp bold large titles, a floating pill tab bar whose selected item expands into a lime pill, round back buttons, filled text fields without notched labels, pill chips and buttons with spring press feedback and a light haptic tick, a lime Home hero card (calories left, macros), quick-action tiles, a status-bar scrim, and gradient area fills on line charts. Light theme follows an iOS grouped-background style. Everything is driven from `core/designsystem` (Color, Tokens, Type, components), so screens not touched individually pick the look up automatically; Home was rebuilt. Verified on the emulator: onboarding, Home, Nutrition, Workouts, Progress, Profile, Hydration, Daily targets, light theme. Not re-checked after the restyle: food logging, workout, recipe/food editors, weight/sleep/supplement screens, coaching screens, dialogs and date/time pickers. Fonts are still the system sans (no bundled typeface); no blur (Compose has no cheap backdrop blur), the tab bar is a translucent fill instead. Launcher icon is unchanged.- TODO next: all six planned phases are built. Suggested follow-ups: exercise the unverified device flows above, startup profiling (Baseline Profile, release build), TalkBack pass, instrumented tests, migration tests.

## 8. Build & test
```
set JAVA_HOME=<JDK 17>
gradlew assembleDebug testDebugUnitTest
```
