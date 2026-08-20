package org.feeluown.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class PlaylistControllerState {
    var localPlaylists by mutableStateOf<List<LocalPlaylist>>(emptyList())
    var selectedLocalPlaylist by mutableStateOf<LocalPlaylist?>(null)
    var selectedLocalPlaylistTracks by mutableStateOf<List<MusicTrack>>(emptyList())
    var selectedLocalPlaylistError by mutableStateOf<String?>(null)

    var playlistTargetTrack by mutableStateOf<MusicTrack?>(null)
    var playlistTargetType by mutableStateOf(PlaylistTargetType.Provider)
    var playlistTargetPickerShowSwitcher by mutableStateOf(true)
    var playlistOperationTargets by mutableStateOf<List<ProviderPlaylist>>(emptyList())
    var playlistOperationError by mutableStateOf<String?>(null)

    private val playlistOperationFeedbackState = mutableStateOf<String?>(null)
    private val mutablePlaylistOperationFeedback = MutableStateFlow<String?>(null)
    val playlistOperationFeedbackFlow: StateFlow<String?> = mutablePlaylistOperationFeedback.asStateFlow()
    var playlistOperationFeedback: String?
        get() = playlistOperationFeedbackState.value
        set(value) {
            playlistOperationFeedbackState.value = value
            mutablePlaylistOperationFeedback.value = value
        }

    var localPlaylistOperationError by mutableStateOf<String?>(null)
    var localPlaylistImportPreview by mutableStateOf<LocalPlaylistImportPreview?>(null)
}
