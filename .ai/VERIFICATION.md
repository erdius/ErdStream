# Verification — BUG-004

Status: `VERIFICATION PASSED: READY TO COMMIT`

Codex owns execution evidence here; Claude reviews it against the repo and task contract.

## Feasibility Review
Result: `DESIGN FEASIBLE`

Read `AGENTS.md`, `.ai/PROJECT.md`, `.ai/ARCHITECTURE.md`,
`.ai/CURRENT_TASK.md`, and `.ai/BUG_HUNT.md` in full. Independently inspected
the queue, its JVM tests, the playlist-details call site, and
`loadPlaylistDetail`. The contract is compatible with the existing coroutine
and test setup; no dependency or architectural change is needed.

## Reproduction

Proved from source and the pre-fix regression test before editing. The existing
`handledFailureDoesNotSkipLaterRemovals` test modeled a handled first failure
and explicitly asserted that queued indices `1` and `2` were still attempted.
In production, each index is computed against an optimistically shortened list.
A failed request awaits an authoritative reload, then returns normally, allowing
the worker to send queued pre-reload indices. A live reproduction was unavailable
because no device/emulator is attached.

## Root Cause

The queue's `suspend (Int) -> Unit` worker had no failure signal and always
continued. The UI handled and swallowed removal failures after reloading, so the
worker could not know that every pending optimistic index was stale.

## Files Changed

- `app/src/main/java/com/erdman/erdstream/PlaylistRemovalQueue.kt`: callback
  now returns success/failure; after failure, the worker drains pending indices.
- `app/src/main/java/com/erdman/erdstream/MainActivity.kt`: returns `true`
  after server success and `false` after the existing error/reload path.
  `CancellationException` is still rethrown.
- `app/src/test/java/com/erdman/erdstream/PlaylistRemovalQueueTest.kt`: asserts
  failure drops queued followers while retaining FIFO/disposal coverage.

## Regression Test

`failedRemovalDropsIndicesQueuedBehindIt` holds the first item in flight,
queues two followers, reports failure, and asserts neither follower reaches the
callback. No mocking library was needed.

## Commands Executed

1. `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew testDebugUnitTest`
2. `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug lintDebug`
3. `adb devices -l`
4. `rg -n "tests=|failures=|errors=|skipped=" app/build/test-results/testDebugUnitTest/*.xml`
5. `git diff --check`
6. `git status --short`, `git diff --stat`, and scoped `git diff` reviews.

## Results

- Build: `assembleDebug` passed (`BUILD SUCCESSFUL`).
- Unit tests: 4 tests passed; 0 failures, errors, or skipped tests.
- Lint: `lintDebug` passed (`BUILD SUCCESSFUL`).
- Device: not run; `adb devices -l` listed zero devices.
- Only existing AGP/compileSdk and Gradle deprecation warnings were emitted.

## Deviations From Contract

`NONE`.

## Residual Risk

No device/server evidence confirms the airplane-mode/throttled-request flow,
UI error presentation, or client/server convergence. The JVM test proves the
queue-level invariant. In production, enqueue and drain run on the Compose-owned
main coroutine, so pre-reload taps cannot interleave between the callback
returning `false` and the non-suspending drain. Cross-thread producers are not
part of this queue's contract.

## Diff Review

`git diff --check` passed. The implementation diff is limited to the two
contract-listed production files and one queue test. Pre-existing changes to
`.ai/BUG_BACKLOG.md` and `.ai/CURRENT_TASK.md` were read but not edited by
Codex. The unrelated `PlaybackService.kt` ICY-title/Bluetooth AVRCP diff was
inspected before and after implementation and left untouched.

## Claude Review

Independently inspected the actual diff (not just this record) across all
four changed files, plus reran the reasoning behind the fix rather than
trusting the summary alone.

**Root cause genuinely fixed, not masked.** The old worker (`for (index in
indices) remove(index)`) had no way to know a removal had failed; the new
one (`if (!remove(index)) { while (indices.tryReceive().isSuccess) {} }`)
drains every index still sitting in the channel the instant a removal
reports failure — implementing Acceptance Criteria option (a) (drop the
whole pending queue) exactly as the contract asked for, with no new
abstraction.

**Found and confirmed correct — a real pre-existing test had encoded the
bug as expected behavior.** `handledFailureDoesNotSkipLaterRemovals`
literally asserted `attempted == listOf(0, 1, 2)` after a failure — i.e.
it was a regression test *for the bug itself*. Codex replacing it with
`failedRemovalDropsIndicesQueuedBehindIt` (asserting `attempted ==
listOf(0)`) is the correct call, not scope creep — the old test would make
this fix look like a failure if left in place, and the new one is a real,
non-tautological assertion that fails against the pre-fix code.

**Four adversarial scenarios constructed:**
1. *Concurrent tap during the reload window* — a tap that lands while
   `loadPlaylistDetail` is still in flight enqueues an index that's
   probably also stale; it's synchronous `trySend`/`tryReceive` on the
   same Compose main-thread coroutine, so it either lands before the drain
   (and gets correctly discarded too) or after (and is valid against the
   now-reloaded state). No window where a bad index slips through.
2. *True concurrency/interleaving* — `enqueue()` (UI click) and the drain
   `while` loop both run on `rememberCoroutineScope()`'s
   `Dispatchers.Main.immediate`-equivalent single thread, and the drain
   loop contains no suspension point, so it can't be interrupted
   mid-drain by a click event. Matches Codex's own residual-risk note;
   verified independently rather than taken on faith.
3. *Partial-batch failure (success, then failure, then a third queued
   item)* — traced by hand: index 2 succeeds, index 5 fails and reloads
   (list now correctly missing only index 2's song), index 7 (computed
   against the old two-already-gone optimistic list) is correctly
   discarded rather than sent with a doubly-stale offset.
4. *Regression on the success path / ordering guarantee* — the `for`
   loop's happy path is textually unchanged; BUG-002's FIFO/one-at-a-time
   guarantee is intact. Confirmed via the untouched `enqueuedInTapOrder`
   and `disposeCancelsWorkerAndStopsFurtherProcessing` tests, which still
   pass.

No remaining failure mode found beyond what's already disclosed as
residual risk (no live device verification — acceptable, matches how
BUG-001/002/003 were also verified without device access when none was
attached). No simpler implementation available — draining with
`tryReceive` is already about as minimal as this gets. No missing test —
the new test is the right one and it's non-tautological.

**Diff scope confirmed clean:** exactly `PlaylistRemovalQueue.kt`,
`MainActivity.kt`'s one call site, and the test file — nothing else. The
unrelated uncommitted `PlaybackService.kt` AVRCP diff was independently
re-diffed against its pre-task state and is byte-identical — genuinely
untouched.

No Critical/High defect remains.

Approval:
`VERIFICATION PASSED: READY TO COMMIT`

## Final Status
READY FOR CLAUDE ADVERSARIAL REVIEW
