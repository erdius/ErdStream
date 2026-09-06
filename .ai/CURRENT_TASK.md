# Current Task

Status: `READY FOR CODEX`

Claude owns this file for the active implementation contract.

## Objective

Fix BUG-001 (see `.ai/BUG_BACKLOG.md`): `ErdStreamViewModel.toggleShuffle()` must never change whether playback is playing or paused. Today, toggling shuffle (in either direction — turning it on or off) unconditionally forces `playWhenReady = true` on the `MediaController` and forces `_playbackState.value.isPlaying = true`, because it reuses `startPlaybackFromQueue()`, a function designed for "start playing something new" call sites. A user who pauses playback and then toggles shuffle will hear playback silently resume and see the Play/Pause icon flip to "Pause" with no play action from them.

## Acceptance Criteria

1. Given playback is **paused** (`_playbackState.value.isPlaying == false`) with an active queue, calling `toggleShuffle(controller)` (either direction: shuffle off→on, or on→off) must:
   - Still reorder the queue exactly as it does today (shuffled order when turning on; restored original order + correct `restoreIndex` when turning off).
   - Leave playback paused: `controller.playWhenReady` must not be forced to `true`, and `_playbackState.value.isPlaying` must remain `false` after the call.
2. Given playback is **actively playing** (`_playbackState.value.isPlaying == true`) when `toggleShuffle(controller)` is called, behavior must be unchanged from today: the queue reorders and playback continues seamlessly (`playWhenReady` stays/ends up `true`, `isPlaying` stays `true`).
3. `seekTo(state.positionMs, controller)` (the existing playback-position restoration after reordering) must still run in both the playing and paused cases — i.e., don't lose the fix for "shuffle preserves position," only fix the play/pause-state leak.
4. All other callers of `startPlaybackFromQueue()` — `playQueue()` (song/album/search taps) and `startShuffledPlaybackFromQueue()` (album shuffle FAB) — must keep their current "always start playing" behavior unchanged. These are genuinely "start something new" call sites where forcing `playWhenReady = true` is correct and must not regress.

## Non-Goals

- Do not fix BUG-002 (playlist removal race) or BUG-003 (stale MediaController monitoring) in this task — they are separate, already logged in `.ai/BUG_BACKLOG.md` for a future pass.
- Do not add a general dependency-injection/testing seam for `MediaController` (e.g., wrapping it behind a fake-able interface) unless it is the only reasonable way to write the regression test — prefer the smallest change that satisfies the acceptance criteria, per `.ai/ARCHITECTURE.md`'s change-discipline guidance ("prefer small diffs... without a concrete need").
- Do not change shuffle's queue-building logic (`state.queue.take(index) + state.queue.drop(index + 1)).shuffled()`, or the `restoreIndex` lookup) — only the play/pause side effect.
- Do not touch the uncommitted, in-progress local changes already present in `app/src/main/java/com/erdman/erdstream/playback/PlaybackService.kt` (ICY radio-title propagation to the MediaSession for Bluetooth AVRCP) — that is unrelated active work-in-progress, not part of this task.

## Repository Findings

- `ErdStreamViewModel.startPlaybackFromQueue()` (`app/src/main/java/com/erdman/erdstream/ErdStreamViewModel.kt:76-123`) is called from three places:
  1. `playQueue()` in `MainActivity.kt` (`isNewQueue` defaults to `true`) — song/album/playlist/search taps. Correct to always start playing.
  2. `startShuffledPlaybackFromQueue()` (`ErdStreamViewModel.kt:125-130`, `isNewQueue = false`) — the album/playlist shuffle FAB, called when nothing is necessarily already playing. Correct to always start playing.
  3. `toggleShuffle()` (`ErdStreamViewModel.kt:196-221`, both branches, `isNewQueue = false`) — reorders an **already active** queue. This is the buggy call site: it should preserve whatever play/pause state was true immediately before the call.
- `startPlaybackFromQueue()` itself has no parameter controlling whether playback should be forced on; the `controller.playWhenReady = true` line and `isPlaying = true` in the returned state are unconditional.
- `togglePlayback()` (`ErdStreamViewModel.kt:132-143`) is the only other place that mutates `isPlaying`/`playWhenReady`, and does so correctly (branches on current `isPlaying`).

## Affected Components

- `app/src/main/java/com/erdman/erdstream/ErdStreamViewModel.kt`: `startPlaybackFromQueue()`, `toggleShuffle()`. Likely the only file that needs a production-code change.
- No changes expected to `MainActivity.kt`, `PlaybackService.kt`, or any `data/` or `ui/` files — `toggleShuffle(controller)`'s public signature should stay the same, since `MainActivity.kt` and `NowPlayingScreen.kt`/`AlbumDetailsScreen.kt`/`PlaylistDetailsScreen.kt` all call it with just a `MediaController?`.

## State / Data Flow

- Today: `toggleShuffle()` reads `state = _playbackState.value` once at entry (capturing `isPlaying` before any mutation), then unconditionally ends up with `isPlaying = true` after calling `startPlaybackFromQueue()`.
- Needed: the play/pause state captured in that initial `state` read must be the one preserved through to the end of `toggleShuffle()`, regardless of which branch (shuffle on vs. off) runs, and regardless of what `startPlaybackFromQueue()` does internally.
- Suggested shapes for the fix (either is acceptable — pick whichever is the smaller, cleaner diff against the current code):
  - **(a)** Give `startPlaybackFromQueue()` a `resumePlayback: Boolean = true` parameter. `playQueue()` and `startShuffledPlaybackFromQueue()` keep relying on the default (`true`). `toggleShuffle()` passes `resumePlayback = state.isPlaying` explicitly (the state captured before mutation) instead of the default. Inside `startPlaybackFromQueue()`, only set `controller.playWhenReady = true` / `isPlaying = true` in the returned state when `resumePlayback` is true; when false, leave `playWhenReady` untouched (or explicitly `false`) and set `isPlaying = false` in the returned state instead.
  - **(b)** Leave `startPlaybackFromQueue()` untouched, and instead have `toggleShuffle()` capture `val wasPlaying = state.isPlaying` up front, call `startPlaybackFromQueue(...)` as it does today, then — if `!wasPlaying` — explicitly call `controller?.pause()` and patch `_playbackState.value = _playbackState.value.copy(isPlaying = false)` afterward, in both branches, before/after the existing `seekTo(...)` call.
  - Either way, `seekTo(state.positionMs, controller)` must still execute in both branches exactly as today (this already runs after `startPlaybackFromQueue()` in both the "turn on" and "turn off" cases, and must keep doing so).

## Interface Contracts

- If option (a) is chosen: `startPlaybackFromQueue(queue: List<SongUiModel>, startIndex: Int, controller: MediaController?, isNewQueue: Boolean = true, resumePlayback: Boolean = true)` — new trailing parameter with a default that preserves every existing call site's current behavior unchanged (only `toggleShuffle()` passes a non-default value).
- No changes to any other public function signature in `ErdStreamViewModel`, and no changes to `PlaybackState`'s shape.

## Failure Modes

- Must not regress the "shuffle while playing keeps playing seamlessly" case — verify this explicitly, not just the paused case, since that's what most users will experience most of the time.
- Must not break `toggleShuffle()`'s existing edge-case guards (`state.queue.isEmpty()`, `index !in state.queue.indices`, `state.originalQueue.isEmpty()` when turning off) — these are unrelated to this bug and must be left exactly as-is.
- Watch for a subtle reintroduction of the bug: `startPlaybackFromQueue()`'s returned state always sets `isBuffering = true` today, even for a reorder — decide deliberately whether a paused reorder should also force `isBuffering = true` (arguably should stay `false` if nothing is being fetched/prepared while paused) as part of the fix, since leaving it `true` forever with no corresponding playback happening would leave the UI stuck showing a spinner. Prefer keeping `isBuffering` accurate to the (paused) state you land on.

## Security / Privacy

N/A — this task touches only in-memory playback/queue state; no credentials, network requests, or persisted data are involved.

## Compatibility / Migration

N/A — no persisted schema, public API, or navigation changes. `toggleShuffle(controller: MediaController?)`'s call sites (`MainActivity.kt`'s `NowPlayingScreen` and `AlbumDetailsScreen`/`PlaylistDetailsScreen` shuffle-FAB wiring — actually verify: the FAB wiring calls `startShuffledPlaybackFromQueue`, not `toggleShuffle`; only `NowPlayingScreen`'s shuffle icon calls `viewModel.toggleShuffle(mediaController)`) are unaffected either way.

## Test Matrix

- The project currently has **no unit tests** (`find app/src -path "*test*"` returns nothing) and no mocking library configured (`app/build.gradle.kts` only has `junit:junit:4.13.2` for JVM tests; Espresso/Compose-UI-test are `androidTest`-only, instrumentation-based). `MediaController` is a final Android framework class with no fake/interface seam in this codebase today.
- Given `Non-Goals` rules out adding a broad DI/fake seam just for this: if a fast JVM unit test isn't practically achievable without one, it is acceptable to rely on the Runtime/Device Verification below instead — but if a small, targeted way to verify `_playbackState.value.isPlaying` and the `controller.playWhenReady` call pattern exists without a broad refactor (e.g., a plain JUnit test against just the `PlaybackState` transition logic, extracted or asserted independently of the real `MediaController`), prefer adding it.
- If a real automated test isn't practical, clearly say so in `.ai/VERIFICATION.md` rather than skipping verification — device verification below is then the primary evidence.

## Runtime / Device Verification

Manual repro/verification steps (run before and after the fix):
1. Connect to a test Subsonic/Navidrome server, play any song.
2. Tap pause. Confirm the Now Playing screen shows the Play icon and audio is stopped.
3. Tap the shuffle icon. **Before the fix**: audio audibly resumes and the icon flips to Pause. **After the fix**: audio stays paused, icon stays showing Play, queue still visibly reorders (check via track advancing to a different next-up order, or via logcat/state inspection).
4. Repeat step 3 a second time to toggle shuffle back off — confirm the same paused-state preservation.
5. Separately, confirm the non-regression case: play a song, do **not** pause, toggle shuffle — confirm playback continues uninterrupted (no stutter/restart), exactly as before this change.
6. Run `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`, and `./gradlew lintDebug` per `.ai/PROJECT.md`'s standard verification list.

## Codex Handoff
When ready, replace `IDLE` with `READY FOR CODEX`. Codex must perform an independent feasibility challenge before implementation and record its evidence in `.ai/VERIFICATION.md`.
