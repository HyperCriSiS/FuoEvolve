# FuoPlayerController refactor

The controller migration is intentionally incremental. `FuoPlayerController` remains a compatibility facade while feature state and UI contracts move to narrower owners.

## Phase 1: search boundary

- `SearchFeatureScreen` consumes `SearchUiState`, provider/download snapshots, `SearchAction`, and narrow cross-feature callbacks instead of `FuoPlayerController`.
- `SearchScreenBridge` is the only search UI file that knows about `FuoPlayerController`; it preserves existing callers while the composition root is migrated.
- `SearchController` now has a narrow constructor based on `ProviderSearchRepository` and owns its `SearchControllerState` by default.
- The legacy aggregate-repository/global-callback constructor remains for the current `FuoPlayerController` facade.
- Focused tests cover the standalone search controller/action path.

## Next phases

1. Construct `SearchController` from the app composition root and remove `SearchScreenBridge` plus search state/action proxies from `FuoPlayerController`.
2. Apply the same state-plus-actions UI boundary to local music, downloads, provider content, settings, and recognition.
3. Extract playback runtime ownership into a `PlaybackSession` so platform services and player UI no longer coordinate through `FuoPlayerController`.
4. Remove remaining legacy aggregate provider dependencies and global loading/message state as feature owners become authoritative.
