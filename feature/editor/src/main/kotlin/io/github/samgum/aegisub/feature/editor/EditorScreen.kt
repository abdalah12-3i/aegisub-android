package io.github.samgum.aegisub.feature.editor

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
import io.github.samgum.aegisub.data.hotkeys.rememberHotkeyController
import io.github.samgum.aegisub.domain.edit.HotkeyAction
import io.github.samgum.aegisub.data.settings.LayoutMode
import io.github.samgum.aegisub.feature.editor.R
import io.github.samgum.aegisub.domain.edit.FramerateConverter
import io.github.samgum.aegisub.domain.edit.KaraokeMode
import io.github.samgum.aegisub.domain.edit.ScriptInfoOps
import io.github.samgum.aegisub.domain.edit.ShiftTarget
import io.github.samgum.aegisub.domain.edit.SortKey
import io.github.samgum.aegisub.domain.edit.SortOrder
import io.github.samgum.aegisub.domain.format.AssFormat
import io.github.samgum.aegisub.domain.format.SrtFormat
import io.github.samgum.aegisub.domain.format.SubtitleFormat
import io.github.samgum.aegisub.domain.format.VttFormat
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssInfo
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.feature.editor.compact.EventEditSheet
import io.github.samgum.aegisub.feature.editor.compact.EventListScreen
import io.github.samgum.aegisub.feature.editor.components.EditorActions
import io.github.samgum.aegisub.feature.editor.components.LineAction
import io.github.samgum.aegisub.feature.editor.components.SelectionActionBar
import io.github.samgum.aegisub.feature.editor.components.StylingAssistantSheet
import io.github.samgum.aegisub.feature.editor.components.TranslationAssistantSheet
import io.github.samgum.aegisub.feature.editor.expanded.EditorTwoPane

/** دالة مساعدة لتحديد اللغة المعروضة حالياً */
@Composable
private fun tr(en: String, ar: String, tr: String = en): String {
    val lang = LocalConfiguration.current.locales[0]?.language ?: "en"
    return when {
        lang.startsWith("ar") -> ar
        lang.startsWith("tr") -> tr
        else -> en
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    onBack: () -> Unit,
    onOpenPreview: (Long) -> Unit,
    onOpenStyles: (Long) -> Unit,
    viewModel: EditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val canUndo by viewModel.canUndo.collectAsStateWithLifecycle()
    val canRedo by viewModel.canRedo.collectAsStateWithLifecycle()
    val layoutMode by viewModel.layoutMode.collectAsStateWithLifecycle()
    val snapshots by viewModel.snapshotList.collectAsStateWithLifecycle()
    val hotkeys = rememberHotkeyController()
    var editingId by remember { mutableStateOf<Long?>(null) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    fun exitSelection() { selectionMode = false; selectedIds = emptySet() }
    fun enterSelection(id: Long) { selectionMode = true; selectedIds = setOf(id) }
    fun toggleSelect(id: Long) {
        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExportFormat by remember { mutableStateOf<SubtitleFormat?>(null) }
    var showExportFormat by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri ->
        val fmt = pendingExportFormat
        pendingExportFormat = null
        if (uri != null && fmt != null) {
            scope.launch { writeExportFile(context, uri, viewModel.exportAs(fmt)) }
        }
    }
    val onExport = { showExportFormat = true }
    var showToolbox by remember { mutableStateOf(false) }
    var showFindReplace by remember { mutableStateOf(false) }
    var showShiftTimes by remember { mutableStateOf(false) }
    var showDeleteEmpty by remember { mutableStateOf(false) }
    var showStyleReplace by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showFramerate by remember { mutableStateOf(false) }
    var showProperties by remember { mutableStateOf(false) }
    var showStyling by remember { mutableStateOf(false) }
    var showTranslation by remember { mutableStateOf(false) }
    var showKaraoke by remember { mutableStateOf(false) }
    var showTimingPP by remember { mutableStateOf(false) }
    var showResample by remember { mutableStateOf(false) }
    var showPasteOver by remember { mutableStateOf(false) }

    when (val s = state) {
        EditorUiState.Loading ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        is EditorUiState.Error ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "${s.message}",
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = onBack) { Text(stringResource(R.string.common_cancel)) }
                }
            }

        is EditorUiState.Loaded -> {
            val isCompact = when (layoutMode) {
                LayoutMode.COMPACT -> true
                LayoutMode.EXPANDED -> false
                LayoutMode.AUTO -> LocalConfiguration.current.screenWidthDp < 600
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { event ->
                        val action = hotkeys.match(event) ?: return@onPreviewKeyEvent false
                        when (action) {
                            HotkeyAction.SAVE -> true
                            HotkeyAction.EXPORT -> { showExportFormat = true; true }
                            else -> handleEditorHotkey(action, viewModel, editingId) {
                                showFindReplace = true
                            } ?: false
                        }
                    },
            ) {
                if (isCompact) {
                    CompactEditor(
                        script = s.script,
                        editingId = editingId,
                        onEventClick = { editingId = it.id },
                        onDismissEdit = { editingId = null },
                        onBack = { if (selectionMode) exitSelection() else onBack() },
                        onOpenPreview = { onOpenPreview(viewModel.projectId) },
                        onExport = onExport,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        viewModel = viewModel,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onToggleSelect = ::toggleSelect,
                        onEnterSelection = ::enterSelection,
                    )
                } else {
                    EditorTwoPane(
                        script = s.script,
                        editingId = editingId,
                        onEventClick = { editingId = it.id },
                        onBack = { if (selectionMode) exitSelection() else onBack() },
                        onOpenPreview = { onOpenPreview(viewModel.projectId) },
                        onExport = onExport,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onUndo = viewModel::undo,
                        onRedo = viewModel::redo,
                        onTextChanged = viewModel::updateEventText,
                        onTimesChanged = viewModel::updateEventTimes,
                        onStyleChanged = viewModel::updateEventStyle,
                        onLayerChanged = viewModel::setEventLayer,
                        onLineAction = viewModel::applyLineAction,
                        selectionMode = selectionMode,
                        selectedIds = selectedIds,
                        onToggleSelect = ::toggleSelect,
                        onEnterSelection = ::enterSelection,
                    )
                }
                if (selectionMode) {
                    SelectionActionBar(
                        count = selectedIds.size,
                        total = s.script.events.size,
                        onMoveUp = { viewModel.moveSelectedUp(selectedIds) },
                        onMoveDown = { viewModel.moveSelectedDown(selectedIds) },
                        onDuplicate = {
                            viewModel.duplicateSelected(selectedIds)
                            exitSelection()
                        },
                        onDelete = {
                            viewModel.deleteSelected(selectedIds)
                            exitSelection()
                        },
                        onSelectAll = { selectedIds = s.script.events.map { it.id }.toSet() },
                        onCancel = ::exitSelection,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
                if (!selectionMode) {
                    FloatingActionButton(
                        onClick = { showToolbox = true },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                    ) { Icon(Icons.Filled.Build, contentDescription = stringResource(R.string.toolbox_title)) }
                }
            }
        }
    }

    if (showToolbox) {
        ToolboxSheet(
            onDismiss = { showToolbox = false },
            onFindReplace = { showToolbox = false; showFindReplace = true },
            onShiftTimes = { showToolbox = false; showShiftTimes = true },
            onSort = { showToolbox = false; showSort = true },
            onFramerate = { showToolbox = false; showFramerate = true },
            onProperties = { showToolbox = false; showProperties = true },
            onStyling = { showToolbox = false; showStyling = true },
            onTranslation = { showToolbox = false; showTranslation = true },
            onKaraoke = { showToolbox = false; showKaraoke = true },
            onTimingPP = { showToolbox = false; showTimingPP = true },
            onResample = { showToolbox = false; showResample = true },
            onPasteOver = { showToolbox = false; showPasteOver = true },
            onDeleteEmpty = { showToolbox = false; showDeleteEmpty = true },
            onStyleReplace = { showToolbox = false; showStyleReplace = true },
            onOpenStyleManager = { showToolbox = false; onOpenStyles(viewModel.projectId) },
            onOpenHistory = { showToolbox = false; showHistory = true },
        )
    }

    if (showFindReplace) {
        FindReplaceDialog(
            onDismiss = { showFindReplace = false },
            onReplace = { q, r, regex, ic ->
                viewModel.replaceAll(q, r, regex, ic)
                showFindReplace = false
            },
        )
    }

    if (showShiftTimes) {
        ShiftTimesDialog(
            onDismiss = { showShiftTimes = false },
            onApply = { deltaMs, target, onlyAfterSelected ->
                val fromStart = if (onlyAfterSelected) currentSelectedStart(state, editingId) else null
                viewModel.shiftTimes(deltaMs, target, fromStart)
                showShiftTimes = false
            },
        )
    }

    if (showDeleteEmpty) {
        AlertDialog(
            onDismissRequest = { showDeleteEmpty = false },
            title = { Text(stringResource(R.string.dialog_delete_empty)) },
            text = { Text(stringResource(R.string.dialog_delete_empty_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEmptyLines()
                    showDeleteEmpty = false
                }) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = { TextButton(onClick = { showDeleteEmpty = false }) { Text(stringResource(R.string.common_cancel)) } },
        )
    }

    if (showStyleReplace) {
        val styles = (state as? EditorUiState.Loaded)?.script?.styles?.map { it.name } ?: emptyList()
        StyleReplaceDialog(
            styles = styles,
            onDismiss = { showStyleReplace = false },
            onApply = { from, to ->
                viewModel.replaceStyles(from, to)
                showStyleReplace = false
            },
        )
    }

    if (showSort) {
        SortDialog(
            onDismiss = { showSort = false },
            onApply = { key, order ->
                viewModel.sortLines(key, order)
                showSort = false
            },
        )
    }

    if (showFramerate) {
        FramerateDialog(
            onDismiss = { showFramerate = false },
            onApply = { from, to ->
                viewModel.convertFramerate(from, to)
                showFramerate = false
            },
        )
    }

    if (showProperties) {
        val info = (state as? EditorUiState.Loaded)?.script?.info
        if (info != null) {
            PropertiesSheet(
                info = info,
                onDismiss = { showProperties = false },
                onApply = { changes ->
                    viewModel.applyScriptInfo(changes)
                    showProperties = false
                },
            )
        }
    }

    if (showStyling) {
        val loaded = state as? EditorUiState.Loaded
        val events = loaded?.script?.events
        if (loaded != null && events != null && events.isNotEmpty()) {
            val currentId = editingId ?: events.first().id
            val pos = events.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
            val ev = events[pos]
            StylingAssistantSheet(
                event = ev,
                position = pos,
                total = events.size,
                styles = loaded.script.styles,
                onAssign = { style ->
                    viewModel.updateEventStyle(ev.id, style)
                    if (pos + 1 < events.size) editingId = events[pos + 1].id
                },
                onPrev = { if (pos > 0) editingId = events[pos - 1].id },
                onNext = { if (pos + 1 < events.size) editingId = events[pos + 1].id },
                onDismiss = { showStyling = false },
            )
        }
    }

    if (showTranslation) {
        val loaded = state as? EditorUiState.Loaded
        val events = loaded?.script?.events
        if (events != null && events.isNotEmpty()) {
            val currentId = editingId ?: events.first().id
            val pos = events.indexOfFirst { it.id == currentId }.let { if (it < 0) 0 else it }
            val ev = events[pos]
            TranslationAssistantSheet(
                event = ev,
                position = pos,
                total = events.size,
                onSave = { original, translation ->
                    viewModel.setTranslation(ev.id, original, translation)
                    if (pos + 1 < events.size) editingId = events[pos + 1].id
                },
                onPrev = { if (pos > 0) editingId = events[pos - 1].id },
                onNext = { if (pos + 1 < events.size) editingId = events[pos + 1].id },
                onDismiss = { showTranslation = false },
            )
        }
    }

    if (showKaraoke) {
        val loaded = state as? EditorUiState.Loaded
        val events = loaded?.script?.events
        val hasSelection = events != null && events.any { it.id == editingId }
        if (loaded != null && hasSelection && editingId != null) {
            KaraokeDialog(
                onDismiss = { showKaraoke = false },
                onApply = { mode, useKf ->
                    viewModel.makeKaraoke(editingId!!, mode, useKf)
                    showKaraoke = false
                },
            )
        }
    }

    if (showTimingPP) {
        TimingPostProcessDialog(
            onDismiss = { showTimingPP = false },
            onApply = { leadIn, leadOut, gap ->
                viewModel.applyTimingPostProcess(leadIn, leadOut, gap)
                showTimingPP = false
            },
        )
    }

    if (showResample) {
        val (fromW, fromH) = viewModel.playRes()
        ResolutionResampleDialog(
            fromW = fromW,
            fromH = fromH,
            onDismiss = { showResample = false },
            onApply = { toW, toH, scalePos, scaleBorders ->
                viewModel.resampleResolution(fromW, fromH, toW, toH, scalePos, scaleBorders)
                showResample = false
            },
        )
    }

    if (showPasteOver) {
        val loaded = state as? EditorUiState.Loaded
        val events = loaded?.script?.events
        if (events != null && events.isNotEmpty()) {
            val orderedIds = if (selectionMode && selectedIds.isNotEmpty()) {
                events.filter { it.id in selectedIds }.map { it.id }
            } else {
                events.map { it.id }
            }
            PasteOverDialog(
                targetCount = orderedIds.size,
                onDismiss = { showPasteOver = false },
                onApply = { text ->
                    viewModel.pasteOver(orderedIds, text)
                    showPasteOver = false
                },
            )
        }
    }

    if (showExportFormat) {
        ExportFormatDialog(
            onDismiss = { showExportFormat = false },
            onPick = { fmt ->
                showExportFormat = false
                pendingExportFormat = fmt
                exportLauncher.launch("Subtitles${fmt.extensions.first()}")
            },
        )
    }

    if (showHistory) {
        HistorySheet(
            snapshots = snapshots,
            onDismiss = { showHistory = false },
            onSaveSnapshot = { label ->
                viewModel.takeSnapshot(label)
            },
            onRestore = { id ->
                viewModel.restoreSnapshot(id)
                showHistory = false
            },
            onDelete = { id -> viewModel.deleteSnapshot(id) },
        )
    }
}

private fun currentSelectedStart(state: EditorUiState, editingId: Long?): Long? {
    val loaded = state as? EditorUiState.Loaded ?: return null
    val ev = loaded.script.events.firstOrNull { it.id == editingId } ?: return null
    return ev.start.millis
}

private fun handleEditorHotkey(
    action: HotkeyAction,
    viewModel: EditorViewModel,
    editingId: Long?,
    onFindReplace: () -> Unit,
): Boolean? = when (action) {
    HotkeyAction.UNDO -> { viewModel.undo(); true }
    HotkeyAction.REDO -> { viewModel.redo(); true }
    HotkeyAction.FIND_REPLACE -> { onFindReplace(); true }
    HotkeyAction.DUPLICATE_LINE -> { editingId?.let { viewModel.applyLineAction(it, LineAction.DUPLICATE) }; true }
    HotkeyAction.DELETE_LINE -> { editingId?.let { viewModel.applyLineAction(it, LineAction.DELETE) }; true }
    HotkeyAction.SPLIT_LINE -> { editingId?.let { viewModel.applyLineAction(it, LineAction.SPLIT) }; true }
    HotkeyAction.JOIN_KEEP_FIRST, HotkeyAction.JOIN_CONCAT -> {
        editingId?.let { viewModel.applyLineAction(it, LineAction.JOIN_NEXT) }; true
    }
    HotkeyAction.MOVE_LINE_UP -> { editingId?.let { viewModel.applyLineAction(it, LineAction.MOVE_UP) }; true }
    HotkeyAction.MOVE_LINE_DOWN -> { editingId?.let { viewModel.applyLineAction(it, LineAction.MOVE_DOWN) }; true }
    HotkeyAction.INSERT_AFTER -> { editingId?.let { viewModel.applyLineAction(it, LineAction.INSERT_AFTER) }; true }
    else -> null
}

@Composable
private fun CompactEditor(
    script: AssScript,
    editingId: Long?,
    onEventClick: (AssEvent) -> Unit,
    onDismissEdit: () -> Unit,
    onBack: () -> Unit,
    onOpenPreview: () -> Unit,
    onExport: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    viewModel: EditorViewModel,
    selectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onToggleSelect: (Long) -> Unit = {},
    onEnterSelection: (Long) -> Unit = {},
) {
    EventListScreen(
        events = script.events,
        onEventClick = onEventClick,
        onBack = onBack,
        title = stringResource(R.string.subtitle_list_title),
        selectionMode = selectionMode,
        selectedIds = selectedIds,
        onToggleSelect = onToggleSelect,
        onEnterSelection = onEnterSelection,
        actions = {
            IconButton(onClick = onOpenPreview) {
                Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.common_preview))
            }
            TextButton(onClick = onExport) { Text(tr("Export", "تصدير", "Dışa Aktar")) }
            EditorActions(
                canUndo = canUndo,
                canRedo = canRedo,
                onUndo = viewModel::undo,
                onRedo = viewModel::redo,
            )
        },
    )
    script.events.firstOrNull { it.id == editingId }?.let { ev ->
        EventEditSheet(
            event = ev,
            styles = script.styles.map { it.name }.toPersistentList(),
            onDismiss = onDismissEdit,
            onTextChanged = { viewModel.updateEventText(ev.id, it) },
            onTimesChanged = { start, end -> viewModel.updateEventTimes(ev.id, start, end) },
            onStyleChanged = { viewModel.updateEventStyle(ev.id, it) },
            onLayerChanged = { viewModel.setEventLayer(ev.id, it) },
            onLineAction = { viewModel.applyLineAction(ev.id, it) },
        )
    }
}

private suspend fun writeExportFile(context: Context, uri: Uri, content: String) =
    withContext(Dispatchers.IO) {
        context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
    }

@Composable
private fun FindReplaceDialog(
    onDismiss: () -> Unit,
    onReplace: (query: String, replacement: String, useRegex: Boolean, ignoreCase: Boolean) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var useRegex by remember { mutableStateOf(false) }
    var ignoreCase by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_find_replace)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(tr("Find", "بحث", "Bul")) }, singleLine = true)
                OutlinedTextField(value = replacement, onValueChange = { replacement = it }, label = { Text(tr("Replace with", "استبدال بـ", "Şununla değiştir")) }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useRegex, onCheckedChange = { useRegex = it })
                    Text(tr("Regex", "تعبير نمطي (Regex)", "Düzenli İfade"))
                    Checkbox(checked = ignoreCase, onCheckedChange = { ignoreCase = it })
                    Text(tr("Ignore Case", "تجاهل حالة الأحرف", "Büyük/Küçük harf duyarsız"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onReplace(query, replacement, useRegex, ignoreCase) }) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolboxSheet(
    onDismiss: () -> Unit,
    onFindReplace: () -> Unit,
    onShiftTimes: () -> Unit,
    onSort: () -> Unit,
    onFramerate: () -> Unit,
    onProperties: () -> Unit,
    onStyling: () -> Unit,
    onTranslation: () -> Unit,
    onKaraoke: () -> Unit,
    onTimingPP: () -> Unit,
    onResample: () -> Unit,
    onPasteOver: () -> Unit,
    onDeleteEmpty: () -> Unit,
    onStyleReplace: () -> Unit,
    onOpenStyleManager: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            stringResource(R.string.toolbox_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp),
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            item {
                ToolEntry(Icons.Filled.Search, stringResource(R.string.tool_find_replace), stringResource(R.string.tool_find_replace_desc)) { onFindReplace() }
            }
            item {
                ToolEntry(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.tool_shift_times), stringResource(R.string.tool_shift_times_desc)) { onShiftTimes() }
            }
            item {
                ToolEntry(Icons.AutoMirrored.Filled.Sort, stringResource(R.string.tool_sort), stringResource(R.string.tool_sort_desc)) { onSort() }
            }
            item {
                ToolEntry(Icons.Filled.Movie, stringResource(R.string.tool_framerate), stringResource(R.string.tool_framerate_desc)) { onFramerate() }
            }
            item {
                ToolEntry(Icons.Filled.Settings, stringResource(R.string.tool_properties), stringResource(R.string.tool_properties_desc)) { onProperties() }
            }
            item {
                ToolEntry(Icons.Filled.Delete, stringResource(R.string.tool_delete_empty), stringResource(R.string.tool_delete_empty_desc)) { onDeleteEmpty() }
            }
            item {
                ToolEntry(Icons.Filled.Edit, stringResource(R.string.tool_style_replace), stringResource(R.string.tool_style_replace_desc)) { onStyleReplace() }
            }
            item {
                ToolEntry(Icons.Filled.Build, stringResource(R.string.tool_style_manager), stringResource(R.string.tool_style_manager_desc)) { onOpenStyleManager() }
            }
            item {
                ToolEntry(Icons.Filled.Palette, stringResource(R.string.tool_styling), stringResource(R.string.tool_styling_desc)) { onStyling() }
            }
            item {
                ToolEntry(Icons.Filled.Translate, stringResource(R.string.tool_translation), stringResource(R.string.tool_translation_desc)) { onTranslation() }
            }
            item {
                ToolEntry(Icons.Filled.MusicNote, stringResource(R.string.tool_karaoke), stringResource(R.string.tool_karaoke_desc)) { onKaraoke() }
            }
            item {
                ToolEntry(Icons.Filled.Timer, stringResource(R.string.tool_timing_pp), stringResource(R.string.tool_timing_pp_desc)) { onTimingPP() }
            }
            item {
                ToolEntry(Icons.Filled.AspectRatio, stringResource(R.string.tool_resample), stringResource(R.string.tool_resample_desc)) { onResample() }
            }
            item {
                ToolEntry(Icons.Filled.ContentPaste, stringResource(R.string.tool_paste_over), stringResource(R.string.tool_paste_over_desc)) { onPasteOver() }
            }
            item {
                ToolEntry(Icons.Filled.PlayArrow, stringResource(R.string.tool_history), stringResource(R.string.tool_history_desc)) { onOpenHistory() }
            }
            item { HorizontalDivider() }
        }
    }
}

@Composable
private fun ToolEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
private fun ShiftTimesDialog(
    onDismiss: () -> Unit,
    onApply: (deltaMs: Long, target: ShiftTarget, onlyAfterSelected: Boolean) -> Unit,
) {
    var deltaText by remember { mutableStateOf("0") }
    var target by remember { mutableStateOf(ShiftTarget.BOTH) }
    var onlyAfter by remember { mutableStateOf(false) }
    val delta = deltaText.toLongOrNull() ?: 0L
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_shift_times)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = deltaText,
                    onValueChange = { deltaText = it.filter { ch -> ch.isDigit() || ch == '-' } },
                    label = { Text(tr("Shift (ms, negative = earlier)", "الإزاحة (ميلي ثانية، السالب = تقديم)", "Kaydırma (ms)")) },
                    singleLine = true,
                )
                Text(tr("Target", "الهدف", "Hedef"), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = target == ShiftTarget.BOTH, onClick = { target = ShiftTarget.BOTH }, label = { Text(tr("Start & End", "البداية والنهاية", "Başlangıç ve Bitiş")) })
                    FilterChip(selected = target == ShiftTarget.START, onClick = { target = ShiftTarget.START }, label = { Text(tr("Start Only", "البداية فقط", "Yalnızca Başlangıç")) })
                    FilterChip(selected = target == ShiftTarget.END, onClick = { target = ShiftTarget.END }, label = { Text(tr("End Only", "النهاية فقط", "Yalnızca Bitiş")) })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = onlyAfter, onCheckedChange = { onlyAfter = it })
                    Text(tr("Only selected line and after", "فقط السطر المحدد وما بعده", "Yalnızca seçili satır ve sonrası"))
                }
                Text(
                    if (delta >= 0) tr("Shift forward by ${delta}ms", "تقديم للأمام بمقدار ${delta} ميلي ثانية", "${delta}ms ileri kaydır")
                    else tr("Shift backward by ${-delta}ms", "تأخير للخلف بمقدار ${-delta} ميلي ثانية", "${-delta}ms geri kaydır"),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(delta, target, onlyAfter) }) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun StyleReplaceDialog(
    styles: List<String>,
    onDismiss: () -> Unit,
    onApply: (fromStyle: String, toStyle: String) -> Unit,
) {
    val distinct = styles.distinct()
    var from by remember { mutableStateOf(distinct.firstOrNull() ?: "") }
    var to by remember { mutableStateOf(distinct.firstOrNull() ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_style_replace)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Replace all events of selected style with another style.", "استبدال جميع الأسطر ذات النمط المحدد بنمط آخر.", "Seçili stildeki tüm satırları başka bir stille değiştirin."))
                StyleDropdown(tr("From Style", "النمط الحالي", "Mevcut Stil"), distinct, from) { from = it }
                StyleDropdown(tr("To Style", "النمط الجديد", "Yeni Stil"), distinct, to) { to = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(from, to) }, enabled = from.isNotEmpty()) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StyleDropdown(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                IconButton(onClick = { expanded = true }) { Text("▾") }
            },
        )
        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { name ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(name) },
                    onClick = { onSelect(name); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SortDialog(
    onDismiss: () -> Unit,
    onApply: (SortKey, SortOrder) -> Unit,
) {
    var key by remember { mutableStateOf(SortKey.START) }
    var descending by remember { mutableStateOf(false) }
    val keys = listOf(
        SortKey.START to tr("Start Time", "وقت البداية", "Başlangıç Zamanı"),
        SortKey.END to tr("End Time", "وقت النهاية", "Bitiş Zamanı"),
        SortKey.STYLE to tr("Style", "النمط", "Stil"),
        SortKey.ACTOR to tr("Actor", "الممثل", "Oyuncu"),
        SortKey.EFFECT to tr("Effect", "التأثير", "Efekt"),
        SortKey.TEXT to tr("Text", "النص", "Metin"),
        SortKey.LAYER to tr("Layer", "الطبقة", "Katman"),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_sort)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Sort By", "ترتيب الأسطر حسب", "Sıralama Ölçütü"), style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    keys.forEach { (k, label) ->
                        FilterChip(selected = key == k, onClick = { key = k }, label = { Text(label) })
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilterChip(
                        selected = !descending,
                        onClick = { descending = false },
                        label = { Text(tr("Ascending", "تصاعدي", "Artan")) },
                    )
                    FilterChip(
                        selected = descending,
                        onClick = { descending = true },
                        label = { Text(tr("Descending", "تنازلي", "Azalan")) },
                    )
                }
                Text(
                    tr("Equal items keep relative order (stable sort). Undoable.", "تحتفظ العناصر المتساوية بترتيبها (ترتيب مستقر). قابل للتراجع.", "Eşit öğeler göreli sırayı korur."),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(key, if (descending) SortOrder.DESCENDING else SortOrder.ASCENDING)
            }) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun FramerateDialog(
    onDismiss: () -> Unit,
    onApply: (fromFps: Double, toFps: Double) -> Unit,
) {
    val presets = FramerateConverter.PRESETS
    var from by remember { mutableStateOf(presets.first().second) }
    var to by remember { mutableStateOf(presets[2].second) }
    val ratio = to / from
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_framerate)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Source FPS", "معدل إطارات المصدر", "Kaynak FPS"), style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    presets.forEach { (label, value) ->
                        FilterChip(selected = from == value, onClick = { from = value }, label = { Text(label) })
                    }
                }
                Text(tr("Target FPS", "معدل إطارات الهدف", "Hedef FPS"), style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    presets.forEach { (label, value) ->
                        FilterChip(selected = to == value, onClick = { to = value }, label = { Text(label) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(from, to) }, enabled = from > 0 && to > 0) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PropertiesSheet(
    info: ImmutableList<AssInfo>,
    onDismiss: () -> Unit,
    onApply: (Map<String, String>) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var title by remember { mutableStateOf(ScriptInfoOps.get(info, "Title") ?: "") }
    var resX by remember { mutableStateOf(ScriptInfoOps.get(info, "PlayResX") ?: "") }
    var resY by remember { mutableStateOf(ScriptInfoOps.get(info, "PlayResY") ?: "") }
    var wrap by remember { mutableStateOf(ScriptInfoOps.get(info, "WrapStyle") ?: "0") }
    var sbs by remember { mutableStateOf(ScriptInfoOps.get(info, "ScaledBorderAndShadow") ?: "yes") }
    var collisions by remember { mutableStateOf(ScriptInfoOps.get(info, "Collisions") ?: "Normal") }
    var timer by remember { mutableStateOf(ScriptInfoOps.get(info, "Timer") ?: "100") }
    val wrapOptions = listOf(
        "0" to tr("Smart wrap (top)", "التفاف ذكي (أعلى)", "Akıllı kaydırma (üst)"),
        "1" to tr("End-of-line wrap", "التفاف عند نهاية السطر", "Satır sonu kaydırma"),
        "2" to tr("No wrap", "بدون التفاف", "Kaydırma yok"),
        "3" to tr("Smart wrap (bottom)", "التفاف ذكي (أسفل)", "Akıllı kaydırma (alt)"),
    )
    val authorFields = listOf(
        "Script" to tr("Original Script", "السكربت الأصلي", "Orijinal Komut Dosyası"),
        "Translation" to tr("Translation", "الترجمة", "Çeviri"),
        "Editing" to tr("Editing", "التدقيق والتعديل", "Düzenleme"),
        "Timing" to tr("Timing", "التوقيت", "Zamanlama"),
        "Synch Point" to tr("Synch Point", "نقطة التزامن", "Senkron Noktası"),
        "Updated By" to tr("Updated By", "تم التحديث بواسطة", "Güncelleyen"),
        "YCbCr Matrix" to "YCbCr Matrix",
    )
    val authorValues = remember(info) {
        authorFields.map { (k, _) -> mutableStateOf(ScriptInfoOps.get(info, k) ?: "") }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.tool_properties), style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(tr("Title", "عنوان الترجمة", "Başlık")) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = resX,
                    onValueChange = { resX = it.filter { ch -> ch.isDigit() } },
                    label = { Text("PlayResX") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = resY,
                    onValueChange = { resY = it.filter { ch -> ch.isDigit() } },
                    label = { Text("PlayResY") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Text(tr("WrapStyle", "نمط الالتفاف (WrapStyle)", "Satır Kaydırma Stili"), style = MaterialTheme.typography.labelLarge)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                wrapOptions.forEach { (v, label) ->
                    FilterChip(selected = wrap == v, onClick = { wrap = v }, label = { Text(label) })
                }
            }
            Text(tr("Scale Border & Shadow", "تحجيم الحدود والظل (ScaledBorderAndShadow)", "Kenarlık ve Gölge Ölçekle"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = sbs == "yes", onClick = { sbs = "yes" }, label = { Text(tr("Yes", "نعم", "Evet")) })
                FilterChip(selected = sbs == "no", onClick = { sbs = "no" }, label = { Text(tr("No", "لا", "Hayır")) })
            }
            Text(tr("Collisions", "تصادم الأسطر (Collisions)", "Çakışmalar"), style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = collisions == "Normal", onClick = { collisions = "Normal" }, label = { Text(tr("Normal (stack up)", "عادي (تراكم لأعلى)", "Normal (yukarı yığ)")) })
                FilterChip(selected = collisions == "Reverse", onClick = { collisions = "Reverse" }, label = { Text(tr("Reverse (stack down)", "عكسي (تراكم لأسفل)", "Ters (aşağı yığ)")) })
            }
            OutlinedTextField(
                value = timer,
                onValueChange = { timer = it.filter { ch -> ch.isDigit() || ch == '.' } },
                label = { Text(tr("Timer (100 = 1.0x)", "معدل السرعة (100 = عادي)", "Zamanlayıcı (100 = normal)")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider()
            Text(tr("Metadata", "معلومات إضافية", "Meta Veriler"), style = MaterialTheme.typography.labelLarge)
            authorFields.forEachIndexed { i, (_, label) ->
                OutlinedTextField(
                    value = authorValues[i].value,
                    onValueChange = { authorValues[i].value = it },
                    label = { Text(label) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                Button(onClick = {
                    val changes = buildMap {
                        if (title.isNotBlank()) put("Title", title)
                        if (resX.isNotBlank()) put("PlayResX", resX)
                        if (resY.isNotBlank()) put("PlayResY", resY)
                        put("WrapStyle", wrap)
                        put("ScaledBorderAndShadow", sbs)
                        put("Collisions", collisions)
                        if (timer.isNotBlank()) put("Timer", timer)
                        authorFields.forEachIndexed { i, (key, _) ->
                            val v = authorValues[i].value
                            if (v.isNotBlank()) put(key, v)
                        }
                    }
                    onApply(changes)
                }) { Text(stringResource(R.string.common_apply)) }
            }
        }
    }
}

@Composable
private fun PasteOverDialog(
    targetCount: Int,
    onDismiss: () -> Unit,
    onApply: (text: String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_paste_over)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.paste_over_hint) + " ($targetCount)",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    maxLines = 8,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(text) }) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun ExportFormatDialog(
    onDismiss: () -> Unit,
    onPick: (SubtitleFormat) -> Unit,
) {
    val formats = listOf(
        Triple(AssFormat, "ASS", tr("Full styling and tags, standard ASS", "تنسيقات ووسوم كاملة، صيغة ASS القياسية", "Tam stil ve etiketler, standart ASS")),
        Triple(SrtFormat, "SRT", tr("Plain text subtitles, stripped tags", "نصوص مجردة، بدون وسوم أو تنسيقات", "Düz metin altyazılar")),
        Triple(VttFormat, "WebVTT (VTT)", tr("HTML5 web video subtitles", "ترجمة خاصة بمشغلات الويب وفيديوهات HTML5", "Web video altyazıları")),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_export_format)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                formats.forEach { (fmt, label, desc) ->
                    ListItem(
                        headlineContent = { Text(label) },
                        supportingContent = { Text(desc, style = MaterialTheme.typography.bodySmall) },
                        modifier = Modifier.clickable { onPick(fmt) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun ResolutionResampleDialog(
    fromW: Int,
    fromH: Int,
    onDismiss: () -> Unit,
    onApply: (toW: Int, toH: Int, scalePositions: Boolean, scaleBorders: Boolean) -> Unit,
) {
    val presets = listOf(
        "384×288" to (384 to 288),
        "640×480" to (640 to 480),
        "1280×720" to (1280 to 720),
        "1920×1080" to (1920 to 1080),
        "3840×2160" to (3840 to 2160),
    )
    var toW by remember { mutableStateOf((fromW * 2).toString()) }
    var toH by remember { mutableStateOf((fromH * 2).toString()) }
    var scalePos by remember { mutableStateOf(true) }
    var scaleBorders by remember { mutableStateOf(true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_resample)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(tr("Source resolution: $fromW × $fromH", "دقة المصدر: $fromW × $fromH", "Kaynak çözünürlük: $fromW × $fromH"), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = toW,
                        onValueChange = { toW = it.filter { ch -> ch.isDigit() } },
                        label = { Text(tr("Target W", "العرض المستهدف", "Hedef Genişlik")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = toH,
                        onValueChange = { toH = it.filter { ch -> ch.isDigit() } },
                        label = { Text(tr("Target H", "الارتفاع المستهدف", "Hedef Yükseklik")) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    presets.forEach { (label, res) ->
                        FilterChip(
                            selected = toW == res.first.toString() && toH == res.second.toString(),
                            onClick = { toW = res.first.toString(); toH = res.second.toString() },
                            label = { Text(label) },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = scalePos, onCheckedChange = { scalePos = it })
                    Text(tr("Scale \\pos/\\move", "تحجيم \\pos/\\move", "\\pos/\\move ölçekle"))
                    Checkbox(checked = scaleBorders, onCheckedChange = { scaleBorders = it })
                    Text(tr("Scale Outline/Shadow", "تحجيم الحدود والظل", "Kenarlık/Gölge ölçekle"))
                }
            }
        },
        confirmButton = {
            val w = toW.toIntOrNull() ?: 0
            val h = toH.toIntOrNull() ?: 0
            TextButton(onClick = { onApply(w, h, scalePos, scaleBorders) }, enabled = w > 0 && h > 0) {
                Text(stringResource(R.string.common_apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun TimingPostProcessDialog(
    onDismiss: () -> Unit,
    onApply: (leadInMs: Long, leadOutMs: Long, gapMs: Long) -> Unit,
) {
    var leadIn by remember { mutableStateOf("100") }
    var leadOut by remember { mutableStateOf("100") }
    var gap by remember { mutableStateOf("200") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_timing_pp)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = leadIn,
                    onValueChange = { leadIn = it.filter { ch -> ch.isDigit() } },
                    label = { Text(tr("Lead-in (ms)", "تقديم البداية Lead-in (ms)", "Başlangıç payı (ms)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = leadOut,
                    onValueChange = { leadOut = it.filter { ch -> ch.isDigit() } },
                    label = { Text(tr("Lead-out (ms)", "تمديد النهاية Lead-out (ms)", "Bitiş payı (ms)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = gap,
                    onValueChange = { gap = it.filter { ch -> ch.isDigit() } },
                    label = { Text(tr("Min gap (ms)", "الحد الأدنى للفراغ (ms)", "Minimum boşluk (ms)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApply(leadIn.toLongOrNull() ?: 0L, leadOut.toLongOrNull() ?: 0L, gap.toLongOrNull() ?: 0L)
            }) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun KaraokeDialog(
    onDismiss: () -> Unit,
    onApply: (KaraokeMode, useKf: Boolean) -> Unit,
) {
    var mode by remember { mutableStateOf(KaraokeMode.BY_WORD) }
    var useKf by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_karaoke)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(tr("Split Mode", "نمط التقطيع", "Bölme Modu"), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = mode == KaraokeMode.BY_WORD,
                        onClick = { mode = KaraokeMode.BY_WORD },
                        label = { Text(tr("By Word (Space)", "بالكلمة (المسافات)", "Kelimeye Göre")) },
                    )
                    FilterChip(
                        selected = mode == KaraokeMode.BY_CHAR,
                        onClick = { mode = KaraokeMode.BY_CHAR },
                        label = { Text(tr("By Char", "بالحرف", "Harfe Göre")) },
                    )
                }
                Text(tr("Fill Type", "نوع التعبئة", "Doldurma Türü"), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = !useKf, onClick = { useKf = false }, label = { Text(tr("{\\k} Step-by-step", "{\\k} خطوة بخطوة", "{\\k} Adım Adım")) })
                    FilterChip(selected = useKf, onClick = { useKf = true }, label = { Text(tr("{\\kf} Smooth", "{\\kf} متصل ناعم", "{\\kf} Akıcı")) })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(mode, useKf) }) { Text(stringResource(R.string.common_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(
    snapshots: List<io.github.samgum.aegisub.data.repository.Snapshot>,
    onDismiss: () -> Unit,
    onSaveSnapshot: (label: String) -> Unit,
    onRestore: (id: Long) -> Unit,
    onDelete: (id: Long) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var label by remember { mutableStateOf("Snapshot") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.tool_history), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(tr("Snapshot Note", "ملاحظة النسخة", "Anlık Görüntü Notu")) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onSaveSnapshot(label.ifBlank { "Snapshot" }) }) { Text(tr("Save", "حفظ الحالي", "Kaydet")) }
            }
            HorizontalDivider()
            if (snapshots.isEmpty()) {
                Text(tr("No snapshots yet. Save current script to restore it anytime.", "لا توجد نسخ سابقة. احفظ المشروع كنسخة احتياطية للرجوع إليها في أي وقت.", "Henüz anlık görüntü yok."), style = MaterialTheme.typography.bodySmall)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth()) {
                    items(snapshots.size) { i ->
                        val s = snapshots[i]
                        ListItem(
                            headlineContent = { Text(s.label.ifBlank { "(No note)" }) },
                            supportingContent = { Text(formatTimestamp(s.createdAt), style = MaterialTheme.typography.bodySmall) },
                            trailingContent = {
                                        Row {
                                            TextButton(onClick = { onRestore(s.id) }) { Text(tr("Restore", "استعادة", "Geri Yükle")) }
                                            TextButton(onClick = { onDelete(s.id) }) { Text(stringResource(R.string.common_delete)) }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

private fun formatTimestamp(ms: Long): String {
    if (ms <= 0L) return "—"
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(ms))
}
