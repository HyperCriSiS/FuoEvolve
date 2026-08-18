package org.feeluown.mobile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Owns sleep-timer state and lifecycle while FuoPlayerController remains the UI facade. */
internal class SleepTimerController(
    private val scope: CoroutineScope,
    private val playbackEngine: PlaybackEngine,
    private val nowMillis: () -> Long,
    private val currentTrackId: () -> String?,
    private val onFeedback: (String) -> Unit,
) {
    var state by mutableStateOf(SleepTimerState())
        private set

    private var timerJob: Job? = null
    private var timerSerial: Long = 0L

    fun setDurationMinutes(minutes: Int) {
        if (currentTrackId() == null) {
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

    fun setToEndOfTrack() {
        val trackId = currentTrackId()
        if (trackId == null) {
            onFeedback("请先播放一首歌曲")
            return
        }
        replace(
            SleepTimerState(
                mode = SleepTimerMode.EndOfTrack,
                targetTrackId = trackId,
            ),
        )
    }

    fun clear() {
        reset()
    }

    fun prepareTrack(trackId: String) {
        clearIfTrackChanged(trackId)
        if (state.mode == SleepTimerMode.EndOfTrack && state.targetTrackId == trackId) {
            playbackEngine.setStopAfterCurrentTrack(true)
        }
    }

    fun clearIfTrackChanged(trackId: String?) {
        if (state.mode == SleepTimerMode.EndOfTrack && state.targetTrackId != trackId) {
            reset()
        }
    }

    fun shouldCompleteEndOfTrack(trackId: String?, isFinalPlaybackPart: Boolean): Boolean =
        state.mode == SleepTimerMode.EndOfTrack &&
            state.targetTrackId == trackId &&
            isFinalPlaybackPart

    fun completeEndOfTrack() {
        reset()
        onFeedback("当前曲目已播放完，播放已暂停")
    }

    private fun replace(next: SleepTimerState) {
        reset()
        state = next
        playbackEngine.setStopAfterCurrentTrack(next.mode == SleepTimerMode.EndOfTrack)
        if (next.mode != SleepTimerMode.Duration) return
        val deadlineMs = next.deadlineMs ?: return
        val serial = timerSerial
        timerJob = scope.launch {
            while (serial == timerSerial) {
                val remainingMs = deadlineMs - nowMillis()
                if (remainingMs <= 0L) {
                    reset()
                    playbackEngine.pause()
                    onFeedback("睡眠定时已结束，播放已暂停")
                    break
                }
                state = state.copy(remainingMs = remainingMs)
                delay(minOf(remainingMs, 1_000L))
            }
        }
    }

    private fun reset() {
        timerSerial += 1L
        timerJob?.cancel()
        timerJob = null
        state = SleepTimerState()
        playbackEngine.setStopAfterCurrentTrack(false)
    }
}
