# Verification — BUG-005

Status: `VERIFICATION PASSED: READY TO COMMIT`

## Feasibility Review
Result: `DESIGN FEASIBLE`

The installed MMD 1.0.0 bytecode exposes the expected controlled-slider overload with both `onValueChange` and `onValueChangeFinished`. The fix requires only local Compose state in `NowPlayingScreen`; no ViewModel, Media3, API, dependency, or persistence contract changes are needed.

## Reproduction
Proven by static execution-path inspection before editing. `NowPlayingScreen` passed every `SliderMMD.onValueChange` value directly to `onSeek`; `MainActivity` wired that callback to `viewModel.seekTo(position, mediaController)`; and `seekTo` immediately called `MediaController.seekTo`. Meanwhile, the slider value came directly from `currentPositionMs`, which `startPlaybackMonitoring` replaces with `controller.currentPosition` every polling iteration. A physical gesture was not run because no device/emulator was connected.

The MMD bytecode signature was independently confirmed with `javap`: `SliderMMD(float, Function1<Float, Unit>, Modifier, boolean, ClosedFloatingPointRange<Float>, int, Function0<Unit>, SliderColorsMMD, MutableInteractionSource, ...)`.

## Root Cause
The composable had no gesture-local preview value and performed the real seek inside the continuously invoked `onValueChange` callback. Consequently, each drag update sought the player, while unrelated live-position recompositions could replace the slider value during the same gesture.

## Files Changed
- `app/src/main/java/com/erdman/erdstream/ui/NowPlayingScreen.kt`: keep the latest gesture value in non-saveable remembered local state, render it during the gesture, issue one seek from `onValueChangeFinished`, then clear the preview so live playback position resumes control.
- `.ai/VERIFICATION.md`: record BUG-005 implementation and verification evidence.

## Regression Test
No automated test was added. The project has no Compose UI test infrastructure, and adding it solely for this interaction is explicitly a non-goal. Existing four JUnit tests pass. Manual gesture checks remain required on hardware/emulator.

## Commands Executed
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home /opt/homebrew/opt/openjdk@17/bin/javap -classpath /Users/david/.gradle/caches/9.0-milestone-1/transforms/e30b669a2d97b0c7283503f9396a968a/transformed/mmd-core-release/jars/classes.jar com.mudita.mmd.components.slider.SliderMMDKt` — succeeded; confirmed callback signature.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew assembleDebug` — `BUILD SUCCESSFUL in 4s`; 36 tasks, 4 executed and 32 up-to-date.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew testDebugUnitTest` — `BUILD SUCCESSFUL in 1s`; four tests, zero failures/errors/skips.
- `JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew lintDebug` — `BUILD SUCCESSFUL in 5s`; 23 tasks, 3 executed and 20 up-to-date.
- `/opt/homebrew/share/android-commandlinetools/platform-tools/adb devices -l` — succeeded; no connected devices.
- `git diff --check` — succeeded with no whitespace errors.
- `git diff --stat`, scoped `git diff` commands, and `git status --short` — inspected repository changes and task scope.

## Results
- Debug build: PASS.
- Unit tests: PASS (4/4).
- Android lint: PASS.
- Device/emulator interaction test: NOT RUN; `adb devices -l` reported no connected targets.
- API 28: compile compatibility unchanged; runtime behavior not exercised.
- Media3 service/session lifecycle, notification controls, audio focus, foreground/background transitions, process recreation, cache, and offline/reconnect: production code paths unchanged. The drag-interrupted recreation behavior is bounded because `remember`, rather than `rememberSaveable`, does not restore the preview into a recreated composition; runtime verification was unavailable.

Gradle emitted the pre-existing warning that Android Gradle Plugin 8.2.0 was tested only through compileSdk 34 while the project uses compileSdk 35, plus Gradle 10 deprecation warnings. Neither warning failed a gate or originated in this change.

## Deviations From Contract
NONE.

## Residual Risk
- Slow streamed/transcoded drag smoothness, exactly-one-seek behavior, tap-to-seek, rapid successive drags, and interrupted-drag recreation remain unverified on a real device/emulator.
- Correct tap behavior follows MMD's compiled slider implementation: tap updates the controlled value and invokes the finish callback, but it was not physically exercised here.

## Diff Review
The implementation diff is limited to `NowPlayingScreen.kt` plus this required verification record. `ErdStreamViewModel.kt` was not changed. The existing modified `.ai/BUG_BACKLOG.md` and `.ai/CURRENT_TASK.md` were read but not edited. The unrelated pre-existing `PlaybackService.kt` Bluetooth AVRCP/ICY-title work was not edited; its scoped diff hash at final inspection was `3b272493056babc5f31b53da4e7a4d005f4b7e55b70a77283d0490441ef72f08`.

No commit or push was performed.

## Claude Review

Independently inspected the actual diff (not just this record):
`NowPlayingScreen.kt` only, 12 lines changed, exactly matches the intended
minimal fix — `seekPreview: MutableState<Float?>` holds the in-progress
drag value; the slider renders `seekPreview.value ?: <live position>`;
`onValueChange` now only updates that local preview (no more per-pixel
`onSeek`); `onValueChangeFinished` fires the real `onSeek` exactly once
using the final preview value, then clears it back to `null` so live
polling resumes control. This is the textbook Compose seek-bar pattern and
directly closes both halves of the root cause (per-pixel seek spam, and
the live-poll-vs-drag race).

**Four adversarial scenarios:**
1. *Tap-to-seek (no sustained drag)* — a plain tap fires one
   `onValueChange` (sets the preview) immediately followed by
   `onValueChangeFinished` (sends the seek, clears the preview). AC #4
   holds.
2. *Drag interrupted by recreation mid-gesture* — `seekPreview` uses
   `remember`, not `rememberSaveable`, so an in-flight drag's preview is
   lost on recreation and that gesture's seek never fires. This is a real,
   disclosed behavior change from before the fix (previously, partial
   per-pixel seeks already sent up to the interruption point would have
   stuck) — but it's the same trade-off any release-triggered action makes
   under interruption, isn't a stuck/inconsistent state (satisfies the
   contract's actual requirement), and is explicitly called out in the
   Residual Risk section rather than hidden. Acceptable, not a defect.
3. *Rapid successive drags* — `onValueChangeFinished` resets
   `seekPreview.value = null` at the end of every gesture, so each
   subsequent drag starts clean with no leftover state. Exactly one seek
   per gesture, per AC.
4. *`durationMs` not yet known (0) when a drag starts* — `onValueChange`'s
   `if (durationMs > 0)` guard means the preview is never set in this
   case, identical to the pre-fix behavior where `onSeek` was gated behind
   the same check. Not a new edge case, not a regression.

No remaining failure mode found beyond what's already disclosed (no
on-device gesture verification — no emulator/device was attached to
either of us this round, consistent with precedent on BUG-001/003/004).
No simpler implementation available. No missing test given this project's
explicit, precedented no-Compose-UI-test-infra constraint.

**Diff scope confirmed clean:** only `NowPlayingScreen.kt` plus this
record. Re-diffed the unrelated uncommitted `PlaybackService.kt` AVRCP
work myself — still byte-identical to its pre-task state.

No Critical/High defect remains.

Approval:
`VERIFICATION PASSED: READY TO COMMIT`
