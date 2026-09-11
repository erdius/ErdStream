# Bug Backlog

Track credible bugs here. Keep `.ai/CURRENT_TASK.md` limited to the one bug currently being worked.

## Status values
- NEW
- CONFIRMED
- IN_PROGRESS
- FIXED_PENDING_REVIEW
- VERIFIED
- REJECTED

## Finding template
### BUG-XXX — Short title
- Status: NEW
- Severity: Critical / High / Medium / Low
- Confidence: High / Medium / Low
- Area:
- File/component:
- Evidence:
- Reproduction steps:
- Expected behavior:
- Current behavior:
- Proposed regression test:
- Runtime verification needed:
- Notes:

## Findings

### BUG-001 — Toggling shuffle unconditionally resumes playback, even if the user had paused
- Status: VERIFIED
- Severity: High
- Confidence: High
- Area: Queue/shuffle/repeat state, playback control
- File/component: `app/src/main/java/com/erdman/erdstream/ErdStreamViewModel.kt` — `toggleShuffle()` (lines 196-221), via `startPlaybackFromQueue()` (lines 76-123)
- Evidence:
  - `toggleShuffle()` (both the "turn on" and "turn off" branches) calls `startPlaybackFromQueue(newQueue, ..., controller, isNewQueue = false)`.
  - `startPlaybackFromQueue()` unconditionally executes `controller.prepare()` followed by `controller.playWhenReady = true` (line ~110), and unconditionally sets `_playbackState.value = previous.copy(..., isPlaying = true, isBuffering = true, ...)` (line ~118).
  - There is no code path in `startPlaybackFromQueue()` that consults the *current* `isPlaying` state before forcing playback on. It was clearly designed for "start playing something new" call sites (`playQueue` from song/album taps, `startShuffledPlaybackFromQueue` from the shuffle FAB), where forcing `playWhenReady = true` is correct — but `toggleShuffle()` reuses the same function purely to reorder an *already active* queue, inheriting that forced-resume side effect unintentionally.
- Reproduction steps:
  1. Start playing any song or album.
  2. Tap pause (playback stops, Now Playing shows the Play icon).
  3. Tap the shuffle toggle (in `NowPlayingScreen`, or from an album's shuffle FAB while a queue is already active).
  4. Observe: audio resumes playing and the Play/Pause icon flips to "Pause", with no play action from the user.
- Expected behavior: Toggling shuffle should only reorder the queue; play/pause state should be preserved exactly as it was before the toggle.
- Current behavior: Playback unconditionally resumes (`playWhenReady = true`), regardless of whether it was paused beforehand.
- Proposed regression test: A test around `ErdStreamViewModel.toggleShuffle()` that starts from a paused state (`isPlaying = false`) and asserts that after calling `toggleShuffle(controller)`, `controller.playWhenReady` is not set to `true` and `_playbackState.value.isPlaying` remains `false`. Exercising this needs either a fake/stub in place of `MediaController` (a final Android framework class) or an instrumentation/Robolectric-based test, since the project currently has no unit tests and no mocking library (JUnit4 only, no MockK/Mockito/Robolectric configured in `app/build.gradle.kts`).
- Runtime verification needed: Manual/device verification of the repro steps above pre- and post-fix (build has no CI/device access from this environment), since there's no existing automated coverage of `ErdStreamViewModel` to lean on.
- Notes: Selected as the current task (see `.ai/CURRENT_TASK.md`) — 100% reproducible with no timing/race dependency, isolated to one function, and directly affects core playback UX explicitly called out in `.ai/BUG_HUNT.md` ("queue/shuffle/repeat state").

### BUG-002 — Playlist song removal is index-based and races with itself under rapid successive removals
- Status: VERIFIED
- Severity: Medium
- Confidence: Medium
- Area: Playlists / Subsonic API / networking
- File/component: `app/src/main/java/com/erdman/erdstream/MainActivity.kt` — `onRemoveSongClick` handler inside the `Screen.PlaylistDetails.route` composable (~lines 671-689); `app/src/main/java/com/erdman/erdstream/data/SubsonicRepository.kt` — `removeSongFromPlaylist(playlistId, songIndex)` (lines 154-157), which calls `SubsonicApi.updatePlaylist(playlistId, songIndexToRemove)`.
- Evidence:
  - The Subsonic `updatePlaylist.view` endpoint removes a song by its **0-based position** in the playlist as currently stored server-side (`songIndexToRemove`), not by song ID.
  - `onRemoveSongClick` applies the removal **optimistically** to local Compose state immediately (`playlistDetail = currentDetail.copy(songs = ...removeAt(index))`), then separately fires `app.subsonicRepository.removeSongFromPlaylist(playlistId, index)` in `scope.launch { ... }` — a fire-and-forget coroutine that is not awaited by, or serialized against, any other in-flight removal.
  - If a user removes two songs in quick succession (each removal already requires long-press → tap "Remove from Playlist" → confirm in a bottom sheet, so it's a few distinct taps, not a single click — plausible on a slow/high-latency mobile connection matching this app's real-world usage), two `updatePlaylist.view?songIndexToRemove=N` requests can be in flight concurrently. Local (optimistic) index bookkeeping assumes request 1 has already been applied server-side by the time request 2's index is computed, but the two HTTP requests can arrive at / be processed by the server out of order or overlapping, since nothing here awaits request 1's completion before index 2 is computed and sent.
  - If the server processes them out of order (or concurrently against the same playlist row), the second request's index can point at a different song than the one the user actually selected, silently deleting the wrong song from the user's playlist — a data-loss/correctness bug with no user-visible error (the optimistic UI already shows the "expected" result regardless of what the server actually did, until the next full reload).
- Reproduction steps: Requires either a slow/high-latency Subsonic server or artificially delaying the HTTP client, then removing two different songs from the same playlist within about the same round-trip window, and comparing final server-side playlist order/contents against the client's optimistic view after a fresh reload.
- Expected behavior: Playlist song removals should be applied to the server in the same order and against the same indices the user intended, even under rapid successive taps.
- Current behavior: Concurrent, unserialized index-based removal requests can be applied out of order server-side, potentially removing the wrong song.
- Proposed regression test: Not practically unit-testable without a fake Subsonic server or an injectable API client with controllable response timing; would need an instrumentation-level or fake-server-backed test that delays one request to prove serialization/ordering once fixed.
- Runtime verification needed: Manual verification against a real (or deliberately throttled) Navidrome instance, comparing client vs. server playlist state after rapid multi-song removal.
- Notes: Not selected this round — lower reproducibility confidence than BUG-001 (needs a timing race), and a clean fix (serializing removals per playlist, e.g., via a per-playlist queue/mutex, or disabling further removal taps while one is in flight) is a reasonable follow-up bug hunt task.

### BUG-003 — Playback-monitoring coroutine can keep polling a released MediaController if the replacement connection never completes
- Status: VERIFIED
- Severity: Medium
- Confidence: Medium
- Area: Media3 lifecycle / process recreation / resource leak
- File/component: `app/src/main/java/com/erdman/erdstream/MainActivity.kt` — `DisposableEffect(Unit)` controller-connection block (~lines 285-308) and `LaunchedEffect(mediaController)` (~lines 306-308); `app/src/main/java/com/erdman/erdstream/ErdStreamViewModel.kt` — `startPlaybackMonitoring()` (lines 237-324).
- Evidence:
  - `ErdStreamViewModel` is an `AndroidViewModel`, so it (and anything launched in `viewModelScope`, including the `while (true)` monitoring loop in `startPlaybackMonitoring`) survives Activity recreation (config change, or the Activity being torn down and recreated after process death with saved state) — only `onCleared()` (real ViewModel destruction) cancels `monitorJob`.
  - On Activity recreation, the old Compose composition's `DisposableEffect(Unit)` disposes, calling `MediaController.releaseFuture(controllerFuture)` and setting `mediaController = null` — but this does **not** touch `monitorJob`, which keeps running against the now-released `MediaController` instance it already captured as a parameter.
  - The new composition's own `DisposableEffect(Unit)` starts a **new** async `MediaController.Builder(...).buildAsync()`; only once that future resolves and `mediaController` becomes non-null does `LaunchedEffect(mediaController)` call `viewModel.startPlaybackMonitoring(it)`, whose first line (`monitorJob?.cancel()`) finally cancels the stale job.
  - Until that new connection resolves — or indefinitely, if it never resolves (e.g. the service fails to bind, or `onGetSession` returns null under some condition) — the stale `monitorJob` keeps calling `controller.playWhenReady`, `controller.currentPosition`, `controller.duration`, `controller.playbackState`, and `controller.currentMediaItem` on a released `MediaController` every 200ms-1000ms, forever, in a coroutine the user has no way to stop short of killing the app process.
- Reproduction steps: Trigger an Activity recreation while a song is playing (e.g. a config change, or simulate "Don't keep activities" + backgrounding on a device/emulator), then check logcat / instrument the loop to see whether it's still invoking methods on the pre-recreation `MediaController` instance during the reconnation window.
- Expected behavior: The monitoring coroutine for a stale/released `MediaController` should stop as soon as that controller is released, not wait for a replacement connection to succeed.
- Current behavior: The old monitoring job is only cancelled as a side effect of a *new* controller connection succeeding; nothing cancels it directly when the old controller is released.
- Proposed regression test: Hard to express as a fast unit test without an injectable/fake `MediaController` abstraction (present code depends on the concrete Media3 class directly); would need an instrumentation test exercising Activity recreation.
- Runtime verification needed: Device/emulator test simulating Activity recreation during active playback, confirming no stale-controller calls/exceptions occur after the old controller is released.
- Notes: Not selected this round — lower reproducibility confidence (depends on recreation timing/failure windows) and would likely need a small interface seam (e.g., cancelling monitoring directly from the disposing effect) rather than a single-function fix. Good candidate for a future bug-hunt pass.

### BUG-004 — A failed playlist-song removal desyncs the index of every removal already queued behind it
- Status: VERIFIED
- Severity: High
- Confidence: High
- Area: Playlists / Subsonic API / networking — direct regression risk in the exact mechanism BUG-002 fixed
- File/component: `app/src/main/java/com/erdman/erdstream/MainActivity.kt` — the `PlaylistRemovalQueue` worker lambda inside `Screen.PlaylistDetails.route` (~lines 665-679); `app/src/main/java/com/erdman/erdstream/PlaylistRemovalQueue.kt` (the BUG-002 fix itself).
- Evidence:
  - `PlaylistRemovalQueue` (added to fix BUG-002) correctly serializes removals: a single worker coroutine drains a `Channel<Int>` one index at a time, `for (index in indices) remove(index)`, so request *N+1* is not sent until request *N*'s full suspend call (including its catch block) has returned. This does fix the original out-of-order-requests race.
  - But the worker's `remove` lambda, on failure, does: `catch (e: Exception) { playlistError = errorText(e); loadPlaylistDetail(playlistId) }` — and `loadPlaylistDetail` is a **suspend** call that's awaited inline, replacing `playlistDetail` with a fresh, authoritative reload from the server.
  - Every index still sitting in the channel behind the failed one was computed **optimistically**, before that reload, against a local list that assumed the failed removal *had* succeeded (`onRemoveSongClick` removes the row from local state immediately, before the network call even starts, then calls `removalQueue.enqueue(index)`).
  - Once the failed removal's reload restores the server's true list (which still contains the song whose removal failed), every subsequent already-queued index is now off by however many positions that restored song shifts things by — the worker keeps draining the channel and sends those stale indices to `removeSongFromPlaylist` regardless, silently removing whatever song actually sits at that index server-side now, not the song the user tapped.
  - This is the same data-loss failure mode BUG-002 described (wrong song silently removed, no error shown for the *second* mistaken removal since that request itself "succeeds" against the wrong index) — just triggered by an ordinary transient failure (timeout, dropped connection, momentary server error) on the first of a batch of taps, rather than by raw unserialized concurrency.
- Reproduction steps:
  1. Open a playlist with at least 3 songs.
  2. Remove two different songs in quick succession (tap song A's remove, confirm; before/shortly after, tap song B's remove, confirm) while the first request is arranged to fail — e.g. toggle airplane mode / kill the network for a moment right as the first tap fires, then restore it before the second tap's request would go out (or throttle/blackhole the first `updatePlaylist.view` call in a proxy).
  3. After both requests resolve, compare the client's list (post-reload) against the actual server-side playlist contents.
- Expected behavior: If a queued removal fails, only that one removal should be affected (surfaced as the existing error banner + reload); every removal still queued behind it should be re-validated against the just-reloaded true state before being sent, not fired blindly with its stale pre-reload index.
- Current behavior: A failed removal's recovery reload silently invalidates the indices of every other removal already queued behind it, which are sent anyway.
- Proposed regression test: Same testability constraint as BUG-002 — needs a fake/injectable Subsonic API client with controllable failure on the first call, since there's no mocking library configured. Assert that after a first-call failure + reload, a second still-queued removal call either (a) is skipped/re-derived against the reloaded list rather than sent with its original index, or (b) the whole queue is drained/invalidated so the user must re-tap post-reload.
- Runtime verification needed: Manual verification against a real/throttled Navidrome instance — force one removal to fail (airplane mode toggle or a delaying proxy) while a second is already queued, then confirm which song actually got removed server-side vs. which one the user intended.
- Notes: Found during this round's fresh discovery pass (2026-09-11); the previous BUG_HUNT round's own fix for BUG-002 is what introduced this residual gap. Selected as the current task (see `.ai/CURRENT_TASK.md`) — concrete, reproducible without exotic timing (an ordinary network blip is enough), and high-severity (silent data loss) per `.ai/BUG_HUNT.md`'s stated priorities.
