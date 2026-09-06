# ErdStream Architecture Guidance

Preserve existing repository patterns unless a task explicitly requires architectural change.

## Boundaries to protect
- Compose UI should consume stable observable state rather than own long-running playback/network work.
- Playback belongs behind Media3 service/session boundaries; do not couple UI lifetime to playback lifetime.
- Network API concerns stay separated from UI state and credential storage.
- Credentials must remain encrypted/local and excluded from logs, analytics, crash text, and source control.
- Cache changes must preserve correctness under interruption, partial files, eviction, and reconnects.

## Android/mobile checks
For relevant changes verify process death/recreation, foreground/background transitions, coroutine cancellation, stale responses, duplicate requests, audio focus, notification/media actions, service restart behavior, offline mode, slow/failed server responses, API 28 behavior, and e-ink refresh/animation costs.

## Change discipline
Prefer small diffs. Do not introduce abstraction, dependencies, or broad refactors without a concrete need. Shared/public contracts require explicit migration and regression analysis.
