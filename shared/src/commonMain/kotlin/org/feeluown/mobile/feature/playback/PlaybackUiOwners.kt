package org.feeluown.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class PlaybackNavigationSnapshot(
    val isFullPlayerOpen: Boolean = false,
    val isQueueOpen: Boolean = false,
)

/**
 * Owns player-overlay navigation.
 *
 * The optional compatibility callbacks keep legacy feature callers working while their
 * `MiniPlayer(controller)` signatures are retired. The playback owner itself remains free of any
 * FuoPlayerController dependency and can drop this mirror once those call sites are migrated.
 */
internal class DefaultPlaybackNavigationPort(
    scope: CoroutineScope,
    compatibilityState: (() -> PlaybackNavigationSnapshot)? = null,
    private val compatibilityOpenFullPlayer: () -> Unit = {},
    private val compatibilityCloseFullPlayer: () -> Unit = {},
    private val compatibilityToggleQueue: () -> Unit = {},
) : PlaybackNavigationPort {
    override var isFullPlayerOpen by mutableStateOf(false)
        private set
    override var isQueueOpen by mutableStateOf(false)
        private set

    init {
        val compatibilityReader = compatibilityState
        if (compatibilityReader != null) {
            scope.launch {
                snapshotFlow { compatibilityReader() }.collect { snapshot ->
                    isFullPlayerOpen = snapshot.isFullPlayerOpen
                    isQueueOpen = snapshot.isQueueOpen && snapshot.isFullPlayerOpen
                }
            }
        }
    }

    override fun openFullPlayer() {
        isFullPlayerOpen = true
        compatibilityOpenFullPlayer()
    }

    override fun closeFullPlayer() {
        isQueueOpen = false
        isFullPlayerOpen = false
        compatibilityCloseFullPlayer()
    }

    override fun toggleQueue() {
        if (!isFullPlayerOpen) return
        isQueueOpen = !isQueueOpen
        compatibilityToggleQueue()
    }
}

/**
 * Reads rich presentation directly from the playback engine and settings owner.
 * Transport remains in PlaybackSession and queue edits remain in PlaybackQueueCoordinator.
 */
class DefaultPlaybackPresentationPort(
    private val playbackEngine: PlaybackEngine,
    settingsRepository: AppSettingsRepository,
    scope: CoroutineScope,
) : PlaybackPresentationPort {
    private var playbackState by mutableStateOf(playbackEngine.state.value)
    private var settings by mutableStateOf(settingsRepository.state.value.settings)

    init {
        scope.launch {
            playbackEngine.state.collect { playbackState = it }
        }
        scope.launch {
            settingsRepository.state.collect { settings = it.settings }
        }
    }

    override val currentTrack: MusicTrack?
        get() = playbackState.currentTrack
    override val playbackParts: List<PlaybackPart>
        get() = playbackState.playbackParts
    override val currentPartIndex: Int
        get() = playbackState.currentPartIndex
    override val lyricFontSize: LyricFontSize
        get() = settings.lyricFontSize
    override val themeMode: ThemeMode
        get() = settings.themeMode
    override val dynamicCoverColorEnabled: Boolean
        get() = settings.dynamicCoverColorEnabled
    override val audioQuality: String?
        get() = playbackState.audioQuality
    override val audioFormatInfo: AudioFormatInfo?
        get() = playbackState.audioFormatInfo
    override val audioDecoderInfo: AudioDecoderInfo?
        get() = playbackState.audioDecoderInfo

    override fun seekTo(positionMs: Long) {
        val normalizedPosition = positionMs.coerceAtLeast(0L).let { position ->
            playbackState.durationMs.takeIf { it > 0L }?.let(position::coerceAtMost) ?: position
        }
        playbackEngine.seekTo(normalizedPosition)
    }
}

/** Aggregate only for the current composable surface; ownership stays in the delegated ports. */
class DefaultPlaybackUiPort(
    navigation: PlaybackNavigationPort,
    presentation: PlaybackPresentationPort,
    queue: PlaybackQueueUiPort,
    sleepTimer: PlaybackSleepTimerPort,
    nowPlayingActions: NowPlayingActionPort,
) : PlaybackUiPort,
    PlaybackNavigationPort by navigation,
    PlaybackPresentationPort by presentation,
    PlaybackQueueUiPort by queue,
    PlaybackSleepTimerPort by sleepTimer,
    NowPlayingActionPort by nowPlayingActions
