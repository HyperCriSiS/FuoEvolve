package org.feeluown.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class RecognitionFeatureActions(
    val dispatch: (RecognitionAction) -> Unit,
    val onBack: () -> Unit,
    val onSearchSong: (RecognizedSong) -> Unit,
    val canOpenNeteaseDetail: (RecognizedSong) -> Boolean,
    val onOpenNeteaseDetail: (RecognizedSong) -> Unit,
)

/** Recognition UI depends only on feature state/actions and narrow cross-feature callbacks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AudioRecognitionFeatureScreen(
    uiState: RecognitionUiState,
    actions: RecognitionFeatureActions,
    hasMicrophonePermission: Boolean,
    onRequestMicrophonePermission: () -> Unit,
) {
    DisposableEffect(Unit) {
        onDispose { actions.dispatch(RecognitionAction.CancelIfInProgress) }
    }
    LaunchedEffect(hasMicrophonePermission, uiState) {
        if (hasMicrophonePermission && uiState == RecognitionUiState.Idle) {
            actions.dispatch(RecognitionAction.Start)
        }
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Musik erkennen") },
                navigationIcon = {
                    IconButton(onClick = actions.onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
        bottomBar = {
            if (uiState is RecognitionUiState.Capturing || uiState is RecognitionUiState.Matching) {
                Surface(tonalElevation = 3.dp) {
                    OutlinedButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        onClick = { actions.dispatch(RecognitionAction.Cancel) },
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("Erkennung stoppen")
                    }
                }
            }
        },
    ) { paddingValues ->
        if (hasMicrophonePermission) {
            RecognitionContent(
                uiState = uiState,
                actions = actions,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
            )
        } else {
            MicrophonePermissionContent(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                onRequestPermission = onRequestMicrophonePermission,
            )
        }
    }
}

@Composable
private fun MicrophonePermissionContent(
    modifier: Modifier,
    onRequestPermission: () -> Unit,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RecognitionIcon()
        Spacer(Modifier.size(24.dp))
        Text("Mikrofonberechtigung erforderlich", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.size(8.dp))
        Text(
            text = "Die Aufnahme wird nur im Arbeitsspeicher zur Erstellung eines Audio-Fingerabdrucks verwendet. Das Originalaudio wird weder gespeichert noch hochgeladen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(24.dp))
        Button(onClick = onRequestPermission) {
            Icon(Icons.Filled.Mic, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Berechtigen und Erkennung starten")
        }
    }
}

@Composable
private fun RecognitionContent(
    uiState: RecognitionUiState,
    actions: RecognitionFeatureActions,
    modifier: Modifier,
) {
    when (val state = uiState) {
        RecognitionUiState.Idle -> ListeningContent(
            modifier = modifier,
            title = "Mikrofon wird vorbereitet",
            subtitle = "Die Aufnahme wird nicht auf dem Gerät gespeichert",
            progress = null,
        )
        is RecognitionUiState.Capturing -> ListeningContent(
            modifier = modifier,
            title = "Hört zu",
            subtitle = "Gehe näher an die Audioquelle und halte die Umgebung möglichst ruhig.",
            progress = (state.capturedMs.toFloat() / state.windowDurationMs).coerceIn(0f, 1f),
        )
        RecognitionUiState.Matching -> ListeningContent(
            modifier = modifier,
            title = "Titel wird gesucht",
            subtitle = "Fast geschafft – lass die Musik weiterlaufen.",
            progress = null,
        )
        is RecognitionUiState.Success -> RecognitionResults(
            actions = actions,
            songs = state.songs,
            modifier = modifier,
        )
        RecognitionUiState.NoResult -> RecognitionMessage(
            modifier = modifier,
            title = "Noch kein Titel erkannt",
            message = "Halte das Smartphone näher an die Audioquelle oder versuche es in einer ruhigeren Umgebung erneut.",
            actionLabel = "Erneut erkennen",
            onAction = { actions.dispatch(RecognitionAction.Retry) },
        )
        is RecognitionUiState.Error -> RecognitionMessage(
            modifier = modifier,
            title = "Erkennung fehlgeschlagen",
            message = state.message,
            actionLabel = "Erneut versuchen",
            onAction = { actions.dispatch(RecognitionAction.Retry) },
        )
        RecognitionUiState.Cancelled -> RecognitionMessage(
            modifier = modifier,
            title = "Erkennung gestoppt",
            message = "Du kannst die Erkennung jederzeit erneut starten.",
            actionLabel = "Erneut erkennen",
            onAction = { actions.dispatch(RecognitionAction.Retry) },
        )
    }
}

@Composable
private fun ListeningContent(
    modifier: Modifier,
    title: String,
    subtitle: String,
    progress: Float?,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RecognitionIcon()
        Spacer(Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.size(8.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(24.dp))
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CircularProgressIndicator()
        }
        Spacer(Modifier.size(16.dp))
        Text(
            "An den Erkennungsdienst wird nur der Audio-Fingerabdruck gesendet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecognitionResults(
    actions: RecognitionFeatureActions,
    songs: List<RecognizedSong>,
    modifier: Modifier,
) {
    LazyColumn(
        modifier = modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "${songs.size} Titel erkannt",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        items(songs, key = { it.neteaseSongId ?: "${it.title}:${it.artists}" }) { song ->
            RecognizedSongCard(actions, song)
        }
        item { Spacer(Modifier.size(16.dp)) }
    }
}

@Composable
private fun RecognizedSongCard(
    actions: RecognitionFeatureActions,
    song: RecognizedSong,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier.padding(FuoSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverBox(
                    track = MusicTrack(
                        id = song.neteaseSongId.orEmpty(),
                        title = song.title,
                        artists = song.artists.joinToString(" / "),
                        album = song.album,
                        source = "netease",
                        sourceType = TrackSourceType.Provider,
                        coverUrl = song.coverUrl,
                    ),
                    modifier = Modifier.size(64.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title.ifBlank { "Unbekannter Titel" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        song.artists.joinToString(" / ").ifBlank { "Unbekannter Interpret" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (song.album.isNotBlank()) {
                        Text(
                            song.album,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { actions.onSearchSong(song) }) {
                    Icon(Icons.Filled.Search, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Suchen")
                }
                if (actions.canOpenNeteaseDetail(song)) {
                    OutlinedButton(onClick = { actions.onOpenNeteaseDetail(song) }) {
                        Text("Details bei NetEase ansehen")
                    }
                }
            }
        }
    }
}

@Composable
private fun RecognitionMessage(
    modifier: Modifier,
    title: String,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        RecognitionIcon()
        Spacer(Modifier.size(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.size(8.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(24.dp))
        Button(onClick = onAction) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text(actionLabel)
        }
    }
}

@Composable
private fun RecognitionIcon() {
    Surface(
        modifier = Modifier.size(112.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = null,
            modifier = Modifier.padding(28.dp),
        )
    }
}
