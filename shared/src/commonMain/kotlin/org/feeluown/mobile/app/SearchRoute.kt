package org.feeluown.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** App-shell composition for the search feature. */
@Composable
internal fun SearchRoute(
    appViewModel: FuoAppViewModel,
    controller: FuoPlayerController,
    onOpenRecognition: () -> Unit,
) {
    val uiState by appViewModel.searchUiState.collectAsStateWithLifecycle()

    SearchFeatureScreen(
        uiState = uiState,
        providers = controller.providers,
        downloadStates = controller.downloadStates,
        actions = SearchFeatureActions(
            dispatch = appViewModel::dispatchSearch,
            onBack = controller::closeSearch,
            onPlayResult = controller::playFromSearch,
            onAddToUpNext = controller::addToUpNext,
            onDownload = controller::download,
            onDeleteDownload = controller::deleteDownload,
            onOpenArtist = controller::openTrackArtist,
            onOpenAlbum = controller::openTrackAlbum,
            onOpenTrackDetail = { track ->
                if (track.sourceType == TrackSourceType.Provider) {
                    { controller.openTrackDetail(track) }
                } else {
                    null
                }
            },
            onAddToPlaylist = { track ->
                if (controller.canAddTrackToPlaylist(track)) {
                    { controller.openPlaylistTargetPicker(track) }
                } else {
                    null
                }
            },
            onOpenMediaItem = controller::openMediaItem,
            onOpenPlaylist = { playlist -> controller.openPlaylist(playlist) },
            onOpenVideo = controller::openVideo,
        ),
        onOpenRecognition = onOpenRecognition,
    )
}
