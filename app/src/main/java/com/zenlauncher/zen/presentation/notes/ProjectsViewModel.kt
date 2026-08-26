package com.zenlauncher.zen.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.domain.notes.NotesRepository
import com.zenlauncher.zen.domain.notes.Project
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Un proyecto con cuantas notas tiene, para la fila de la lista. */
data class ProjectRow(val project: Project, val noteCount: Int)

data class ProjectsUiState(
    val projects: List<ProjectRow> = emptyList(),
    val loading: Boolean = true,
) {
    val empty: Boolean get() = !loading && projects.isEmpty()
}

/**
 * Lista de proyectos.
 *
 * El conteo sale de las notas ya cargadas (`observeNotes`) y no de `notesInProject` por
 * proyecto: una consulta por fila no reemitiria cuando una nota cambia de proyecto, y
 * aqui ya se tiene la lista entera en memoria.
 */
class ProjectsViewModel(private val notes: NotesRepository) : ViewModel() {

    val state: StateFlow<ProjectsUiState> = combine(
        notes.observeProjects(),
        notes.observeNotes(),
    ) { projects, all ->
        val counts = all.groupingBy { it.projectId }.eachCount()
        ProjectsUiState(
            projects = projects
                .sortedByDescending { it.createdAtMillis }
                .map { ProjectRow(it, counts[it.id] ?: 0) },
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ProjectsUiState(),
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
