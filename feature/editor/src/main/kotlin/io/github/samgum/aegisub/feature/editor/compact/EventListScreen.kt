package io.github.samgum.aegisub.feature.editor.compact

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.collections.immutable.ImmutableList
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.feature.editor.R
import io.github.samgum.aegisub.feature.editor.components.EventRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    events: ImmutableList<AssEvent>,
    onEventClick: (AssEvent) -> Unit,
    onBack: () -> Unit,
    title: String = "Subtitles",
    selectionMode: Boolean = false,
    selectedIds: Set<Long> = emptySet(),
    onToggleSelect: (Long) -> Unit = {},
    onEnterSelection: (Long) -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionMode) stringResource(R.string.selected_count, selectedIds.size) else title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_cancel))
                    }
                },
                actions = actions,
            )
        },
    ) { padding ->
        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No subtitles yet",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                itemsIndexed(events, key = { _, it -> it.id }) { index, event ->
                    EventRow(
                        event = event,
                        index = index,
                        onClick = { onEventClick(event) },
                        selectionMode = selectionMode,
                        isSelected = event.id in selectedIds,
                        onToggleSelect = { onToggleSelect(event.id) },
                        onLongClick = { onEnterSelection(event.id) },
                    )
                }
            }
        }
    }
}
