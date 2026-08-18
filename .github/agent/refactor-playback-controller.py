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
    """    var playbackState by mutableStateOf(PlaybackState())
        private set
    var sleepTimerState by mutableStateOf(SleepTimerState())
        private set
    var trackChangeDirection by mutableStateOf(TrackChangeDirection.Next)
""",
    """    var playbackState by mutableStateOf(PlaybackState())
        private set
    var sleepTimerState: SleepTimerState
        get() = sleepTimerController.state
        private set(value) {
            sleepTimerController.state = value
        }
    var trackChangeDirection by mutableStateOf(TrackChangeDirection.Next)
""",
    "sleep timer facade state",
)

replace_once(
    """    private var lyricsLoadJob: Job? = null
    private var lyricsLoadedForTrackId: String? = null
    private var sleepTimerJob: Job? = null
    private var sleepTimerSerial: Long = 0L
    private var playbackParts: List<PlaybackPart> = emptyList()
""",
    """    private var lyricsLoadJob: Job? = null
    private var lyricsLoadedForTrackId: String? = null
    private var playbackParts: List<PlaybackPart> = emptyList()
""",
    "sleep timer job fields",
)

replace_once(
    """    )

    init {
        scope.launch {
            refreshLocalPlaylistsInternal(showMessage = false)
""",
    """    )
    private val sleepTimerController = PlaybackSleepTimerController(
        playbackEngine = playbackEngine,
        scope = scope,
        nowMillis = nowMillis,
        onFeedback = { playbackFeedback = it },
    )

    init {
        scope.launch {
            refreshLocalPlaylistsInternal(showMessage = false)
""",
    "sleep timer controller wiring",
)

replace_once(
    """                val queueTrackId = currentQueueTrack()?.id
                if (queueTrackId != null) {
                    clearEndOfTrackTimerIfTrackChanged(queueTrackId)
                }
                var shouldAutoAdvance = false
""",
    """                val queueTrackId = currentQueueTrack()?.id
                if (queueTrackId != null) {
                    sleepTimerController.onTrackChanged(queueTrackId)
                }
                var shouldAutoAdvance = false
""",
    "engine track-change timer hook",
)

replace_once(
    """                            if (
                                sleepTimerState.mode == SleepTimerMode.EndOfTrack &&
                                sleepTimerState.targetTrackId == endedTrackId &&
                                isFinalPlaybackPart
                            ) {
                                shouldCompleteEndOfTrackTimer = true
                            } else {
                                shouldAutoAdvance = true
                            }
""",
    """                            if (
                                sleepTimerController.shouldCompleteEndOfTrack(
                                    trackId = endedTrackId,
                                    isFinalPlaybackPart = isFinalPlaybackPart,
                                )
                            ) {
                                shouldCompleteEndOfTrackTimer = true
                            } else {
                                shouldAutoAdvance = true
                            }
""",
    "end-of-track timer decision",
)

replace_once(
    """                if (shouldCompleteEndOfTrackTimer) {
                    resetSleepTimer()
                    playbackFeedback = \"当前曲目已播放完，播放已暂停\"
                } else if (shouldAutoAdvance) {
""",
    """                if (shouldCompleteEndOfTrackTimer) {
                    sleepTimerController.completeEndOfTrack()
                } else if (shouldAutoAdvance) {
""",
    "end-of-track timer completion",
)

replace_once(
    """    fun setSleepTimerDurationMinutes(minutes: Int) {
        if (currentSleepTimerTrackId() == null) {
            playbackFeedback = \"请先播放一首歌曲\"
            return
        }
        if (minutes !in SLEEP_TIMER_MIN_MINUTES..SLEEP_TIMER_MAX_MINUTES) {
            playbackFeedback = \"请输入 $SLEEP_TIMER_MIN_MINUTES–$SLEEP_TIMER_MAX_MINUTES 分钟\"
            return
        }
        val durationMs = minutes.toLong() * 60_000L
        val deadlineMs = nowMillis() + durationMs
        replaceSleepTimer(
            SleepTimerState(
                mode = SleepTimerMode.Duration,
                deadlineMs = deadlineMs,
                remainingMs = durationMs,
            ),
        )
    }

    fun setSleepTimerToEndOfTrack() {
        val trackId = currentSleepTimerTrackId()
        if (trackId == null) {
            playbackFeedback = \"请先播放一首歌曲\"
            return
        }
        replaceSleepTimer(
            SleepTimerState(
                mode = SleepTimerMode.EndOfTrack,
                targetTrackId = trackId,
            ),
        )
    }

    fun clearSleepTimer() {
        resetSleepTimer()
    }

    private fun currentSleepTimerTrackId(): String? = currentQueueTrack()?.id
        ?: playbackState.currentTrack?.id

    private fun replaceSleepTimer(state: SleepTimerState) {
        resetSleepTimer()
        sleepTimerState = state
        playbackEngine.setStopAfterCurrentTrack(state.mode == SleepTimerMode.EndOfTrack)
        if (state.mode != SleepTimerMode.Duration) return
        val deadlineMs = state.deadlineMs ?: return
        val serial = sleepTimerSerial
        sleepTimerJob = scope.launch {
            while (serial == sleepTimerSerial) {
                val remainingMs = deadlineMs - nowMillis()
                if (remainingMs <= 0L) {
                    resetSleepTimer()
                    playbackEngine.pause()
                    playbackFeedback = \"睡眠定时已结束，播放已暂停\"
                    break
                }
                sleepTimerState = sleepTimerState.copy(remainingMs = remainingMs)
                delay(minOf(remainingMs, 1_000L))
            }
        }
    }

    private fun resetSleepTimer() {
        sleepTimerSerial += 1L
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        sleepTimerState = SleepTimerState()
        playbackEngine.setStopAfterCurrentTrack(false)
    }

    private fun clearEndOfTrackTimerIfTrackChanged(trackId: String?) {
        if (
            sleepTimerState.mode == SleepTimerMode.EndOfTrack &&
            sleepTimerState.targetTrackId != trackId
        ) {
            resetSleepTimer()
        }
    }
""",
    """    fun setSleepTimerDurationMinutes(minutes: Int) {
        sleepTimerController.setDurationMinutes(
            minutes = minutes,
            currentTrackId = currentQueueTrack()?.id ?: playbackState.currentTrack?.id,
        )
    }

    fun setSleepTimerToEndOfTrack() {
        sleepTimerController.setToEndOfTrack(
            currentTrackId = currentQueueTrack()?.id ?: playbackState.currentTrack?.id,
        )
    }

    fun clearSleepTimer() {
        sleepTimerController.clear()
    }
""",
    "sleep timer public API delegation",
)

replace_once(
    """    ) {
        clearEndOfTrackTimerIfTrackChanged(track.id)
        if (
            sleepTimerState.mode == SleepTimerMode.EndOfTrack &&
            sleepTimerState.targetTrackId == track.id
        ) {
            playbackEngine.setStopAfterCurrentTrack(true)
        }
        val requestSerial = ++playRequestSerial
""",
    """    ) {
        sleepTimerController.prepareForTrack(track.id)
        val requestSerial = ++playRequestSerial
""",
    "playback-start timer preparation",
)

for stale in (
    "sleepTimerJob",
    "sleepTimerSerial",
    "resetSleepTimer()",
    "clearEndOfTrackTimerIfTrackChanged",
):
    if stale in source:
        raise SystemExit(f"stale sleep-timer implementation remains: {stale}")

controller_path.write_text(source, encoding="utf-8")

sleep_timer_controller = r'''package org.feeluown.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class PlaybackSleepTimerController(
    private val playbackEngine: PlaybackEngine,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long,
    private val onFeedback: (String) -> Unit,
) {
    var state by mutableStateOf(SleepTimerState())

    private var timerJob: Job? = null
    private var timerSerial: Long = 0L

    fun setDurationMinutes(minutes: Int, currentTrackId: String?) {
        if (currentTrackId == null) {
            onFeedback("请先播放一首歌曲")
            return
        }
        if (minutes !in SLEEP_TIMER_MIN_MINUTES..SLEEP_TIMER_MAX_MINUTES) {
            onFeedback("请输入 $SLEEP_TIMER_MIN_MINUTES–$SLEEP_TIMER_MAX_MINUTES 分钟")
            return
        }
        val durationMs = minutes.toLong() * 60_000L
        replace(
            SleepTimerState(
                mode = SleepTimerMode.Duration,
                deadlineMs = nowMillis() + durationMs,
                remainingMs = durationMs,
            ),
        )
    }

    fun setToEndOfTrack(currentTrackId: String?) {
        if (currentTrackId == null) {
            onFeedback("请先播放一首歌曲")
            return
        }
        replace(
            SleepTimerState(
                mode = SleepTimerMode.EndOfTrack,
                targetTrackId = currentTrackId,
            ),
        )
    }

    fun clear() {
        timerSerial += 1L
        timerJob?.cancel()
        timerJob = null
        state = SleepTimerState()
        playbackEngine.setStopAfterCurrentTrack(false)
    }

    fun onTrackChanged(trackId: String?) {
        if (
            state.mode == SleepTimerMode.EndOfTrack &&
            state.targetTrackId != trackId
        ) {
            clear()
        }
    }

    fun prepareForTrack(trackId: String) {
        onTrackChanged(trackId)
        if (
            state.mode == SleepTimerMode.EndOfTrack &&
            state.targetTrackId == trackId
        ) {
            playbackEngine.setStopAfterCurrentTrack(true)
        }
    }

    fun shouldCompleteEndOfTrack(trackId: String, isFinalPlaybackPart: Boolean): Boolean {
        return state.mode == SleepTimerMode.EndOfTrack &&
            state.targetTrackId == trackId &&
            isFinalPlaybackPart
    }

    fun completeEndOfTrack() {
        clear()
        onFeedback("当前曲目已播放完，播放已暂停")
    }

    private fun replace(nextState: SleepTimerState) {
        clear()
        state = nextState
        playbackEngine.setStopAfterCurrentTrack(nextState.mode == SleepTimerMode.EndOfTrack)
        if (nextState.mode != SleepTimerMode.Duration) return
        val deadlineMs = nextState.deadlineMs ?: return
        val serial = timerSerial
        timerJob = scope.launch {
            while (serial == timerSerial) {
                val remainingMs = deadlineMs - nowMillis()
                if (remainingMs <= 0L) {
                    clear()
                    playbackEngine.pause()
                    onFeedback("睡眠定时已结束，播放已暂停")
                    break
                }
                state = state.copy(remainingMs = remainingMs)
                delay(minOf(remainingMs, 1_000L))
            }
        }
    }
}
'''

Path("shared/src/commonMain/kotlin/org/feeluown/mobile/PlaybackSleepTimerController.kt").write_text(
    sleep_timer_controller,
    encoding="utf-8",
)
