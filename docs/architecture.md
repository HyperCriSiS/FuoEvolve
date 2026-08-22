# FuoEvolve architecture boundaries

This document records the compile-time and ownership boundaries after the P2 ownership migration and the current P3 physical feature extraction.

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
- `:feature:localplaylist`: physical Local Playlist state/orchestration boundary.
- `:feature:localmusic`: physical Local Music state/orchestration boundary.
- `:feature:download`: physical Download state/orchestration and offline-library coordination boundary.
- `:feature:providercatalog`: physical provider discovery/configuration/catalog state boundary.
- `:feature:providerauth`: physical provider authentication and device-OAuth orchestration boundary.
- `:feature:providerdetail`: physical provider feature/playlist/track/media/video detail orchestration boundary.
- `:feature:settings`: physical Settings state/orchestration boundary using feature-owned preference and collaborator ports.
- `:shared`: app shell, shared Compose/design primitives and application bindings for physical feature modules plus feature implementations not yet physically extracted.
- `:androidApp`: Android composition root and platform adapters.

Recognition, Search, Offline Library, Provider Catalog/Auth, Provider Detail and Settings have one-way dependency graphs. Application-domain types remain at the `:shared` binding layer when moving them lower would unnecessarily widen a feature contract.

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

## P3-A Search physical boundary

Search is implemented by `:feature:search` rather than mutable controller/state implementations under `:shared`.

The physical module owns Search actions/state, repository/result ports and orchestration while remaining generic over application track/result types. `:shared` binds concrete repositories/models and keeps Compose UI.

## P3-B offline-library physical boundaries

`:feature:localplaylist`, `:feature:localmusic` and `:feature:download` own their respective business state/orchestration. Concrete application repositories, models, settings and navigation remain in `:shared`. Download coordinates with Local Music through a narrow library port rather than a concrete controller dependency.

## P3-C Provider Catalog/Auth physical boundaries

`:feature:providercatalog` owns provider discovery/configuration/catalog/session-sync orchestration. `:feature:providerauth` independently owns authentication inputs, login/logout and device-code OAuth lifecycle. Both are generic over app/provider models and consume feature-owned ports rather than concrete shared repositories.

## P3-D Provider Detail physical boundary

`:feature:providerdetail` contains five destination-specific owners for Feature, Playlist, Track, Media Item and Video details. Each consumes its own capability port; repository, playback, settings, provider-session/capability lookup, failure mapping and navigation remain in shared adapters.

The stable app-facing Provider Detail UiState/controller APIs remain concrete in `org.feeluown.mobile` while business ownership lives in the physical module.

## P3-E1 Settings physical boundary

Settings business ownership is implemented by `:feature:settings`.

The module owns:

- observation and composition of Settings preference, cache, download and local-music state;
- application of persisted Wi-Fi/cellular audio-quality policies at startup;
- application of persisted cache limits and cache usage refresh;
- theme, playback-policy, lyric/display and dynamic-cover preference actions;
- download parallelism normalization and runtime coordination;
- cache-limit updates, cache cleanup busy/feedback state and cache refresh;
- local-music directory/min-duration commands;
- Settings close/download-manager/debug-log navigation requests through a port.

Critically, the module does not receive `AppSettings`. `SettingsFeaturePreferences` is a feature-owned snapshot containing only Settings-owned fields, and persistence is expressed through `SettingsPreferencesPort`. Runtime collaborators use separate `SettingsAudioQualityPort`, `SettingsDownloadPort`, `SettingsCachePort`, `SettingsLocalMusicPort` and `SettingsNavigationPort` contracts.

`:shared` adapts `AppSettingsRepository`, provider audio-quality updates, download/cache repositories, Local Music and `AppNavigator` into these ports. The legacy shared `SettingsController` and `SettingsControllerState` are retired.

For migration safety, the app-facing `SettingsFeatureUiState.settings` property and `SettingsFeatureController.update(AppSettings -> AppSettings)` remain temporarily in `:shared` because both the large Settings Compose screen and Onboarding still consume them. The compatibility method diffs only Settings-owned fields and dispatches explicit actions to the physical owner; the aggregate object never crosses into `:feature:settings`. P3-E2 Onboarding removes this remaining application-layer compatibility bridge.

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

`RuntimeMiniPlayer` and `RuntimeFullPlayer` consume these narrow contracts. Controller-backed player UI and the retired broad `PlaybackUiPort` aggregate must not be reintroduced.

## Provider and feature dependencies

New features should depend on narrow provider capability interfaces or feature-owned ports instead of aggregate repositories wherever a lower-level boundary has been extracted.

A feature may move to its own Gradle module only when all dependencies point to core/api contracts, lower-level modules, or generic/feature-owned ports bound by the application integration layer. `feature -> shared` is forbidden because it distributes the monolith rather than establishing a real boundary.

Cross-feature behavior should use the smallest stable contract. Download asks for local-library readiness/refresh rather than depending on the Local Music controller; Provider Detail uses destination-specific ports; Settings uses separate preference/audio/download/cache/local-music/navigation ports instead of receiving the app settings aggregate and concrete collaborators.

## Composition roots

Android uses `AndroidAppContainer`; iOS uses `IosAppContainer`. They compose feature owners, playback runtime and app ports directly. Neither platform constructs `FuoPlayerController`.

`AppRoot` installs the resulting app/feature/playback graphs and renders typed routes. It does not rebuild feature business state.

## Architecture fitness checks

`checkArchitectureBoundaries` remains the global ownership/playback regression gate.

Physical feature modules add feature-local fitness checks that reject `feature -> shared`, concrete application dependencies leaking downward and retired shared business owners returning. In particular:

- `checkOfflineFeatureBoundaries` covers Local Playlist, Local Music and Download;
- `checkProviderFeatureBoundaries` covers Provider Catalog/Auth;
- `checkProviderDetailFeatureBoundaries` covers the five Provider Detail owners and stable app-facing UiState contracts;
- `checkSettingsFeatureBoundaries` requires the Settings physical owner/source/tests/binding, rejects `AppSettings` and concrete Settings collaborators from the physical module, and rejects the retired `SettingsController` / `SettingsControllerState` from returning.

Android and iOS CI run Recognition, Search, Local Playlist, Local Music, Download, Provider Catalog, Provider Auth, Provider Detail, Settings, playback runtime and shared tests in addition to architecture gates.

## Migration rule

Architecture changes remain behavior-preserving and independently reviewable: move ownership first, introduce narrow ports at cross-feature boundaries, then remove compatibility surfaces only after the last production caller has migrated. Physical module extraction is the final step for a feature, not a substitute for ownership isolation.

P2 sequencing and P3 progress are tracked in [`p2-architecture-roadmap.md`](p2-architecture-roadmap.md). P3-E2 Onboarding is next; Home remains last because it is the application-level feature aggregation surface.
