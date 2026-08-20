# FuoEvolve architecture boundaries

This document records the architecture boundaries used while the project migrates away from a flat `shared` source tree.

## Dependency direction

The intended dependency direction is:

`app -> feature -> core/api`

Platform hosts and adapters (`androidApp`, `iosMain`) implement or assemble dependencies required by shared code. Feature code must not become a platform service locator.

The first compile-time module boundaries are now explicit:

- `:core:model` owns stable cross-feature model contracts used by architecture APIs.
- `:playback:api` owns the app-scoped playback session contract and depends only on `:core:model` plus coroutines.
- `:playback:runtime` owns the default `PlaybackSession` state/transport implementation and depends only on `:playback:api` plus coroutines.
- `:provider:api` owns provider-neutral cross-feature capability contracts.
- `:shared` consumes the playback/provider API contracts and contains the current feature implementations and legacy playback/provider contracts that are still being migrated.
- `:androidApp` consumes `:shared` plus playback/core APIs and adapts the platform engine and playback-owned coordinators into `:playback:runtime`.

These modules intentionally start small. New cross-feature/platform contracts should move into the appropriate API/runtime module instead of expanding the flat shared contract surface. Feature implementation modules can be split later after their ownership boundaries are stable.

## Shared source layout

The first migration stage groups existing source files physically while keeping the current `org.feeluown.mobile` Kotlin package. Keeping the package stable makes the move behavior-neutral and preserves binary/source references while the public controller facade is reduced in later changes.

- `app/`: app shell, navigation and app-scoped state.
- `core/model/`: legacy shared models during migration; stable new architecture models belong in `:core:model`.
- `core/ui/`: design system and cross-feature UI/platform abstractions.
- `feature/<name>/`: feature-local controller, state and UI.
- `feature/playback/`: playback composition, queue/start coordinators, playback UI owners and the remaining narrow compatibility ports during migration.

Provider protocol implementations remain under `provider/<provider>` because they already have a useful adapter boundary.

## State ownership

Feature state should have one owner. New feature work should expose immutable UI state (preferably `StateFlow`) instead of adding new delegated properties to `FuoPlayerController`.

App-scoped navigation belongs to `AppNavigator` / `FuoAppViewModel`. Playback status, timing, current stable track reference, lyrics, queue identity/index, errors, and transport policy belong to `PlaybackSession` / `DefaultPlaybackRuntime`. Avoid introducing new app-global `isLoading`, `message`, or error flags; loading and errors should be feature-local.

Queue and start orchestration now have explicit playback owners. `PlaybackQueueCoordinator` owns `startCurrent` / `previous` / `next`, up-next priority, repeat/part transitions and queue-index selection while `PlaybackQueueController` remains the durable queue state holder. `PlaybackStartCoordinator` owns the prepare -> resolve/plan -> engine-start pipeline, including direct provider resolution on platforms that do not resolve resources inside the engine and `PlaybackPlan` construction for engines that do.

Pre-engine start failures are published through `PlaybackStartFailureSource`. Android and iOS runtime adapters combine that playback-owned failure with engine state, so the old iOS compatibility path that read coordinator/controller `Error` state has been retired.

MiniPlayer and FullPlayer read authoritative playback state/transport from `PlaybackSession`. Rich player UI concerns are now split into narrow contracts instead of being owned by one controller-backed UI adapter:

- `PlaybackNavigationPort` owns FullPlayer / queue-overlay visibility.
- `PlaybackPresentationPort` reads rich engine presentation plus lyric/theme settings and owns seek normalization.
- `PlaybackQueueUiPort` is implemented by `PlaybackQueueCoordinator` and owns queue display/edit, shuffle/repeat and transition direction for player UI.
- `PlaybackSleepTimerPort` isolates the remaining sleep-timer compatibility until end-of-track completion leaves the legacy engine-ended loop.
- `NowPlayingActionPort` isolates cross-feature download, playlist, provider-detail, local-metadata and replacement actions until those feature owners are extracted.

`ControllerPlaybackUiPort` has been retired. `PlaybackUiPort` remains only as a transitional composition facade that delegates to the narrow owners above so the existing FullPlayer implementation can migrate incrementally without putting those responsibilities back into one owner. Android and iOS construct the same owner graph in their composition roots.

Legacy feature screens that still call the old `MiniPlayer(controller)` signature are temporarily supported by a two-flag navigation mirror at the composition edge. The actual player overlay source remains `PlaybackNavigationPort`; the mirror exists only so old MiniPlayer entry points and controller-owned detail actions can open/close the same overlay during feature migration.

## Repository dependencies

New features should depend on narrow provider capability interfaces (`ProviderSearchRepository`, `ProviderPlaybackRepository`, `ProviderAuthRepository`, and provider-neutral API contracts) rather than adding calls to the legacy aggregate `ProviderMusicRepository`.

## Composition roots

Platform dependency construction is isolated in platform containers. Android uses `AndroidAppContainer`; iOS uses `IosAppContainer`. `Application`, `UIViewController`, activities and services should remain thin hosts around those composition roots.

Search and Recognition are composed through explicit `SearchAppPort` / `RecognitionAppPort` contracts. Their routes and feature UI no longer accept `FuoPlayerController`. During the remaining migration, platform composition roots may adapt still-centralized controller operations to those ports; the dependency must not leak back into the feature or app route contract.

Android playback uses `AndroidPlaybackRuntime.kt` and iOS uses `IosPlaybackRuntime.kt` as composition-edge adapters. Both receive `PlaybackTransportCoordinator` and `PlaybackStartFailureSource` explicitly; they no longer dispatch runtime transport through controller methods or inspect controller error state. `FuoAppViewModel` exposes the resulting app-scoped `PlaybackSession` plus the composed playback UI facade, and `AppRoot` supplies them directly. `AppRoot` no longer constructs a controller-backed playback UI adapter.

## Architecture fitness check

`checkArchitectureBoundaries` rejects new `FuoPlayerController` code dependencies inside migrated Search/Recognition boundaries, the entire `:playback:runtime` common source tree, `PlaybackQueueController`, `PlaybackQueueCoordinator`, `PlaybackStartCoordinator`, `PlaybackUiPort`, `PlaybackUiOwners`, playback composition contracts, controller-free MiniPlayer/FullPlayer implementations, app-port contracts/routes, and Android playback service/Lyricon integration. It also rejects reintroduction of the retired Search/Recognition route shims, `ControllerPlaybackSession`, and `ControllerPlaybackUiPort`, plus direct `controller.toggle()/previous()/next()` calls in platform playback runtime adapters.

## Migration rule

Architecture migration should be incremental and behavior-preserving. Move ownership first, introduce narrow ports at cross-feature boundaries, and remove legacy facades only after callers have migrated and tests cover the new boundary.
