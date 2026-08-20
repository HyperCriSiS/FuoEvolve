package org.feeluown.mobile

import kotlinx.coroutines.CoroutineScope

/**
 * Keep the two legacy overlay flags synchronized while old feature entry points still call
 * `MiniPlayer(controller)` and controller-owned detail actions still close the player.
 */
fun controllerMirroredPlaybackNavigationPort(
    controller: FuoPlayerController,
    scope: CoroutineScope,
): PlaybackNavigationPort = DefaultPlaybackNavigationPort(
    scope = scope,
    compatibilityState = {
        PlaybackNavigationSnapshot(
            isFullPlayerOpen = controller.isFullPlayerOpen,
            isQueueOpen = controller.isQueueOpen,
        )
    },
    compatibilityOpenFullPlayer = controller::openFullPlayer,
    compatibilityCloseFullPlayer = controller::closeFullPlayer,
    compatibilityToggleQueue = controller::toggleQueue,
)

/**
 * Narrow compatibility bridge kept only until sleep-timer completion moves out of the legacy
 * engine-ended loop in FuoPlayerController.
 */
class ControllerPlaybackSleepTimerPort(
    private val controller: FuoPlayerController,
) : PlaybackSleepTimerPort {
    override val sleepTimerState: SleepTimerState
        get() = controller.sleepTimerState

    override fun setSleepTimerDurationMinutes(minutes: Int) =
        controller.setSleepTimerDurationMinutes(minutes)

    override fun clearSleepTimer() = controller.clearSleepTimer()

    override fun setSleepTimerToEndOfTrack() = controller.setSleepTimerToEndOfTrack()
}

/** Cross-feature now-playing actions that have not yet moved to their feature owners. */
class ControllerNowPlayingActionPort(
    private val controller: FuoPlayerController,
) : NowPlayingActionPort {
    override val replacementCandidateState: ReplacementCandidateState
        get() = controller.replacementCandidateState
    override val downloadStates: Map<String, DownloadState>
        get() = controller.downloadStates

    override fun download(track: MusicTrack) = controller.download(track)
    override fun deleteDownload(track: MusicTrack) = controller.deleteDownload(track)
    override fun openTrackArtist(track: MusicTrack) = controller.openTrackArtist(track)
    override fun openTrackAlbum(track: MusicTrack) = controller.openTrackAlbum(track)
    override fun openOriginalTrackDetail(track: MusicTrack) = controller.openOriginalTrackDetail(track)
    override fun openLocalMetadataEditor(track: MusicTrack) = controller.openLocalMetadataEditor(track)
    override fun canAddTrackToPlaylist(track: MusicTrack): Boolean = controller.canAddTrackToPlaylist(track)
    override fun openPlaylistTargetPicker(track: MusicTrack) = controller.openPlaylistTargetPicker(track)
    override fun canRemoveTrackFromSelectedPlaylist(track: MusicTrack): Boolean =
        controller.canRemoveTrackFromSelectedPlaylist(track)
    override fun removeTrackFromSelectedPlaylist(track: MusicTrack) =
        controller.removeTrackFromSelectedPlaylist(track)
    override fun canSetSongDisliked(track: MusicTrack): Boolean = controller.canSetSongDisliked(track, true)
    override fun setSongDisliked(track: MusicTrack) = controller.setSongDisliked(track, true)

    override fun loadReplacementCandidates(track: MusicTrack) = controller.loadReplacementCandidates(track)
    override fun selectReplacementCandidate(track: MusicTrack, candidate: ReplacementCandidate) =
        controller.selectReplacementCandidate(track, candidate)
    override fun openReplacementTrackDetail(track: MusicTrack) = controller.openReplacementTrackDetail(track)
}
