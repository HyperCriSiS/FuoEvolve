# P2 architecture roadmap

P2 moved FuoEvolve from a broad controller facade to explicit app, feature and playback ownership while preserving the dependency direction:

`platform composition root -> app shell -> feature owner -> core/api`

## Closeout status

P2 ownership migration is complete. PR #104 removed the production `FuoPlayerController` dependency, controller-backed feature/player surfaces and the remaining compatibility ownership bridges. Android/iOS composition roots build the owner graph directly, and architecture checks enforce that those retired surfaces do not return.

Recognition became the first physical feature module during P2 because its dependency graph was already one-way.

## P2-1: app navigation and app-scoped state

Completed.

Navigation/back-stack policy is app-owned. Startup/onboarding state and app-scoped feedback are exposed through app state/ports rather than a concrete player controller. Feature loading/errors no longer compete through a global controller state pair.

## P2-2: low-risk feature ownership

Completed for the targeted production surfaces:

- Debug logs;
- Download manager;
- Local Music;
- Local Playlists;
- Settings/provider authentication/onboarding.

Each active screen consumes its owning state/actions or narrow ports. Retired controller screens are listed in the architecture fitness check so they cannot silently return.

## P2-3: provider detail ownership

Completed.

Provider detail state is split by destination rather than collected into another provider mega-controller:

- feature detail;
- playlist detail;
- track detail/related content;
- media-item detail;
- video detail.

Typed route payloads remain navigation identity and each owner activates/restores itself from the route.

## P2-4: Home/provider-content ownership

Completed.

Recommend/Explore/Mine content is owned by `HomeFeatureController` and related feature owners. Legacy Home controller bridges/screens have been removed. Refresh/loading/selection state is feature-local.

## P2-5: compatibility facade retirement

Completed.

`FuoPlayerController.kt` and `FuoPlayerControllerTest.kt` are deleted. The old controller-backed `PlayerScreen.kt` was decomposed into reusable controller-free player primitives before retirement. Android/iOS composition roots no longer construct or receive the facade.

The architecture gate scans production Kotlin roots globally and rejects any executable `FuoPlayerController` reference.

## P2-6: first physical feature boundary and fitness checks

Completed.

`:feature:recognition` is a real Kotlin Multiplatform feature module containing recognition contracts, state/controller implementation and tests. `:shared` consumes it through a one-way Gradle dependency and keeps only shared Compose/application integration.

## P3 physical modularization

P3 completes the final step of the migration: turning stable logical feature ownership boundaries into physical Gradle modules without introducing `feature -> shared` dependencies.

### P3-A: Search

Completed.

`:feature:search` owns Search actions, state, repository/result ports, orchestration and tests. The module is generic over application track/result types, so it does not depend on `MusicTrack`, `ProviderSearchResults`, `ProviderMusicRepository`, `LocalMusicRepository` or `RecognizedSong` from `:shared`.

`:shared` keeps the Search Compose UI and a thin integration layer that binds application repositories/models to the feature-owned ports. Existing app-facing Search state/controller names remain compatibility APIs rather than duplicate owners.

Architecture checks reject moving Search ownership back into `:shared`, adding `:feature:search -> :shared`, or leaking aggregate application repositories/models into the physical Search module. Android and iOS CI run `:feature:search:allTests`.

### P3-B: Offline library cluster

Completed as one physical-boundary change set for the three mutually related low-risk features:

- `:feature:localplaylist` owns local-playlist state, CRUD/import/export orchestration and feature tests;
- `:feature:localmusic` owns local-library refresh/filter/permission state, metadata/lyrics workflows and feature tests;
- `:feature:download` owns download state/actions, task observation and offline-library refresh coordination.

The three modules are generic over application-facing track/repository/provider/navigation types. Concrete `MusicTrack`, provider repositories, settings and `AppNavigator` are bound only in `:shared`, so none of the new feature modules depends back on `:shared`.

The previous direct Download -> Local Music controller coordination is replaced by a narrow local-library port. Completion-triggered refresh, media-change debounce and delete-download refresh behavior remain owned by Download without a concrete feature-to-feature dependency.

Shared Compose screens remain in `:shared`; the physical modules own business state and orchestration rather than UI styling/navigation composition. Android and iOS CI run all three feature suites, and the offline feature fitness check rejects reintroducing the retired shared owners or leaking concrete shared dependencies into the modules.

### P3-C: Provider catalog and authentication

Completed as two independent physical feature boundaries delivered together:

- `:feature:providercatalog` owns provider discovery, enabled-provider normalization, ordering, section visibility, capability/feature catalog state and session rehydration orchestration;
- `:feature:providerauth` owns authentication input state, cookie/header login orchestration, device-code OAuth polling/cancellation/timeout state, OAuth import decisions, logout/refresh feedback and feature tests.

Both modules use feature-owned ports and generic provider/auth/session types. Concrete `ProviderMusicRepository`, `ProviderSessionRepository`, `AppSettingsRepository`, provider models, YTMusic JSON parsing and platform notification/clipboard helpers are adapted only in `:shared`.

Provider catalog and authentication remain separate owners/modules even though they ship in one change set, preventing the provider area from becoming another aggregate controller. The provider boundary fitness check rejects `feature -> shared` back-dependencies, reintroduction of retired shared Catalog/Auth owners, and concrete application/provider types leaking into either module.

Android and iOS CI run both provider feature suites alongside shared tests.

### P3-D: Provider Detail

Completed as one physical feature module with destination-specific owners rather than a provider-detail mega-owner.

`:feature:providerdetail` owns five independent state/orchestration boundaries:

- feature detail paging, merge/prefetch and complete-queue playback;
- playlist detail paging, playback-background pagination, remove/delete policy and mutation orchestration;
- track detail plus similar-track/comment/video related-content loading;
- media-item track/album pagination and complete-track playback;
- video payload loading and fullscreen state.

Each destination consumes its own feature-owned capability port. The module is generic over provider/application models and does not depend on aggregate application repositories or navigation/playback controllers.

`:shared` keeps navigation, playback queue, repository, settings/playback-stat persistence, provider-session/capability lookup and provider-failure adaptation. Existing provider-detail UiState/controller APIs remain stable compatibility APIs.

The Provider Detail fitness check rejects `feature -> shared` dependencies, concrete app/provider types leaking into the physical module and reintroduction of previous shared business owners. Android and iOS CI run `:feature:providerdetail:allTests` alongside shared characterization coverage.

### P3-E: Settings and Onboarding

Completed as two independent physical feature boundaries delivered in the same change set.

`:feature:settings` owns the Settings state machine and runtime coordination for playback/audio-quality preferences, appearance preferences, download parallelism, cache limits/cleanup, local-music settings actions, navigation requests and transient feedback. Its preference input is a feature-owned snapshot containing only fields used by Settings rather than the aggregate `AppSettings` object.

Cross-feature collaborators are expressed through narrow ports for preferences, audio quality, downloads, cache, local music and navigation. The physical module therefore does not depend on aggregate shared repositories/controllers.

`:feature:onboarding` separately owns startup provider selection, Bilibili replacement-only policy, validation, provider enablement/persistence transaction handling, rollback, completion and transient feedback. It consumes only a narrow provider-preference snapshot plus provider-runtime operations.

Onboarding continues to reuse Settings for theme/audio-quality pages and Provider Auth for login pages instead of duplicating those owners. Settings and Onboarding each have an architecture fitness gate and Android/iOS feature suites.

### P3-F: Home

Completed.

`:feature:home` is the final planned P3 physical feature boundary. It owns:

- Recommend / Explore / Mine selection state and startup readiness gating;
- provider section selection, incremental loading, ordering and stale-refresh suppression;
- login-required/deferred section policy;
- Home Play All pagination and track de-duplication;
- dynamic feature first-load-and-play orchestration;
- Mine playlist/favorite/content refresh coordination;
- provider-playlist creation flow and transient loading/error feedback.

Home consumes feature-owned preference, catalog, content, playback and local-library ports. The physical module is generic over provider/content/track/playlist/stat representations and therefore does not depend on `AppSettings`, `ProviderMusicRepository`, `ProviderCatalogFeatureController`, `PlaybackQueueUiPort`, Local Music/Playlist controllers, `AppNavigator`, `MusicTrack`, `ProviderFeature`, `ProviderContentSection` or `:shared`.

`:shared` keeps the existing concrete `HomeFeatureController` / `HomeFeatureUiState` API, Compose UI, application navigation/detail routing and adapters from the concrete repositories/owners to Home-owned ports. The former shared `DefaultHomeFeatureController` business owner is retired rather than mirrored.

`checkHomeFeatureBoundaries` rejects `:feature:home -> :shared`, concrete application dependencies in the module and movement of Home business orchestration back into the shared binding. Android and iOS CI run `:feature:home:allTests`.

## P3 closeout

P3 physical modularization is complete once P3-F is merged. The feature layer now consists of one-way KMP boundaries for Recognition, Search, Local Playlist, Local Music, Download, Provider Catalog, Provider Auth, Provider Detail, Settings, Onboarding and Home.

The invariant remains:

`platform composition root -> app/shared integration -> physical feature owner -> core/api or feature-owned ports`

A physical feature must never depend back on `:shared`. Cross-feature behavior must use the smallest stable read/action port rather than another aggregate controller.

The next architecture phase should stop creating feature modules mechanically. P4 should focus on reducing the remaining responsibilities of `:shared` as the application integration layer: identify stable provider/persistence/application contracts that can move downward, keep Compose and composition-root concerns above feature owners, and avoid turning integration cleanup into a second broad facade migration.
