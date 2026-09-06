package io.github.samgum.aegisub.feature.preview

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlin.math.roundToInt
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samgum.aegisub.data.hotkeys.rememberHotkeyController
import io.github.samgum.aegisub.domain.edit.HotkeyAction
import io.github.samgum.aegisub.domain.edit.VisualTags
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.time.SubTime
import io.github.samgum.aegisub.feature.preview.R
import io.github.samgum.aegisub.feature.preview.components.NudgeTarget
import io.github.samgum.aegisub.feature.preview.components.PlayerSurface
import io.github.samgum.aegisub.feature.preview.components.SubtitleOverlay
import io.github.samgum.aegisub.feature.preview.components.AudioTimeline
import io.github.samgum.aegisub.feature.preview.components.KaraokeTimeline
import io.github.samgum.aegisub.feature.preview.components.SpectrogramView
import io.github.samgum.aegisub.feature.preview.components.TimingEditPanel
import io.github.samgum.aegisub.feature.preview.components.VisualToolMode
import io.github.samgum.aegisub.feature.preview.components.VisualTypesettingOverlay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    onBack: () -> Unit,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val hotkeys = rememberHotkeyController()
    val context = LocalContext.current

    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            viewModel.attachMedia(uri.toString())
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.preview_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.preview_back))
                    }
                },
                actions = {
                    IconButton(onClick = { pickVideo.launch(arrayOf("video/*")) }) {
                        Icon(Icons.Filled.Movie, contentDescription = stringResource(R.string.preview_change_video))
                    }
                    IconButton(onClick = viewModel::undo, enabled = canUndo) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.preview_undo))
                    }
                    IconButton(onClick = viewModel::redo, enabled = canRedo) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.preview_redo))
                    }
                },
            )
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    val action = hotkeys.match(event) ?: return@onPreviewKeyEvent false
                    val selectedId = (state as? PreviewUiState.Loaded)?.selectedEventId
                    when (action) {
                        HotkeyAction.PLAY_PAUSE -> { viewModel.playPause(); true }
                        HotkeyAction.SEEK_BACK -> { viewModel.seekRelative(-5_000); true }
                        HotkeyAction.SEEK_FORWARD -> { viewModel.seekRelative(5_000); true }
                        HotkeyAction.FRAME_BACK -> { viewModel.frameStepBack(); true }
                        HotkeyAction.FRAME_FORWARD -> { viewModel.frameStepForward(); true }
                        HotkeyAction.SELECT_PREV -> { viewModel.selectPrevEvent(); true }
                        HotkeyAction.SELECT_NEXT -> { viewModel.selectNextEvent(); true }
                        HotkeyAction.SET_START_TO_POS -> {
                            selectedId?.let { viewModel.setStartToPosition(it) }; true
                        }
                        HotkeyAction.SET_END_TO_POS -> {
                            selectedId?.let { viewModel.setEndToPosition(it) }; true
                        }
                        HotkeyAction.UNDO -> { viewModel.undo(); true }
                        else -> false
                    }
                },
        ) {
            when (val s = state) {
                PreviewUiState.Loading ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }

                is PreviewUiState.Error ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.preview_load_failed, s.message))
                    }

                is PreviewUiState.Loaded -> {
                    val isCompact = LocalConfiguration.current.screenWidthDp < 600
                    if (isCompact) {
                        CompactPreview(
                            state = s,
                            viewModel = viewModel,
                            onPickVideo = { pickVideo.launch(arrayOf("video/*")) },
                        )
                    } else {
                        ExpandedPreview(
                            state = s,
                            viewModel = viewModel,
                            onPickVideo = { pickVideo.launch(arrayOf("video/*")) },
                        )
                    }
                }
            }
        }
    }
}

enum class PreviewPanel { SUBTITLES, AUDIO, TIMING, TYPES }

@Composable
private fun VideoBlock(
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    onPickVideo: () -> Unit,
    vtActive: Boolean,
    vtToolMode: VisualToolMode,
    onVtToolModeChange: (VisualToolMode) -> Unit,
    videoMaxHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val selectedEvent = state.script.events.firstOrNull { it.id == state.selectedEventId }
    val playResX = state.script.getScriptInfo("PlayResX")?.toIntOrNull() ?: 384
    val playResY = state.script.getScriptInfo("PlayResY")?.toIntOrNull() ?: 288
    Column(modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(videoMaxHeight)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            PlayerSurface(player = viewModel.videoPlayer, modifier = Modifier.fillMaxSize())
            ActiveSubtitleLayer(viewModel = viewModel)
            if (vtActive && state.hasMedia && selectedEvent != null) {
                VisualTypesettingOverlay(
                    playResX = playResX,
                    playResY = playResY,
                    mode = vtToolMode,
                    currentPos = VisualTags.getPos(selectedEvent.text),
                    currentMove = VisualTags.getMove(selectedEvent.text),
                    onPosChange = { x, y -> viewModel.setEventPos(selectedEvent.id, x, y) },
                    onMoveChange = { x1, y1, x2, y2 ->
                        viewModel.setEventMove(selectedEvent.id, x1, y1, x2, y2)
                    },
                )
            }
            if (!state.hasMedia) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.preview_no_video), color = Color.White)
                    Button(onClick = onPickVideo) { Text(stringResource(R.string.preview_select_video)) }
                }
            }
            if (vtActive) {
                Text(
                    if (vtToolMode == VisualToolMode.POSITION) stringResource(R.string.preview_vt_pos_hint)
                    else stringResource(R.string.preview_vt_move_hint),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                )
            }
        }
        PlaybackControls(
            playback = state.playback,
            onPlayPause = viewModel::playPause,
            onSeek = viewModel::seekTo,
            onSpeedChange = viewModel::setSpeed,
            onFrameBack = viewModel::frameStepBack,
            onFrameForward = viewModel::frameStepForward,
        )
    }
}

@Composable
private fun PreviewTabs(
    panel: PreviewPanel,
    onPanelChange: (PreviewPanel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = listOf(
        PreviewPanel.SUBTITLES to stringResource(R.string.preview_tab_subtitles),
        PreviewPanel.AUDIO to stringResource(R.string.preview_tab_audio),
        PreviewPanel.TIMING to stringResource(R.string.preview_tab_timing),
        PreviewPanel.TYPES to stringResource(R.string.preview_tab_types),
    )
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tabs.forEach { (p, label) ->
            FilterChip(selected = panel == p, onClick = { onPanelChange(p) }, label = { Text(label) })
        }
    }
}

@Composable
private fun PreviewPanelContent(
    panel: PreviewPanel,
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    showSpectrogram: Boolean,
    onShowSpectrogramChange: (Boolean) -> Unit,
    vtToolMode: VisualToolMode,
    onVtToolModeChange: (VisualToolMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedEvent = state.script.events.firstOrNull { it.id == state.selectedEventId }
    val waveform by viewModel.waveform.collectAsStateWithLifecycle()
    val spectrogram by viewModel.spectrogram.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    when (panel) {
        PreviewPanel.SUBTITLES -> EventListColumn(
            events = state.script.events,
            currentEventId = state.currentEventId,
            selectedEventId = state.selectedEventId,
            onSelect = viewModel::selectEvent,
            modifier = modifier,
        )

        PreviewPanel.AUDIO -> Column(modifier) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onShowSpectrogramChange(false) }) { Text(stringResource(R.string.preview_waveform)) }
                TextButton(onClick = { onShowSpectrogramChange(true) }) { Text(stringResource(R.string.preview_spectrogram)) }
            }
            if (showSpectrogram) {
                SpectrogramView(
                    data = spectrogram,
                    positionMs = state.playback.positionMs,
                    durationMs = state.playback.durationMs,
                )
            } else {
                AudioTimeline(
                    waveform = waveform,
                    events = state.script.events,
                    selectedEventId = state.selectedEventId,
                    positionMs = state.playback.positionMs,
                    durationMs = state.playback.durationMs,
                    onCommitDrag = { id, startMs, endMs ->
                        viewModel.editEventTimes(id, SubTime.ofMillis(startMs), SubTime.ofMillis(endMs))
                        viewModel.selectEvent(id)
                    },
                )
            }
        }

        PreviewPanel.TIMING -> Column(modifier.verticalScroll(rememberScrollState())) {
            if (selectedEvent != null) {
                TimingToolbar(state = state, viewModel = viewModel)
                TimingEditLayer(state = state, viewModel = viewModel)
            } else {
                Text(stringResource(R.string.timing_pick_row), modifier = Modifier.padding(16.dp))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.preview_bookmarks), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Button(onClick = { viewModel.addBookmark("") }) { Text(stringResource(R.string.preview_add_bookmark)) }
            }
            if (bookmarks.isEmpty()) {
                Text(stringResource(R.string.preview_no_bookmarks), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            } else {
                bookmarks.forEach { bm ->
                    ListItem(
                        headlineContent = { Text(bm.label.ifBlank { "Bookmark ${formatTime(bm.timeMs)}" }) },
                        supportingContent = {
                            Text(formatTime(bm.timeMs), style = MaterialTheme.typography.bodySmall)
                        },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { viewModel.seekToBookmark(bm.timeMs) }) { Text(stringResource(R.string.preview_jump)) }
                                TextButton(onClick = { viewModel.deleteBookmark(bm.id) }) { Text(stringResource(R.string.preview_delete)) }
                            }
                        },
                        modifier = Modifier.clickable { viewModel.seekToBookmark(bm.timeMs) },
                    )
                }
            }
        }

        PreviewPanel.TYPES -> Column(modifier.verticalScroll(rememberScrollState()).padding(8.dp)) {
            if (selectedEvent != null && state.hasMedia) {
                Text(stringResource(R.string.types_title), style = MaterialTheme.typography.titleSmall)
                VisualTypesettingControls(
                    event = selectedEvent,
                    toolMode = vtToolMode,
                    onToolModeChange = onVtToolModeChange,
                    onRotationChange = { deg -> viewModel.setEventRotation(selectedEvent.id, deg) },
                    onFadeChange = { fin, fout -> viewModel.setEventFade(selectedEvent.id, fin, fout) },
                    onClearPos = { viewModel.clearEventPos(selectedEvent.id) },
                    onClearMove = { viewModel.clearEventMove(selectedEvent.id) },
                    onClipChange = { x1, y1, x2, y2, inv ->
                        viewModel.setEventClip(selectedEvent.id, x1, y1, x2, y2, inv)
                    },
                    onClearClip = { viewModel.clearEventClip(selectedEvent.id) },
                )
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text(stringResource(R.string.preview_karaoke_timing), style = MaterialTheme.typography.titleSmall)
                KaraokeTimeline(
                    text = selectedEvent.text,
                    onCommit = { viewModel.setEventText(selectedEvent.id, it) },
                )
            } else {
                Text(stringResource(R.string.types_pick_row), modifier = Modifier.padding(16.dp))
            }
        }
    }
}

@Composable
private fun VisualTypesettingControls(
    event: AssEvent,
    toolMode: VisualToolMode,
    onToolModeChange: (VisualToolMode) -> Unit,
    onRotationChange: (Int) -> Unit,
    onFadeChange: (fadeIn: Int, fadeOut: Int) -> Unit,
    onClearPos: () -> Unit,
    onClearMove: () -> Unit,
    onClipChange: (x1: Int, y1: Int, x2: Int, y2: Int, inverse: Boolean) -> Unit,
    onClearClip: () -> Unit,
) {
    var slider by remember(event.id) { mutableStateOf(VisualTags.getRotation(event.text).toFloat()) }
    val existingFade = remember(event.id, event.text) { VisualTags.getFade(event.text) }
    var fadeIn by remember(event.id) { mutableStateOf((existingFade?.fadeIn ?: 0).toString()) }
    var fadeOut by remember(event.id) { mutableStateOf((existingFade?.fadeOut ?: 0).toString()) }
    val existingClip = remember(event.id, event.text) { VisualTags.getClip(event.text) }
    var cx1 by remember(event.id) { mutableStateOf((existingClip?.x1 ?: 0).toString()) }
    var cy1 by remember(event.id) { mutableStateOf((existingClip?.y1 ?: 0).toString()) }
    var cx2 by remember(event.id) { mutableStateOf((existingClip?.x2 ?: 0).toString()) }
    var cy2 by remember(event.id) { mutableStateOf((existingClip?.y2 ?: 0).toString()) }
    var clipInverse by remember(event.id) { mutableStateOf(existingClip?.inverse ?: false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = toolMode == VisualToolMode.POSITION,
                onClick = { onToolModeChange(VisualToolMode.POSITION) },
                label = { Text(stringResource(R.string.preview_pos_mode)) },
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = toolMode == VisualToolMode.MOVE,
                onClick = { onToolModeChange(VisualToolMode.MOVE) },
                label = { Text(stringResource(R.string.preview_move_mode)) },
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = if (toolMode == VisualToolMode.MOVE) onClearMove else onClearPos,
            ) {
                Text(if (toolMode == VisualToolMode.MOVE) stringResource(R.string.preview_clear_move) else stringResource(R.string.preview_clear_pos))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\\fr", style = MaterialTheme.typography.labelMedium)
            Slider(
                value = slider,
                onValueChange = { slider = it },
                valueRange = 0f..359f,
                onValueChangeFinished = { onRotationChange(slider.roundToInt()) },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            )
            Text("${slider.roundToInt()}°", style = MaterialTheme.typography.labelMedium)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\\fad", style = MaterialTheme.typography.labelMedium)
            OutlinedTextField(
                value = fadeIn,
                onValueChange = { fadeIn = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.preview_fade_in)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            OutlinedTextField(
                value = fadeOut,
                onValueChange = { fadeOut = it.filter { ch -> ch.isDigit() } },
                label = { Text(stringResource(R.string.preview_fade_out)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
            )
            TextButton(onClick = {
                onFadeChange(fadeIn.toIntOrNull() ?: 0, fadeOut.toIntOrNull() ?: 0)
            }) { Text(stringResource(R.string.preview_apply)) }
        }
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("\\clip", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = cx1, onValueChange = { cx1 = it.filter { c -> c.isDigit() || c == '-' } },
                label = { Text("x1") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
            )
            OutlinedTextField(
                value = cy1, onValueChange = { cy1 = it.filter { c -> c.isDigit() || c == '-' } },
                label = { Text("y1") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
            )
            OutlinedTextField(
                value = cx2, onValueChange = { cx2 = it.filter { c -> c.isDigit() || c == '-' } },
                label = { Text("x2") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
            )
            OutlinedTextField(
                value = cy2, onValueChange = { cy2 = it.filter { c -> c.isDigit() || c == '-' } },
                label = { Text("y2") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(
                selected = !clipInverse,
                onClick = { clipInverse = false },
                label = { Text(stringResource(R.string.preview_clip_in)) },
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = clipInverse,
                onClick = { clipInverse = true },
                label = { Text(stringResource(R.string.preview_clip_out)) },
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onClearClip) { Text(stringResource(R.string.preview_clear)) }
            Button(onClick = {
                onClipChange(
                    cx1.toIntOrNull() ?: 0, cy1.toIntOrNull() ?: 0,
                    cx2.toIntOrNull() ?: 0, cy2.toIntOrNull() ?: 0, clipInverse,
                )
            }) { Text(stringResource(R.string.preview_apply)) }
        }
    }
}

@Composable
private fun ActiveSubtitleLayer(viewModel: PreviewViewModel) {
    val infos by viewModel.activeSubtitles.collectAsStateWithLifecycle()
    SubtitleOverlay(renderInfos = infos, modifier = Modifier.fillMaxSize())
}

@Composable
private fun PlaybackControls(
    playback: PlaybackState,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onFrameBack: () -> Unit,
    onFrameForward: () -> Unit,
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    var speedExpanded by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = "Play/Pause",
                )
            }
            IconButton(onClick = onFrameBack, enabled = playback.isReady) {
                Text("◀▏", style = MaterialTheme.typography.titleSmall)
            }
            IconButton(onClick = onFrameForward, enabled = playback.isReady) {
                Text("▕▶", style = MaterialTheme.typography.titleSmall)
            }
            Text(formatTime(playback.positionMs), style = MaterialTheme.typography.bodySmall)
            Box {
                TextButton(onClick = { speedExpanded = true }) { Text("${playback.speed}x") }
                DropdownMenu(expanded = speedExpanded, onDismissRequest = { speedExpanded = false }) {
                    speeds.forEach { rate ->
                        DropdownMenuItem(
                            text = { Text("${rate}x") },
                            onClick = { onSpeedChange(rate); speedExpanded = false },
                        )
                    }
                }
            }
            Text(" / ${formatTime(playback.durationMs)}", style = MaterialTheme.typography.bodySmall)
            if (playback.fps > 0f) {
                Text(
                    "  ${"%.2f".format(playback.fps)}fps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        val ratio = if (playback.durationMs > 0) {
            (playback.positionMs.toFloat() / playback.durationMs).coerceIn(0f, 1f)
        } else 0f
        Slider(
            value = ratio,
            onValueChange = { v ->
                if (playback.durationMs > 0) onSeek((v * playback.durationMs).toLong())
            },
        )
    }
}

@Composable
private fun PreviewToolbar(state: PreviewUiState.Loaded, viewModel: PreviewViewModel) {
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = viewModel::undo, enabled = canUndo) {
            Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = stringResource(R.string.preview_undo))
        }
        IconButton(onClick = viewModel::redo, enabled = canRedo) {
            Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = stringResource(R.string.preview_redo))
        }
        IconButton(onClick = viewModel::playPause) {
            Icon(
                if (state.playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = "Play/Pause",
            )
        }
        IconButton(onClick = viewModel::frameStepBack, enabled = state.playback.isReady) {
            Text("◀▏", style = MaterialTheme.typography.labelSmall)
        }
        IconButton(onClick = viewModel::frameStepForward, enabled = state.playback.isReady) {
            Text("▕▶", style = MaterialTheme.typography.labelSmall)
        }
        Text(
            "${formatTime(state.playback.positionMs)} / ${formatTime(state.playback.durationMs)}",
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Spacer(Modifier.weight(1f))
        if (state.playback.fps > 0f) {
            Text(
                "${"%.2f".format(state.playback.fps)}fps",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun AudioBand(
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    showSpectrogram: Boolean,
    onShowSpectrogramChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val waveform by viewModel.waveform.collectAsStateWithLifecycle()
    val spectrogram by viewModel.spectrogram.collectAsStateWithLifecycle()
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(selected = !showSpectrogram, onClick = { onShowSpectrogramChange(false) }, label = { Text(stringResource(R.string.preview_waveform)) })
            Spacer(Modifier.width(6.dp))
            FilterChip(selected = showSpectrogram, onClick = { onShowSpectrogramChange(true) }, label = { Text(stringResource(R.string.preview_spectrogram)) })
        }
        if (showSpectrogram) {
            SpectrogramView(
                data = spectrogram,
                positionMs = state.playback.positionMs,
                durationMs = state.playback.durationMs,
            )
        } else {
            AudioTimeline(
                waveform = waveform,
                events = state.script.events,
                selectedEventId = state.selectedEventId,
                positionMs = state.playback.positionMs,
                durationMs = state.playback.durationMs,
                onCommitDrag = { id, startMs, endMs ->
                    viewModel.editEventTimes(id, SubTime.ofMillis(startMs), SubTime.ofMillis(endMs))
                    viewModel.selectEvent(id)
                },
            )
        }
    }
}

@Composable
private fun BookmarksSection(
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    modifier: Modifier = Modifier,
) {
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.preview_bookmarks), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Button(onClick = { viewModel.addBookmark("") }) { Text(stringResource(R.string.preview_add_bookmark)) }
        }
        if (bookmarks.isEmpty()) {
            Text(stringResource(R.string.preview_no_bookmarks), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            bookmarks.forEach { bm ->
                ListItem(
                    headlineContent = { Text(bm.label.ifBlank { "Bookmark ${formatTime(bm.timeMs)}" }) },
                    supportingContent = { Text(formatTime(bm.timeMs), style = MaterialTheme.typography.bodySmall) },
                    trailingContent = {
                        Row {
                            TextButton(onClick = { viewModel.seekToBookmark(bm.timeMs) }) { Text(stringResource(R.string.preview_jump)) }
                            TextButton(onClick = { viewModel.deleteBookmark(bm.id) }) { Text(stringResource(R.string.preview_delete)) }
                        }
                    },
                    modifier = Modifier.clickable { viewModel.seekToBookmark(bm.timeMs) },
                )
            }
        }
    }
}

@Composable
private fun CompactPreview(
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    onPickVideo: () -> Unit,
) {
    var vtActive by remember { mutableStateOf(false) }
    var vtToolMode by remember { mutableStateOf(VisualToolMode.POSITION) }
    var karaokeMode by remember { mutableStateOf(false) }
    var showSpectrogram by remember { mutableStateOf(false) }
    val selected = state.script.events.firstOrNull { it.id == state.selectedEventId }
    Column(Modifier.fillMaxSize()) {
        VideoBlock(
            state = state,
            viewModel = viewModel,
            onPickVideo = onPickVideo,
            vtActive = vtActive && selected != null && state.hasMedia,
            vtToolMode = vtToolMode,
            onVtToolModeChange = { vtToolMode = it },
            videoMaxHeight = 200.dp,
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = viewModel::selectPrevEvent, enabled = selected != null) {
                Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.preview_prev_line))
            }
            Button(onClick = { selected?.let { viewModel.setStartToPosition(it.id) } }, enabled = selected != null) { Text(stringResource(R.string.preview_set_start)) }
            Button(onClick = { selected?.let { viewModel.setEndToPosition(it.id) } }, enabled = selected != null) { Text(stringResource(R.string.preview_set_end)) }
            IconButton(onClick = viewModel::selectNextEvent, enabled = selected != null) {
                Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.preview_next_line))
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showSpectrogram = !showSpectrogram }) {
                Text(if (showSpectrogram) stringResource(R.string.preview_waveform) else stringResource(R.string.preview_spectrogram))
            }
            TextButton(onClick = { vtActive = !vtActive }) {
                Text(if (vtActive) stringResource(R.string.preview_exit_typesetting) else stringResource(R.string.preview_typesetting))
            }
            TextButton(onClick = { karaokeMode = !karaokeMode }) {
                Text(if (karaokeMode) stringResource(R.string.preview_exit_karaoke) else stringResource(R.string.preview_karaoke))
            }
        }
        if (vtActive && selected != null && state.hasMedia) {
            Column(Modifier.fillMaxWidth().heightIn(max = 170.dp).verticalScroll(rememberScrollState())) {
                VisualTypesettingControls(
                    event = selected,
                    toolMode = vtToolMode,
                    onToolModeChange = { vtToolMode = it },
                    onRotationChange = { viewModel.setEventRotation(selected.id, it) },
                    onFadeChange = { f, fo -> viewModel.setEventFade(selected.id, f, fo) },
                    onClearPos = { viewModel.clearEventPos(selected.id) },
                    onClearMove = { viewModel.clearEventMove(selected.id) },
                    onClipChange = { x1, y1, x2, y2, inv -> viewModel.setEventClip(selected.id, x1, y1, x2, y2, inv) },
                    onClearClip = { viewModel.clearEventClip(selected.id) },
                )
            }
        } else if (karaokeMode && selected != null) {
            KaraokeTimeline(
                text = selected.text,
                onCommit = { viewModel.setEventText(selected.id, it) },
                modifier = Modifier.padding(horizontal = 8.dp),
            )
        } else {
            AudioBand(state, viewModel, showSpectrogram, { showSpectrogram = it }, Modifier.height(110.dp).fillMaxWidth())
        }
        EventListColumn(
            events = state.script.events,
            currentEventId = state.currentEventId,
            selectedEventId = state.selectedEventId,
            onSelect = viewModel::selectEvent,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ExpandedPreview(
    state: PreviewUiState.Loaded,
    viewModel: PreviewViewModel,
    onPickVideo: () -> Unit,
) {
    var vtActive by remember { mutableStateOf(false) }
    var vtToolMode by remember { mutableStateOf(VisualToolMode.POSITION) }
    var karaokeMode by remember { mutableStateOf(false) }
    var showSpectrogram by remember { mutableStateOf(false) }
    var sortKey by remember { mutableStateOf<io.github.samgum.aegisub.domain.edit.SortKey?>(null) }
    var sortOrder by remember { mutableStateOf(io.github.samgum.aegisub.domain.edit.SortOrder.ASCENDING) }
    val selected = state.script.events.firstOrNull { it.id == state.selectedEventId }
    Column(Modifier.fillMaxSize()) {
        PreviewToolbar(state, viewModel)
        Row(Modifier.fillMaxSize().weight(1f)) {
            Column(Modifier.weight(0.60f)) {
                VideoBlock(
                    state = state,
                    viewModel = viewModel,
                    onPickVideo = onPickVideo,
                    vtActive = vtActive && selected != null && state.hasMedia,
                    vtToolMode = vtToolMode,
                    onVtToolModeChange = { vtToolMode = it },
                    videoMaxHeight = 160.dp,
                )
                SubtitleGrid(
                    events = state.script.events,
                    currentEventId = state.currentEventId,
                    selectedEventId = state.selectedEventId,
                    onSelect = viewModel::selectEvent,
                    sortKey = sortKey,
                    sortOrder = sortOrder,
                    onSort = { key ->
                        if (sortKey == key) {
                            sortOrder = if (sortOrder == io.github.samgum.aegisub.domain.edit.SortOrder.ASCENDING)
                                io.github.samgum.aegisub.domain.edit.SortOrder.DESCENDING
                            else io.github.samgum.aegisub.domain.edit.SortOrder.ASCENDING
                        } else {
                            sortKey = key
                            sortOrder = io.github.samgum.aegisub.domain.edit.SortOrder.ASCENDING
                        }
                        viewModel.sortLines(key, sortOrder)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            Column(Modifier.weight(0.40f)) {
                Row(
                    Modifier.fillMaxWidth().padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { showSpectrogram = !showSpectrogram }) {
                        Text(if (showSpectrogram) stringResource(R.string.preview_waveform) else stringResource(R.string.preview_spectrogram))
                    }
                    TextButton(onClick = { vtActive = !vtActive }) {
                        Text(if (vtActive) stringResource(R.string.preview_exit_typesetting) else stringResource(R.string.preview_typesetting))
                    }
                    TextButton(onClick = { karaokeMode = !karaokeMode }) {
                        Text(if (karaokeMode) stringResource(R.string.preview_exit_karaoke) else stringResource(R.string.preview_karaoke))
                    }
                }
                if (vtActive && selected != null && state.hasMedia) {
                    Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        VisualTypesettingControls(
                            event = selected,
                            toolMode = vtToolMode,
                            onToolModeChange = { vtToolMode = it },
                            onRotationChange = { viewModel.setEventRotation(selected.id, it) },
                            onFadeChange = { f, fo -> viewModel.setEventFade(selected.id, f, fo) },
                            onClearPos = { viewModel.clearEventPos(selected.id) },
                            onClearMove = { viewModel.clearEventMove(selected.id) },
                            onClipChange = { x1, y1, x2, y2, inv -> viewModel.setEventClip(selected.id, x1, y1, x2, y2, inv) },
                            onClearClip = { viewModel.clearEventClip(selected.id) },
                        )
                    }
                } else if (karaokeMode && selected != null) {
                    KaraokeTimeline(
                        text = selected.text,
                        onCommit = { viewModel.setEventText(selected.id, it) },
                        modifier = Modifier.padding(8.dp).weight(1f),
                    )
                } else {
                    AudioBand(state, viewModel, showSpectrogram, { showSpectrogram = it }, Modifier.weight(1f))
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = viewModel::selectPrevEvent, enabled = selected != null) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.preview_prev_line))
                    }
                    Button(onClick = { selected?.let { viewModel.setStartToPosition(it.id) } }, enabled = selected != null) { Text(stringResource(R.string.preview_set_start)) }
                    Button(onClick = { selected?.let { viewModel.setEndToPosition(it.id) } }, enabled = selected != null) { Text(stringResource(R.string.preview_set_end)) }
                    IconButton(onClick = viewModel::selectNextEvent, enabled = selected != null) {
                        Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.preview_next_line))
                    }
                }
                selected?.let {
                    Text(
                        "${it.start.toAssString(false)} → ${it.end.toAssString(false)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SubtitleGrid(
    events: List<AssEvent>,
    currentEventId: Long?,
    selectedEventId: Long?,
    onSelect: (Long) -> Unit,
    sortKey: io.github.samgum.aegisub.domain.edit.SortKey?,
    sortOrder: io.github.samgum.aegisub.domain.edit.SortOrder,
    onSort: (io.github.samgum.aegisub.domain.edit.SortKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("#", Modifier.weight(0.05f), style = MaterialTheme.typography.labelMedium)
            SortHeader(stringResource(R.string.preview_grid_start), Modifier.weight(0.19f), sortKey == io.github.samgum.aegisub.domain.edit.SortKey.START, sortOrder) {
                onSort(io.github.samgum.aegisub.domain.edit.SortKey.START)
            }
            SortHeader(stringResource(R.string.preview_grid_end), Modifier.weight(0.19f), sortKey == io.github.samgum.aegisub.domain.edit.SortKey.END, sortOrder) {
                onSort(io.github.samgum.aegisub.domain.edit.SortKey.END)
            }
            SortHeader(stringResource(R.string.preview_grid_style), Modifier.weight(0.14f), sortKey == io.github.samgum.aegisub.domain.edit.SortKey.STYLE, sortOrder) {
                onSort(io.github.samgum.aegisub.domain.edit.SortKey.STYLE)
            }
            Text(stringResource(R.string.preview_grid_text), Modifier.weight(0.43f), style = MaterialTheme.typography.labelMedium)
        }
        HorizontalDivider()
        LazyColumn(Modifier.fillMaxSize()) {
            itemsIndexed(events, key = { _, e -> e.id }) { index, event ->
                SubtitleGridRow(
                    event = event,
                    index = index,
                    isCurrent = event.id == currentEventId,
                    isSelected = event.id == selectedEventId,
                    onClick = { onSelect(event.id) },
                )
            }
        }
    }
}

@Composable
private fun SortHeader(
    label: String,
    modifier: Modifier,
    active: Boolean,
    order: io.github.samgum.aegisub.domain.edit.SortOrder,
    onClick: () -> Unit,
) {
    Text(
        "$label ${if (active) (if (order == io.github.samgum.aegisub.domain.edit.SortOrder.ASCENDING) "↑" else "↓") else ""}",
        modifier = modifier.clickable(onClick = onClick),
        style = MaterialTheme.typography.labelMedium,
        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun SubtitleGridRow(
    event: AssEvent,
    index: Int,
    isCurrent: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isCurrent -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${index + 1}", Modifier.weight(0.05f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
        Text(event.start.toAssString(false), Modifier.weight(0.19f), style = MaterialTheme.typography.bodySmall)
        Text(event.end.toAssString(false), Modifier.weight(0.19f), style = MaterialTheme.typography.bodySmall)
        Text(
            event.style,
            Modifier.weight(0.14f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            event.strippedText.ifBlank { stringResource(R.string.subtitle_no_text) },
            Modifier.weight(0.43f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (event.comment) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EventListColumn(
    events: List<AssEvent>,
    currentEventId: Long?,
    selectedEventId: Long?,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier) {
        items(events, key = { it.id }) { event ->
            PreviewEventRow(
                event = event,
                isCurrent = event.id == currentEventId,
                isSelected = event.id == selectedEventId,
                onClick = { onSelect(event.id) },
            )
        }
    }
}

@Composable
private fun PreviewEventRow(event: AssEvent, isCurrent: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val bg = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isCurrent -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }
    ListItem(
        headlineContent = {
            Text(
                text = event.strippedText.ifBlank { stringResource(R.string.subtitle_no_text) },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (event.comment) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.onSurface,
            )
        },
        supportingContent = {
            Text(
                text = "${event.start.toAssString(false)} → ${event.end.toAssString(false)}  ·  ${event.style}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        modifier = Modifier.background(bg).clickable(onClick = onClick),
    )
}

@Composable
private fun TimingEditLayer(state: PreviewUiState.Loaded, viewModel: PreviewViewModel) {
    val event = state.script.events.firstOrNull { it.id == state.selectedEventId } ?: return
    var target by remember(state.selectedEventId) { mutableStateOf(NudgeTarget.START) }
    TimingEditPanel(
        startMs = event.start.millis,
        endMs = event.end.millis,
        durationMs = state.playback.durationMs,
        nudgeTarget = target,
        onNudgeTargetChange = { target = it },
        onSeek = viewModel::seekTo,
        onCommitStart = { ms -> viewModel.editEventTimes(event.id, SubTime.ofMillis(ms), event.end) },
        onCommitEnd = { ms -> viewModel.editEventTimes(event.id, event.start, SubTime.ofMillis(ms)) },
        onNudge = { delta ->
            when (target) {
                NudgeTarget.START -> viewModel.nudgeStart(delta)
                NudgeTarget.END -> viewModel.nudgeEnd(delta)
            }
        },
    )
}

@Composable
private fun TimingToolbar(state: PreviewUiState.Loaded, viewModel: PreviewViewModel) {
    val id = state.selectedEventId ?: return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = viewModel::selectPrevEvent) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = stringResource(R.string.preview_prev_line))
        }
        Button(onClick = { viewModel.setStartToPosition(id) }) { Text(stringResource(R.string.preview_set_start)) }
        Button(onClick = { viewModel.setEndToPosition(id) }) { Text(stringResource(R.string.preview_set_end)) }
        IconButton(onClick = viewModel::selectNextEvent) {
            Icon(Icons.Filled.SkipNext, contentDescription = stringResource(R.string.preview_next_line))
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
