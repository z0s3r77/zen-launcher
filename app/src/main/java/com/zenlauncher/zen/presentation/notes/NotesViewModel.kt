package com.zenlauncher.zen.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.domain.notes.NoteIndexer
import com.zenlauncher.zen.domain.notes.NoteLink
import com.zenlauncher.zen.domain.notes.NoteStage
import com.zenlauncher.zen.domain.notes.NotesRepository
import com.zenlauncher.zen.domain.notes.Project
import com.zenlauncher.zen.domain.notes.RecurringCluster
import com.zenlauncher.zen.domain.notes.RecurringThemes
import com.zenlauncher.zen.domain.notes.RecurringWord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class NotesUiState(
    val query: String = "",
    val notes: List<Note> = emptyList(),
    /**
     * Encontradas por significado y **no** por las palabras escritas.
     *
     * Van aparte y con su propio rotulo: lo que contiene lo que buscaste y lo que se
     * parece a lo que buscaste son dos cosas, y mezclarlas haria dudar de si el
     * buscador entiende lo que se le pide.
     */
    val related: List<Note> = emptyList(),
    /** Notas con alguna conexion propuesta sin responder. */
    val withSuggestions: List<Note> = emptyList(),
    /**
     * Raices a las que el usuario vuelve una y otra vez, en notas distintas.
     *
     * Nunca una afirmacion sobre el usuario, solo un dato: cuantas notas comparten esa
     * raiz. Buscando no aparece, igual que [withSuggestions]: quien busca esta a otra
     * cosa.
     */
    val patterns: List<RecurringWord> = emptyList(),
    /**
     * Grupos de notas conectadas entre si que todavia no son un proyecto.
     *
     * Un clúster ya contenido entero en un proyecto existente no vuelve a proponerse.
     */
    val projectSuggestions: List<RecurringCluster> = emptyList(),
    /** Si hay al menos un proyecto. La fila "Proyectos" solo existe si esto es cierto. */
    val hasProjects: Boolean = false,
    /** Cuantas hay en total, se este filtrando o no. Es lo que va en la cabecera. */
    val total: Int = 0,
    val loading: Boolean = true,
) {
    val searching: Boolean get() = query.isNotBlank()

    /** Sin notas y sin busqueda: es una biblioteca vacia, no un filtro sin resultados. */
    val empty: Boolean get() = !loading && total == 0

    /** Se busco algo y no hay nada, ni por palabra ni por significado. */
    val noResults: Boolean
        get() = !loading && searching && notes.isEmpty() && related.isEmpty()
}

/**
 * La pantalla de Notas: capturar, buscar y recuperar.
 *
 * No hay orden por relevancia ni secciones plegables: la lista es cronologica y punto.
 * Lo que ordena por parecido es el indice semantico, y sale rotulado como tal.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModel(
    private val notes: NotesRepository,
    private val indexer: NoteIndexer,
    private val clock: ZenClock,
    /** Aceptar una sugerencia de proyecto no puede morir con la pantalla: ver [QuickNoteViewModel]. */
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _query = MutableStateFlow("")

    /**
     * Clústers descartados en esta sesion de la aplicacion.
     *
     * Deliberadamente **no persistido**, a diferencia de las parejas ignoradas del
     * indice (`ignoredPairs`, baratas de recalcular y por eso si se guardan): una tabla
     * nueva solo para "clústers ignorados para siempre" es coste para un caso de uso
     * secundario. Si en el uso real molesta, se anade despues.
     */
    private val _ignoredClusters = MutableStateFlow<Set<Set<String>>>(emptySet())

    /**
     * Lo que hay escrito en el buscador, **sin pasar por el filtro**.
     *
     * El campo tiene que leer de aqui y no de [NotesUiState.query]. Ese otro viaja por
     * `mapLatest` y una consulta a SQLite, asi que llega tarde: mientras vuelve, el
     * campo seguia ensenando el texto anterior y la siguiente tecla se aplicaba encima
     * de un valor viejo. Comprobado en el dispositivo, escribiendo despacio: teclear
     * "aburri" dejaba "buar" —letras perdidas y cambiadas de sitio—. Esto se actualiza
     * en la misma llamada, asi que no puede quedarse atras.
     */
    val query: StateFlow<String> = _query.asStateFlow()

    init {
        // Indexar lo que falte, al entrar y en segundo plano. Va en [appScope] y no en
        // el del ViewModel porque salir de Notas a mitad de una tanda dejaria notas sin
        // vector: se encontrarian por palabra pero no tendrian conexiones nunca, y nada
        // volveria a intentarlo hasta la siguiente visita.
        appScope.launch { indexer.sync() }
    }

    val state: StateFlow<NotesUiState> = combine(
        notes.observeNotes(),
        notes.observePendingLinks(),
        notes.observeAcceptedLinks(),
        _query,
        _ignoredClusters,
    ) { all, pending, accepted, currentQuery, ignored ->
        NotesSources(all, pending, accepted, currentQuery, ignored)
    }
        // Los proyectos se combinan aparte: kotlinx.coroutines.flow.combine tiene
        // sobrecargas tipadas hasta cinco flujos, y ya estan ocupados arriba.
        .combine(notes.observeProjects()) { sources, projects -> sources to projects }
        // El filtro literal lo resuelve el almacen, no una copia del criterio aqui: si
        // la pantalla y la base de datos separaran las palabras de forma distinta, una
        // nota podria encontrarse escribiendo y no aparecer en su propia lista.
        // `mapLatest` cancela la busqueda anterior en cuanto se teclea otra letra.
        .mapLatest { (sources, projects) ->
            val (all, pending, accepted, currentQuery, ignored) = sources
            val literal = if (currentQuery.isBlank()) all else notes.search(currentQuery)
            NotesUiState(
                query = currentQuery,
                notes = literal,
                related = if (currentQuery.isBlank()) {
                    emptyList()
                } else {
                    semanticExtras(currentQuery, exclude = literal.mapTo(mutableSetOf()) { it.id }, all = all)
                },
                withSuggestions = notesWithSuggestions(pending, all, currentQuery),
                patterns = if (currentQuery.isBlank()) RecurringThemes.words(all) else emptyList(),
                projectSuggestions = if (currentQuery.isBlank()) {
                    projectSuggestions(all, accepted, ignored)
                } else {
                    emptyList()
                },
                hasProjects = projects.isNotEmpty(),
                total = all.size,
                loading = false,
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
            initialValue = NotesUiState(),
        )

    /**
     * Lo que se parece a la busqueda y **no** salio ya por palabra.
     *
     * Repetir una nota en las dos listas la haria parecer dos notas distintas.
     */
    private suspend fun semanticExtras(
        query: String,
        exclude: Set<String>,
        all: List<Note>,
    ): List<Note> {
        val byId = all.associateBy { it.id }
        return indexer.similarTo(query)
            .asSequence()
            .filter { it.noteId !in exclude }
            .mapNotNull { byId[it.noteId] }
            .toList()
    }

    /**
     * Las notas que tienen algo esperando respuesta, para la seccion de conexiones.
     *
     * Se corta en [MAX_SUGGESTED_NOTES] porque esto es un aviso, no una bandeja de
     * entrada: una lista larga de cosas pendientes es una lista de tareas, y Zen no
     * reparte tareas. Buscando no aparece: quien busca esta a otra cosa.
     */
    private fun notesWithSuggestions(
        pending: List<NoteLink>,
        all: List<Note>,
        query: String,
    ): List<Note> {
        if (query.isNotBlank() || pending.isEmpty()) return emptyList()
        val byId = all.associateBy { it.id }
        return pending.asSequence()
            .flatMap { sequenceOf(it.fromNoteId, it.toNoteId) }
            .distinct()
            .mapNotNull { byId[it] }
            .sortedByDescending { it.createdAtMillis }
            .take(MAX_SUGGESTED_NOTES)
            .toList()
    }

    /**
     * Clústers que todavia no son un proyecto entero y que no se han descartado en esta
     * sesion.
     *
     * Uno ya contenido por completo en un proyecto existente no vuelve a proponerse: la
     * comprobacion vive aqui y no en [RecurringThemes.clusters] porque esa funcion es
     * pura sobre notas y conexiones, y no sabe nada de "ya propuesto" o "ya aceptado".
     */
    private fun projectSuggestions(
        all: List<Note>,
        accepted: List<NoteLink>,
        ignored: Set<Set<String>>,
    ): List<RecurringCluster> {
        val byId = all.associateBy { it.id }
        return RecurringThemes.clusters(all, accepted)
            .filter { cluster -> cluster.noteIds.any { id -> byId[id]?.projectId == null } }
            .filter { cluster -> cluster.noteIds !in ignored }
    }

    /**
     * Crea un proyecto con todas las notas del clúster.
     *
     * Mismo patron que [DevelopIdeaViewModel.convertToProject]: asignar y avanzar la
     * etapa se hace explicito aqui, nota a nota, no con un disparador oculto en el
     * repositorio.
     */
    fun acceptClusterSuggestion(cluster: RecurringCluster, title: String) {
        if (title.isBlank()) return
        appScope.launch {
            val projectId = UUID.randomUUID().toString()
            notes.saveProject(Project(id = projectId, title = title, createdAtMillis = clock.wallTimeMillis()))
            cluster.noteIds.forEach { id ->
                notes.assignToProject(id, projectId)
                val note = notes.note(id) ?: return@forEach
                notes.save(note.copy(stage = NoteStage.PROJECT))
            }
        }
    }

    /** Descarta el clúster solo para esta sesion: ver [_ignoredClusters]. */
    fun ignoreClusterSuggestion(cluster: RecurringCluster) {
        _ignoredClusters.update { it + setOf(cluster.noteIds) }
    }

    fun onQueryChange(value: String) {
        _query.value = value
    }

    private data class NotesSources(
        val all: List<Note>,
        val pending: List<NoteLink>,
        val accepted: List<NoteLink>,
        val query: String,
        val ignoredClusters: Set<Set<String>>,
    )

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_SUGGESTED_NOTES = 3
    }
}
