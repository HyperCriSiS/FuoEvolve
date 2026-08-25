package org.feeluown.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    port: DownloadActionPort,
    onBack: () -> Unit,
) {
    val managerState by port.managerState.collectAsStateWithLifecycle()
    var completedLimit by remember { mutableStateOf(5) }
    var pendingDelete by remember { mutableStateOf<DownloadTask?>(null) }
    var deleteFile by remember { mutableStateOf(true) }
    val active = managerState.tasks.filter { it.status != DownloadTaskStatus.Completed }
    val completed = managerState.tasks.filter { it.status == DownloadTaskStatus.Completed }
        .sortedByDescending { it.createdAt }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Downloadverwaltung") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück")
                    }
                },
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (active.isNotEmpty()) {
                item { DownloadSectionTitle("Downloadaufträge") }
                items(active, key = { it.id }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onPause = { port.pause(task.id) },
                        onResume = { port.resume(task.id) },
                        onRetry = { port.retry(task.id) },
                        onDelete = { pendingDelete = task; deleteFile = true },
                    )
                }
            }
            item { DownloadSectionTitle("Abgeschlossen") }
            if (completed.isEmpty()) {
                item {
                    FuoEmptyState(
                        modifier = Modifier.fillMaxWidth(),
                        title = "Keine abgeschlossenen Downloads",
                    )
                }
            } else {
                items(completed.take(completedLimit), key = { it.id }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onPause = {}, onResume = {}, onRetry = {},
                        onDelete = { pendingDelete = task; deleteFile = true },
                    )
                }
                if (completed.size > completedLimit) {
                    item {
                        TextButton(onClick = { completedLimit += 20 }) {
                            Text(if (completedLimit == 5) "Mehr anzeigen" else "Mehr laden")
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Download löschen") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(task.track.title.ifBlank { "Diesen Downloadauftrag" })
                    if (task.status == DownloadTaskStatus.Completed) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = deleteFile, onCheckedChange = { deleteFile = it })
                            Text("Lokale Datei ebenfalls löschen")
                        }
                    } else {
                        Text("Dabei werden auch temporäre Dateien gelöscht; der Download kann danach nicht fortgesetzt werden.")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    port.deleteTask(task.id, deleteFile || task.status != DownloadTaskStatus.Completed)
                    pendingDelete = null
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun DownloadSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(FuoSpacing.lg),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(task.track.title.ifBlank { "Unbekannter Titel" }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOf(task.track.artists, downloadTaskStatusText(task)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (task.status == DownloadTaskStatus.Downloading) {
                    val progress = task.totalBytes?.takeIf { it > 0 }?.let { task.downloadedBytes.toFloat() / it }
                    if (progress != null) LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                }
            }
            when (task.status) {
                DownloadTaskStatus.Downloading, DownloadTaskStatus.Queued -> IconButton(onClick = onPause) {
                    Icon(Icons.Filled.Pause, contentDescription = "Download pausieren")
                }
                DownloadTaskStatus.Paused -> IconButton(onClick = onResume) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Download fortsetzen")
                }
                DownloadTaskStatus.Failed -> IconButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Download erneut versuchen")
                }
                DownloadTaskStatus.Completed -> Unit
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, contentDescription = "Download löschen") }
        }
    }
}

private fun downloadTaskStatusText(task: DownloadTask): String = when (task.status) {
    DownloadTaskStatus.Queued -> "Wartet auf Download"
    DownloadTaskStatus.Downloading -> task.totalBytes?.let { "${formatBytes(task.downloadedBytes)} / ${formatBytes(it)}" } ?: "Wird heruntergeladen"
    DownloadTaskStatus.Paused -> "Pausiert, kann fortgesetzt werden"
    DownloadTaskStatus.Failed -> task.failureMessage ?: "Download fehlgeschlagen"
    DownloadTaskStatus.Completed -> "Abgeschlossen"
}
