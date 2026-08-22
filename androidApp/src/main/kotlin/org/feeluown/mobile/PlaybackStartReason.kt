package org.feeluown.mobile

/**
 * Describes why playback was started.
 *
 * User initiated playback must be separated from session restoration so the
 * player can decide whether stale Media3 state should be discarded.
 */
enum class PlaybackStartReason {
    USER_SELECTION,
    PLAYLIST_REPLACE,
    AUTO_NEXT,
    RESUME,
    RESTORE_SESSION,
}
