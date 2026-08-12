package org.feeluown.mobile

private const val AUDIO_QUALITY_LQ_MAX_BITRATE = 128_000L
private const val AUDIO_QUALITY_HQ_MIN_BITRATE = 256_000L

private val LOSSLESS_AUDIO_MARKERS = listOf(
    "flac",
    "alac",
    "ape",
    "audio/wav",
    "audio/x-wav",
    "audio/raw",
    "pcm",
    "lossless",
)

/**
 * Converts provider-specific quality values into the small set of quality tiers
 * shown by the player. Codec/container names stay in [AudioFormatInfo] and must
 * not leak into the quality chip.
 *
 * The tier boundary intentionally follows the qualities already used by the
 * providers in this project: 128 kbps and below is LQ, 129-255 kbps is SQ,
 * 256 kbps and above is HQ, while lossless formats are SHQ.
 *
 * Hi-Res is deliberately not inferred here because bitrate/codec alone cannot
 * establish the required sample rate and bit depth.
 */
fun normalizedAudioQualityLabel(
    rawQuality: String?,
    formatInfo: AudioFormatInfo?,
): String? {
    val raw = rawQuality?.trim()?.takeIf(String::isNotEmpty)
    val normalizedRaw = raw?.lowercase()

    if (isLosslessAudio(normalizedRaw, formatInfo)) return "SHQ"

    when (normalizedRaw) {
        "shq" -> return "SHQ"
        "hq", "hq<>" -> return "HQ"
        "sq", "sq<>" -> return "SQ"
        "lq", "lq<>" -> return "LQ"
    }

    val bitrate = formatInfo?.averageBitrate?.takeIf { it > 0 }
        ?: normalizedRaw?.toLongOrNull()?.takeIf { it > 0 }
        ?: formatInfo?.peakBitrate?.takeIf { it > 0 }

    return audioQualityLabelForBitrate(bitrate)
}

fun audioQualityLabelForBitrate(bitrate: Long?): String? {
    if (bitrate == null || bitrate <= 0) return null
    return when {
        bitrate <= AUDIO_QUALITY_LQ_MAX_BITRATE -> "LQ"
        bitrate < AUDIO_QUALITY_HQ_MIN_BITRATE -> "SQ"
        else -> "HQ"
    }
}

private fun isLosslessAudio(rawQuality: String?, formatInfo: AudioFormatInfo?): Boolean {
    val description = buildList {
        rawQuality?.let(::add)
        formatInfo?.format?.lowercase()?.let(::add)
        formatInfo?.codec?.lowercase()?.let(::add)
    }.joinToString(" ")
    return LOSSLESS_AUDIO_MARKERS.any(description::contains)
}
