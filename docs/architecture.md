# FuoEvolve architecture boundaries

This document records the compile-time and ownership boundaries after the P2 ownership migration and the P3-A Search module extraction.

## Dependency direction

The intended dependency direction is:

`platform composition root -> app shell -> feature owner -> core/api`

Platform hosts (`androidApp`, iOS host/adapters) construct platform dependencies and feature owners. Feature implementations must not use a platform service locator or depend back on `:shared`.

The current compile-time modules are:

- `:core:model`: stable cross-feature model contracts.
- `:provider:api`: provider-neutral capability contracts.
- `:playback:api`: app-scoped playback session contracts.
- `:playback:runtime`: controller-free playback session state/transport implementation.
- `:feature:recognition`: physical Recognition feature module containing recognition contracts, state, controller and tests.
- `:feature:search`: physical Search feature module containing search actions, state ownership, repository/result ports, orchestration and tests.
- `:shared`: app shell, shared UI/design primitives, platform-neutral adapters and feature implementations whose lower-level contracts still live in the shared graph.
- `:androidApp`: Android composition root and platform adapters.

Recognition and Search are physical modules with one-way dependency graphs. Search deliberately keeps application domain models out of the module by parameterizing the feature owner over track/provider-result types and accepting narrow local/provider search ports plus result operations. `:shared` binds `MusicTrack`, `ProviderSearchResults`, `ProviderMusicRepository` and `LocalMusicRepository` at the composition boundary; `:feature:search` does not depend on those shared types.

Download, Local Music and other feature implementations remain logically owned inside `:shared` until their aggregate repository dependencies can be moved behind similarly narrow lower-level boundaries. Creating modules that depend back on `:shared` is forbidden because that would only distribute the monolith instead of establishing a real boundary.

## P2 ownership result

`FuoPlayerController` has been retired. Production navigation, screens and platform composition no longer depend on a broad compatibility facade.

App-scoped navigation is owned by `AppNavigator` / `FuoAppViewModel`. Feature state is owned by dedicated feature controllers/owners. Loading, errors and transient feedback are feature-local unless they are genuinely app-scoped.

Major migrated ownership boundaries include:

- Search and Recognition feature owners and app ports;
- Debug log and Download manager owners;
- Local Music and Local Playlist owners;
- Settings, provider authentication and onboarding owners;
- provider feature/playlist/track/media/video detail owners;
- Home/provider-content owner;
- playback queue/start/lifecycle/replacement/sleep-timer owners.

Legacy Home, Settings, Onboarding, provider-detail and controller-backed player screens were deleted after their owner-based replacements became active.

## P3-A Search physical boundary

Search is now implemented by `:feature:search` rather than by mutable controller/state implementations under `:shared`.

The physical module owns:

- `SearchScope`, `ProviderSearchTab` and `SearchAction`;
- `SearchFeatureState` and `SearchFeatureOwner`;
- `SearchProviderRepository` and `SearchLocalRepository` feature ports;
- `SearchResultOperations`, which supplies the minimal result semantics needed for merge/count/error handling;
- query/scope/provider selection, recognized-song query construction, loading/feedback state and search orchestration;
- Search owner tests.

The module is generic over the application's track and provider-result types. This keeps it independent from `MusicTrack`, `ProviderSearchResults`, `ProviderMusicRepository`, `LocalMusicRepository` and `RecognizedSong`, while preserving the existing app-facing `SearchUiState` / `SearchFeatureController` names through compile-time type aliases in `SearchFeatureBindings.kt`.

`SearchFeatureBindings.kt` is an integration boundary, not a second state owner: it adapts application repositories into Search ports, supplies result operations and maps Recognition into primitive title/artist inputs. Search UI remains in `:shared` because it uses shared Compose/design-system and cross-feature actions.

## Playback

Playback status, timing and transport come from `PlaybackSession`. Rich UI concerns use narrow contracts:

- `PlaybackNavigationPort` — FullPlayer and queue visibility;
- `PlaybackPresentationPort` — current presentation, seek, lyric/theme settings;
- `PlaybackQueueUiPort` — queue display/edit, source selection, shuffle/repeat and transition direction;
- `PlaybackSleepTimerPort` — sleep timer lifecycle;
- `DownloadActionPort` — download actions;
- `PlaylistActionPort` — playlist actions;
- `ProviderTrackActionPort` — provider-track navigation/actions;
- `LocalMusicActionPort` — local music actions;
- `ReplacementActionPort` — smart replacement actions.

`RuntimeMiniPlayer` and `RuntimeFullPlayer` consume these narrow contracts. Controller-backed `MiniPlayer`, `FullPlayer`, queue sheets and now-playing actions were removed together with `PlayerScreen.kt`; reusable player formatting, dialogs, lyrics and transport primitives live in controller-free files.

The retired broad `PlaybackUiPort` aggregate and all controller-backed playback compatibility adapters must not be reintroduced.

## Provider and feature dependencies

New features should depend on narrow provider capability interfaces or feature-owned ports instead of the aggregate `ProviderMusicRepository` wherever a lower-level boundary has been extracted.

A feature may move to its own Gradle module only when all of its dependencies point to core/api contracts, other lower-level modules, or generic feature-owned ports that are bound by the app composition layer. If moving it would require `feature -> shared`, leave it logically isolated in `:shared` and extract the missing contract first.

## Composition roots

Android uses `AndroidAppContainer`; iOS uses `IosAppContainer`. They compose feature owners, playback runtime and app ports directly. Neither platform constructs `FuoPlayerController` or platform-local forwarding versions of Search/Recognition app ports.

`AppRoot` installs the resulting app/feature/playback graphs and renders typed routes. It does not rebuild feature business state or controller compatibility bridges.

## Architecture fitness checks

`checkArchitectureBoundaries` is the regression gate for the completed ownership/module boundaries. It now:

- scans all production Kotlin roots in `core`, `feature`, `playback`, `provider`, `shared` and `androidApp` and rejects any executable `FuoPlayerController` reference;
- rejects reintroduction of retired controller facades, monolithic controller tests and legacy controller-backed screens/bridges;
- rejects retired playback aggregate/compatibility adapters and controller transport calls;
- rejects platform-local Search/Recognition forwarding bridges;
- requires the physical `:feature:recognition` and `:feature:search` boundaries to remain present;
- rejects `:feature:search -> :shared`;
- rejects direct Search-module references to `ProviderMusicRepository`, `LocalMusicRepository`, `ProviderSearchResults`, `MusicTrack` or `RecognizedSong`.

Android and iOS CI run both feature module test suites in addition to playback/shared tests and the architecture gate.

## Migration rule

Architecture changes remain behavior-preserving and independently reviewable: move ownership first, introduce narrow ports at cross-feature boundaries, then remove compatibility surfaces only after the last production caller has migrated. Physical module extraction is the final step for a feature, not a substitute for ownership isolation.

P2 sequencing and closeout criteria are tracked in [`p2-architecture-roadmap.md`](p2-architecture-roadmap.md). P3 continues physical module extraction in the order documented there, with Search completed first and Download next.
