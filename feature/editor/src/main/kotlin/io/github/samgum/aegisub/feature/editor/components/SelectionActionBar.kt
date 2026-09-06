package io.github.samgum.aegisub.feature.editor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.samgum.aegisub.feature.editor.R

@Composable
fun SelectionActionBar(
    count: Int,
    total: Int,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_cancel))
            }
            Text(
                stringResource(R.string.selected_count, count),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(end = 4.dp),
            )
            IconButton(onClick = onMoveUp) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = stringResource(R.string.action_move_up))
            }
            IconButton(onClick = onMoveDown) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = stringResource(R.string.action_move_down))
            }
            TextButton(onClick = onDuplicate) { Text(stringResource(R.string.action_duplicate)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.action_delete)) }
            TextButton(onClick = onSelectAll, enabled = count < total) { Text("Select All") }
        }
    }
}
