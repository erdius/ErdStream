# Verification — Bluetooth AVRCP radio metadata

Status: `VERIFICATION PASSED: READY TO COMMIT`

## Feasibility Review

Result: `DESIGN FEASIBLE`

The existing change is compatible with pinned Media3 1.3.1 and requires no dependency, persistence, API-level, or architectural change. It remains confined to `PlaybackService`: ICY callbacks update the existing UI-facing manager and independently update current-item metadata for MediaSession/AVRCP clients.

## Verification Findings

- Exact AndroidX Media3 1.3.1 `ExoPlayerImpl.java` source was inspected from tag `1.3.1`. For a compatible metadata-only replacement, `replaceMediaItems` takes `updateMediaSourcesWithMediaItems`, preserves the source-holder/window UID, and calls `updatePlaybackInfo` without position discontinuity. `evaluateMediaItemTransitionReason` returns false because the window UID is unchanged and there is no repeat/seek discontinuity; therefore `EVENT_MEDIA_ITEM_TRANSITION` / `onMediaItemTransition` is not emitted.
- Official APIs agree: replacing the current item with compatible content (same URL, metadata-only change) can continue seamlessly; ExoPlayer documents that retained MediaSources do not interrupt playback.
- Runtime corroboration: across a real title change, PID `5646`, active item `0`, queue size `1`, and `STATE_PLAYING` at speed 1.0 stayed stable. Position advanced from 108568 ms at 07:33:19 to 144774 ms at 07:33:55 while the title changed, without reset/error/restart. It later remained playing at 250226 ms.
- No feedback loop exists. `PlaybackService.onMetadata` independently writes to `radioMetadataManager` and player metadata. `ErdStreamViewModel` reads only the manager and dedupes with its separate `lastProcessedIcyTitle`; it never writes player metadata.
- Regular Subsonic playback is unaffected because the helper is invoked only for `IcyInfo` in `onMetadata`.
- Player callbacks/commands share the application thread; the helper's current-item lookup and replacement are synchronous. Blank titles/missing items return early, and service recreation starts with a null dedupe field.

### Real device

Mudita Kompakt `MK20250402537`, Android API 31:

- Release `run-as` was correctly refused because the app is non-debuggable. UI hierarchy showed one preserved station: `ErdRadio`, `https://radio.erdcloud.org/stream.mp3`. No station was added, edited, or deleted.
- Initial: PID 5646, playing, position 8859 ms, buffered 31190 ms, item 0, live MediaSession title `Wake Up Dead (Remastered)` rather than static `ErdRadio`.
- 07:31:30: position 20923 ms, `Wake Up Dead (Remastered)`.
- 07:32:10: position 60105 ms, same title.
- 07:32:39: position 66423 ms, same title.
- 07:33:19: position 108568 ms, same title.
- 07:33:55: position 144774 ms, title changed to `Doin' the Shout` with unchanged PID/item/state.
- 07:34:35: position 183916 ms, `Doin' the Shout`.
- Final: position 250226 ms, same PID/item/state/title.
- Notification updated to title `Doin' the Shout`, artist `Hooker John Lee`, action `Pause`. App UI independently showed the same title/artist and station `ErdRadio`.
- Process-scoped and broad logcat scans found no ErdStream fatal exception, playback exception, ExoPlayer exception, or app error. Broad `AndroidRuntime` matches were normal uiautomator shell startup/shutdown.
- No temporary logging was added. Source proves transition semantics; dumpsys proves continuity. Stable state/identity/position objectively rule out restart/rebuffer; no human acoustic measurement was available.

### Signing and in-place installation

- `assembleRelease`: `BUILD SUCCESSFUL in 3s` (48 tasks; 1 executed, 47 up-to-date).
- `apksigner verify --verbose --print-certs` verified both fresh and pre-install device APKs using v2, one RSA-2048 signer, DN `CN=David Erdman, OU=ErdStream, O=David Erdman, L=Unknown, ST=Unknown, C=US`, certificate SHA-256 `2d659d99687eafb95f98e434e4396dc820fd15c31905ecf0dc3e6db26b410e35`. This is the release identity, not Android debug.
- The sole install command was exactly `adb -s MK20250402537 install -r app/build/outputs/apk/release/ErdStream-0.3.10.apk`; it returned `Performing Streamed Install` then `Success`. No uninstall, fallback, or data clear occurred.
- Existing station data remained visible. Version stayed 0.3.10/code 24; firstInstallTime stayed `2026-07-27 07:10:24`, lastUpdateTime became `2026-09-11 07:30:09`.
- Baseline correction: pre-update and fresh APK SHA-256 were identically `6f27d25404aba96926c96084e278772a0405894d94627fefb6b1e4410f8c13ad`. The device already had byte-identical current release bits despite the stated older-code baseline. The requested in-place update still succeeded; runtime proves these release bits contain the fix. Source mtime was Aug 30 and APK mtime Sep 11 07:20.

## Files Changed

- `PlaybackService.kt`: pre-existing AVRCP fix; inspected, not rewritten.
- `.ai/VERIFICATION.md`: this evidence record.
- `.ai/CURRENT_TASK.md`: pre-existing contract modification; read, not edited by Codex.

## Commands Executed

- Required file/diff/status inspections and `adb devices -l`.
- Exact release Gradle command requested by user.
- Read-only installed-APK pull; `apksigner verify --verbose --print-certs` for both APKs.
- Exact `adb ... install -r ...` above; no other install/uninstall command.
- `run-as` preference read (expected non-debuggable refusal); UI automation with `monkey`, `input tap`, `uiautomator dump`.
- Repeated `dumpsys media_session`; `dumpsys notification --noredact`; `logcat`; `dumpsys package`; API query.
- Official Media3 source download and scoped inspection.
- `JAVA_HOME=... ./gradlew assembleDebug`, `testDebugUnitTest`, and `lintDebug`.
- Test XML inspection, `shasum -a 256`, `stat`, `git diff --check`, scoped diff/stat/status.

Initial apksigner calls without `JAVA_HOME` failed before verification; reruns with JDK 17 succeeded. The normal runner rejected the configured symlinked workspace root, so approved commands used resolved `/Volumes/Projects/ErdStream`.

## Results

- Release build/signature/in-place install: PASS.
- Debug build: PASS (`BUILD SUCCESSFUL in 724ms`).
- Unit tests: PASS (4 tests; 0 failures/errors/skips; `BUILD SUCCESSFUL in 400ms`).
- Lint: PASS (`BUILD SUCCESSFUL in 463ms`).
- Device MediaSession live metadata: PASS; two titles over several minutes with continuous playback.
- Notification and independent UI metadata: PASS.
- Crash/exception scan: PASS.
- `git diff --check`: PASS.

Pre-existing warnings: AGP 8.2.0 tested through compileSdk 34 while project uses 35; Gradle 10 deprecations. Neither failed a gate or originated here.

## Deviations From Contract

- User's explicit release/in-place instruction superseded the generic debug-install matrix, preventing destructive uninstall/data loss.
- Release `run-as` could not inspect preferences; the preserved station was inspected through UI.
- No Bluetooth receiver was used, consistent with the non-goal; MediaSession metadata was verified directly.

## Residual Risk

- No paired AVRCP head unit was observed.
- Audible continuity was not assessed by a human; continuous state/position and clean logs are the objective proxy.
- API 28 runtime was not exercised (device API 31); no API-sensitive surface changed and minSdk compilation passed.
- A physical user-skip race was not injected; application-thread serialization and synchronous lookup cover the code-level risk.

## Diff Review

Implementation scope is the pre-existing `PlaybackService.kt` diff plus this record. `.ai/CURRENT_TASK.md` was already modified and was not edited by Codex. No temporary logging, dependencies, UI, persistence, credentials, cache, or unrelated code changed. No commit or push was performed.

## Claude Review

Independently re-verified rather than trusting the record alone: the
`PlaybackService.kt` diff is still byte-for-byte what it was before this
task started (`git diff --stat` unchanged at 44 insertions/1 deletion — no
rewrite occurred), and Media3 1.3.1 is genuinely what's pinned in
`app/build.gradle.kts`, matching what the source-inspection evidence is
based on.

This is the most rigorous verification pass of this session — real Media3
1.3.1 source-code tracing *and* multi-minute real-device observation, two
independent lines of evidence converging on the same answer.

**Five adversarial angles:**
1. *Is "no `onMediaItemTransition`" actually proven, or just plausible?*
   Proven two ways independently: source-level (`evaluateMediaItemTransitionReason`
   returns false when the window UID is unchanged, traced in the real
   1.3.1 `ExoPlayerImpl` source) and empirically (position advanced
   continuously — 108568ms → 144774ms → 183916ms → 250226ms — straight
   through a real title change, with no reset toward zero that a
   transition-triggered reload would produce).
2. *Could a transition fire without visibly disrupting playback,
   invalidating the "no reset" evidence as proof of "no transition"?*
   No — this wasn't inferred from symptoms alone; the source trace
   directly shows the internal code path decides not to raise the event
   at all under this condition. The device evidence corroborates rather
   than substitutes for that.
3. *Does this reintroduce AC #1's original worry — `onMediaItemTransition`
   spuriously resetting `lastAppliedRadioTitle`/`radioMetadataManager`
   mid-station?* Resolved by #1 above: since transition genuinely doesn't
   fire for metadata-only swaps, the reset code (which is real and correct
   for genuine station switches) stays dormant exactly when it should.
4. *Feedback loop between the two independent ICY consumers?* None
   possible — `radioMetadataManager` (UI path) and
   `applyIcyTitleToMediaItem` (MediaSession path) are parallel branches off
   the same `onMetadata` callback with separate dedup fields; neither
   reads the other's output.
5. *Is the device evidence genuine, not a coincidental static fallback?*
   Two distinct, specific real song titles were captured
   ("Wake Up Dead (Remastered)" then "Doin' the Shout", artist "Hooker
   John Lee" on the notification) — consistent, varied, specific detail
   that only live ICY parsing would produce, cross-confirmed
   independently across the MediaSession dump, the system notification,
   and the app's own UI simultaneously.

No remaining failure mode found. No simpler implementation — this is
already the minimal fix (one field, one method, using
`replaceMediaItem`'s documented same-window-UID fast path). No missing
test — Compose/Media3 UI test infra isn't present and wasn't required by
the contract; the real-device evidence substitutes appropriately here,
consistent with this project's established precedent. Diff scope is
exactly `PlaybackService.kt` — no temporary logging was left behind, no
unrelated files touched, no data loss on the real device (verified: same
`firstInstallTime`, preserved radio station, in-place signed install).

No Critical/High defect remains.

Approval:
`VERIFICATION PASSED: READY TO COMMIT`
