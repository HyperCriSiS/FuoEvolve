# FuoPlayerController refactor

The controller migration is intentionally incremental. `FuoPlayerController` remains a compatibility facade while feature state and UI contracts move to narrower owners.

## Phase 1: search ownership

Search ownership has moved out of `FuoPlayerController`.

- `SearchFeatureScreen` consumes `SearchUiState`, provider/download snapshots, `SearchAction`, and narrow cross-feature callbacks instead of `FuoPlayerController`.
- `SearchFeatureController` is constructed by the Android/iOS composition roots. The same owner instance is injected into `FuoAppViewModel` and the compatibility `FuoPlayerController` facade, so production has one search state owner.
- `SearchRoute` is the app-shell composition point for search UI. The temporary bridge has been removed.
- Search scope/provider preferences are restored into the feature owner and persisted through `AppSettingsRepository`.
- Search provider ordering/selection comes from `AppSettings`, while actual provider availability is gated by initialized `ProviderSessionRepository` providers.

## Phase 2: recognition ownership

Recognition now follows the same ownership model.

- `RecognitionFeatureController` owns `StateFlow<RecognitionUiState>` and recognition operations through `RecognitionAction`.
- Android/iOS composition roots construct the recognition owner from the platform `AudioRecognitionRepository` and `PlaybackEngine`; pausing active playback no longer routes through `FuoPlayerController`.
- `RecognitionRoute` composes `AudioRecognitionFeatureScreen` from feature state/actions plus narrow provider-detail/search callbacks.
- Permission changes and app-background cancellation enter through `FuoAppViewModel`, not the global controller.
- `FuoPlayerController` keeps only compatibility delegates to the same injected recognition owner, so production still has a single recognition state.
- Focused controller tests cover success/state ownership, playback pause, cancellation, and close/reset behavior.

## Next phases

1. Remove remaining Search/Recognition compatibility delegates as local-music metadata lookup, provider detail navigation, and playback result actions move to explicit app/domain boundaries.
2. Apply the feature-owned state plus app-shell composition pattern to local music, downloads, provider content, and settings.
3. Extract playback runtime ownership into a `PlaybackSession` so platform services and player UI no longer coordinate through `FuoPlayerController`.
4. Remove remaining legacy aggregate provider dependencies and global loading/message state as feature owners become authoritative.
