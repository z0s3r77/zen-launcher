package com.zenlauncher.zen.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.domain.notes.AttachmentStore
import com.zenlauncher.zen.domain.notes.LinkState
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.domain.notes.NoteLink
import com.zenlauncher.zen.domain.notes.NotesRepository
import com.zenlauncher.zen.domain.notes.Project
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class NoteDetailUiState(
    val note: Note? = null,
    /**
     * Rutas absolutas de las imagenes, ya resueltas.
     *
     * La pantalla no sabe donde viven los ficheros: si manana se guardan en otro sitio,
     * cambia el almacen y no la interfaz.
     */
    val imagePaths: List<String> = emptyList(),
    /** Conexiones que el usuario ya acepto. */
    val connections: List<ConnectedNote> = emptyList(),
    /** Propuestas del indice, esperando respuesta. */
    val suggestions: List<ConnectedNote> = emptyList(),
    /** Todos los proyectos, para el selector: "asignar a" o "nuevo proyecto". */
    val projects: List<Project> = emptyList(),
    val loading: Boolean = true,
) {
    /** El proyecto de esta nota, ya resuelto. Null si no tiene o si no se ha cargado. */
    val currentProject: Project? get() = projects.firstOrNull { it.id == note?.projectId }
}

/** La otra nota de una conexion, ya resuelta, con el enlace que las une. */
data class ConnectedNote(
    val note: Note,
    val link: NoteLink,
)

/**
 * Una nota.
 *
 * El id llega por [open] y no por `SavedStateHandle`: en Zen ningun ViewModel usa
 * estado guardado todavia, y montar esa via para un solo argumento anadiria una pieza
 * mas al contenedor manual sin resolver nada que este flujo no resuelva.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NoteDetailViewModel(
    private val notes: NotesRepository,
    private val attachments: AttachmentStore,
    private val clock: ZenClock,
    /** Borrar no puede morir con la pantalla: ver [QuickNoteViewModel]. */
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val noteId = MutableStateFlow<String?>(null)

    val state: StateFlow<NoteDetailUiState> = noteId
        .flatMapLatest { id ->
            if (id == null) {
                flowOf(NoteDetailUiState())
            } else {
                combine(
                    notes.observeNote(id),
                    notes.observeLinks(id),
                    notes.observeNotes(),
                    notes.observeProjects(),
                ) { note, links, all, projects ->
                    val byId = all.associateBy { it.id }
                    // El enlace no tiene direccion: la "otra" nota es la que no es esta.
                    // Sin resolverlo asi, la mitad de las conexiones se ensenarian a si
                    // mismas, segun por donde se hubieran creado.
                    val resolved = links.mapNotNull { link ->
                        val otherId = if (link.fromNoteId == id) link.toNoteId else link.fromNoteId
                        byId[otherId]?.let { ConnectedNote(it, link) }
                    }
                    NoteDetailUiState(
                        note = note,
                        imagePaths = note?.images.orEmpty()
                            .map { attachments.absolutePath(it.value) },
                        connections = resolved.filter { it.link.state == LinkState.ACCEPTED },
                        suggestions = resolved.filter { it.link.state == LinkState.PENDING },
                        projects = projects,
                        loading = false,
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = NoteDetailUiState(),
        )

    fun open(id: String) {
        noteId.value = id
    }

    /**
     * El usuario acepta la conexion: pasa a ser parte de la nota.
     *
     * Se reescribe el enlace entero con el estado nuevo. `MANUAL` no: la propuso el
     * indice y eso sigue siendo verdad; lo que cambia es que ahora esta aceptada.
     */
    fun accept(link: NoteLink) {
        appScope.launch {
            notes.putLink(link.copy(state = LinkState.ACCEPTED, createdAtMillis = clock.wallTimeMillis()))
        }
    }

    /**
     * El usuario la descarta. **Para siempre**: el indice se recalcula en cada visita y
     * sin dejar constancia volveria a proponer la misma pareja eternamente.
     */
    fun ignore(link: NoteLink) {
        appScope.launch {
            notes.putLink(link.copy(state = LinkState.IGNORED, createdAtMillis = clock.wallTimeMillis()))
        }
    }

    /** Mete la nota en un proyecto ya existente. Reusa lo que ya hay en el repositorio. */
    fun assignToProject(projectId: String) {
        val id = noteId.value ?: return
        appScope.launch { notes.assignToProject(id, projectId) }
    }

    /** Crea un proyecto nuevo con solo esta nota y la asigna. */
    fun createProjectAndAssign(title: String) {
        val id = noteId.value ?: return
        if (title.isBlank()) return
        appScope.launch {
            val projectId = UUID.randomUUID().toString()
            notes.saveProject(Project(id = projectId, title = title, createdAtMillis = clock.wallTimeMillis()))
            notes.assignToProject(id, projectId)
        }
    }

    /**
     * Borra la nota y sus imagenes.
     *
     * Las filas de adjuntos caen solas por `ON DELETE CASCADE`, pero **los ficheros no
     * los borra la base de datos**: sin esta llamada, borrar una nota con fotos dejaria
     * los JPEG ocupando sitio para siempre sin que nada los mencione.
     */
    fun delete() {
        val id = noteId.value ?: return
        appScope.launch {
            notes.delete(id)
            attachments.deleteFor(id)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
