# Current Task

Status: `READY FOR CODEX`

Claude owns this file for the active implementation contract.

## Objective

Fix BUG-003 (see `.ai/BUG_BACKLOG.md`): when the `MediaController` connection is
released (`MainActivity.kt`'s `DisposableEffect(Unit)` `onDispose` block, e.g. on
Activity recreation from a config change, or the Activity being torn down and
recreated after process death), the playback-monitoring coroutine started by
`ErdStreamViewModel.startPlaybackMonitoring()` is never told to stop. It keeps
calling methods (`playWhenReady`, `currentPosition`, `duration`, `playbackState`,
`currentMediaItem`) on the now-released `MediaController` instance it originally
captured, every 200ms-1000ms, for as long as it takes a *replacement* connection
to succeed — or forever, if the replacement connection never resolves (e.g. the
service fails to bind). Today the stale job is only ever cancelled as a side
effect of `startPlaybackMonitoring()` being called again by a *new* successful
connection (its first line is `monitorJob?.cancel()`); nothing cancels it
directly when the old controller is actually released.

## Acceptance Criteria

1. As soon as `MainActivity.kt`'s connection-owning `DisposableEffect(Unit)`
   disposes (releases `controllerFuture` and sets `mediaController = null`), the
   playback-monitoring coroutine for the controller that effect owned must stop
   — not merely "stop once a replacement connection later succeeds."
2. This must not depend on `LaunchedEffect(mediaController)` re-triggering:
   verify explicitly that the fix works even if `mediaController`'s transition
   to `null` and back to a new instance happens in a way Compose might coalesce
   or reorder relative to other recompositions — the cancellation must be
   invoked directly from the `onDispose` block itself, not inferred indirectly
   from a state change.
3. Reconnection behavior must be unchanged: when a new `MediaController`
   connects successfully after a recreation, `startPlaybackMonitoring()` must
   still run normally against the new controller (its existing
   `monitorJob?.cancel()` guard against a *concurrent* stale job is harmless to
   keep, and should remain, as defense in depth — but must no longer be the
   *only* thing that ever cancels a stale job).
4. Must not introduce a crash if disposal happens before any monitoring job was
   ever started (e.g. the very first composition's `future.addListener`
   callback never fired before the user backed out) — cancelling a `null` job
   must be a no-op, not a `NullPointerException`.
5. Must not change `ErdStreamViewModel.onCleared()`'s existing
   `monitorJob?.cancel()` behavior (true ViewModel destruction, not Activity
   recreation) — that path already works correctly and must keep working.
6. No change to `startPlaybackMonitoring()`'s polling logic itself (position/
   duration/buffering/queue-index/ICY-title/auto-advance handling) — only when
   and how the job gets cancelled.

## Non-Goals

- Do not fix any other item in `.ai/BUG_BACKLOG.md` — none remain open as of
  this task (BUG-001 and BUG-002 are `VERIFIED`).
- Do not change how `MediaController` itself is connected/reconnected (the
  `MediaController.Builder(...).buildAsync()` call, `SessionToken` construction,
  or the `future.addListener` callback) — only add the missing "stop
  monitoring" signal at disposal time.
- Do not add a general lifecycle-observer abstraction or DI/testing seam for
  `MediaController`/`ErdStreamViewModel` beyond what's needed for this one fix,
  per `.ai/ARCHITECTURE.md`'s change-discipline guidance.
- Do not touch the uncommitted, in-progress local changes already present in
  `app/src/main/java/com/erdman/erdstream/playback/PlaybackService.kt` (ICY
  radio-title propagation to the MediaSession for Bluetooth AVRCP) — unrelated
  active work-in-progress, not part of this task.

## Repository Findings

- `MainActivity.kt:286-305` — the connection-owning `DisposableEffect(Unit)`:
  - `future.addListener({ mediaController = future.get() }, ...)` sets
    `mediaController` once the async connection resolves.
  - `onDispose { controllerFuture?.let { MediaController.releaseFuture(it) }; mediaController = null }`
    releases the controller and clears the Compose state var, but does not
    touch `ErdStreamViewModel`'s `monitorJob` at all.
- `MainActivity.kt:307-309` — `LaunchedEffect(mediaController) { mediaController?.let { viewModel.startPlaybackMonitoring(it) } }`:
  re-triggers whenever `mediaController` changes (including transitioning to
  `null`), but its body is a no-op when the new value is `null` — so a
  transition to `null` doesn't call anything that would cancel the running job.
- `ErdStreamViewModel.kt:238-243` — `startPlaybackMonitoring(controller: MediaController)`:
  `monitorJob?.cancel()` runs first, then `monitorJob = viewModelScope.launch { while (true) { ...controller.playWhenReady... } }`
  — the `controller` parameter is captured by the launched coroutine and never
  re-checked against a "is this still the current connection" flag.
- `ErdStreamViewModel.kt:327-330` — `onCleared() { monitorJob?.cancel() }` — the
  only existing direct cancellation path, and it only fires on true ViewModel
  destruction (not Activity recreation, since `ErdStreamViewModel` is obtained
  via a `ViewModelProvider.Factory` tied to the Activity's `ViewModelStore`,
  which survives configuration changes).
- `ErdStreamViewModel.kt` has no public function today that only stops
  monitoring without starting a new job.

## Affected Components

- `app/src/main/java/com/erdman/erdstream/ErdStreamViewModel.kt`: add a small
  public function (e.g. `fun stopPlaybackMonitoring() { monitorJob?.cancel() }`)
  callable from outside the ViewModel to cancel monitoring without starting a
  replacement job.
- `app/src/main/java/com/erdman/erdstream/MainActivity.kt`: call that new
  function from the connection-owning `DisposableEffect(Unit)`'s `onDispose`
  block, alongside the existing `controllerFuture?.let { ... }` release and
  `mediaController = null` assignment.
- No changes expected to `PlaybackService.kt`, `data/`, or `ui/` files.

## State / Data Flow

- Today: `monitorJob` is cancelled only (a) inside `startPlaybackMonitoring()`
  itself, when a *new* connection calls it again, or (b) inside
  `onCleared()`, on true ViewModel destruction.
- Needed: add a third, direct path — the moment the `DisposableEffect` that
  owns the controller connection disposes, `monitorJob` must be cancelled
  immediately, symmetrically with how that same `onDispose` already releases
  `controllerFuture` and nulls `mediaController`.
- Suggested shape: `viewModel.stopPlaybackMonitoring()` called from
  `onDispose` right next to `mediaController = null`. Since `monitorJob` is a
  private `var` on the ViewModel, this requires exposing a minimal public
  method rather than reaching into ViewModel internals from `MainActivity.kt`.
  `startPlaybackMonitoring()`'s own `monitorJob?.cancel()` at its top can stay
  as-is (harmless, defends against a hypothetical double-start), it just must
  no longer be relied upon as the *only* stop signal.

## Interface Contracts

- New: `ErdStreamViewModel.stopPlaybackMonitoring(): Unit` — cancels the
  current `monitorJob` if one exists; safe to call when no job is running
  (`monitorJob` is `null`) or after it has already completed/been cancelled.
- No changes to `startPlaybackMonitoring(controller: MediaController)`'s
  existing signature or behavior when called with a live controller.
- No changes to `PlaybackState`'s shape or any other public
  `ErdStreamViewModel` function.

## Failure Modes

- Must not throw if called before any monitoring job ever started (Acceptance
  Criteria #4) — `monitorJob?.cancel()` on a `null` `monitorJob` is already
  safe in Kotlin (null-safe call), so this should be naturally satisfied; just
  don't introduce a non-null assertion (`!!`) anywhere in the process.
- Must not create a race where `stopPlaybackMonitoring()` (from the disposing
  effect) and a *new* `startPlaybackMonitoring()` (from a fast-reconnecting
  replacement effect) interleave in a way that leaves monitoring permanently
  stopped when it shouldn't be — since both run on the main thread (Compose
  effects execute on the main dispatcher, and `viewModelScope` defaults to
  `Dispatchers.Main.immediate`), verify there's no dispatcher-ordering
  assumption being violated. Reasoning through the sequence: `onDispose` (old
  effect) always runs to completion before the new `DisposableEffect(Unit)`'s
  body runs for the recomposed subtree, and `LaunchedEffect(mediaController)`
  only fires `startPlaybackMonitoring()` after the new controller resolves
  (asynchronously, later) — so `stopPlaybackMonitoring()` always happens
  first, and a later `startPlaybackMonitoring()` unconditionally starts a
  fresh job regardless of what `stopPlaybackMonitoring()` already did. No
  invalid interleaving is possible here, but confirm this reasoning holds
  against the actual diff rather than assuming it.
- Must not regress the case where the controller is still alive and well
  (no recreation happening) — monitoring should continue completely normally
  through the app's ordinary lifetime.

## Security / Privacy

N/A — this task only touches in-process coroutine lifecycle management; no
credentials, network requests, or persisted data are involved.

## Compatibility / Migration

N/A — no persisted schema, public API (beyond the one new internal-facing
ViewModel method), or navigation changes.

## Test Matrix

- The project has no unit tests exercising `ErdStreamViewModel`'s coroutine
  lifecycle end-to-end (confirmed across BUG-001/BUG-002: no mocking library,
  `MediaController` is a final Android framework class). A true reproduction
  of "Activity recreation while `AndroidViewModel` survives" is fundamentally
  an instrumentation-level/Robolectric concern, not a plain JVM JUnit concern.
- If a narrow, real JVM-testable slice exists — e.g. extracting just the
  "cancel is idempotent / safe when never started" behavior, or a fake
  interface standing in for the polled controller methods if that can be done
  without a broad refactor — prefer adding it. Otherwise, clearly say so in
  `.ai/VERIFICATION.md` and rely on the runtime/device verification below,
  same as BUG-001.
- Do not add a general `MediaController`-faking seam purely to make this one
  bug testable (Non-Goals).

## Runtime / Device Verification

Manual repro/verification steps (run before and after the fix; requires a
device/emulator — note that neither Codex's nor Claude's execution environment
in the BUG-001/BUG-002 passes had ADB device access, so this may need to be
run by the user directly if that remains the case):
1. Start playing a song.
2. Trigger an Activity recreation while it's playing — easiest via Developer
   Options -> "Don't keep activities" enabled, then send the app to background
   and bring it back to the foreground (this destroys and recreates the
   Activity), or via a manual configuration change if one applies to this
   app's supported orientations/configs.
3. **Before the fix**: instrument or log inside `startPlaybackMonitoring`'s
   loop (or attach a debugger) to confirm whether the *old* monitoring
   coroutine keeps invoking methods on the released `MediaController` during
   the window between disposal and the new connection resolving — this should
   be observable as either continued invocations on a released controller, or
   (if Media3 throws on use of a released controller) exceptions in logcat
   from that stale job.
4. **After the fix**: confirm no invocations occur on the released controller
   after disposal — monitoring should cleanly stop within one recomposition,
   and cleanly restart against the new controller once it connects, with no
   gap where two jobs are running simultaneously and no crash/log spam during
   the transition.
5. Confirm the non-regression case: normal playback with no recreation
   happening continues to update the Now Playing UI (position, buffering
   state, track advancement) exactly as before.
6. Run `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug testDebugUnitTest` per `.ai/PROJECT.md`'s
   standard verification list. (Note: `lintDebug` currently fails on one
   pre-existing, unrelated error at `MainActivity.kt` — see BUG-001's
   `.ai/VERIFICATION.md` entry; do not introduce *additional* lint errors, but
   this pre-existing one is not part of this task's scope.)

## Codex Handoff
When ready, replace `IDLE` with `READY FOR CODEX`. Codex must perform an
independent feasibility challenge before implementation and record its
evidence in `.ai/VERIFICATION.md`.
