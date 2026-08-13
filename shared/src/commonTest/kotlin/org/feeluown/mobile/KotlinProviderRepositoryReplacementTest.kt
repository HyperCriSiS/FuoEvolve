package org.feeluown.mobile

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KotlinProviderRepositoryReplacementTest {
    @Test
    fun textSimilarityKeepsCharacterOrder() {
        assertEquals(1.0, replacementTextSimilarity("abc", "abc"), 0.0001)
        assertTrue(replacementTextSimilarity("abc", "cba") < 0.5)
        assertTrue(replacementTextSimilarity("后来我们", "我们后来") < 0.8)
    }

    @Test
    fun replacementRejectsDifferentVersionQualifiers() {
        val origin = track("netease", "晴天", "周杰伦", durationMs = 269_000)
        val live = track("qqmusic", "晴天 Live", "周杰伦", durationMs = 270_000)
        val cover = track("bilibili", "周杰伦 晴天 Cover 翻唱", "Uploader", durationMs = 269_000)

        assertEquals(0.0, replacementMatchScore(origin, live), 0.0001)
        assertEquals(0.0, replacementMatchScore(origin, cover), 0.0001)
    }

    @Test
    fun replacementKeepsMatchingVersionQualifier() {
        val origin = track("netease", "晴天 Remix", "周杰伦", durationMs = 240_000)
        val candidate = track("qqmusic", "晴天 REMIX", "周杰伦", durationMs = 241_000)

        assertTrue(replacementMatchScore(origin, candidate) > 0.85)
    }

    @Test
    fun replacementRejectsLargeDurationDifference() {
        val origin = track("netease", "Night Song", "Alice", durationMs = 200_000)
        val candidate = track("qqmusic", "Night Song", "Alice", durationMs = 230_000)

        assertEquals(0.0, replacementMatchScore(origin, candidate), 0.0001)
    }

    @Test
    fun matchingAlbumImprovesConfidence() {
        val origin = track("netease", "Night Song", "Alice", album = "Moon", durationMs = 200_000)
        val sameAlbum = track("qqmusic", "Night Song", "Alice", album = "Moon", durationMs = 200_000)
        val otherAlbum = track("ytmusic", "Night Song", "Alice", album = "Different Collection", durationMs = 200_000)

        assertTrue(replacementMatchScore(origin, sameAlbum) > replacementMatchScore(origin, otherAlbum))
    }

    @Test
    fun artistSetsIgnoreOrderingAndCommonSeparators() {
        val origin = track("netease", "Together", "Alice / Bob", durationMs = 180_000)
        val candidate = track("qqmusic", "Together", "Bob & Alice", durationMs = 180_000)

        assertTrue(replacementMatchScore(origin, candidate) > 0.90)
    }

    @Test
    fun bilibiliScoreUsesSameZeroToOneConfidenceScale() {
        val origin = track(
            source = "netease",
            title = "Night Song",
            artists = "Alice",
            durationMs = 200_000,
        )
        val candidate = track(
            source = "bilibili",
            title = "ALICE - Night Song Hi-Res MV",
            artists = "Uploader",
            durationMs = 201_000,
        )

        val score = bilibiliReplacementScore(origin, candidate)
        assertTrue(score in 0.80..1.0)
    }

    @Test
    fun automaticSelectionAbstainsWhenTopCandidatesAreTooClose() = runTest {
        val first = ReplacementCandidate(track("qqmusic", "First", "Artist", id = "first"), 0.90)
        val second = ReplacementCandidate(track("ytmusic", "Second", "Artist", id = "second"), 0.87)
        val attempts = mutableListOf<String>()

        val selected = selectRankedReplacementCandidate(
            candidates = listOf(first, second),
            minScoreMargin = 0.06,
            resolve = { candidate ->
                attempts += candidate.id
                "payload:${candidate.id}"
            },
        )

        assertNull(selected)
        assertEquals(listOf("first"), attempts)
    }

    @Test
    fun automaticSelectionAcceptsClearWinner() = runTest {
        val first = ReplacementCandidate(track("qqmusic", "First", "Artist", id = "first"), 0.92)
        val second = ReplacementCandidate(track("ytmusic", "Second", "Artist", id = "second"), 0.80)

        val selected = selectRankedReplacementCandidate(
            candidates = listOf(first, second),
            minScoreMargin = 0.06,
            resolve = { candidate -> "payload:${candidate.id}" },
        )

        assertNotNull(selected)
        assertEquals("first", selected.first.id)
    }

    @Test
    fun replacementSelectionResolvesInScoreOrderAndStopsAtFirstPlayableCandidate() = runTest {
        val low = track("bilibili", "Low", "Uploader", id = "low")
        val medium = track("ytmusic", "Medium", "Artist", id = "medium")
        val high = track("qqmusic", "High", "Artist", id = "high")
        val scores = mapOf(
            low.id to 0.40,
            medium.id to 0.80,
            high.id to 0.95,
        )
        val attempts = mutableListOf<String>()

        val selected = selectReplacementCandidate(
            candidates = listOf(low, medium, high),
            minScore = 0.55,
            scoreOf = { candidate -> scores.getValue(candidate.id) },
            resolve = { candidate ->
                attempts += candidate.id
                when (candidate.id) {
                    high.id -> null
                    medium.id -> "playable"
                    else -> error("below-threshold candidate should not be resolved")
                }
            },
        )

        assertNotNull(selected)
        assertEquals(listOf(high.id, medium.id), attempts)
        assertEquals(medium.id, selected.first.id)
        assertEquals(0.80, selected.second, 0.0001)
        assertEquals("playable", selected.third)
    }

    @Test
    fun replacementCandidatesAreDeduplicatedFilteredAndStableForEqualScores() {
        val duplicateFirst = track("qqmusic", "First result", "Artist", id = "duplicate")
        val duplicateLater = track("qqmusic", "Later result", "Artist", id = "duplicate")
        val tieFirst = track("bilibili", "Tie first", "Artist", id = "tie-first")
        val tieSecond = track("ytmusic", "Tie second", "Artist", id = "tie-second")
        val ranked = rankReplacementCandidates(
            candidates = listOf(
                track("qqmusic", "Below threshold", "Artist", id = "below"),
                tieFirst,
                duplicateFirst,
                tieSecond,
                duplicateLater,
                track("bilibili", "Best", "Artist", id = "best"),
            ),
            minScore = 0.5,
            scoreOf = { candidate ->
                mapOf(
                    "below" to 0.4,
                    "tie-first" to 0.8,
                    "tie-second" to 0.8,
                    "duplicate" to 0.7,
                    "best" to 0.95,
                ).getValue(candidate.id)
            },
        )

        assertEquals(listOf("best", "tie-first", "tie-second", "duplicate"), ranked.map { it.track.id })
        assertEquals("First result", ranked.last().track.title)
    }

    private fun track(
        source: String,
        title: String,
        artists: String,
        album: String = "",
        durationMs: Long? = null,
        id: String = "$source:$title",
    ): MusicTrack = MusicTrack(
        id = id,
        title = title,
        artists = artists,
        album = album,
        source = source,
        sourceType = TrackSourceType.Provider,
        durationMs = durationMs,
        providerId = id,
    )
}
