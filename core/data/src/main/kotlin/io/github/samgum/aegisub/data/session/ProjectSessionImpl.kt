package io.github.samgum.aegisub.data.session

import io.github.samgum.aegisub.data.repository.ProjectRepository
import io.github.samgum.aegisub.domain.format.AssFormat
import io.github.samgum.aegisub.domain.format.FormatRegistry
import io.github.samgum.aegisub.domain.model.AssEvent
import io.github.samgum.aegisub.domain.model.AssInfo
import io.github.samgum.aegisub.domain.model.AssScript
import io.github.samgum.aegisub.domain.model.AssStyle
import io.github.samgum.aegisub.domain.undo.SnapshotUndoStack
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Default implementation of [ProjectSession]: replicates edit, undo, and debounced autosave semantics.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal class ProjectSessionImpl(
    override val projectId: Long,
    private val repo: ProjectRepository,
) : ProjectSession {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var stack: SnapshotUndoStack<AssScript>? = null

    private val _script = MutableStateFlow<AssScript?>(null)
    override val script: StateFlow<AssScript?> = _script.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    override val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    override val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    init {
        // Debounced autosave: write changes back to Room after debounce
        scope.launch {
            _script
                .filterNotNull()
                .distinctUntilChanged()
                .drop(1)
                .debounce(AUTOSAVE_DEBOUNCE_MS)
                .collect { script ->
                    repo.updateContent(projectId, AssFormat.write(script), System.currentTimeMillis())
                }
        }
    }

    /** Triggers asynchronous loading. */
    fun start() {
        if (stack != null || _errorMessage.value != null) return
        scope.launch {
            try {
                val content = repo.getContent(projectId)
                val parsed = FormatRegistry.detect(content)?.read(content) ?: AssScript.default()
                val script = parsed.withEvents(parsed.events.mapIndexed { i, e -> e.copy(id = i.toLong()) })
                stack = SnapshotUndoStack(script)
                _script.value = script
            } catch (e: Exception) {
                _errorMessage.value = e.message ?: "Failed to load"
            }
        }
    }

    override fun editEvent(eventId: Long, transform: (AssEvent) -> AssEvent) {
        val s = stack ?: return
        val current = s.current
        val newEvents = current.events
            .map { if (it.id == eventId) transform(it) else it }
            .toPersistentList()
        commit(current.withEvents(newEvents))
    }

    override fun editAllEvents(transform: (AssEvent) -> AssEvent) {
        editEvents { events -> events.map(transform) }
    }

    override fun editEvents(transform: (List<AssEvent>) -> List<AssEvent>) {
        val s = stack ?: return
        val current = s.current
        val newEvents = transform(current.events).toPersistentList()
        commit(current.withEvents(newEvents))
    }

    override fun editStyles(transform: (List<AssStyle>) -> List<AssStyle>) {
        val s = stack ?: return
        val current = s.current
        val newStyles = transform(current.styles).toPersistentList()
        commit(current.withStyles(newStyles))
    }

    override fun editInfo(transform: (List<AssInfo>) -> List<AssInfo>) {
        val s = stack ?: return
        val current = s.current
        val newInfo = transform(current.info).toPersistentList()
        commit(current.withInfo(newInfo))
    }

    override fun editScript(transform: (AssScript) -> AssScript) {
        val s = stack ?: return
        commit(transform(s.current))
    }

    override fun restoreFromContent(content: String) {
        val s = stack ?: return
        try {
            val parsed = FormatRegistry.detect(content)?.read(content) ?: AssScript.default()
            val withIds = parsed.withEvents(parsed.events.mapIndexed { i, e -> e.copy(id = i.toLong()) })
            s.commit(withIds, "restore")
            _script.value = s.current
            syncFlags()
        } catch (e: Exception) {
            // Ignore parse errors, keep current script intact
        }
    }

    private fun commit(newScript: AssScript) {
        val s = stack ?: return
        s.commit(newScript, "edit")
        _script.value = s.current
        syncFlags()
    }

    override fun undo() {
        val s = stack ?: return
        s.undo()?.let {
            _script.value = it
            syncFlags()
        }
    }

    override fun redo() {
        val s = stack ?: return
        s.redo()?.let {
            _script.value = it
            syncFlags()
        }
    }

    private fun syncFlags() {
        val s = stack
        _canUndo.value = s?.canUndo ?: false
        _canRedo.value = s?.canRedo ?: false
    }

    private companion object {
        const val AUTOSAVE_DEBOUNCE_MS = 800L
    }
}
