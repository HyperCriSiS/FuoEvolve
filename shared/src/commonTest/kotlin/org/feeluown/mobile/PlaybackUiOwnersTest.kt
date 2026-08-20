package org.feeluown.mobile

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackUiOwnersTest {
    @Test
    fun navigationOwnsFullPlayerAndQueueVisibility() = runTest {
        val navigation = DefaultPlaybackNavigationPort(scope = this)

        navigation.toggleQueue()
        assertFalse(navigation.isQueueOpen)

        navigation.openFullPlayer()
        navigation.toggleQueue()
        assertTrue(navigation.isFullPlayerOpen)
        assertTrue(navigation.isQueueOpen)

        navigation.closeFullPlayer()
        assertFalse(navigation.isFullPlayerOpen)
        assertFalse(navigation.isQueueOpen)
    }

    @Test
    fun navigationMirrorsLegacyOverlayStateInBothDirections() = runTest {
        val legacyFullPlayerOpen = mutableStateOf(false)
        val legacyQueueOpen = mutableStateOf(false)
        val navigation = DefaultPlaybackNavigationPort(
            scope = backgroundScope,
            compatibilityState = {
                PlaybackNavigationSnapshot(
                    isFullPlayerOpen = legacyFullPlayerOpen.value,
                    isQueueOpen = legacyQueueOpen.value,
                )
            },
            compatibilityOpenFullPlayer = { legacyFullPlayerOpen.value = true },
            compatibilityCloseFullPlayer = {
                legacyQueueOpen.value = false
                legacyFullPlayerOpen.value = false
            },
            compatibilityToggleQueue = { legacyQueueOpen.value = !legacyQueueOpen.value },
        )
        Snapshot.sendApplyNotifications()
        runCurrent()

        navigation.openFullPlayer()
        navigation.toggleQueue()
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertTrue(legacyFullPlayerOpen.value)
        assertTrue(legacyQueueOpen.value)
        assertTrue(navigation.isQueueOpen)

        legacyQueueOpen.value = false
        legacyFullPlayerOpen.value = false
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertFalse(navigation.isFullPlayerOpen)
        assertFalse(navigation.isQueueOpen)
    }

    @Test
    fun presentationFallsBackToRestoredQueueTrackWhenEngineIsEmpty() {
        val restored = track("track:1").copy(title = "Restored title")

        assertEquals(
            restored,
            resolvePlaybackPresentationTrack(
                engineTrack = null,
                queueTrack = restored,
            ),
        )
    }

    @Test
    fun presentationPrefersQueueMetadataForSameTrackIdentity() {
        val engineTrack = track("track:1").copy(title = "Stale title")
        val editedQueueTrack = engineTrack.copy(title = "Edited local title")

        assertEquals(
            editedQueueTrack,
            resolvePlaybackPresentationTrack(
                engineTrack = engineTrack,
                queueTrack = editedQueueTrack,
            ),
        )
    }

    @Test
    fun presentationKeepsEngineTrackDuringIdentityTransition() {
        val engineTrack = track("track:1")
        val queueTrack = track("track:2")

        assertEquals(
            engineTrack,
            resolvePlaybackPresentationTrack(
                engineTrack = engineTrack,
                queueTrack = queueTrack,
            ),
        )
    }

    private fun track(id: String): MusicTrack = MusicTrack(
        id = id,
        title = id,
        artists = "Artist",
        album = "Album",
        source = "netease",
        sourceType = TrackSourceType.Provider,
    )
}
