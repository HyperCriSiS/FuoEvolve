package org.feeluown.mobile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Temporary compatibility bridge while search ownership is migrated out of [FuoPlayerController].
 *
 * Keep controller-specific knowledge here so the feature UI can depend on a narrow contract.
 * Once SearchController is constructed by the app composition root, this bridge can be removed
 * without changing SearchFeatureScreen.
 */
@Composable
fun SearchScreen(
    controller: FuoPlayerController,
    onOpenRecognition: () -> Unit,
) {
    val uiState by controller.searchUiState.collectAsStateWithLifecycle()

    SearchFeatureScreen(
        uiState = uiState,
        providers = controller.providers,
        downloadStates = controller.downloadStates,
        actions = SearchFeatureActions(
            dispatch = { action ->
                when (action) {
                    is SearchAction.QueryChanged -> controller.onQueryChange(action.value)
                    is SearchAction.ScopeChanged -> controller.onSearchScopeChange(action.value)
                    is SearchAction.ProviderChanged -> controller.onSearchProviderChange(action.providerId)
                    is SearchAction.ProviderTabChanged -> controller.onProviderSearchTabChange(action.value)
                    SearchAction.Submit -> controller.search()
                }
            },
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
            onOpenPlaylist = controller::openPlaylist,
            onOpenVideo = controller::openVideo,
        ),
        onOpenRecognition = onOpenRecognition,
    )
}
