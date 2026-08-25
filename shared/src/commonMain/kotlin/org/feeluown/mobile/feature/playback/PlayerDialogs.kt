package org.feeluown.mobile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

@Composable
fun AudioFormatInfoDialog(
    info: AudioFormatInfo?,
    decoderInfo: AudioDecoderInfo? = null,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audioinformationen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                info?.format?.takeIf { it.isNotBlank() }?.let { ReplacementInfoLine("Aktuelles Format", it) }
                info?.codec?.takeIf { it.isNotBlank() }?.let { ReplacementInfoLine("Codec", it) }
                formatAudioBitrate(info?.averageBitrate)?.let { ReplacementInfoLine("Durchschnittliche Bitrate", it) }
                formatAudioBitrate(info?.peakBitrate)?.let { ReplacementInfoLine("Spitzen-Bitrate", it) }
                decoderInfo?.let { decoder ->
                    ReplacementInfoLine(
                        "Decodierung",
                        if (decoder.type == AudioDecoderType.Software) "Software-Decodierung" else "Hardware-Decodierung",
                    )
                    decoder.name.takeIf { it.isNotBlank() }?.let {
                        ReplacementInfoLine("Decoder", it)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen")
            }
        },
    )
}

fun AudioFormatInfo.hasDisplayableValue(): Boolean {
    return !format.isNullOrBlank() ||
        !codec.isNullOrBlank() ||
        formatAudioBitrate(averageBitrate) != null ||
        formatAudioBitrate(peakBitrate) != null
}

fun formatAudioBitrate(value: Long?): String? {
    if (value == null || value <= 0) return null
    return "${(value / 1_000.0).roundToInt()} kbps"
}

@Composable
fun InfoTag(text: String, onClick: (() -> Unit)? = null) {
    FuoMetadataChip(label = text, onClick = onClick)
}

@Composable
fun ReplacementInfoDialog(
    track: MusicTrack,
    onDismiss: () -> Unit,
    onOpenDetail: (() -> Unit)? = null,
    candidateState: ReplacementCandidateState = ReplacementCandidateState(),
    onRetry: (() -> Unit)? = null,
    onSelectCandidate: ((ReplacementCandidate) -> Unit)? = null,
) {
    val detailAction = onOpenDetail?.takeIf { track.replacementId?.isNotBlank() == true }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Audio ersetzen") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReplacementInfoLine("Titel", track.replacementTitle ?: track.title)
                ReplacementInfoLine("Interpreten", track.replacementArtists ?: track.artists)
                replacementProviderLabel(track).takeIf { it.isNotBlank() }?.let {
                    ReplacementInfoLine("Quelle", it)
                }
                track.replacementStrategy?.let {
                    ReplacementInfoLine("Strategie", it)
                }
                track.replacementScore?.let {
                    ReplacementInfoLine("Übereinstimmung", formatSmartReplacementScore(it))
                }
                Text(
                    text = "Alternative Quellen",
                    style = MaterialTheme.typography.titleSmall,
                )
                when {
                    candidateState.isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                    candidateState.errorMessage != null -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Abfrage der Alternativen fehlgeschlagen: ${candidateState.errorMessage}",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            onRetry?.let { retry ->
                                TextButton(onClick = retry) {
                                    Text("Erneut versuchen")
                                }
                            }
                        }
                    }
                    candidateState.candidates.isEmpty() -> {
                        Text(
                            text = "Keine passende alternative Quelle gefunden",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            itemsIndexed(candidateState.candidates) { _, candidate ->
                                val isSelected = candidate.track.id == track.replacementId
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = !isSelected) {
                                                onSelectCandidate?.invoke(candidate)
                                            }
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        CoverBox(
                                            track = candidate.track,
                                            modifier = Modifier.size(48.dp),
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = candidate.track.title.ifBlank { "Unbekannter Titel" },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                            Text(
                                                text = candidate.track.artists.ifBlank { "Unbekannter Interpret" },
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodySmall,
                                            )
                                            Text(
                                                text = "${sourceLabel(candidate.track, null)} · Übereinstimmung ${formatSmartReplacementScore(candidate.score)}",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.labelSmall,
                                            )
                                        }
                                        if (isSelected) {
                                            Text(
                                                text = "Ausgewählt",
                                                color = MaterialTheme.colorScheme.primary,
                                                style = MaterialTheme.typography.labelMedium,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onDismiss()
                    if (detailAction != null) detailAction()
                },
            ) {
                Text(if (detailAction != null) "Titeldetails" else "Schließen")
            }
        },
        dismissButton = if (detailAction != null) {
            {
                TextButton(onClick = onDismiss) {
                    Text("Schließen")
                }
            }
        } else {
            null
        },
    )
}

@Composable
fun ReplacementInfoLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
