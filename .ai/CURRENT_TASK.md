# Current Task

Status: `READY FOR CODEX`

Claude owns this file for the active implementation contract.

## Objective

Fix BUG-002 (see `.ai/BUG_BACKLOG.md`): removing songs from a playlist sends
`updatePlaylist.view?songIndexToRemove=<index>` requests to the server without
serializing them against each other. Two removals fired in quick succession are
independent, unawaited coroutines (`scope.launch { ... }` in `MainActivity.kt`'s
`onRemoveSongClick`), so the server can receive/process them out of order or
overlapping. Since Subsonic's `updatePlaylist.view` removes "the song currently
at position N," a second request computed against the client's optimistic
(pre-server-confirmation) view can end up deleting the wrong song server-side,
with no visible error — the optimistic UI already shows what the user expects
regardless of what actually happened server-side, until the next full reload.

## Acceptance Criteria

1. When a user removes multiple songs from the same open playlist in quick
   succession (before each prior request has completed), the
   `updatePlaylist.view?songIndexToRemove=...` requests must reach the server
   **in the same order the user tapped remove, and only one in flight at a
   time** — i.e., request 2 must not be sent until request 1 has completed
   (success or failure).
2. This ordering guarantee must not depend on unverified assumptions about
   library-level lock fairness. If a `Mutex` (`kotlinx.coroutines.sync.Mutex`)
   is used for serialization, either confirm from the `kotlinx-coroutines-core`
   version pinned in `gradle/libs.versions.toml` (or `app/build.gradle.kts`)
   that `Mutex.lock`/`withLock` is documented as FIFO/fair, or use a
   construction with an unambiguous ordering guarantee by construction (e.g. a
   single dedicated coroutine draining a `Channel` of pending removals in send
   order). Do not ship a fix whose ordering guarantee rests on an unverified
   assumption.
3. The existing optimistic-UI behavior must be preserved exactly: the row
   still disappears from `playlistDetail` immediately on tap (no waiting for
   the network call), and on failure the existing rollback
   (`playlistError = errorText(e); loadPlaylistDetail(playlistId)`) must still
   run for the request(s) that failed.
4. If request 1 fails and request 2 was already enqueued behind it, request 2
   must still run afterward (its own success/failure is independent) — a
   failure in an earlier queued removal must not silently drop or skip a
   later one.
5. Switching away from the currently-open playlist (returning to
   `PlaylistsScreen`, or opening a different playlist's details) must not leak
   the serialization queue/state into the next playlist opened, and must not
   throw or deadlock if songs were still queued for removal when the user
   navigated away.
6. No change to `addSongToPlaylist`, `deletePlaylist`, or any other
   `SubsonicRepository`/`SubsonicApi` function not named in this contract.

## Non-Goals

- Do not fix BUG-003 (stale `MediaController` monitoring after Activity
  recreation) in this task — already logged in `.ai/BUG_BACKLOG.md` for a
  future pass.
- Do not change the server-side contract, add song-ID-based removal, or touch
  `SubsonicApi.updatePlaylist` signatures — `updatePlaylist.view` is a fixed
  Subsonic/Navidrome server endpoint; the fix is purely about client-side
  request ordering/serialization.
- Do not add a general per-repository-call serialization framework or DI/testing
  seam beyond what's needed to serialize this one call path, per
  `.ai/ARCHITECTURE.md`'s change-discipline guidance ("prefer small diffs...
  without a concrete need").
- Do not change the optimistic-removal UX (e.g. don't switch to
  "disable the row until confirmed" or "await the network call before removing
  the row") — the row must still disappear immediately on tap.
- Do not touch the uncommitted, in-progress local changes already present in
  `app/src/main/java/com/erdman/erdstream/playback/PlaybackService.kt` (ICY
  radio-title propagation to the MediaSession for Bluetooth AVRCP) — unrelated
  active work-in-progress, not part of this task.

## Repository Findings

- `MainActivity.kt`'s `Screen.PlaylistDetails.route` composable,
  `onRemoveSongClick = { index -> ... }` handler (~lines 671-690):
  - Reads `playlistId` / `currentDetail` from Compose state, applies the
    removal optimistically to local `playlistDetail`, then calls
    `scope.launch { app.subsonicRepository.removeSongFromPlaylist(playlistId, index) }`
    — a fire-and-forget coroutine launched on `scope` (a
    `rememberCoroutineScope()` tied to this composable's lifecycle).
  - Nothing here awaits a prior removal's `Job`, or otherwise prevents two
    `scope.launch` blocks for the same playlist from running concurrently.
- `SubsonicRepository.removeSongFromPlaylist(playlistId: String, songIndex: Int)`
  (`data/SubsonicRepository.kt:154-157`): a plain `withContext(Dispatchers.IO)`
  suspend call straight through to `api().updatePlaylist(playlistId, songIndex)` —
  no serialization, locking, or per-playlist state exists here today.
- `SubsonicApi.kt:31-36`: `updatePlaylist(playlistId, songIndexToRemove: Int)` —
  confirms via its own doc comment that `songIndexToRemove` is "the song's
  0-based position within the playlist, not its song ID," i.e. positional, not
  identity-based — this is what makes ordering matter.
- `scope` in `MainActivity.kt` is `rememberCoroutineScope()` (imported at
  `MainActivity.kt:36`), scoped to the composition, not to any specific
  playlist — reused across whichever playlist happens to be open.

## Affected Components

- `app/src/main/java/com/erdman/erdstream/MainActivity.kt`: the
  `onRemoveSongClick` handler (and possibly a small amount of new
  state/plumbing near it, e.g. a `remember { }`-held queue/channel/mutex scoped
  to the currently open playlist).
- Possibly `app/src/main/java/com/erdman/erdstream/data/SubsonicRepository.kt`,
  if the serialization is implemented at the repository layer instead (see
  State/Data Flow below for both shapes) — this is architecturally preferable
  per `.ai/ARCHITECTURE.md` ("network API concerns stay separated from UI
  state"), but is not mandatory if the `MainActivity.kt`-local shape more
  cleanly satisfies the acceptance criteria with a smaller diff.
- No changes expected to `SubsonicApi.kt`, `ErdStreamViewModel.kt`, or any
  `ui/` composable files — `PlaylistDetailsScreen`'s `onRemoveSongClick`
  callback signature (`(Int) -> Unit`) must not change.

## State / Data Flow

- Today: each tap -> optimistic local removal (synchronous) -> independent
  `scope.launch { repository call }` (asynchronous, unserialized).
- Needed: each tap still applies the optimistic local removal synchronously
  (unchanged), but the actual `removeSongFromPlaylist` network calls for a
  given playlist must run strictly one-at-a-time, in tap order.
- Two acceptable shapes (pick whichever is the smaller, cleaner diff against
  the current code; either satisfies the acceptance criteria):
  - **(a) Repository-layer, per-playlist serialization.** In
    `SubsonicRepository`, hold a per-`playlistId` serial queue — e.g. a
    `ConcurrentHashMap<String, Mutex>` (only if `Mutex` is confirmed FIFO — see
    Acceptance Criteria #2) or a small actor built on a
    `Channel<PlaylistRemoval>` (`data class PlaylistRemoval(val playlistId: String, val songIndex: Int, val onResult: CompletableDeferred<Result<Unit>>)`)
    with one worker coroutine per playlist that drains it in order, calling
    `api().updatePlaylist(...)` and completing the `Deferred` so
    `MainActivity.kt` can still `try`/`catch` per-call for its existing
    rollback logic. `removeSongFromPlaylist` becomes the enqueue+await call;
    its public suspend signature can stay identical
    (`removeSongFromPlaylist(playlistId: String, songIndex: Int)`), so
    `MainActivity.kt`'s call site does not need to change at all.
  - **(b) `MainActivity.kt`-local serialization.** Hold
    `var pendingRemoval: Job? by remember { mutableStateOf<Job?>(null) }`
    (or similar) scoped to the currently-open playlist (reset to `null`
    whenever `selectedPlaylistId`/the loaded playlist changes, so it never
    leaks across playlists per Acceptance Criteria #5). Each new removal's
    `scope.launch` block first `pendingRemoval?.join()`s before calling
    `removeSongFromPlaylist`, and immediately reassigns `pendingRemoval` to its
    own new `Job` so the next tap chains after it, not after some earlier,
    already-completed job.
- Either way, per Acceptance Criteria #4, one queued removal's failure must
  not cancel/skip a later queued removal — use `try { } catch { }` around each
  individual network call in the chain, not a chain-wide cancellation.

## Interface Contracts

- If shape (a): `SubsonicRepository.removeSongFromPlaylist(playlistId: String, songIndex: Int)`
  keeps its exact current suspend signature and thrown-exception contract
  (`SubsonicException`/network exceptions propagate to the caller exactly as
  today) — only its internal implementation gains serialization.
  `MainActivity.kt`'s call site is unchanged.
- If shape (b): no `SubsonicRepository`/`SubsonicApi` signature changes at all;
  only `MainActivity.kt`'s `onRemoveSongClick` closure gains local state/logic.
- Either way: no change to `PlaylistDetailsScreen`'s public parameters
  (`onRemoveSongClick: (Int) -> Unit`), and no change to `PlaylistDetail`'s
  shape.

## Failure Modes

- Must not deadlock: if a worker coroutine (shape a) or job chain (shape b)
  throws inside the serialized call, later queued removals must still run —
  verify with a 3-removal-in-a-row scenario where the *first* one is made to
  fail.
- Must not leak coroutines/state across playlist navigation (Acceptance
  Criteria #5) — verify by opening playlist A, queuing removals, immediately
  navigating to playlist B, and confirming no crash, no stale removals applied
  to B, and no unbounded growth of per-playlist worker state if the user opens
  many playlists over a session (shape (a)'s `ConcurrentHashMap` would need
  entries cleaned up eventually, or bounded/acceptable given typical playlist
  counts — call out whichever tradeoff is chosen in `.ai/VERIFICATION.md`).
- Must not change perceived UI latency for the *first* removal in a burst —
  only subsequent overlapping removals should queue; the common case (one
  removal, then waiting) must feel identical to today.

## Security / Privacy

N/A — this task touches only request ordering for an already-authenticated,
already-authorized playlist-management endpoint; no new data is transmitted,
logged, or stored.

## Compatibility / Migration

N/A — no persisted schema, public API, or navigation changes. No server-side
behavior change (same endpoint, same parameters, just strictly serialized from
the client).

## Test Matrix

- The project has **no unit tests** and no mocking library configured
  (confirmed during BUG-001's task: JUnit4 only, no MockK/Mockito/Robolectric).
  `SubsonicApi`/`Retrofit` calls are not currently fake-able without a real or
  fake HTTP layer.
- If shape (a) is chosen and the serialization logic (e.g. the
  queue-draining order, or a small pure function extracted from it) can be
  exercised with a fake `suspend` call standing in for `api().updatePlaylist`
  (e.g. a lambda with an artificial `delay()` to prove ordering under
  concurrency, using `kotlinx-coroutines-test`'s `runTest`/virtual time if
  available, or a plain `delay()` + `withContext` under real dispatchers if
  not), prefer adding that test — it does not require faking the network
  layer, only the suspend call shape.
- If a real automated test isn't practical without more test infrastructure
  than this task's Non-Goals allow, say so clearly in `.ai/VERIFICATION.md`
  rather than skipping verification — device/manual verification below is
  then the primary evidence.

## Runtime / Device Verification

Manual repro/verification steps (run before and after the fix; a slow/high
latency connection or an artificial delay makes the race far more likely to
show up, so prefer testing against a deliberately throttled connection, e.g.
OS-level network link conditioning, or a temporary `delay()` inserted for the
test only and removed before verifying the final diff):
1. Open a playlist with at least 4-5 distinct songs.
2. Rapidly tap "Remove from Playlist" (long-press -> Remove -> confirm) on two
   or three different songs in quick succession, ideally under artificial
   network latency.
3. **Before the fix**: reload the playlist detail screen (navigate away and
   back, or pull-to-refresh if available) and compare the resulting
   server-side playlist against what the optimistic UI showed right after the
   taps — under enough latency/timing pressure, a different song than
   expected may be missing, or the wrong final order may result.
4. **After the fix**: repeat the same rapid-tap sequence; confirm the reloaded
   playlist always matches exactly what the optimistic UI showed (the correct
   songs removed, none extra, none missing).
5. Confirm the failure-doesn't-block-later-removals case: with airplane mode
   or a deliberately broken network briefly toggled during the first removal
   of a burst, confirm the second/third queued removals still complete once
   they're reached in the queue.
6. Run `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug testDebugUnitTest` per `.ai/PROJECT.md`'s
   standard verification list. (Note: `lintDebug` currently fails on one
   pre-existing, unrelated error at `MainActivity.kt:90` — see BUG-001's
   `.ai/VERIFICATION.md` entry; this is not a new regression to fix as part of
   this task, but do not introduce *additional* lint errors.)

## Codex Handoff
When ready, replace `IDLE` with `READY FOR CODEX`. Codex must perform an
independent feasibility challenge before implementation and record its
evidence in `.ai/VERIFICATION.md`.
