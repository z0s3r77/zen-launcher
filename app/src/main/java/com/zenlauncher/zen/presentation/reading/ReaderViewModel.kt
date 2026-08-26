package com.zenlauncher.zen.presentation.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.domain.reading.Book
import com.zenlauncher.zen.domain.reading.BookBlock
import com.zenlauncher.zen.domain.reading.BookChapter
import com.zenlauncher.zen.domain.reading.BookCoverStore
import com.zenlauncher.zen.domain.reading.BookRepository
import com.zenlauncher.zen.domain.reading.Bookmark
import com.zenlauncher.zen.domain.reading.Highlight
import com.zenlauncher.zen.domain.reading.ReadingHit
import com.zenlauncher.zen.domain.reading.ReadingPosition
import com.zenlauncher.zen.domain.reading.ReadingSearch
import com.zenlauncher.zen.domain.reading.ReadingSettings
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class ReaderUiState(
    val book: Book? = null,
    val blocks: List<BookBlock> = emptyList(),
    val chapters: List<BookChapter> = emptyList(),
    val settings: ReadingSettings = ReadingSettings(),
    /** Resultados de buscar dentro del libro. Vacio mientras no se busca nada. */
    val hits: List<ReadingHit> = emptyList(),
    val bookmarks: List<Bookmark> = emptyList(),
    /**
     * Todos los subrayados del libro, no solo los de la pagina.
     *
     * Son unas decenas, y tenerlos enteros permite pintar los que caen en la hoja que se
     * esta mirando **sin consultar nada al pasar de pagina**: pasar hoja tiene que ser
     * instantaneo o deja de parecer un libro.
     */
    val highlights: List<Highlight> = emptyList(),
    val loading: Boolean = true,
) {
    /** El libro ya no esta: se borro desde otra pantalla mientras esta seguia abierta. */
    val missing: Boolean get() = !loading && book == null

    /**
     * Saltos por pagina, cuando el libro no tiene indice.
     *
     * Es la alternativa prometida: un PDF sin indice impreso y sin titulos detectables
     * —unos apuntes escritos de corrido— se sigue pudiendo recorrer. No se guardan como
     * capitulos en la base de datos porque no lo son: son un apano de navegacion, y
     * escribirlos convertiria una carencia del documento en un dato del libro.
     */
    val pageStops: List<BookBlock>
        get() = if (chapters.isNotEmpty()) {
            emptyList()
        } else {
            blocks.filterIndexed { index, _ -> index % PAGE_STOP_EVERY == 0 }
        }
}

/** Un salto cada tantos bloques: en un libro normal cae mas o menos cada dos paginas. */
private const val PAGE_STOP_EVERY = 40

/**
 * El lector de un libro.
 *
 * **Carga el libro entero en memoria** al abrirlo, y es a proposito: un libro de 350
 * paginas son unos dos megabytes de texto, componer una pagina necesita poder medir los
 * bloques de alrededor, y buscar dentro del libro sobre memoria es instantaneo frente a
 * una consulta a SQLite por cada tecla. Se suelta al salir de la pantalla, que es cuando
 * este ViewModel muere; no hay ninguna copia viva mientras se mira la hora.
 *
 * **La paginacion no vive aqui**: partir el texto en hojas necesita medirlo con el
 * tipo, el cuerpo y el ancho reales, y eso solo lo sabe la pantalla. Aqui esta lo que hay
 * que recordar —por donde ibas, que marcaste, que subrayaste— y la pantalla dice donde
 * esta mirando. Ver [com.zenlauncher.zen.domain.reading.Paginator].
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ReaderViewModel(
    private val books: BookRepository,
    private val covers: BookCoverStore,
    private val preferences: PreferencesRepository,
    private val clock: ZenClock,
    /** Guardar el sitio de lectura no puede morir con la pantalla: por eso este scope. */
    private val appScope: CoroutineScope,
) : ViewModel() {

    private data class Loaded(
        val book: Book? = null,
        val blocks: List<BookBlock> = emptyList(),
        val chapters: List<BookChapter> = emptyList(),
        val loading: Boolean = true,
    )

    private val loaded = MutableStateFlow(Loaded())
    private val openBookId = MutableStateFlow<String?>(null)

    /**
     * Lo escrito en el buscador, **sin pasar por el filtro**.
     *
     * Va aparte del estado por la misma razon que en Notas: el campo no puede leer un
     * valor que vuelve de un `mapLatest`, o pierde letras al escribir deprisa.
     */
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Por donde va la vista ahora mismo. Lo dice la pantalla al pasar de pagina. */
    private val visible = MutableStateFlow(ReadingPosition.Start)

    private val hits: StateFlow<List<ReadingHit>> = combine(_query, loaded) { text, state ->
        text to state.blocks
    }.mapLatest { (text, blocks) ->
        if (text.isBlank()) {
            emptyList()
        } else {
            // Fuera del hilo principal: son miles de parrafos y esto corre en cada tecla.
            withContext(Dispatchers.Default) { ReadingSearch.find(blocks, text) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val bookmarks: StateFlow<List<Bookmark>> = openBookId
        .flatMapLatest { id -> if (id == null) emptyFlow() else books.observeBookmarks(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val highlights: StateFlow<List<Highlight>> = openBookId
        .flatMapLatest { id -> if (id == null) emptyFlow() else books.observeHighlights(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), emptyList())

    private val marks = combine(bookmarks, highlights) { marcas, subrayados -> marcas to subrayados }

    val state: StateFlow<ReaderUiState> = combine(
        loaded,
        preferences.readingSettings,
        hits,
        marks,
    ) { state, settings, found, (marcas, subrayados) ->
        ReaderUiState(
            book = state.book,
            blocks = state.blocks,
            chapters = state.chapters,
            settings = settings,
            hits = found,
            bookmarks = marcas,
            highlights = subrayados,
            loading = state.loading,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = ReaderUiState(),
    )

    init {
        // El progreso se guarda cuando la lectura **se queda quieta**, no en cada pagina
        // que pasa de largo: quien busca un capitulo pasa diez hojas seguidas, y eso
        // serian diez escrituras en SQLite dentro del proceso del launcher. El primer
        // valor se descarta porque es el que acaba de restaurarse al abrir, y guardarlo
        // seria escribir lo mismo que se acaba de leer.
        viewModelScope.launch {
            visible.drop(1).debounce(PROGRESS_SAVE_DELAY_MILLIS).collect(::persistProgress)
        }
    }

    fun open(bookId: String) {
        openBookId.value = bookId
        viewModelScope.launch {
            val book = books.book(bookId)
            if (book == null) {
                loaded.value = Loaded(loading = false)
                return@launch
            }
            // El texto y el indice, de un tiron y fuera del hilo principal: la consulta
            // devuelve miles de filas y es lo unico pesado que hace esta pantalla.
            val blocks = books.blocks(bookId)
            val chapters = books.chapters(bookId)
            loaded.value = Loaded(book, blocks, chapters, loading = false)
            visible.value = book.lastPosition
        }
    }

    /** La pantalla dice por donde va. Ver el `debounce` de [init]. */
    fun onPositionVisible(position: ReadingPosition) {
        visible.value = position
    }

    fun onQueryChange(text: String) {
        _query.value = text
    }

    fun setTextStep(step: Int) = update { it.withText(step) }

    fun setLeadingStep(step: Int) = update { it.withLeading(step) }

    fun setMarginStep(step: Int) = update { it.withMargin(step) }

    fun toggleSerif() = update { it.copy(serif = !it.serif) }

    // --- Marcas y subrayados ---

    fun addBookmark(position: ReadingPosition, snippet: String, page: Int) {
        val book = loaded.value.book ?: return
        appScope.launch {
            books.addBookmark(
                Bookmark(
                    id = UUID.randomUUID().toString(),
                    bookId = book.id,
                    position = position,
                    // Recortado al guardar y no al pintar: la lista de marcas se lee sin
                    // cargar el libro, asi que lo que se guarda es lo que se va a ver.
                    snippet = snippet.take(SNIPPET_LENGTH).trim(),
                    page = page,
                    createdAtMillis = clock.wallTimeMillis(),
                ),
            )
        }
    }

    fun deleteBookmark(id: String) {
        appScope.launch { books.deleteBookmark(id) }
    }

    /**
     * Subraya un fragmento, con o sin nota.
     *
     * Un `id` nulo crea uno nuevo; con id, edita el que ya estaba —que es lo que pasa al
     * escribirle una nota a algo ya subrayado—.
     */
    fun putHighlight(
        blockIndex: Int,
        start: Int,
        end: Int,
        text: String,
        page: Int,
        note: String? = null,
        id: String? = null,
    ) {
        val book = loaded.value.book ?: return
        if (start >= end) return
        appScope.launch {
            books.putHighlight(
                Highlight(
                    id = id ?: UUID.randomUUID().toString(),
                    bookId = book.id,
                    blockIndex = blockIndex,
                    start = start,
                    end = end,
                    text = text,
                    note = note?.takeIf { it.isNotBlank() },
                    page = page,
                    createdAtMillis = clock.wallTimeMillis(),
                ),
            )
        }
    }

    /** Escribir, cambiar o borrar la nota de algo que ya estaba subrayado. */
    fun setNote(highlight: Highlight, note: String?) {
        appScope.launch {
            books.putHighlight(highlight.copy(note = note?.takeIf { it.isNotBlank() }))
        }
    }

    fun deleteHighlight(id: String) {
        appScope.launch { books.deleteHighlight(id) }
    }

    fun delete() {
        val book = loaded.value.book ?: return
        appScope.launch {
            books.delete(book.id)
            covers.deleteFor(book.id)
        }
    }

    /**
     * Al salir se guarda el sitio **ya**, sin esperar al `debounce`.
     *
     * Es el caso que mas importa de todos —cerrar el libro es cuando el usuario espera
     * que quede guardado— y el unico en el que el retardo llegaria tarde: `viewModelScope`
     * esta cancelado en este punto, de ahi el [appScope].
     */
    override fun onCleared() {
        persistProgressIn(appScope, visible.value)
        super.onCleared()
    }

    private fun persistProgress(position: ReadingPosition) =
        persistProgressIn(viewModelScope, position)

    private fun persistProgressIn(scope: CoroutineScope, position: ReadingPosition) {
        val book = loaded.value.book ?: return
        scope.launch { books.updateProgress(book.id, position, clock.wallTimeMillis()) }
    }

    private fun update(change: (ReadingSettings) -> ReadingSettings) {
        val current = state.value.settings
        appScope.launch { preferences.setReadingSettings(change(current)) }
    }

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L

        /**
         * Medio segundo desde que la lectura se para. Bastante corto para que salir de la
         * aplicacion por la fuerza no pierda la pagina, bastante largo para que pasar
         * diez hojas buscando un capitulo escriba una vez y no diez.
         */
        const val PROGRESS_SAVE_DELAY_MILLIS = 500L

        /** Lo que se guarda de una marca para reconocerla en la lista. */
        const val SNIPPET_LENGTH = 120
    }
}
