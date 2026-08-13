package org.feeluown.mobile

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class KotlinProviderRepositoryReplacementTest {
    @Test
    fun bilibiliScoreMatchesTitleArtistAndBonusKeywords() {
        val origin = track(
            source = "netease",
            title = "Night Song",
            artists = "Alice",
        )
        val candidate = track(
            source = "bilibili",
            title = "ALICE - night song Hi-Res MV",
            artists = "Uploader",
        )

        assertEquals(0.70, bilibiliReplacementScore(origin, candidate), 0.0001)
    }

    @Test
    fun bilibiliScoreMatchesArtistMetadataWhenUploaderTitleOmitsArtist() {
        val origin = track(
            source = "netease",
            title = "人间芳菲",
            artists = "音阙诗听",
        )
        val candidate = track(
            source = "bilibili",
            title = "人间芳菲",
            artists = "音阙诗听",
        )

        assertEquals(0.60, bilibiliReplacementScore(origin, candidate), 0.0001)
    }

    @Test
    fun bilibiliScorePenalizesCoverKeywords() {
        val origin = track(
            source = "netease",
            title = "晴天",
            artists = "周杰伦",
        )
        val candidate = track(
            source = "bilibili",
            title = "周杰伦 晴天 Cover 翻唱",
            artists = "Uploader",
        )

        assertEquals(0.40, bilibiliReplacementScore(origin, candidate), 0.0001)
    }

    @Test
    fun bilibiliScoreKeepsOriginalKeywordVersions() {
        val origin = track(
            source = "netease",
            title = "晴天 Remix",
            artists = "周杰伦",
        )
        val candidate = track(
            source = "bilibili",
            title = "周杰伦 晴天 REMIX",
            artists = "Uploader",
        )

        assertEquals(0.60, bilibiliReplacementScore(origin, candidate), 0.0001)
    }

    @Test
    fun bilibiliScoreAppliesRelativeDurationPenalty() {
        val origin = track(
            source = "netease",
            title = "Night Song",
            artists = "Alice",
            durationMs = 200_000,
        )
        val candidate = track(
            source = "bilibili",
            title = "ALICE - night song Hi-Res MV",
            artists = "Uploader",
            durationMs = 300_000,
        )

        assertEquals(0.55, bilibiliReplacementScore(origin, candidate), 0.0001)
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

    @Test
    fun rankedCandidateResolverDoesNotTryCandidatesAfterFirstPlayableResult() = runTest {
        val first = ReplacementCandidate(track("qqmusic", "First", "Artist", id = "first"), 0.9)
        val second = ReplacementCandidate(track("bilibili", "Second", "Artist", id = "second"), 0.8)
        val attempts = mutableListOf<String>()

        val selected = selectRankedReplacementCandidate(
            candidates = listOf(first, second),
            resolve = { candidate ->
                attempts += candidate.id
                "payload:${candidate.id}"
            },
        )

        assertEquals(listOf("first"), attempts)
        assertEquals("first", selected?.first?.id)
    }

    private fun track(
        source: String,
        title: String,
        artists: String,
        durationMs: Long? = null,
        id: String = "$source:$title",
    ): MusicTrack = MusicTrack(
        id = id,
        title = title,
        artists = artists,
        album = "",
        source = source,
        sourceType = TrackSourceType.Provider,
        durationMs = durationMs,
        providerId = id,
    )
}
