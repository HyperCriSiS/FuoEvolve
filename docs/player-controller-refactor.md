# FuoPlayerController refactor

The controller migration is intentionally incremental. `FuoPlayerController` remains a compatibility facade while feature state and UI contracts move to narrower owners.

## Phase 1: search ownership

Search ownership has moved out of `FuoPlayerController`.

- `SearchFeatureScreen` consumes `SearchUiState`, provider/download snapshots, `SearchAction`, and narrow cross-feature callbacks instead of the global controller.
- `SearchFeatureController` is constructed by the Android/iOS composition roots. The same owner instance is injected into `FuoAppViewModel` and the compatibility facade, so production has one search state owner.
- The primary `SearchRoute` consumes `SearchAppPort`; it no longer accepts a global controller or a controller-backed route overload.
- Search scope/provider preferences are restored into the feature owner and persisted through `AppSettingsRepository`.
- Search provider ordering/selection comes from `AppSettings`, while actual provider availability is gated by initialized provider sessions through a provider-neutral availability contract.

## Phase 2: recognition ownership

Recognition now follows the same ownership model.

- `RecognitionFeatureController` owns `StateFlow<RecognitionUiState>` and recognition operations through `RecognitionAction`.
- Android/iOS composition roots construct the recognition owner from the platform `AudioRecognitionRepository` and `PlaybackEngine`; pausing active playback no longer routes through the global controller.
- The primary `RecognitionRoute` consumes `RecognitionAppPort`; the previous controller-backed app-shell overload has been removed.
- Permission changes and app-background cancellation enter through `FuoAppViewModel`.
- The compatibility facade keeps delegates to the same injected recognition owner where legacy callers still require them, so production still has a single recognition state.
- Focused controller tests cover success/state ownership, playback pause, cancellation, and close/reset behavior.

## Phase 3: playback runtime boundary

The first playback runtime seam is now established.

- `:playback:api` defines `PlaybackSession` and immutable `PlaybackSessionState` as the platform/cross-feature playback contract.
- Android's media-session transport wiring, lock-screen lyric publication, durable queue/resume flushing, and Lyricon integration consume `PlaybackSession` instead of reading playback state directly from the global controller.
- `:core:model` provides the stable `TrackRef` used by the playback API, and `:provider:api` starts the provider-neutral compile-time boundary used by Search.
- `checkArchitectureBoundaries` prevents migrated Search/Recognition and platform playback boundaries from regaining direct controller dependencies.

## Phase 4: app-shell compatibility removal

Search and Recognition no longer require controller-specific app-shell bridges.

- `SearchRouteCompat` and `RecognitionRouteCompat` are removed.
- `AppRoot` composes the primary routes directly from `FuoAppViewModel` plus `SearchAppPort` / `RecognitionAppPort`.
- Android and iOS composition roots implement those ports. While sibling playback/download/provider-detail responsibilities remain centralized, the controller dependency is confined to the composition edge instead of leaking into route or feature contracts.
- The architecture fitness check fails if either retired compat file is reintroduced.

## Phase 5: dedicated playback runtime

Playback platform state and transport policy now have a dedicated owner.

- New `:playback:runtime` contains `DefaultPlaybackRuntime`, which owns the authoritative `PlaybackSessionState` consumed by platform integrations.
- Runtime state combines engine timing/status/error with queue/current-track/lyrics presentation supplied at the composition edge.
- Play/pause/toggle decisions now live in the runtime and call the engine directly for pause/resume instead of round-tripping through `FuoPlayerController`.
- The former Android-only `ControllerPlaybackSession` adapter is removed. Android now uses `AndroidPlaybackRuntime.kt` only to adapt the existing engine and playback composition edge into the controller-free runtime module.
- `startCurrent`, `previous`, and `next` were initially left as temporary queue-transition callbacks while queue selection and resource resolution remained in the compatibility controller.
- Focused runtime tests cover state composition and transport dispatch, and CI runs `:playback:runtime:allTests` on both Android and iOS workflows.
- Architecture fitness rules scan the runtime module and reject reintroduction of `ControllerPlaybackSession`.

## Phase 6: playback UI migration

### C1: MiniPlayer

The MiniPlayer rendering/transport path consumes the dedicated playback session on both platforms.

- iOS has `IosPlaybackRuntime.kt`, mirroring the Android composition-edge adapter and constructing the same `DefaultPlaybackRuntime` contract.
- `FuoAppViewModel` receives the app-scoped `PlaybackSession`; `AppRoot` provides it to playback UI through `LocalPlaybackSession`.
- `RuntimeMiniPlayer` renders `PlaybackSessionState`, cover metadata from `TrackRef`, progress, lyrics, and previous/toggle/next transport without reading `FuoPlayerController`.
- Review feedback exposed an iOS pre-engine resource-resolution failure path. C1 temporarily bridged the same-track `Loading -> coordinator Error` transition until start orchestration received a playback-owned failure channel.

### C2: FullPlayer, queue and lyrics

The active FullPlayer path is now controller-free.

- `RuntimeFullPlayer` consumes authoritative status, timing, lyrics, error, queue index, and previous/toggle/next transport from `PlaybackSession`.
- `PlaybackUiPort` initially carried richer UI/application concerns that do not belong in the narrow runtime API: rich `MusicTrack` metadata, queue edits, seek/shuffle/repeat, sleep timer, audio/replacement information, downloads and now-playing actions.
- Existing pure rendering helpers for cover transitions, progress, karaoke lyrics, controls and detail dialogs are reused through a render-only `PlaybackState` snapshot; session-owned fields remain sourced from `PlaybackSession`.
- `AppRoot` supplies the app-scoped playback contracts and renders `RuntimeFullPlayer` as the active overlay.
- Home and local-playlist MiniPlayer call sites use the controller-free `PlaybackMiniPlayer` host.

## Phase 7: playback orchestration ownership (P1-D)

Queue transition and playback-start policy now have dedicated playback owners instead of living in `FuoPlayerController`.

- `PlaybackQueueCoordinator` owns `startCurrent`, `previous`, `next`, queue-index selection, up-next priority, repeat behavior, multi-part transitions, and dynamic-feature continuation decisions.
- `PlaybackQueueController` remains the durable queue state holder; the compatibility controller delegates queue transition entry points to the coordinator instead of implementing the policy itself.
- `PlaybackStartCoordinator` owns the prepare -> resolve/plan -> engine-start pipeline, including downloaded/local payload handling, provider resolution on iOS-style engines, `PlaybackPlan` look-ahead construction on Android-style internally resolving engines, multi-part payload mapping, stale-request protection, and start-failure publication.
- `PlaybackStartFailureSource` publishes pre-engine resolution failures. Android and iOS runtime adapters combine this playback-owned failure with engine state, replacing the temporary iOS bridge that inspected `FuoPlayerController.playbackState` for an `Error`.
- Android and iOS composition roots explicitly pass `PlaybackTransportCoordinator` and `PlaybackStartFailureSource` into their runtime adapters. Runtime transport no longer dispatches `controller.toggle()`, `controller.previous()`, or `controller.next()`.
- `FuoPlayerController` keeps facade methods for legacy feature callers, but those methods delegate to the playback owners. Request serial / part-selection storage remains facade-compatible in this phase so the migration does not mix state relocation with orchestration policy changes.
- Focused common tests cover up-next/repeat/part queue transitions, direct resource resolution success/failure, and internally resolving playback-plan construction. iOS tests verify same-track playback-start failures override Loading without unrelated failures leaking across tracks.
- Architecture checks prevent the new coordinators from depending on `FuoPlayerController` and reject direct controller transport calls in the platform runtime adapters.

## Phase 8: playback UI ownership split (P1-E1)

The broad controller-backed playback UI bridge has been removed.

- `ControllerPlaybackUiPort` is deleted and protected as a retired compatibility file.
- `PlaybackNavigationPort` owns FullPlayer and queue-overlay visibility. Android back handling and `FuoAppViewModel` dispatch use this playback owner first.
- A two-flag navigation mirror at the composition edge keeps legacy `MiniPlayer(controller)` entry points and controller-owned detail actions behavior-compatible. The mirror is intentionally narrow and does not expose playback state or actions back through the controller.
- `PlaybackPresentationPort` reads rich track/part/audio presentation directly from `PlaybackEngine`, reads lyric/theme presentation from `AppSettingsRepository`, and calls the engine directly for seek.
- `PlaybackQueueUiPort` is implemented by `PlaybackQueueCoordinator`. Player queue display/edit, add-up-next, clear, shuffle/repeat and cover transition direction therefore use the playback owner rather than controller forwarding methods.
- `PlaybackQueueController` queue fields are observable so direct playback-owner mutations recompose player UI without using `FuoPlayerController.playbackState` as an observation bridge.
- `PlaybackSleepTimerPort` is intentionally a narrow temporary compatibility bridge because end-of-track timer completion is still coordinated in the legacy engine-ended loop.
- `NowPlayingActionPort` intentionally isolates the remaining cross-feature actions: downloads, playlist mutations, provider/artist/album detail navigation, local metadata and smart replacement.
- `PlaybackUiPort` remains only as a transitional composition facade delegating to these separate owners. It is no longer implemented by a controller adapter, and `AppRoot` receives the composed facade from the platform composition root instead of constructing it itself.
- Focused tests cover playback-navigation ownership/mirroring and direct queue UI mutations; architecture checks scan the new playback-owned files and reject reintroduction of `ControllerPlaybackUiPort`.

## Next phases

1. Split `NowPlayingActionPort` by feature owner: Download, Provider Detail/Replacement, Playlist and Local Music. Remove the remaining cross-feature controller adapter as each owner becomes explicit.
2. Move sleep-timer end-of-track completion into a playback-owned lifecycle coordinator, then remove `ControllerPlaybackSleepTimerPort`.
3. Change `RuntimeFullPlayer` and `PlaybackMiniPlayer` to consume the narrow ports directly and retire the transitional aggregate `PlaybackUiPort` plus the two-flag legacy navigation mirror as old MiniPlayer call sites disappear.
4. Replace controller-backed Search/Recognition app-port operations as their sibling download/provider-detail owners become explicit domain/feature ports.
5. Apply the feature-owned state plus app-shell composition pattern to local music, downloads, provider content, and settings.
6. Move remaining queue/start compatibility state (request serial, playback parts/index, rich queue presentation) behind playback-owned state once downstream feature callers are ready, then retire the playback facade methods from `FuoPlayerController`.
7. Continue moving stable contracts into compile-time modules and retire legacy aggregate provider dependencies and global loading/message state.
