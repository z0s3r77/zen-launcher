package com.zenlauncher.zen.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.domain.notes.NoteIndexer
import com.zenlauncher.zen.domain.notes.NoteLink
import com.zenlauncher.zen.domain.notes.NotesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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
    appScope: CoroutineScope,
) : ViewModel() {

    private val _query = MutableStateFlow("")

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
        _query,
    ) { all, pending, currentQuery -> Triple(all, pending, currentQuery) }
        // El filtro literal lo resuelve el almacen, no una copia del criterio aqui: si
        // la pantalla y la base de datos separaran las palabras de forma distinta, una
        // nota podria encontrarse escribiendo y no aparecer en su propia lista.
        // `mapLatest` cancela la busqueda anterior en cuanto se teclea otra letra.
        .mapLatest { (all, pending, currentQuery) ->
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

    fun onQueryChange(value: String) {
        _query.value = value
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
        const val MAX_SUGGESTED_NOTES = 3
    }
}
