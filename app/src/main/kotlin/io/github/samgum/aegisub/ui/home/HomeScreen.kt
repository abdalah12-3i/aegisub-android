package io.github.samgum.aegisub.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.samgum.aegisub.R
import io.github.samgum.aegisub.data.mkv.MkvExtractor
import io.github.samgum.aegisub.data.mkv.MkvSubtitleTrack
import io.github.samgum.aegisub.data.repository.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
fun HomeScreen(
    onOpenProject: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingMkvUri by remember { mutableStateOf<Uri?>(null) }
    var pendingMkvName by remember { mutableStateOf("") }
    var mkvTracks by remember { mutableStateOf<List<MkvSubtitleTrack>>(emptyList()) }
    var showTrackPicker by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val fileName = getFileName(context, uri)
                val isMkv = fileName.endsWith(".mkv", ignoreCase = true)

                if (isMkv) {
                    try {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    } catch (_: Exception) {}

                    val tracks = withContext(Dispatchers.IO) {
                        MkvExtractor.getSubtitleTracks(context, uri)
                    }

                    if (tracks.isEmpty()) {
                        viewModel.importMkvVideo(fileName, uri, null)
                    } else if (tracks.size == 1) {
                        viewModel.importMkvVideo(fileName, uri, tracks[0].trackIndex)
                    } else {
                        pendingMkvUri = uri
                        pendingMkvName = fileName
                        mkvTracks = tracks
                        showTrackPicker = true
                    }
                } else {
                    val (name, content) = readSubtitleFile(context, uri)
                    viewModel.importSubtitle(name, content)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.home_settings))
                    }
                    TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) {
                        Text(stringResource(R.string.home_import))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.createSampleProject() }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { padding ->
        if (projects.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.home_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(projects, key = { it.id }) { project ->
                    ProjectRow(project, onClick = { onOpenProject(project.id) })
                }
            }
        }
    }

    // نافذة اختيار مسار الترجمة عند استيراد فيديو MKV يحتوي على ترجمات متعددة
    if (showTrackPicker && pendingMkvUri != null) {
        AlertDialog(
            onDismissRequest = { showTrackPicker = false },
            title = { Text(tr("Select Subtitle Track", "اختر مسار الترجمة", "Altyazı İzi Seçin")) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        tr(
                            "This MKV video contains multiple subtitle tracks. Choose one to import:",
                            "يحتوي هذا الفيديو على عدة مسارات ترجمة. اختر مساراً لفتحه في المحرر:",
                            "Bu MKV videosu birden fazla altyazı izi içeriyor. İçe aktarmak için birini seçin:"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(mkvTracks) { track ->
                            ListItem(
                                headlineContent = { Text(track.title) },
                                supportingContent = { Text(track.language) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.importMkvVideo(pendingMkvName, pendingMkvUri!!, track.trackIndex)
                                        showTrackPicker = false
                                    },
                            )
                        }
                        item {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        tr(
                                            "Import video only (No subtitles)",
                                            "استيراد الفيديو فقط (بدون ترجمة)",
                                            "Yalnızca videoyu içe aktar (Altyazısız)"
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.importMkvVideo(pendingMkvName, pendingMkvUri!!, null)
                                        showTrackPicker = false
                                    },
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showTrackPicker = false }) {
                    Text(tr("Cancel", "إلغاء", "İptal"))
                }
            },
        )
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    return context.contentResolver
        .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: (uri.lastPathSegment ?: "video.mkv")
}

private suspend fun readSubtitleFile(context: Context, uri: Uri): Pair<String, String> =
    withContext(Dispatchers.IO) {
        val name = getFileName(context, uri)
        val content = context.contentResolver.openInputStream(uri)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: ""
        name to content
    }

@Composable
private fun ProjectRow(project: Project, onClick: () -> Unit) {
    val ts = project.lastOpenedAt ?: project.updatedAt
    val date = remember(ts) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
    }
    ListItem(
        headlineContent = {
            Text(project.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = { Text("${project.format.uppercase()} · $date") },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
