package org.feeluown.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.feeluown.mobile.playback.api.PlaybackSession
import org.feeluown.mobile.playback.api.PlaybackSessionState
import org.feeluown.mobile.playback.api.PlaybackSessionStatus

/** Controller-free FullPlayer/queue/lyrics entry point consuming only narrow feature ports. */
@Composable
fun RuntimeFullPlayer() {
    ProvideNarrowPlaybackUi {
        RuntimeFullPlayerNarrow()
    }
}

@Composable
private fun RuntimeFullPlayerNarrow() {
    val playbackSession = LocalPlaybackSession.current
    val presentation = LocalPlaybackPresentationPort.current
    val queue = LocalPlaybackQueueUiPort.current
    val sessionState by playbackSession.state.collectAsStateWithLifecycle()
    val state = sessionState.toPlayerRenderState(presentation, queue)

    PlayerDynamicColorTheme(
        themeMode = presentation.themeMode,
        dynamicCoverColorEnabled = presentation.dynamicCoverColorEnabled,
        coverImageUrl = presentation.currentTrack?.coverUrl,
        isLoading = sessionState.status == PlaybackSessionStatus.Loading,
    ) {
        RuntimeFullPlayerContent(
            playbackSession = playbackSession,
            state = state,
        )
    }
}

private fun PlaybackSessionState.toPlayerRenderState(
    presentation: PlaybackPresentationPort,
    queue: PlaybackQueueUiPort,
): PlaybackState = PlaybackState(
    status = status.toPlayerStatus(),
    currentTrack = presentation.currentTrack,
    positionMs = positionMs,
    durationMs = durationMs,
    bufferedMs = bufferedMs,
    queue = queue.queue,
    queueIndex = queueIndex,
    lyrics = lyrics,
    audioQuality = presentation.audioQuality,
    audioFormatInfo = presentation.audioFormatInfo,
    audioDecoderInfo = presentation.audioDecoderInfo,
    playbackParts = presentation.playbackParts,
    currentPartIndex = presentation.currentPartIndex,
    errorMessage = errorMessage,
)

private fun PlaybackSessionStatus.toPlayerStatus(): PlayerStatus = when (this) {
    PlaybackSessionStatus.Idle -> PlayerStatus.Idle
    PlaybackSessionStatus.Loading -> PlayerStatus.Loading
    PlaybackSessionStatus.Playing -> PlayerStatus.Playing
    PlaybackSessionStatus.Paused -> PlayerStatus.Paused
    PlaybackSessionStatus.Error -> PlayerStatus.Error
    PlaybackSessionStatus.Ended -> PlayerStatus.Ended
}

@Composable
private fun RuntimeFullPlayerContent(
    playbackSession: PlaybackSession,
    state: PlaybackState,
) {
    val presentation = LocalPlaybackPresentationPort.current
    val currentTrack = state.currentTrack
    val pagerState = rememberPagerState(
        initialPage = PlayerVisualTab.Cover.ordinal,
        pageCount = { PlayerVisualTab.entries.size },
    )
    val scope = rememberCoroutineScope()

    if (LocalAppLayoutInfo.current.isLandscape) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(start = 20.dp, top = 12.dp, end = 20.dp, bottom = 68.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RuntimePlayerHeader(currentTrack)
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        val lyricsPaneWidth = (maxWidth * 0.46f)
                            .coerceAtLeast(240.dp)
                            .coerceAtMost(maxWidth * 0.52f)
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .width(lyricsPaneWidth)
                                    .fillMaxHeight(),
                            ) {
                                LyricsPanel(
                                    state = state,
                                    fontSize = presentation.lyricFontSize,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    RuntimePlayerCoverPage(
                                        track = currentTrack,
                                        isLoading = state.status == PlayerStatus.Loading,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 260.dp),
                                    )
                                    RuntimePlayerTitleBlock(
                                        track = currentTrack,
                                        partLabel = currentPlaybackPartLabel(state),
                                    )
                                    Text(
                                        text = currentTrack?.let(::artistAlbumLabel).orEmpty(),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                RuntimePlayerTransport(playbackSession, state, dense = false)
                            }
                        }
                    }
                }
                RuntimeQueueBottomSheet(state)
            }
        }
        return
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val compactPortrait = maxHeight < 720.dp || maxHeight < maxWidth * 1.55f
            val portraitSpacing = if (compactPortrait) 8.dp else 14.dp
            val portraitBottomPadding = if (compactPortrait) 28.dp else 82.dp
            val portraitHorizontalPadding = if (compactPortrait) 16.dp else 20.dp
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = portraitHorizontalPadding)
                    .padding(bottom = portraitBottomPadding),
                verticalArrangement = Arrangement.spacedBy(portraitSpacing),
            ) {
                RuntimePlayerHeader(currentTrack)
                PrimaryTabRow(selectedTabIndex = pagerState.currentPage.coerceIn(0, PlayerVisualTab.entries.lastIndex)) {
                    PlayerVisualTab.entries.forEach { tab ->
                        Tab(
                            selected = pagerState.currentPage == tab.ordinal,
                            onClick = { scope.launch { pagerState.animateScrollToPage(tab.ordinal) } },
                            text = { Text(tab.title) },
                        )
                    }
                }
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    pageSpacing = 16.dp,
                ) { page ->
                    when (PlayerVisualTab.entries[page]) {
                        PlayerVisualTab.Cover -> RuntimePlayerCoverPage(
                            track = currentTrack,
                            isLoading = state.status == PlayerStatus.Loading,
                        )
                        PlayerVisualTab.Lyrics -> LyricsPanel(
                            state = state,
                            fontSize = presentation.lyricFontSize,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                RuntimePlayerTitleBlock(
                    track = currentTrack,
                    partLabel = currentPlaybackPartLabel(state),
                )
                Text(
                    text = currentTrack?.let(::artistAlbumLabel).orEmpty(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                RuntimePlayerTransport(playbackSession, state, dense = compactPortrait)
            }
            RuntimeQueueBottomSheet(state)
        }
    }
}

@Composable
private fun RuntimePlayerHeader(currentTrack: MusicTrack?) {
    val navigation = LocalPlaybackNavigationPort.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = navigation::closeFullPlayer) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Player minimieren")
        }
        Text(
            text = "Wird abgespielt",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = navigation::toggleQueue) {
                Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Warteschlange")
            }
            currentTrack?.let { RuntimeNowPlayingTrackAction(it) }
        }
    }
}

@Composable
private fun RuntimePlayerTransport(
    playbackSession: PlaybackSession,
    state: PlaybackState,
    dense: Boolean,
) {
    val presentation = LocalPlaybackPresentationPort.current
    val queue = LocalPlaybackQueueUiPort.current
    ProgressBlock(state, presentation::seekTo)
    PlayerControls(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        onPrevious = playbackSession::previous,
        onToggle = playbackSession::toggle,
        onNext = playbackSession::next,
        dense = dense,
        shuffleEnabled = queue.isShuffleEnabled,
        shuffleAvailable = !queue.isFmQueueActive,
        onShuffle = queue::toggleShuffle,
        sleepTimerAction = { RuntimeSleepTimerAction() },
    )
}

@Composable
@OptIn(ExperimentalSharedTransitionApi::class)
private fun RuntimePlayerCoverPage(
    track: MusicTrack?,
    isLoading: Boolean,
    modifier: Modifier = Modifier.fillMaxSize(),
) {
    val navigation = LocalPlaybackNavigationPort.current
    val queue = LocalPlaybackQueueUiPort.current
    BoxWithConstraints(modifier = modifier) {
        val coverSize = minOf(maxWidth, maxHeight * 0.82f)
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerSharedCover(
                track = track ?: emptyDisplayTrack(),
                heroEnabled = navigation.isFullPlayerOpen,
                transitionDirection = queue.trackChangeDirection,
                isLoading = isLoading,
                cornerRadius = 22.dp,
                modifier = Modifier.size(coverSize),
            )
        }
    }
}

@Composable
private fun RuntimePlayerTitleBlock(
    track: MusicTrack?,
    partLabel: String?,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = track?.title ?: "Nicht abgespielt",
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        partLabel?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RuntimePlayerInfoTags(track)
    }
}

@Composable
private fun RuntimePlayerInfoTags(track: MusicTrack?) {
    val presentation = LocalPlaybackPresentationPort.current
    val replacement = LocalReplacementActionPort.current
    var replacementInfoTrack by remember(track?.id) { mutableStateOf<MusicTrack?>(null) }
    var showAudioFormatInfo by remember(track?.id) { mutableStateOf(false) }
    val canShowAudioInfo = presentation.audioFormatInfo?.hasDisplayableValue() == true ||
        presentation.audioDecoderInfo != null

    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (track != null) {
            if (track.isSmartReplacement) {
                InfoTag(
                    text = replacementSourceLabel(track),
                    onClick = {
                        replacementInfoTrack = track
                        replacement.loadReplacementCandidates(track)
                    },
                )
            } else {
                InfoTag(sourceLabel(track, null))
            }
        }
        presentation.audioQuality?.takeIf { it.isNotBlank() }?.let {
            InfoTag(
                text = it.uppercase(),
                onClick = if (canShowAudioInfo) ({ showAudioFormatInfo = true }) else null,
            )
        }
    }

    replacementInfoTrack?.let { infoTrack ->
        ReplacementInfoDialog(
            track = infoTrack,
            candidateState = replacement.replacementCandidateState
                .takeIf { it.trackId == (infoTrack.originalId ?: infoTrack.id) }
                ?: ReplacementCandidateState(),
            onDismiss = { replacementInfoTrack = null },
            onRetry = { replacement.loadReplacementCandidates(infoTrack) },
            onSelectCandidate = { candidate ->
                replacement.selectReplacementCandidate(infoTrack, candidate)
                replacementInfoTrack = null
            },
            onOpenDetail = if (infoTrack.replacementId?.isNotBlank() == true) {
                { replacement.openReplacementTrackDetail(infoTrack) }
            } else {
                null
            },
        )
    }
    if (showAudioFormatInfo) {
        AudioFormatInfoDialog(
            info = presentation.audioFormatInfo,
            decoderInfo = presentation.audioDecoderInfo,
            onDismiss = { showAudioFormatInfo = false },
        )
    }
}

@Composable
private fun RuntimeNowPlayingTrackAction(track: MusicTrack) {
    val queue = LocalPlaybackQueueUiPort.current
    val downloads = LocalDownloadActionPort.current
    val playlists = LocalPlaylistActionPort.current
    val providerActions = LocalProviderTrackActionPort.current
    val localActions = LocalLocalMusicActionPort.current
    val onShare = LocalShareHandler.current
    val sharePayload = track.toSharePayload()
    TrackAction(
        track = track,
        downloadState = downloads.downloadStates[track.id],
        onAddToUpNext = { queue.addToUpNext(track) },
        onDownload = { downloads.download(track) },
        onDeleteDownload = { downloads.deleteDownload(track) },
        onOpenArtist = { providerActions.openTrackArtist(track) },
        onOpenAlbum = { providerActions.openTrackAlbum(track) },
        onOpenDetail = if (track.sourceType == TrackSourceType.Provider) {
            { providerActions.openOriginalTrackDetail(track) }
        } else {
            null
        },
        onEditLocalMetadata = if (track.sourceType == TrackSourceType.LocalMediaStore) {
            { localActions.openLocalMetadataEditor(track) }
        } else {
            null
        },
        onAddToPlaylist = if (playlists.canAddTrackToPlaylist(track)) {
            { playlists.openPlaylistTargetPicker(track) }
        } else {
            null
        },
        onRemoveFromProviderPlaylist = if (playlists.canRemoveTrackFromSelectedPlaylist(track)) {
            { playlists.removeTrackFromSelectedPlaylist(track) }
        } else {
            null
        },
        onSetDisliked = if (providerActions.canSetSongDisliked(track)) {
            { providerActions.setSongDisliked(track) }
        } else {
            null
        },
        dislikedActionLabel = "Gefällt mir nicht",
        onShare = sharePayload?.let { payload -> { onShare(payload) } },
        roundButton = false,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuntimeQueueBottomSheet(state: PlaybackState) {
    val navigation = LocalPlaybackNavigationPort.current
    val isWideLayout = LocalAppLayoutInfo.current.useWideLayout
    if (!isWideLayout) {
        if (navigation.isQueueOpen) {
            ModalBottomSheet(
                onDismissRequest = navigation::toggleQueue,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
            ) {
                RuntimeQueueContent(state, sidePanel = false, embedded = true)
            }
        }
        return
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (isWideLayout) Alignment.CenterEnd else Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = navigation.isQueueOpen,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(tween(160)),
            exit = fadeOut(tween(140)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                    .clickable(role = Role.Button, onClick = navigation::toggleQueue),
            )
        }
        AnimatedVisibility(
            visible = navigation.isQueueOpen,
            modifier = Modifier.align(if (isWideLayout) Alignment.CenterEnd else Alignment.BottomCenter),
            enter = if (isWideLayout) {
                slideInHorizontally(animationSpec = tween(FuoMotion.overlayEnterMillis)) { it } +
                    fadeIn(tween(FuoMotion.overlayFadeMillis))
            } else {
                error("Wide layout required for side-panel queue")
            },
            exit = if (isWideLayout) {
                slideOutHorizontally(animationSpec = tween(FuoMotion.overlayExitMillis)) { it } +
                    fadeOut(tween(FuoMotion.overlayFadeMillis))
            } else {
                error("Wide layout required for side-panel queue")
            },
        ) {
            RuntimeQueueContent(state, sidePanel = true)
        }
    }
}

@Composable
private fun RuntimeQueueContent(
    state: PlaybackState,
    sidePanel: Boolean = false,
    embedded: Boolean = false,
) {
    val queuePort = LocalPlaybackQueueUiPort.current
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    val queueSize = state.queue.size

    Surface(
        modifier = if (sidePanel) {
            Modifier
                .width(380.dp)
                .fillMaxHeight()
                .statusBarsPadding()
                .navigationBarsPadding()
                .clickable { }
        } else {
            Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .then(if (embedded) Modifier else Modifier.navigationBarsPadding())
        },
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 8.dp,
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QueueRepeatModeHeader(
                isFmQueue = queuePort.isFmQueueActive,
                repeatMode = queuePort.repeatMode,
                onRepeat = queuePort::toggleRepeat,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Warteschlange",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "$queueSize Titel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (queueSize > 1) {
                        IconButton(onClick = { showClearConfirmDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Warteschlange leeren")
                        }
                    }
                }
            }
            RuntimeQueueList(
                state = state,
                modifier = if (sidePanel) {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                },
            )
        }
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Wiedergabewarteschlange leeren") },
            text = { Text("Wiedergabewarteschlange wirklich leeren? Der aktuell abgespielte Titel bleibt erhalten.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmDialog = false
                    queuePort.clearQueue()
                }) {
                    Text("Leeren")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Abbrechen")
                }
            },
        )
    }
}

@Composable
private fun RuntimeQueueList(
    state: PlaybackState,
    modifier: Modifier,
) {
    val queuePort = LocalPlaybackQueueUiPort.current
    val presentation = LocalPlaybackPresentationPort.current
    val queue = state.queue
    val playbackParts = presentation.playbackParts
    val currentPartIndex = presentation.currentPartIndex
    val currentCount = if (state.queueIndex == 0 && queue.isNotEmpty()) 1 else 0
    val upNextCount = queuePort.displayUpNextCount
    LazyColumn(modifier = modifier) {
        itemsIndexed(queue) { index, track ->
            if (index == 0 && currentCount == 1) {
                Text(
                    text = "Aktuell",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp, bottom = 4.dp),
                )
            }
            if (index == currentCount && upNextCount > 0) {
                Text(
                    text = "Als Nächstes",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
            }
            if (index == currentCount + upNextCount && index < queue.size && index > 0) {
                Text(
                    text = "Später in der Warteschlange",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                )
            }
            val isCurrent = index == state.queueIndex
            val isUnavailable = track.isUnavailable
            val titleColor = when {
                isUnavailable -> MaterialTheme.colorScheme.onSurfaceVariant
                isCurrent -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .fuoInteractive()
                    .clickable(
                        enabled = !isUnavailable,
                        role = Role.Button,
                    ) { queuePort.playQueueIndex(index) }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CoverBox(track, modifier = Modifier.size(48.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${index + 1}. ${track.title.ifBlank { "Unbekannter Titel" }}",
                        style = MaterialTheme.typography.titleMedium,
                        color = titleColor,
                        fontWeight = if (isCurrent && !isUnavailable) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = listOf(track.artists, track.album).filter { it.isNotBlank() }.joinToString(" · ")
                            .ifBlank { "Unbekannter Interpret" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = sourceLabel(track, null),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { queuePort.removeFromQueue(track) }) {
                    Icon(Icons.Filled.RemoveCircleOutline, contentDescription = "Aus Warteschlange entfernen")
                }
            }
            if (isCurrent && playbackParts.isNotEmpty()) {
                PlaybackPartList(
                    parts = playbackParts,
                    currentPartIndex = currentPartIndex,
                    onPartClick = queuePort::playPlaybackPart,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun RuntimeSleepTimerAction() {
    val sleepTimer = LocalPlaybackSleepTimerPort.current
    var showSheet by remember { mutableStateOf(false) }
    val timerState = sleepTimer.sleepTimerState
    val isActive = timerState.mode != SleepTimerMode.Off
    val contentDescription = when (timerState.mode) {
        SleepTimerMode.Off -> "Sleep-Timer"
        SleepTimerMode.Duration -> "Sleep-Timer, noch ${runtimeFormatSleepTimerRemaining(timerState.remainingMs ?: 0L)}"
        SleepTimerMode.EndOfTrack -> "Nach aktuellem Titel pausieren"
    }
    Box {
        RoundControlButton(
            imageVector = Icons.Filled.Timer,
            contentDescription = contentDescription,
            onClick = { showSheet = true },
            selected = isActive,
        )
        if (showSheet) {
            RuntimeSleepTimerBottomSheet(onDismiss = { showSheet = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuntimeSleepTimerBottomSheet(onDismiss: () -> Unit) {
    val sleepTimer = LocalPlaybackSleepTimerPort.current
    val timerState = sleepTimer.sleepTimerState
    var showCustomDurationDialog by remember { mutableStateOf(false) }
    var customMinutesText by remember { mutableStateOf("") }
    val customMinutes = customMinutesText.toIntOrNull()
    val customMinutesValid = customMinutes?.let {
        it in SLEEP_TIMER_MIN_MINUTES..SLEEP_TIMER_MAX_MINUTES
    } == true
    val presetMinutes = SLEEP_TIMER_PRESET_MINUTES.filter { it <= 90 }
    val firstPresetRow = presetMinutes.take(3)
    val secondPresetRow = presetMinutes.drop(3)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Sleep-Timer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Zeitpunkt für automatisches Pausieren wählen",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (timerState.mode != SleepTimerMode.Off) {
                RuntimeSleepTimerActiveStatus(
                    timerState = timerState,
                    onExtend = if (timerState.mode == SleepTimerMode.Duration) {
                        {
                            sleepTimer.setSleepTimerDurationMinutes(
                                runtimeSleepTimerExtendedMinutes(timerState.remainingMs ?: 0L, 5),
                            )
                        }
                    } else {
                        null
                    },
                    onClear = {
                        sleepTimer.clearSleepTimer()
                        onDismiss()
                    },
                )
            }
            RuntimeSleepTimerSectionLabel("Nach Dauer")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                firstPresetRow.forEach { minutes ->
                    RuntimeSleepTimerPresetOption(
                        minutes = minutes,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            sleepTimer.setSleepTimerDurationMinutes(minutes)
                            onDismiss()
                        },
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                secondPresetRow.forEach { minutes ->
                    RuntimeSleepTimerPresetOption(
                        minutes = minutes,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            sleepTimer.setSleepTimerDurationMinutes(minutes)
                            onDismiss()
                        },
                    )
                }
                RuntimeSleepTimerCustomOption(
                    modifier = Modifier.weight(1f),
                    onClick = { showCustomDurationDialog = true },
                )
            }

            RuntimeSleepTimerSectionLabel("Nach Wiedergabeende")
            RuntimeSleepTimerEndOfTrackOption(
                selected = timerState.mode == SleepTimerMode.EndOfTrack,
                onClick = {
                    sleepTimer.setSleepTimerToEndOfTrack()
                    onDismiss()
                },
            )
        }
    }

    if (showCustomDurationDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDurationDialog = false },
            title = { Text("Benutzerdefinierter Sleep-Timer") },
            text = {
                OutlinedTextField(
                    value = customMinutesText,
                    onValueChange = { value ->
                        customMinutesText = value.filter(Char::isDigit)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = customMinutesText.isNotBlank() && !customMinutesValid,
                    label = { Text("Dauer (Minuten)") },
                    placeholder = { Text("z. B. 45") },
                    supportingText = if (customMinutesText.isNotBlank() && !customMinutesValid) {
                        {
                            Text("Bitte $SLEEP_TIMER_MIN_MINUTES–$SLEEP_TIMER_MAX_MINUTES Minuten eingeben")
                        }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = customMinutesValid,
                    onClick = {
                        customMinutes?.let(sleepTimer::setSleepTimerDurationMinutes)
                        showCustomDurationDialog = false
                        onDismiss()
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDurationDialog = false }) {
                    Text("Abbrechen")
                }
            },
        )
    }
}

@Composable
private fun RuntimeSleepTimerActiveStatus(
    timerState: SleepTimerState,
    onExtend: (() -> Unit)?,
    onClear: () -> Unit,
) {
    val isDuration = timerState.mode == SleepTimerMode.Duration
    val canExtend = isDuration &&
        (timerState.remainingMs ?: 0L) < SLEEP_TIMER_MAX_MINUTES * 60_000L
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isDuration) Icons.Filled.Timer else Icons.Filled.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = if (isDuration) {
                        "Noch ${runtimeFormatSleepTimerRemaining(timerState.remainingMs ?: 0L)}"
                    } else {
                        "Nach aktuellem Titel pausieren"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (isDuration) {
                        "Nach Ablauf automatisch pausieren"
                    } else {
                        "Nach vollständiger Wiedergabe des aktuellen Titels pausieren"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onExtend != null) {
                TextButton(
                    enabled = canExtend,
                    onClick = onExtend,
                ) {
                    Text("+5 Minuten")
                }
            }
            TextButton(onClick = onClear) {
                Text("Timer ausschalten")
            }
        }
        HorizontalDivider()
    }
}

@Composable
private fun RuntimeSleepTimerSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun RuntimeSleepTimerPresetOption(
    minutes: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(
                text = "$minutes Minuten",
                maxLines = 1,
            )
        },
    )
}

@Composable
private fun RuntimeSleepTimerCustomOption(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        label = { Text("Benutzerdefiniert", maxLines = 1) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Edit,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

@Composable
private fun RuntimeSleepTimerEndOfTrackOption(
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick),
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
        ),
        leadingContent = {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        },
        headlineContent = {
            Text(
                text = "Nach aktuellem Titel pausieren",
                fontWeight = FontWeight.Medium,
            )
        },
        supportingContent = {
            Text(
                text = "Alle Teile des aktuellen Titels einschließen",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = if (selected) {
            {
                Text(
                    text = "Ausgewählt",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        } else {
            null
        },
    )
}

private fun runtimeSleepTimerExtendedMinutes(remainingMs: Long, extraMinutes: Int): Int {
    val roundedRemainingMinutes = ((remainingMs.coerceAtLeast(0L) + 30_000L) / 60_000L)
        .coerceAtLeast(1L)
    return (roundedRemainingMinutes + extraMinutes)
        .coerceAtMost(SLEEP_TIMER_MAX_MINUTES.toLong())
        .toInt()
}

private fun runtimeFormatSleepTimerRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs.coerceAtLeast(0L) + 999L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return when {
        minutes >= 60L -> "${minutes / 60L} Std. ${minutes % 60L} Min."
        minutes > 0L -> "${minutes} Min. ${seconds} Sek."
        else -> "${seconds} Sek."
    }
}
