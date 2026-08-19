# Feature source layout

Feature directories are a migration boundary, not new Kotlin packages yet. Source files continue to declare `org.feeluown.mobile` so this restructuring does not change symbol visibility or require a broad import rewrite.

New feature-specific controller/state/UI files should be placed in the corresponding feature directory. Cross-feature models belong in `core/model`; reusable Compose/UI abstractions belong in `core/ui`; app shell/navigation belongs in `app`.

Do not add new feature behavior to `FuoPlayerController` unless it is playback-specific. Prefer feature-local state and narrow provider capability interfaces.

Feature UI should not take `FuoPlayerController` as its long-term state/action contract. App-shell composition should connect a feature-owned state/controller to narrow cross-feature callbacks. Search establishes the migration pattern: `SearchFeatureController` is created by the platform composition roots, `SearchRoute` wires `SearchFeatureScreen`, and `FuoPlayerController` only retains temporary compatibility delegates to the same owner. New migrations should avoid adding a new bridge or duplicate feature state when the feature owner can be injected directly.
