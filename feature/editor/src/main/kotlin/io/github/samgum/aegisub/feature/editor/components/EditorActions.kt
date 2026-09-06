package io.github.samgum.aegisub.feature.editor.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable

@Composable
fun EditorActions(
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    IconButton(onClick = onUndo, enabled = canUndo) {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Undo")
    }
    IconButton(onClick = onRedo, enabled = canRedo) {
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Redo")
    }
}
