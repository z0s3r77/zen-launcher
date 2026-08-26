package com.zenlauncher.zen.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.domain.notes.NoteStage
import com.zenlauncher.zen.domain.notes.NotesRepository
import com.zenlauncher.zen.domain.notes.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProjectDetailUiState(
    val project: Project? = null,
    val notes: List<Note> = emptyList(),
    val loading: Boolean = true,
)

/**
 * Un proyecto: sus notas y el botón para darlo por terminado.
 *
 * El id llega por [open], igual que [NoteDetailViewModel]: un solo argumento no
 * justifica montar `SavedStateHandle`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ProjectDetailViewModel(
    private val notes: NotesRepository,
    /** Marcar terminado no puede morir con la pantalla: ver [QuickNoteViewModel]. */
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val projectId = MutableStateFlow<String?>(null)

    val state: StateFlow<ProjectDetailUiState> = projectId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(ProjectDetailUiState())
            } else {
                combine(notes.observeProjects(), notes.observeNotes()) { projects, all ->
                    ProjectDetailUiState(
                        project = projects.firstOrNull { it.id == id },
                        notes = all.filter { it.projectId == id }.sortedByDescending { it.createdAtMillis },
                        loading = false,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = ProjectDetailUiState(),
        )

    fun open(id: String) {
        projectId.value = id
    }

    /**
     * Termina el proyecto y avanza todas sus notas a [NoteStage.DONE].
     *
     * Explicito aqui, nota a nota, no un disparador oculto en el repositorio: decision
     * del plan, misma razon que [DevelopIdeaViewModel.convertToProject].
     */
    fun markDone() {
        val id = projectId.value ?: return
        appScope.launch {
            val project = notes.observeProjects().first().firstOrNull { it.id == id } ?: return@launch
            notes.saveProject(project.copy(done = true))
            notes.notesInProject(id).forEach { note ->
                notes.save(note.copy(stage = NoteStage.DONE))
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
