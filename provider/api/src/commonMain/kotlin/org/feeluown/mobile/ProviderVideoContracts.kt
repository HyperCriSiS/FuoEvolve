package org.feeluown.mobile

data class ProviderVideoStat(
    val label: String,
    val value: Long,
)

data class ProviderVideoMetadata(
    val description: String = "",
    val publishedAt: String? = null,
    val stats: List<ProviderVideoStat> = emptyList(),
    val width: Int? = null,
    val height: Int? = null,
)
