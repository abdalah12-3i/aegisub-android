package io.github.samgum.aegisub.feature.editor.expanded

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import kotlinx.collections.immutable.toPersistentList
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import io.github.samgum.aegisub.feature.editor.R
import io.github.samgum.aegisub.feature.editor.components.EditorActions
import io.github.samgum.aegisub.feature.editor.components.EventRow
import io.github.samgum.aegisub.feature.editor.components.LineAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTwoPane(
    script: AssScript,
    editingId: Long?,
    onEventClick: (AssEvent) -> Unit,
    onBack: () -> Unit,
    onOpenPreview: () -> Unit,
    onExport: () -> Unit,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onTextChanged: (eventId: Long, text: String) -> Unit,
    onTimesChanged: (eventId: Long, start: SubTime, end: SubTime) -> Unit,
    onStyleChanged: (eventId: Long, style: String) -> Unit,
    onLayerChanged: (eventId: Long, layer: Int) -> Unit,
    onLineAction: (eventId: Long, LineAction) -> Unit,
    selectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onToggleSelect: (Long) -> Unit = {},
    onEnterSelection: (Long) -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.editor_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_cancel))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenPreview) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.common_preview))
                    }
                    TextButton(onClick = onExport) { Text(stringResource(R.string.common_export)) }
                    EditorActions(canUndo, canRedo, onUndo, onRedo)
                },
            )
        },
    ) { padding ->
        Row(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(modifier = Modifier.weight(0.4f)) {
                itemsIndexed(script.events, key = { _, it -> it.id }) { index, ev ->
                    EventRow(
                        event = ev,
                        index = index,
                        onClick = { onEventClick(ev) },
                        selectionMode = selectionMode,
                        isSelected = ev.id in selectedIds,
                        onToggleSelect = { onToggleSelect(ev.id) },
                        onLongClick = { onEnterSelection(ev.id) },
                    )
                }
            }
            val selected = script.events.firstOrNull { it.id == editingId }
            if (selected != null) {
                EventDetail(
                    event = selected,
                    styles = script.styles.map { it.name }.toPersistentList(),
                    onTextChanged = { onTextChanged(selected.id, it) },
                    onTimesChanged = { s, e -> onTimesChanged(selected.id, s, e) },
                    onStyleChanged = { onStyleChanged(selected.id, it) },
                    onLayerChanged = { onLayerChanged(selected.id, it) },
                    onLineAction = { action -> onLineAction(selected.id, action) },
                    modifier = Modifier.weight(0.6f),
                )
            } else {
                Box(
                    modifier = Modifier.weight(0.6f).fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Select a subtitle line on the left to edit",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}
