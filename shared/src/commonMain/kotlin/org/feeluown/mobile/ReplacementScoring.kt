package org.feeluown.mobile

import org.feeluown.mobile.provider.core.KotlinMusicProvider
import kotlin.math.abs
import kotlin.math.max

internal const val MIN_AUTOMATIC_REPLACEMENT_MARGIN = 0.06

private const val BILIBILI_PROVIDER_ID = "bilibili"
private const val YTMUSIC_PROVIDER_ID = "ytmusic"
private const val REPLACEMENT_DURATION_ENRICH_LIMIT = 3
private const val MISSING_METADATA_SCORE = 0.50
private const val MIN_CORE_TITLE_SCORE = 0.42
private const val MIN_ARTIST_SCORE = 0.28
private const val SOFT_VERSION_PENALTY = 0.08
private const val BILIBILI_PREFERRED_TITLE_BONUS = 0.03

private data class ReplacementTitleParts(
    val core: String,
    val qualifiers: Set<String>,
)

private data class ReplacementVersionRule(
    val key: String,
    val pattern: Regex,
    val hardConflict: Boolean = true,
)

private val REPLACEMENT_VERSION_RULES = listOf(
    ReplacementVersionRule(
        "live",
        Regex("""(?i)((^|[^\p{L}\p{N}])(live|concert)($|[^\p{L}\p{N}])|现场|演唱会)"""),
    ),
    ReplacementVersionRule(
        "remix",
        Regex("""(?i)((^|[^\p{L}\p{N}])remix($|[^\p{L}\p{N}])|混音)"""),
    ),
    ReplacementVersionRule(
        "cover",
        Regex("""(?i)((^|[^\p{L}\p{N}])cover($|[^\p{L}\p{N}])|翻唱)"""),
    ),
    ReplacementVersionRule(
        "instrumental",
        Regex("""(?i)((^|[^\p{L}\p{N}])(instrumental|karaoke|off[ -]?vocal)($|[^\p{L}\p{N}])|伴奏|纯音乐)"""),
    ),
    ReplacementVersionRule(
        "acoustic",
        Regex("""(?i)((^|[^\p{L}\p{N}])(acoustic|unplugged)($|[^\p{L}\p{N}])|不插电)"""),
    ),
    ReplacementVersionRule(
        "demo",
        Regex("""(?i)(^|[^\p{L}\p{N}])demo($|[^\p{L}\p{N}])"""),
    ),
    ReplacementVersionRule(
        "sped_up",
        Regex("""(?i)((^|[^\p{L}\p{N}])sped[ -]?up($|[^\p{L}\p{N}])|加速版|倍速版)"""),
    ),
    ReplacementVersionRule(
        "slowed",
        Regex("""(?i)((^|[^\p{L}\p{N}])(slowed|reverb)($|[^\p{L}\p{N}])|慢速版)"""),
    ),
    ReplacementVersionRule(
        "nightcore",
        Regex("""(?i)(^|[^\p{L}\p{N}])nightcore($|[^\p{L}\p{N}])"""),
    ),
    ReplacementVersionRule(
        "remaster",
        Regex("""(?i)((^|[^\p{L}\p{N}])(remaster|remastered)($|[^\p{L}\p{N}])|重制版|数字修复|修复版)"""),
        hardConflict = false,
    ),
    ReplacementVersionRule(
        "radio_edit",
        Regex("""(?i)(^|[^\p{L}\p{N}])radio[ -]?edit($|[^\p{L}\p{N}])"""),
    ),
    ReplacementVersionRule(
        "extended",
        Regex("""(?i)((^|[^\p{L}\p{N}])extended($|[^\p{L}\p{N}])|加长版)"""),
    ),
)

private val HARD_REPLACEMENT_VERSION_KEYS = REPLACEMENT_VERSION_RULES
    .filter { it.hardConflict }
    .mapTo(mutableSetOf()) { it.key }

private val REPLACEMENT_ARTIST_CONNECTOR_REGEX =
    Regex("""(?i)\b(feat|featuring|ft|with)\b\.?""")
private val REPLACEMENT_ARTIST_SPLIT_REGEX =
    Regex("""\s*(?:/|、|,|，|;|；|&|\+|＋|×)\s*""")
private val BILIBILI_PREFERRED_TITLE_PATTERNS = listOf(
    Regex("""(?i)(^|[^a-z0-9])mv($|[^a-z0-9])"""),
    Regex("""(?i)hi[ -]?res"""),
    Regex("""无损|高解析"""),
)

internal suspend fun enrichReplacementDurations(
    origin: MusicTrack,
    provider: KotlinMusicProvider,
    candidates: List<MusicTrack>,
): List<MusicTrack> {
    if (provider.id != YTMUSIC_PROVIDER_ID || origin.durationMs == null) return candidates
    val missing = candidates.filter { it.durationMs == null }
    if (missing.isEmpty()) return candidates

    val enrichIds = missing
        .map { candidate -> candidate to replacementMatchScore(origin, candidate) }
        .filter { (_, score) -> score > 0.0 }
        .sortedByDescending { (_, score) -> score }
        .take(REPLACEMENT_DURATION_ENRICH_LIMIT)
        .mapTo(mutableSetOf()) { (candidate, _) -> candidate.id }
    if (enrichIds.isEmpty()) return candidates

    val enrichedDurations = mutableMapOf<String, Long>()
    candidates.forEach { candidate ->
        if (candidate.id !in enrichIds) return@forEach
        val detail = runCatching {
            provider.trackDetail(candidate.providerId ?: candidate.id)
        }.getOrNull()
        detail?.durationMs?.takeIf { it > 0 }?.let { duration ->
            enrichedDurations[candidate.id] = duration
        }
    }
    if (enrichedDurations.isEmpty()) return candidates
    return candidates.map { candidate ->
        enrichedDurations[candidate.id]?.let { duration -> candidate.copy(durationMs = duration) } ?: candidate
    }
}

internal fun replacementMatchScore(origin: MusicTrack, candidate: MusicTrack): Double {
    val originTitle = replacementTitleParts(origin.title)
    val candidateTitle = replacementTitleParts(candidate.title)
    if (originTitle.core.isBlank() || candidateTitle.core.isBlank()) return 0.0
    if (hasHardVersionConflict(originTitle.qualifiers, candidateTitle.qualifiers)) return 0.0
    if (!replacementDurationCompatible(origin, candidate)) return 0.0

    val coreTitleScore = normalizedReplacementTextSimilarity(originTitle.core, candidateTitle.core)
    if (coreTitleScore < MIN_CORE_TITLE_SCORE) return 0.0

    val fullTitleScore = replacementTextSimilarity(origin.title, candidate.title)
    val artistScore = replacementArtistScore(origin, candidate)
    if (artistScore < MIN_ARTIST_SCORE && replacementArtistMatchTexts(origin.artists).isNotEmpty()) {
        return 0.0
    }
    val durationScore = replacementDurationScore(origin, candidate)
    val albumScore = replacementAlbumScore(origin.album, candidate.album)

    var score = coreTitleScore * 0.40 +
        artistScore * 0.25 +
        fullTitleScore * 0.10 +
        durationScore * 0.15 +
        albumScore * 0.10

    score -= replacementSoftVersionPenalty(originTitle.qualifiers, candidateTitle.qualifiers)
    if (candidate.source == BILIBILI_PROVIDER_ID && isBilibiliPreferredTitle(candidate.title)) {
        score += BILIBILI_PREFERRED_TITLE_BONUS
    }
    return score.coerceIn(0.0, 1.0)
}

internal fun bilibiliReplacementScore(origin: MusicTrack, candidate: MusicTrack): Double =
    replacementMatchScore(origin, candidate)

internal fun replacementTextSimilarity(leftValue: String, rightValue: String): Double =
    normalizedReplacementTextSimilarity(
        normalizeReplacementText(leftValue),
        normalizeReplacementText(rightValue),
    )

private fun normalizedReplacementTextSimilarity(left: String, right: String): Double {
    if (left.isBlank() || right.isBlank()) return 0.0
    if (left == right) return 1.0
    val edit = normalizedEditSimilarity(left, right)
    val bigram = bigramDiceSimilarity(left, right)
    val shorter = if (left.length <= right.length) left else right
    val longer = if (left.length <= right.length) right else left
    val containment = if (shorter in longer) {
        0.78 + 0.20 * (shorter.length.toDouble() / longer.length.toDouble())
    } else {
        0.0
    }
    return max(edit * 0.60 + bigram * 0.40, containment).coerceIn(0.0, 1.0)
}

private fun normalizedEditSimilarity(left: String, right: String): Double {
    val longest = maxOf(left.length, right.length)
    if (longest == 0) return 1.0
    val distance = levenshteinDistance(left, right)
    return (1.0 - distance.toDouble() / longest.toDouble()).coerceIn(0.0, 1.0)
}

private fun levenshteinDistance(left: String, right: String): Int {
    if (left.isEmpty()) return right.length
    if (right.isEmpty()) return left.length
    var previous = IntArray(right.length + 1) { it }
    var current = IntArray(right.length + 1)
    left.forEachIndexed { leftIndex, leftChar ->
        current[0] = leftIndex + 1
        right.forEachIndexed { rightIndex, rightChar ->
            val insertion = current[rightIndex] + 1
            val deletion = previous[rightIndex + 1] + 1
            val substitution = previous[rightIndex] + if (leftChar == rightChar) 0 else 1
            current[rightIndex + 1] = minOf(insertion, deletion, substitution)
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[right.length]
}

private fun bigramDiceSimilarity(left: String, right: String): Double {
    if (left.length < 2 || right.length < 2) return 0.0
    val leftCounts = replacementBigramCounts(left)
    val rightCounts = replacementBigramCounts(right)
    val intersection = leftCounts.entries.sumOf { (gram, count) ->
        minOf(count, rightCounts[gram] ?: 0)
    }
    val total = leftCounts.values.sum() + rightCounts.values.sum()
    if (total == 0) return 0.0
    return (2.0 * intersection.toDouble() / total.toDouble()).coerceIn(0.0, 1.0)
}

private fun replacementBigramCounts(value: String): Map<String, Int> {
    val counts = mutableMapOf<String, Int>()
    for (index in 0 until value.length - 1) {
        val gram = value.substring(index, index + 2)
        counts[gram] = (counts[gram] ?: 0) + 1
    }
    return counts
}

private fun replacementTitleParts(value: String): ReplacementTitleParts {
    var core = value.lowercase()
    val qualifiers = mutableSetOf<String>()
    REPLACEMENT_VERSION_RULES.forEach { rule ->
        if (rule.pattern.containsMatchIn(core)) qualifiers += rule.key
    }
    REPLACEMENT_VERSION_RULES.forEach { rule ->
        core = rule.pattern.replace(core, " ")
    }
    return ReplacementTitleParts(
        core = normalizeReplacementText(core).ifBlank { normalizeReplacementText(value) },
        qualifiers = qualifiers,
    )
}

private fun hasHardVersionConflict(origin: Set<String>, candidate: Set<String>): Boolean {
    val differences = (origin - candidate) + (candidate - origin)
    return differences.any { it in HARD_REPLACEMENT_VERSION_KEYS }
}

private fun replacementSoftVersionPenalty(origin: Set<String>, candidate: Set<String>): Double {
    val differences = ((origin - candidate) + (candidate - origin)) - HARD_REPLACEMENT_VERSION_KEYS
    return (differences.size * SOFT_VERSION_PENALTY).coerceAtMost(0.16)
}

private fun replacementArtistScore(origin: MusicTrack, candidate: MusicTrack): Double {
    val originArtists = replacementArtistMatchTexts(origin.artists)
    if (originArtists.isEmpty()) return MISSING_METADATA_SCORE
    val candidateArtists = replacementArtistMatchTexts(candidate.artists)
    val candidateTitle = normalizeReplacementText(candidate.title)
    val titleMentionsArtist = originArtists.any { artist -> artist.length >= 2 && artist in candidateTitle }
    if (candidateArtists.isEmpty()) {
        return if (candidate.source == BILIBILI_PROVIDER_ID && titleMentionsArtist) 0.95 else 0.20
    }

    val originCoverage = originArtists.map { originArtist ->
        candidateArtists.maxOf { candidateArtist ->
            normalizedReplacementTextSimilarity(originArtist, candidateArtist)
        }
    }.average()
    val candidateCoverage = candidateArtists.map { candidateArtist ->
        originArtists.maxOf { originArtist ->
            normalizedReplacementTextSimilarity(originArtist, candidateArtist)
        }
    }.average()
    var score = originCoverage * 0.70 + candidateCoverage * 0.30
    if (originArtists.first() == candidateArtists.first()) {
        score = max(score, if (originArtists.size == candidateArtists.size) 1.0 else 0.92)
    }
    if (candidate.source == BILIBILI_PROVIDER_ID && titleMentionsArtist) {
        score = max(score, 0.98)
    }
    return score.coerceIn(0.0, 1.0)
}

private fun replacementArtistMatchTexts(value: String): List<String> {
    val expanded = REPLACEMENT_ARTIST_CONNECTOR_REGEX.replace(value, "/")
    return REPLACEMENT_ARTIST_SPLIT_REGEX.split(expanded)
        .map { part -> normalizeReplacementText(part.trim()) }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun replacementAlbumScore(originAlbum: String, candidateAlbum: String): Double {
    if (originAlbum.isBlank() || candidateAlbum.isBlank()) return MISSING_METADATA_SCORE
    return replacementTextSimilarity(originAlbum, candidateAlbum)
}

private fun replacementDurationCompatible(origin: MusicTrack, candidate: MusicTrack): Boolean {
    val originDuration = origin.durationMs?.takeIf { it > 0 } ?: return true
    val candidateDuration = candidate.durationMs?.takeIf { it > 0 } ?: return true
    val tolerance = replacementDurationToleranceMs(originDuration, candidate.source)
    return abs(originDuration - candidateDuration) <= tolerance
}

private fun replacementDurationScore(origin: MusicTrack, candidate: MusicTrack): Double {
    val originDuration = origin.durationMs?.takeIf { it > 0 } ?: return MISSING_METADATA_SCORE
    val candidateDuration = candidate.durationMs?.takeIf { it > 0 } ?: return MISSING_METADATA_SCORE
    val tolerance = replacementDurationToleranceMs(originDuration, candidate.source)
    val difference = abs(originDuration - candidateDuration).toDouble()
    return (1.0 - difference / tolerance.toDouble()).coerceIn(0.0, 1.0)
}

private fun replacementDurationToleranceMs(originDuration: Long, candidateSource: String): Long {
    val ratio = if (candidateSource == BILIBILI_PROVIDER_ID) 0.10 else 0.08
    val minimum = if (candidateSource == BILIBILI_PROVIDER_ID) 15_000L else 12_000L
    return maxOf(minimum, (originDuration.toDouble() * ratio).toLong())
}

private fun isBilibiliPreferredTitle(value: String): Boolean =
    BILIBILI_PREFERRED_TITLE_PATTERNS.any { pattern -> pattern.containsMatchIn(value) }

private fun normalizeReplacementText(value: String): String =
    value.lowercase().replace(Regex("[^\\p{L}\\p{N}]"), "")
