# Verification — BUG-002

Status: `READY FOR CLAUDE ADVERSARIAL REVIEW` (Android verification blocked)

## Feasibility and reproduction

Read AGENTS.md, BUG_HUNT.md, PROJECT.md, ARCHITECTURE.md, CURRENT_TASK.md and relevant source/build configuration. Independently confirmed before editing: each tap launched an independent coroutine in MainActivity; removeSongFromPlaylist switches to IO and directly calls the positional updatePlaylist endpoint. There is no intervening serialization. The diagnosis is feasible with existing coroutine dependencies and no API changes.

Deterministic executable reproduction models the original independent-launch behavior: starting with A,B,C,D, taps at indices 0 then 1 should leave B,D; allowing request 2 to finish first leaves C,D. This passed as an assertion of the original defect. It is a concurrency model, not a live-server reproduction; it was executed after implementation.

## Root cause and implementation

Independent positional requests could overlap and complete out of tap order. PlaylistRemovalQueue receives indices synchronously through an unlimited Channel and has exactly one consumer. The consumer awaits the entire removal/rollback callback before receiving the next index. FIFO follows synchronous enqueue order and a single consumer; no lock-fairness assumption is used.

MainActivity remembers a queue for the selected playlist within the details destination and disposes it when the destination leaves composition or the playlist key changes. Disposal cancels both queued work and the consumer. The existing synchronous optimistic update remains unchanged. Each request still catches failures and runs playlistError = errorText(e), then loadPlaylistDetail(playlistId). Cancellation is rethrown in the request handler and detail loader so navigation cancellation is not treated as a network failure.

## Changed files

- app/src/main/java/com/erdman/erdstream/MainActivity.kt — queue integration and cancellation propagation during rollback.
- app/src/main/java/com/erdman/erdstream/PlaylistRemovalQueue.kt — small dedicated FIFO worker with disposal.
- app/src/test/java/com/erdman/erdstream/PlaylistRemovalQueueTest.kt — four deterministic JUnit regression tests, no sleeps or added dependencies.
- .ai/VERIFICATION.md — this evidence.

Pre-existing changes in PlaybackService.kt, BUG_BACKLOG.md and CURRENT_TASK.md were left untouched. No repository/API signatures or unrelated operations changed.

## Commands and results

1. `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/erdstream-bug001-gradle ./gradlew assembleDebug testDebugUnitTest lintDebug --offline` — exit 1 before build: wrapper download failed with UnknownHostException: services.gradle.org. Log: /private/tmp/erdstream-bug002-checks.log.
2. `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/erdstream-bug001-gradle /Users/david/.gradle/wrapper/dists/gradle-9.0-milestone-1-bin/3vdepk4s12bybhohyuvjcm1bd/gradle-9.0-milestone-1/bin/gradle assembleDebug testDebugUnitTest lintDebug --offline --no-daemon` — exit 1 before build: FileLockContentionHandler could not open a socket (Operation not permitted). Log: /private/tmp/erdstream-bug002-cached-gradle.log.
3. `/opt/homebrew/share/android-commandlinetools/platform-tools/adb devices` — exit 1: daemon could not install smartsocket listener (Operation not permitted). No device/instrumentation checks could run.
4. `python3 /private/tmp/erdstream-bug002-tests.py` — compiled the actual queue and test source with cached Kotlin 1.9.10, coroutines 1.7.3 and JUnit 4.13.2, then executed JUnitCore: OK (4 tests). Exact expanded commands/output below. This is independent JVM verification, not Gradle dependency-resolution or Android integration verification.
5. `git diff --check` — exit 0. Inspected MainActivity diff and new queue/tests for unrelated changes.

### Claude's re-run (unsandboxed host shell, not Codex's exec sandbox)

Codex's own Gradle/ADB invocations were blocked by its exec sandbox (file-lock
socket and ADB smartsocket permission errors above). Claude re-ran the same
tasks directly from `/Users/david/Projects/ErdStream` (unsandboxed):

- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug testDebugUnitTest` →
  **BUILD SUCCESSFUL**. This time `testDebugUnitTest` actually compiled and ran
  (not `NO-SOURCE`, since `PlaylistRemovalQueueTest.kt` now exists) —
  `app/build/test-results/testDebugUnitTest/TEST-com.erdman.erdstream.PlaylistRemovalQueueTest.xml`
  shows `tests="4" failures="0" errors="0"`, matching Codex's manual-classpath
  run but this time through the project's real Gradle/AGP/Kotlin toolchain,
  not a hand-assembled classpath.
- `JAVA_HOME=... ./gradlew lintDebug` → still fails, but on the exact same
  single pre-existing error as BUG-001 (`MainActivity.kt:91` —
  `UnsafeOptInUsageError` on `application as ErdStreamApplication`; line number
  shifted from 90 to 91 only because of the new `CancellationException`
  import). No new lint error was introduced by this diff.

## Regression coverage

- Deterministic original race demonstrates wrong surviving songs.
- Three queued removals: first suspended behind a gate; only first entered until released, then exact tap order and expected final songs.
- First callback handles a simulated failure; second and third still execute.
- Disposing a blocked old queue drops pending work; a fresh queue executes independently.

## Runtime results and remaining risks

Full Android compilation, Gradle unit task, lint, Compose/instrumentation and API 28/device checks remain unverified due to environment restrictions above. No claim is made about the previously reported lint error being the only current error. No playback code was changed; playback runtime matrix was not exercised.

Real-server latency, failure/reconnect, and rapid playlist navigation still need manual validation. Cancellation cannot undo a removal already accepted by the server. Pending removals are discarded on destination disposal; no worker map accumulates across playlists. Tests exercise the queue lifecycle, not actual Compose navigation or HTTP cancellation.

The contract explicitly preserves already-enqueued positional indices after an earlier failure. Those indices can become stale if that removal failed to alter the server playlist; serialization alone does not solve this existing failure-reconciliation limitation. The current rollback behavior can also reload before later queued requests finish. Neither is claimed fixed here.

## Contract deviations

No dependency, API or optimistic-UX changes. Used the expressly permitted single-consumer Channel construction in a small dedicated helper instead of an inline Job chain. Navigation cancellation is propagated rather than displayed as an error. Mandatory Android checks were attempted but blocked; standalone executable tests provide bounded evidence only.

## Claude Adversarial Review

Read `PlaylistRemovalQueue.kt` and `PlaylistRemovalQueueTest.kt` in full, and
the actual `git diff` for `MainActivity.kt` (not just Codex's summary).

Root cause fixed, not masked: ordering is now guaranteed by construction (one
`Channel`, one consumer coroutine draining it in send order), exactly what
Acceptance Criteria #2 required — no `Mutex`-fairness assumption was
introduced.

Three adversarial scenarios constructed:
1. **Composable re-entry / navigation churn.** `remember(selectedPlaylistId)`
   only rebuilds the queue when the key actually changes; re-entering the same
   destination (leaving `PlaylistDetails` and coming back to the same
   playlist) destroys the whole subtree first, so a fresh `remember` block and
   a fresh `DisposableEffect` always pair up — no way to end up with two live
   queues for one playlist, or a queue outliving its `DisposableEffect`.
   Confirmed `onDispose { removalQueue.dispose() }` is keyed on `removalQueue`
   itself, so a key change disposes the *old* instance specifically, not
   whichever queue happens to be current.
2. **In-flight network call at disposal time.** `dispose()` cancels both the
   `Channel` and the consumer `Job`. If `remove(index)` is suspended inside
   the repository's OkHttp call when cancelled, the request may already be
   in-flight/received server-side even though the client stops waiting for a
   response — a real but pre-existing risk (the old code's `scope.launch` was
   equally cancelable via the same `rememberCoroutineScope()`), not a
   regression introduced here. Codex's own "Remaining risks" section already
   discloses this rather than hiding it.
3. **Failure ordering under a queued burst.** The
   `handledFailureDoesNotSkipLaterRemovals` test proves a failure at position 0
   doesn't stop 1 and 2 from running — matches Acceptance Criteria #4. Checked
   the `catch (e: CancellationException) { throw e }` guard added ahead of the
   generic `catch (e: Exception)` in both the `remove` lambda and
   `loadPlaylistDetail`: without it, a `dispose()`-triggered cancellation
   mid-flight would otherwise be swallowed by the generic catch and
   misreported as `playlistError`, corrupting the UI with a spurious error
   message on ordinary navigation. This is exactly the kind of subtle
   lifecycle bug this contract's Failure Modes section asked to guard against,
   and it's handled correctly.

Simpler/safer alternative considered: contract shape (b) (an inline
`Job`-chain in `MainActivity.kt`) was also on the table; the dedicated
`PlaylistRemovalQueue` class is if anything *more* readable given
`MainActivity.kt`'s existing size, and is independently unit-tested, which an
inline chain would not have been. No simpler implementation identified.

Missing test: none of the four tests exercise the real Compose
`remember`/`DisposableEffect` wiring or actual HTTP cancellation — this is a
known, disclosed gap (queue *logic* is unit-tested; queue *integration* is
not), consistent with this contract's Test Matrix allowing device verification
to stand in where Compose/network fakes aren't practical. No on-device
verification was possible in either Codex's or Claude's environment (no
attached device/emulator — same limitation as BUG-001).

Re-ran the build independently (see Commands and results, Claude's re-run):
`assembleDebug` + `testDebugUnitTest` **BUILD SUCCESSFUL**, all 4 new tests
pass through the project's real Gradle toolchain (not just Codex's manual
classpath). `lintDebug` fails only on the same pre-existing, unrelated
`MainActivity.kt` error already documented in BUG-001's verification — no new
lint error introduced.

No remaining Critical/High defect found.

## Final Status
VERIFICATION PASSED: READY TO COMMIT

## Exact standalone test execution

```text
COMMAND: /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java -cp /Users/david/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-compiler-embeddable/1.9.10/57ca1b0823ae3ecb451a97e1f8e6de0b19ea5294/kotlin-compiler-embeddable-1.9.10.jar:/Users/david/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.9.10/72812e8a368917ab5c0a5081b56915ffdfec93b7/kotlin-stdlib-1.9.10.jar:/Users/david/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-reflect/1.6.10/1cbe9c92c12a94eea200d23c2bbaedaf3daf5132/kotlin-reflect-1.6.10.jar:/Users/david/.gradle/caches/modules-2/files-2.1/org.jetbrains.intellij.deps/trove4j/1.0.20200330/3afb14d5f9ceb459d724e907a21145e8ff394f02/trove4j-1.0.20200330.jar:/Users/david/.gradle/caches/modules-2/files-2.1/org.jetbrains/annotations/13.0/919f0dfe192fb4e063e7dacadee7f8bb9a2672a9/annotations-13.0.jar org.jetbrains.kotlin.cli.jvm.K2JVMCompiler -no-stdlib -no-reflect -jvm-target 1.8 -classpath /Users/david/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.9.10/72812e8a368917ab5c0a5081b56915ffdfec93b7/kotlin-stdlib-1.9.10.jar:/Users/david/.gradle/caches/modules-2/files-2.1/org.jetbrains/annotations/13.0/919f0dfe192fb4e063e7dacadee7f8bb9a2672a9/annotations-13.0.jar:/Users/david/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.7.3/2b09627576f0989a436a00a4a54b55fa5026fb86/kotlinx-coroutines-core-jvm-1.7.3.jar:/Users/david/.gradle/caches/modules-2/files-2.1/junit/junit/4.13.2/8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12/junit-4.13.2.jar:/Users/david/.gradle/caches/modules-2/files-2.1/org.hamcrest/hamcrest-core/1.3/42a25dc3219429f0e5d060061f71acb49bf010a0/hamcrest-core-1.3.jar -d /private/tmp/erdstream-bug002-test-classes app/src/main/java/com/erdman/erdstream/PlaylistRemovalQueue.kt app/src/test/java/com/erdman/erdstream/PlaylistRemovalQueueTest.kt
COMMAND: /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home/bin/java -cp /private/tmp/erdstream-bug002-test-classes:/Users/david/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib/1.9.10/72812e8a368917ab5c0a5081b56915ffdfec93b7/kotlin-stdlib-1.9.10.jar:/Users/david/.gradle/caches/modules-2/files-2.1/org.jetbrains/annotations/13.0/919f0dfe192fb4e063e7dacadee7f8bb9a2672a9/annotations-13.0.jar:/Users/david/.gradle/caches/modules-2/files-2.1/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.7.3/2b09627576f0989a436a00a4a54b55fa5026fb86/kotlinx-coroutines-core-jvm-1.7.3.jar:/Users/david/.gradle/caches/modules-2/files-2.1/junit/junit/4.13.2/8ac9e16d933b6fb43bc7f576336b8f4d7eb5ba12/junit-4.13.2.jar:/Users/david/.gradle/caches/modules-2/files-2.1/org.hamcrest/hamcrest-core/1.3/42a25dc3219429f0e5d060061f71acb49bf010a0/hamcrest-core-1.3.jar org.junit.runner.JUnitCore com.erdman.erdstream.PlaylistRemovalQueueTest
JUnit version 4.13.2
....
Time: 0.028

OK (4 tests)

```

READY FOR CLAUDE ADVERSARIAL REVIEW
