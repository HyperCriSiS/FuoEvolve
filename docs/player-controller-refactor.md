# FuoPlayerController refactor

The controller migration is intentionally incremental. `FuoPlayerController` remains a compatibility facade while feature state and UI contracts move to narrower owners.

## Phase 1: search ownership

Search ownership has moved out of `FuoPlayerController`.

- `SearchFeatureScreen` consumes `SearchUiState`, provider/download snapshots, `SearchAction`, and narrow cross-feature callbacks instead of `FuoPlayerController`.
- `SearchFeatureController` is constructed by the Android/iOS composition roots. The same owner instance is injected into `FuoAppViewModel` and the compatibility `FuoPlayerController` facade, so production has one search state owner.
- `SearchRoute` is the app-shell composition point for search UI. The temporary `SearchScreenBridge` has been removed.
- Search scope/provider preferences are restored into the feature owner and persisted through `AppSettingsRepository`.
- Search provider ordering/selection comes from `AppSettings`, while actual provider availability is gated by initialized `ProviderSessionRepository` providers.
- `FuoPlayerController` no longer owns `SearchControllerState`; its remaining search getters/actions are compatibility delegates to the injected owner.
- Focused tests cover standalone search actions and preference restoration without write-back.

## Next phases

1. Remove the remaining search compatibility delegates as recognition, local-music metadata lookup, and playback result actions move to explicit app/domain boundaries.
2. Apply the same feature-owned state plus app-shell composition pattern to local music, downloads, provider content, settings, and recognition.
3. Extract playback runtime ownership into a `PlaybackSession` so platform services and player UI no longer coordinate through `FuoPlayerController`.
4. Remove remaining legacy aggregate provider dependencies and global loading/message state as feature owners become authoritative.
