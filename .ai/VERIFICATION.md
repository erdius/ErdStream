# Verification — BUG-003

Status: `VERIFICATION PASSED: READY TO COMMIT`

Codex owns execution evidence here; Claude reviews it against the repo and task contract.

## Feasibility Review

Read `AGENTS.md`, `BUG_HUNT.md`, `PROJECT.md`, `ARCHITECTURE.md`, and
`CURRENT_TASK.md`; independently inspected the Activity connection effect,
ViewModel job ownership, and Gradle dependencies/test layout before editing.
The contract is feasible with existing APIs — no dependency or architecture
changes needed.

Source proof before editing: the connection-owning `onDispose` released the
future and cleared `mediaController` without cancelling `monitorJob`. The
monitor captures its controller in `viewModelScope`, and its only
cancellation sites were the start of replacement monitoring and `onCleared`.
Disposal with a surviving ViewModel (a configuration change, where
`AndroidViewModel` is retained) therefore leaves the loop alive until
replacement or destruction. No released-controller exception was claimed;
device reproduction was unavailable in Codex's environment.

Clarification recorded by Codex: process death destroys the coroutine too;
the stale-job case is specifically in-process disposal with a *surviving*
ViewModel — most reliably a configuration change or `ActivityScenario.recreate`,
not "Don't keep activities" (which does not reliably exercise retained
ViewModel ownership the same way).

## Files Changed

- `app/src/main/java/com/erdman/erdstream/ErdStreamViewModel.kt`: added
  `fun stopPlaybackMonitoring() { monitorJob?.cancel() }`.
- `app/src/main/java/com/erdman/erdstream/MainActivity.kt`: the
  connection-owning `DisposableEffect(Unit)`'s `onDispose` now calls
  `viewModel.stopPlaybackMonitoring()` **before** `MediaController.releaseFuture(...)`
  and `mediaController = null` — monitoring stops before the controller
  becomes invalid, not after.
- Pre-existing dirty `.ai/BUG_BACKLOG.md`, `.ai/CURRENT_TASK.md`, and
  `PlaybackService.kt` (unrelated ICY/AVRCP WIP) were inspected but left
  untouched.

## Deviations From Contract
`NONE` — matches the contract's suggested shape exactly (a minimal public
`stopPlaybackMonitoring()`, called from `onDispose`).

## Commands Executed

Codex (blocked by its own exec sandbox):
1. `JAVA_HOME=.../openjdk@17/... ./gradlew assembleDebug testDebugUnitTest lintDebug` —
   exit 1 before tasks: `FileNotFoundException` opening the Gradle wrapper
   `zip.lck`, "Operation not permitted".
2. Same with `GRADLE_USER_HOME` redirected to a scratch dir, invoking the
   cached Gradle binary directly with `--offline --no-daemon` — exit 1 before
   tasks: `FileLockContentionHandler` socket creation failed,
   `java.net.SocketException: Operation not permitted`.
3. `adb devices -l` — exit 1: daemon could not install the smartsocket
   listener, "Operation not permitted"; no emulator binary present either.
4. `git diff --check` — exit 0, no whitespace errors.
5. `git diff --stat` / `git diff -- .../PlaybackService.kt` — confirmed scope,
   confirmed the unrelated WIP was untouched.
6. Inline `python3` source assertions against `git show HEAD:<path>` —
   confirmed the baseline lacked a stop call, the new `stopPlaybackMonitoring`
   is null-safe/idempotent, the call precedes `releaseFuture`, and polling/
   `onCleared` text is otherwise byte-identical to `HEAD`. Static source
   checks only, not a runtime/coroutine test.

Claude (re-run directly from the host shell, unsandboxed — same pattern as
BUG-001/BUG-002, since Codex's own exec sandbox blocks Gradle/ADB sockets in
this environment):
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug testDebugUnitTest` →
  **BUILD SUCCESSFUL**. `testDebugUnitTest` ran the existing 4
  `PlaylistRemovalQueueTest` cases (no new tests for this bug, per the
  contract's allowance given no practical JVM-testable slice for this
  coroutine-lifecycle change).
- `JAVA_HOME=... ./gradlew lintDebug` → still fails on exactly the same
  single pre-existing, unrelated error as BUG-001/BUG-002
  (`MainActivity.kt:91`, `UnsafeOptInUsageError` on
  `application as ErdStreamApplication`). No new lint error introduced.
- `adb devices -l` (started the ADB daemon successfully from the host shell)
  → zero attached devices/emulators. Device/instrumentation verification of
  the actual recreation scenario remains not possible in this environment.

## Build / Test / Lint Results

`assembleDebug` + `testDebugUnitTest`: **BUILD SUCCESSFUL**, 4/4 pre-existing
tests pass (none targeted this change directly — see Remaining Risks).
`lintDebug`: fails only on the pre-existing, unrelated `MainActivity.kt:91`
error already documented for BUG-001/BUG-002 — not a regression.

## Emulator / Device Results

Not possible in either Codex's or Claude's execution environment — no
attached device/emulator (`adb devices -l` returns empty in both). The
contract's recreation-based repro/verification (Developer Options
"Don't keep activities" is explicitly noted by Codex as *not* reliably
exercising this bug — a real configuration change or
`ActivityScenario.recreate` is needed) remains outstanding and would need to
be run on a physical device/emulator by the user.

## Playback / Lifecycle Results

Source-level reasoning (Codex, cross-checked by Claude): Compose effect
disposal and the main-thread monitoring loop cannot interleave mid-iteration
— the loop only suspends at its `delay(...)` call, and `Job.cancel()` is
safe to call from any thread. Disposal order is: `stopPlaybackMonitoring()`
runs first (cancelling the old job), *then* `releaseFuture`/`mediaController = null`
— so there is no window where the old job could still be polling a
half-released controller after disposal starts. A later, successful
reconnection still calls `startPlaybackMonitoring()` unconditionally, which
starts a fresh job regardless of what `stopPlaybackMonitoring()` already did
— no interleaving between "stop old" and "start new" can leave monitoring
permanently off. `onCleared()`'s existing `monitorJob?.cancel()` is untouched
and still correct for true ViewModel destruction.

## Remaining Risks

No automated regression test exists for this exact scenario (retained
ViewModel + Activity recreation); this requires Android
instrumentation/Robolectric, which the project does not have configured, and
adding that scaffolding was correctly treated as out of scope (Non-Goals).
Device verification of the actual recreation scenario, notification/audio
focus continuity, and API 28 behavior remain unverified in this environment,
same limitation as BUG-001 and BUG-002.

## Claude Adversarial Review

Read the full diff directly (`ErdStreamViewModel.kt` +4 lines,
`MainActivity.kt` +1 line) — not just Codex's summary — and re-read
`startPlaybackMonitoring`, `onCleared`, and the connection `DisposableEffect`
in context.

Root cause fixed, not masked: the fix adds the missing *direct* cancellation
signal at the exact point of disposal, rather than continuing to rely on a
future reconnection to indirectly clean up (which was the actual defect —
"only ever cancelled as a side effect of a new connection succeeding").

Three adversarial scenarios constructed:
1. **Disposal with no monitoring ever started.** If the async
   `MediaController.Builder(...).buildAsync()` never resolves before the user
   backs out (e.g. immediately navigating away), `monitorJob` is still `null`
   when `onDispose` runs. `monitorJob?.cancel()` on a `null` receiver is a
   Kotlin no-op — confirmed by reading the exact line added; no `!!` or
   unsafe call was introduced. Matches Acceptance Criteria #4.
2. **Rapid recreation loop (dispose → reconnect → dispose again, fast).**
   Each `onDispose` cancels whatever `monitorJob` currently exists at that
   moment; each subsequent successful `LaunchedEffect(mediaController)` call
   unconditionally starts a fresh job. Since `startPlaybackMonitoring()`'s own
   `monitorJob?.cancel()` (pre-existing) still runs first thing, even if two
   consecutive starts somehow raced, the second start would still cancel
   whatever the first had set — no orphaned job can survive two starts in a
   row. No scenario found where a live controller ends up with zero monitoring
   or two simultaneous monitors.
3. **`onCleared()` racing `stopPlaybackMonitoring()`.** If the Activity is
   truly finishing (not recreating) at the same moment the connection effect
   disposes, both `onDispose`'s `stopPlaybackMonitoring()` and the ViewModel's
   own `onCleared()` call `monitorJob?.cancel()`. Cancelling an
   already-cancelled or already-completed `Job` a second time is a documented
   no-op in kotlinx.coroutines (idempotent), so this ordering is harmless
   regardless of which fires first.

No simpler implementation identified — a 4-line ViewModel addition plus a
1-line call site is already the minimal fix; there is no smaller change that
satisfies "cancel directly at disposal" without adding the new method.

Missing test: none, beyond what's already disclosed above — a true
regression test needs Android instrumentation infrastructure this project
doesn't have, and adding it was correctly out of scope per this task's
Non-Goals. Re-ran the build independently: `assembleDebug` +
`testDebugUnitTest` **BUILD SUCCESSFUL**; `lintDebug` shows only the one
pre-existing, unrelated error already tracked since BUG-001. No remaining
Critical/High defect found.

## Final Status
VERIFICATION PASSED: READY TO COMMIT
