package io.github.samgum.aegisub.ui.home

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.samgum.aegisub.data.mkv.MkvExtractor
import io.github.samgum.aegisub.data.repository.Project
import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.domain.format.AssFormat
import io.github.samgum.aegisub.domain.format.SubtitleImport
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.time.SubTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: ProjectRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    val projects: StateFlow<List<Project>> = repo.observeProjects()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun createSampleProject() {
        viewModelScope.launch {
            val sample = AssScript.default().withEvents(
                listOf(
                    AssEvent(
                        start = SubTime.ofMillis(1_000),
                        end = SubTime.ofMillis(3_000),
                        text = "Welcome to Aegisub Android",
                    ),
                    AssEvent(
                        start = SubTime.ofMillis(3_200),
                        end = SubTime.ofMillis(6_000),
                        text = "Tap a subtitle line to edit",
                    ),
                    AssEvent(
                        comment = true,
                        text = "This is a comment line example",
                    ),
                ),
            )
            val content = AssFormat.write(sample)
            val now = System.currentTimeMillis()
            repo.createProject(name = "Project $now", format = "ass", content = content)
        }
    }

    fun importSubtitle(fileName: String, content: String) {
        viewModelScope.launch {
            val resolved = SubtitleImport.resolve(fileName, content)
            repo.createProject(name = resolved.name, format = resolved.format, content = content)
        }
    }

    /** استيراد فيديو MKV واستخراج الترجمة وربط الفيديو بالمعاينة فوراً */
    fun importMkvVideo(name: String, uri: Uri, trackIndex: Int?) {
        viewModelScope.launch {
            val (format, content) = if (trackIndex != null) {
                MkvExtractor.extractSubtitleContent(context, uri, trackIndex)
            } else {
                "ass" to MkvExtractor.defaultAssScript()
            }
            val cleanName = name.substringBeforeLast('.')
            val projectId = repo.createProject(name = cleanName, format = format, content = content)
            repo.setMediaUri(projectId, uri.toString())
        }
    }
}
