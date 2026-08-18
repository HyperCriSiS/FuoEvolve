from pathlib import Path

controller_path = Path("shared/src/commonMain/kotlin/org/feeluown/mobile/FuoPlayerController.kt")
source = controller_path.read_text(encoding="utf-8")


def replace_once(old: str, new: str, label: str) -> None:
    global source
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    source = source.replace(old, new, 1)


replace_once(
    """    private var pendingManualReplacementSwitch: PendingManualReplacementSwitch? = null
    private var suppressPlaybackRecoveryRequestSerial: Long? = null
    private var lyricsLoadJob: Job? = null
    private var lyricsLoadedForTrackId: String? = null
    private var playbackParts: List<PlaybackPart> = emptyList()
""",
    """    private var pendingManualReplacementSwitch: PendingManualReplacementSwitch? = null
    private var suppressPlaybackRecoveryRequestSerial: Long? = null
    private var playbackParts: List<PlaybackPart> = emptyList()
""",
    "lyrics job fields",
)

replace_once(
    """    private val sleepTimerController = PlaybackSleepTimerController(
        playbackEngine = playbackEngine,
        scope = scope,
        nowMillis = nowMillis,
        onFeedback = { playbackFeedback = it },
    )

    init {
""",
    """    private val sleepTimerController = PlaybackSleepTimerController(
        playbackEngine = playbackEngine,
        scope = scope,
        nowMillis = nowMillis,
        onFeedback = { playbackFeedback = it },
    )
    private val playbackLyricsController = PlaybackLyricsController(
        providerRepository = providerRepository,
        scope = scope,
        currentRequestSerial = { playRequestSerial },
        currentTrackId = { currentQueueTrack()?.id ?: playbackState.currentTrack?.id },
        currentLyrics = { playbackState.lyrics },
        updateLyrics = { lyrics -> playbackState = playbackState.copy(lyrics = lyrics) },
    )

    init {
""",
    "lyrics controller wiring",
)

replace_once(
    """                    currentPartIndex = engineState.currentPartIndex.takeIf { it >= 0 } ?: currentPartIndex,
                    lyrics = mergedPlaybackLyrics(engineState),
                )
                maybeLoadLyrics(playbackState.currentTrack)
""",
    """                    currentPartIndex = engineState.currentPartIndex.takeIf { it >= 0 } ?: currentPartIndex,
                    lyrics = playbackLyricsController.mergedLyrics(
                        engineState = engineState,
                        currentQueueTrackId = currentQueueTrack()?.id,
                        previousPlaybackState = playbackState,
                    ),
                )
                playbackLyricsController.maybeLoad(playbackState.currentTrack)
""",
    "engine lyrics integration",
)

replace_once(
    """    private fun mergedPlaybackLyrics(engineState: PlaybackState): String? {
        val engineTrackId = engineState.currentTrack?.id
        val currentId = currentQueueTrack()?.id
            ?: engineTrackId
            ?: playbackState.currentTrack?.id
        engineState.lyrics?.takeIf {
            it.isNotBlank() && (engineTrackId == null || engineTrackId == currentId)
        }?.let { return it }
        val previousTrackId = playbackState.currentTrack?.id
        return playbackState.lyrics?.takeIf {
            it.isNotBlank() && previousTrackId != null && previousTrackId == currentId
        }
    }

    private fun maybeLoadLyrics(track: MusicTrack?) {
        if (track == null) return
        if (!playbackState.lyrics.isNullOrBlank()) {
            lyricsLoadedForTrackId = track.id
            return
        }
        track.lyrics?.takeIf { it.isNotBlank() }?.let {
            playbackState = playbackState.copy(lyrics = it)
            lyricsLoadedForTrackId = track.id
            return
        }
        if (lyricsLoadedForTrackId == track.id) return
        lyricsLoadedForTrackId = track.id
        val requestSerial = playRequestSerial
        lyricsLoadJob?.cancel()
        lyricsLoadJob = scope.launch {
            val lyrics = runCatching {
                providerRepository.lyrics(lyricSourceTrackForPlayback(track))
            }.getOrNull()?.takeIf { it.isNotBlank() }
            if (requestSerial != playRequestSerial) return@launch
            val currentId = currentQueueTrack()?.id ?: playbackState.currentTrack?.id
            if (currentId != track.id) return@launch
            if (!lyrics.isNullOrBlank()) {
                playbackState = playbackState.copy(lyrics = lyrics)
            }
        }
    }

    private fun lyricSourceTrackForPlayback(track: MusicTrack): MusicTrack {
        if (!track.isSmartReplacement) return track
        val originalId = track.originalId?.takeIf { it.isNotBlank() } ?: return track
        val originalSource = track.originalSource?.takeIf { it.isNotBlank() }
            ?: originalId.substringBefore(':').takeIf { it.isNotBlank() }
            ?: track.source
        return track.copy(
            id = originalId,
            providerId = originalId,
            source = originalSource,
            providerName = track.originalProviderName ?: track.providerName,
            title = track.originalTitle ?: track.title,
            artists = track.originalArtists ?: track.artists,
            album = track.originalAlbum ?: track.album,
            coverUrl = track.originalCoverUrl ?: track.coverUrl,
            isSmartReplacement = false,
            lyrics = null,
        )
    }

""",
    "",
    "lyrics helper implementation",
)

replace_once(
    """        updateCurrentTrack(playbackTrack)
        lyricsLoadJob?.cancel()
        lyricsLoadedForTrackId = null
        playbackState = playbackState.copy(
""",
    """        updateCurrentTrack(playbackTrack)
        playbackLyricsController.resetForPlaybackRequest()
        playbackState = playbackState.copy(
""",
    "lyrics reset on playback request",
)

if source.count("maybeLoadLyrics(") != 2:
    raise SystemExit(f"expected two remaining maybeLoadLyrics calls, found {source.count('maybeLoadLyrics(')}")
source = source.replace("maybeLoadLyrics(playbackTrack)", "playbackLyricsController.maybeLoad(playbackTrack)", 1)
source = source.replace("maybeLoadLyrics(playableTrack)", "playbackLyricsController.maybeLoad(playableTrack)", 1)

for stale in (
    "lyricsLoadJob",
    "lyricsLoadedForTrackId",
    "mergedPlaybackLyrics",
    "maybeLoadLyrics(",
    "lyricSourceTrackForPlayback",
):
    if stale in source:
        raise SystemExit(f"stale lyrics implementation remains: {stale}")

controller_path.write_text(source, encoding="utf-8")

lyrics_controller = r'''package org.feeluown.mobile

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class PlaybackLyricsController(
    private val providerRepository: ProviderMusicRepository,
    private val scope: CoroutineScope,
    private val currentRequestSerial: () -> Long,
    private val currentTrackId: () -> String?,
    private val currentLyrics: () -> String?,
    private val updateLyrics: (String) -> Unit,
) {
    private var loadJob: Job? = null
    private var loadedForTrackId: String? = null

    fun resetForPlaybackRequest() {
        loadJob?.cancel()
        loadJob = null
        loadedForTrackId = null
    }

    fun mergedLyrics(
        engineState: PlaybackState,
        currentQueueTrackId: String?,
        previousPlaybackState: PlaybackState,
    ): String? {
        val engineTrackId = engineState.currentTrack?.id
        val currentId = currentQueueTrackId
            ?: engineTrackId
            ?: previousPlaybackState.currentTrack?.id
        engineState.lyrics?.takeIf {
            it.isNotBlank() && (engineTrackId == null || engineTrackId == currentId)
        }?.let { return it }
        val previousTrackId = previousPlaybackState.currentTrack?.id
        return previousPlaybackState.lyrics?.takeIf {
            it.isNotBlank() && previousTrackId != null && previousTrackId == currentId
        }
    }

    fun maybeLoad(track: MusicTrack?) {
        if (track == null) return
        if (!currentLyrics().isNullOrBlank()) {
            loadedForTrackId = track.id
            return
        }
        track.lyrics?.takeIf { it.isNotBlank() }?.let {
            updateLyrics(it)
            loadedForTrackId = track.id
            return
        }
        if (loadedForTrackId == track.id) return
        loadedForTrackId = track.id
        val requestSerial = currentRequestSerial()
        loadJob?.cancel()
        loadJob = scope.launch {
            val lyrics = runCatching {
                providerRepository.lyrics(lyricSourceTrackForPlayback(track))
            }.getOrNull()?.takeIf { it.isNotBlank() }
            if (requestSerial != currentRequestSerial()) return@launch
            if (currentTrackId() != track.id) return@launch
            if (!lyrics.isNullOrBlank()) {
                updateLyrics(lyrics)
            }
        }
    }

    private fun lyricSourceTrackForPlayback(track: MusicTrack): MusicTrack {
        if (!track.isSmartReplacement) return track
        val originalId = track.originalId?.takeIf { it.isNotBlank() } ?: return track
        val originalSource = track.originalSource?.takeIf { it.isNotBlank() }
            ?: originalId.substringBefore(':').takeIf { it.isNotBlank() }
            ?: track.source
        return track.copy(
            id = originalId,
            providerId = originalId,
            source = originalSource,
            providerName = track.originalProviderName ?: track.providerName,
            title = track.originalTitle ?: track.title,
            artists = track.originalArtists ?: track.artists,
            album = track.originalAlbum ?: track.album,
            coverUrl = track.originalCoverUrl ?: track.coverUrl,
            isSmartReplacement = false,
            lyrics = null,
        )
    }
}
'''

Path("shared/src/commonMain/kotlin/org/feeluown/mobile/PlaybackLyricsController.kt").write_text(
    lyrics_controller,
    encoding="utf-8",
)
