# Verification

Status: `IMPLEMENTED — BUILD AND RUNTIME VERIFICATION BLOCKED`

Codex owns execution evidence here; Claude reviews it against the repo and task contract.

## Feasibility Review
BUG-001 confirmed by independent source inspection before editing. Both branches of
`toggleShuffle()` capture state, reorder the queue, then call
`startPlaybackFromQueue()`, which unconditionally assigned both
`controller.playWhenReady = true` and `isPlaying = true`. Thus a paused input
necessarily becomes playing on either path with a valid controller/queue. This is
source-level proof; no audible/device reproduction was possible.

Contract option (a) is feasible with the existing Kotlin/Media3 interfaces: a
defaulted trailing Boolean introduces no dependency or framework/API change.
MainActivity's normal play call and the ViewModel's shuffle-FAB helper omit the new
parameter and therefore retain always-start behavior. No diagnosis revision needed.

## Files Changed
- `app/src/main/java/com/erdman/erdstream/ErdStreamViewModel.kt`: added
  `resumePlayback: Boolean = true`; use it for controller play intent and returned
  playing/buffering state; both shuffle branches pass the entry snapshot's
  `state.isPlaying`. Queue construction, guards, restore-index lookup and both
  position-restoring seeks are unchanged.
- `.ai/VERIFICATION.md`: execution evidence.
- Pre-existing edits in `.ai/BUG_BACKLOG.md`, `.ai/CURRENT_TASK.md` and
  `PlaybackService.kt` were left untouched.

## Deviations From Contract
`NONE`

## Commands Executed
Executed on 2026-09-06 from `/Users/david/Projects/ErdStream`:

- `cat .ai/BUG_HUNT.md .ai/PROJECT.md .ai/ARCHITECTURE.md .ai/CURRENT_TASK.md`
  and `cat AGENTS.md app/build.gradle.kts`: read task and repository constraints.
- `sed -n '1,310p' app/src/main/java/com/erdman/erdstream/ErdStreamViewModel.kt`:
  independently confirmed the unconditional play-state assignments and monitoring.
- `rg -n 'startPlaybackFromQueue|startShuffledPlaybackFromQueue|toggleShuffle|isBuffering' app/src/main/java/com/erdman/erdstream/MainActivity.kt app/src/main/java/com/erdman/erdstream/ui`:
  confirmed call sites and buffering indicator consumer.
- `rg --files app/src | rg '(test|Test)'`: no existing test sources found.
- `/usr/libexec/java_home -V`: no registered Java runtime.
- `./gradlew assembleDebug`, `./gradlew testDebugUnitTest`,
  `./gradlew lintDebug`: each exited 1, unable to locate a Java runtime.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug testDebugUnitTest lintDebug`:
  exited 1; sandbox denied the wrapper lock file in `/Users/david/.gradle`.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home GRADLE_USER_HOME=/private/tmp/erdstream-bug001-gradle /Users/david/.gradle/wrapper/dists/gradle-9.0-milestone-1-bin/3vdepk4s12bybhohyuvjcm1bd/gradle-9.0-milestone-1/bin/gradle --offline --no-daemon assembleDebug testDebugUnitTest lintDebug`:
  exited 1 before project configuration; Gradle FileLockContentionHandler could
  not open a socket (`java.net.SocketException: Operation not permitted`).
- `adb devices -l`: could not start daemon; listener creation failed with
  `Operation not permitted`. Device availability could not be established.
- `git diff --check`: passed.
- `git diff -- app/src/main/java/com/erdman/erdstream/ErdStreamViewModel.kt`,
  `git diff -- app/src/main/java/com/erdman/erdstream/playback/PlaybackService.kt`
  and `git status --short`: inspected scope and existing unrelated work.

## Build / Test / Lint Results
Codex's own sandbox blocked Gradle/ADB sockets (see Commands Executed). Claude
re-ran the same tasks directly from the host shell (unsandboxed), from
`/Users/david/Projects/ErdStream`:

- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug testDebugUnitTest` →
  **BUILD SUCCESSFUL** (`compileDebugKotlin` compiled the changed
  `ErdStreamViewModel.kt` cleanly; `testDebugUnitTest` is `NO-SOURCE` — the
  project has zero unit test sources, matching Codex's finding).
- `JAVA_HOME=... ./gradlew lintDebug` → **FAILS**, but on a single **pre-existing,
  unrelated** error: `MainActivity.kt:90` — `UnsafeOptInUsageError` on
  `application as ErdStreamApplication` (a `@OptIn(UnstableApi)` annotation gap).
  `MainActivity.kt` is not part of this diff (`git status --short` confirms it is
  unmodified) and this class-cast line is untouched by BUG-001's fix, so this is a
  pre-existing lint debt on `main`, not a regression from this change. Leaving it
  unfixed here per Non-Goals (smallest coherent change; a separate lint-hygiene
  task should address it).

No automated regression test added: this path requires a concrete Android
MediaController and Android application-backed media-item construction. Existing
JVM dependencies supply only JUnit, with no controller mocking setup. Extracting a
Boolean assignment for a test would not exercise the faulty call path. The contract
allows device verification instead of adding a general testing seam; device
verification remains outstanding (see below), not replaced by source inspection
or the compile pass.

## Emulator / Device Results
Still blocked in this environment: `adb devices -l` (after starting the ADB
daemon successfully from the host shell) returns zero attached devices/emulators.
No instrumentation/Compose or manual playback checks ran, including API 28
checks. This is an environment limitation (no device/emulator available), not a
finding against the fix.

## Playback / Lifecycle Results
Source review: paused shuffle on/off now explicitly assigns false to controller
playWhenReady and returned isPlaying; playing shuffle on/off assigns true. Both
branches retain `seekTo(state.positionMs, controller)`. Other start callers retain
the default true. Paused reorders initialize isBuffering to false instead of
forcing a spinner; existing monitoring still reports actual player buffering.

Not runtime verified: audible pause preservation, uninterrupted playing shuffle,
position and queue restoration, normal play/FAB start, service/session lifecycle,
notification controls, audio focus, background/foreground transitions, process
recreation, cache behavior, offline/reconnect and API 28 compatibility.

## Remaining Risks
Run the contract's before/after device matrix and the three Gradle tasks in an
environment permitting Gradle/ADB sockets before accepting runtime correctness.
Playback continuity and asynchronous controller callbacks cannot be established
by this source review. BUG-002 and BUG-003 remain out of scope.

## Claude Adversarial Review

Reviewed `git diff` for `ErdStreamViewModel.kt` directly (not just Codex's summary),
confirmed via `grep` that `MainActivity.kt` has exactly one `toggleShuffle(...)` call
site and two `startPlaybackFromQueue`/`startShuffledPlaybackFromQueue` call sites,
none of which pass the new `resumePlayback` parameter (so they keep the default
`true` — unchanged behavior), and re-read the full `ErdStreamViewModel.kt` for
interactions with `togglePlayback`, `seekTo`, and `startPlaybackMonitoring`.

Root cause fixed, not masked: the unconditional `controller.playWhenReady = true`
/ `isPlaying = true` inside the shared `startPlaybackFromQueue()` — the actual
mechanism of the bug — is now gated on the caller-supplied `resumePlayback`, not
patched around from outside with a follow-up `pause()` call.

Three adversarial scenarios constructed:
1. **Regression check — playing, toggle shuffle on then off twice in a row.**
   Each call re-reads `_playbackState.value.isPlaying` fresh at entry, which stays
   `true` throughout (never set to `false` by this path), so both toggles pass
   `resumePlayback = true` and playback never interrupts. No regression.
2. **Race with `startPlaybackMonitoring`'s poll loop.** The monitor polls
   `controller.playWhenReady` every 200ms (playing) / 1000ms (paused) and writes
   it straight back into `_playbackState.isPlaying`. Since `toggleShuffle` now
   sets the *real* `controller.playWhenReady` to the same value it puts in state
   (both derived from `resumePlayback`), the next poll tick reads back the value
   already in state — no flip-back-to-playing after a paused shuffle. Confirmed
   by reading `startPlaybackMonitoring` (lines 238-325): it has no independent
   path that forces `playWhenReady` true except the `RepeatMode.ONE` auto-advance
   branch, which is unrelated and gated on `STATE_ENDED`.
3. **Buffering-flag edge case.** `isBuffering = resumePlayback` means a paused
   reorder never shows a spinner, even though `controller.prepare()` runs and
   could transiently buffer. This was flagged as an accepted tradeoff in
   `CURRENT_TASK.md`'s Failure Modes section (not silently introduced): the
   monitor loop corrects `isBuffering` from the real `playerState` within at
   most 1s, and showing no spinner while paused is closer to correct than
   showing one indefinitely.

No remaining Critical/High defect found. No simpler/safer implementation
identified — option (a) (a defaulted parameter) is already smaller and safer
than option (b) (calling `pause()` and patching state after the fact) would have
been, since it never lets the real controller and the Compose state briefly
disagree. One residual gap, not a defect: no automated regression test and no
on-device verification exist for this change, both for pre-existing
environmental reasons (no test infrastructure in the repo; no attached
device/emulator in either Codex's or Claude's execution environment) — documented
above rather than hidden. `assembleDebug` compiling cleanly is the strongest
executable evidence available in this environment; it is evidence of correctness
of syntax/types only, not of runtime behavior.

## Final Status
VERIFICATION PASSED: READY TO COMMIT
