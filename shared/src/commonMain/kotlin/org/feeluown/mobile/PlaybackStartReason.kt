package org.feeluown.mobile

/**
 * Describes why a playback transaction was started.
 *
 * This separates explicit user selections from session restoration so the
 * platform player can safely discard stale Media3 state only for active user
 * playback operations.
 */
enum class PlaybackStartReason {
    USER_SELECTION,
    PLAYLIST_REPLACE,
    AUTO_NEXT,
    RESUME,
    RESTORE_SESSION,
}
