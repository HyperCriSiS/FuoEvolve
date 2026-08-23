# P4 architecture roadmap

P4 starts after P3 physical feature modularization. P3 established one-way feature ownership; P4 reduces `:shared` as the remaining application-integration monolith without mechanically creating more feature modules.

The target dependency direction remains:

`platform composition root -> app integration -> feature owner -> stable domain/api contracts`

Lower contract/data/provider modules must never depend back on `:shared`.

## P4-A: contract ownership

Completed in PR #115.

The first P4 step retires the broad `shared/.../FuoContracts.kt` aggregate and moves only contracts whose dependency direction is already stable:

- `:core:model` owns `TrackSourceType` alongside `TrackRef`;
- `:provider:api` owns provider-neutral login, capability, resource, feature, playlist/video and mutation contracts;
- `:playback:api` owns playback policy, replacement-selection, resolved payload, sleep-timer and audio-format contracts;
- `:shared` keeps the application contracts that still depend on application orchestration, split by bounded context instead of rebuilding another catch-all file.

Package names remain stable during this migration so P4-A is a behavior-neutral ownership move rather than an application-wide import rewrite.

The iOS `Shared.framework` explicitly re-exports the lower public contract modules. The P4 architecture gate rejects restoring the aggregate, lower-layer back-dependencies, or removing the required Kotlin/Native exports.

## P4-B: media model normalization

Completed in PR #115 after P4-A.

The cross-feature media model is now independent from provider aggregate ownership:

- `MediaRef` / `MediaRefType` provide stable source-neutral media identity in `:core:model`;
- `MusicTrack`, local scan settings, local directory metadata and local track metadata move from `:shared` into `:core:model`;
- `MusicTrack.artistItems` now stores `List<MediaRef>` rather than a provider-owned media class;
- provider-facing `ProviderMediaItem` / `ProviderMediaItemType` names remain temporary Kotlin source-compatible type aliases to the core reference contracts, so existing concrete provider parsers do not need a large behavior-changing rewrite;
- the core reference exposes source naming as the canonical model while compatibility aliases keep provider callers source-compatible during the P4-C migration;
- typed navigation keeps its serialized `artistItems` payload but maps it to/from core refs, preserving route compatibility;
- playback queue persistence remains format `v2`; existing explicit artist/album IDs continue to decode, and a historical-v2 regression fixture protects restore compatibility.

Architecture fitness checks now also reject restoring `MediaContracts.kt`, placing provider/playback/feature dependencies under `:core:model`, reintroducing provider aggregate media types into the core media file, breaking the provider-to-core dependency, or changing the queue persistence version during this behavior-neutral step.

Exit criterion reached: `:core:model` owns the stable track/media identity used across feature, playback and provider layers and has no dependency on provider/application modules.

## P4-C: provider contract/runtime split

In progress on the P4-C provider runtime boundary PR.

This step turns `:provider:api` into the provider-neutral public boundary and introduces a physical `:provider:runtime` module for reusable implementation infrastructure:

- `:provider:api` owns provider registry, search, catalog/detail, library mutation and authentication capability contracts;
- provider search/content/detail result models use canonical `MediaRef` values rather than provider-named media aliases;
- stable provider failure and video-metadata value contracts live in `:provider:api`;
- `:provider:runtime` owns HTTP/retry/cache infrastructure, persistent-cache SPI, credential SPI/value model, provider JSON/resource-key helpers, failure mapping, `BaseKotlinProvider` and the concrete-provider SPI;
- Android OkHttp and iOS Darwin provider HTTP engines are actual implementations inside `:provider:runtime`;
- provider runtime/network and failure-mapping tests move with the implementation instead of remaining in `:shared`;
- `:shared` keeps concrete NetEase, QQ Music, Bilibili and YTMusic providers plus an application compatibility aggregate while callers migrate to narrow provider capabilities;
- YTMusic-specific OAuth types stay above the provider-neutral API/runtime boundary; the shared aggregate adapts them to provider-neutral device-authorization/OAuth contracts;
- `ProviderMediaItem` remains only a temporary P4-B source-compatibility alias, while new lower repository/runtime contracts use `MediaRef` directly.

Architecture fitness checks reject provider API/runtime back-dependencies on `:shared` or features, concrete provider types leaking into lower provider modules, restoration of the old shared provider runtime/network/failure files, and movement of canonical provider-neutral capability contracts back into `:shared`.

P4-C exit criterion: provider API/runtime compile and test independently on Android/iOS, application/provider behavior remains unchanged, and concrete provider implementations depend downward on the extracted runtime without the runtime depending back on app/feature code.

Provider-specific physical modules remain P5 work after this boundary is validated.

## P4-D: persistence boundaries

Goal: remove persistence implementation from app integration.

Start with settings because its lifecycle is already explicit:

- settings snapshot/contracts;
- DataStore implementation;
- migration/serialization policy;
- platform storage construction remains in composition roots.

Other local/download persistence should be extracted only where a stable repository boundary already exists.

## P4-E: app-shell cleanup and closeout

After lower contracts/provider/data boundaries stabilize:

- reduce `AppState` to genuinely app-scoped state only;
- retire remaining legacy route/integration bridges;
- rename/remove P2-era app-root compatibility naming where appropriate;
- keep Compose/navigation/application wiring above feature owners;
- extend architecture fitness checks so lower layers cannot drift back into `:shared`.

P4 is complete when `:shared` primarily contains app shell, Compose/UI and concrete application bindings, rather than generic domain models, provider-neutral contracts or persistence implementations.

## P5 direction

Only after P4 completes should concrete providers become independent physical modules such as `:provider:netease`, `:provider:qqmusic`, `:provider:bilibili` and `:provider:ytmusic`. Those modules should depend only on provider API/runtime and lower stable contracts.
