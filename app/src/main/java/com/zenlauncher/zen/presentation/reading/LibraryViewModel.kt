package com.zenlauncher.zen.presentation.reading

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.domain.reading.Book
import com.zenlauncher.zen.domain.reading.BookCoverStore
import com.zenlauncher.zen.domain.reading.BookImporter
import com.zenlauncher.zen.domain.reading.BookRepository
import com.zenlauncher.zen.domain.reading.ImportState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val import: ImportState = ImportState.Idle,
    /**
     * Si este telefono puede importar libros. Ver `PdfTextSource.available`: la
     * extraccion de texto de un PDF llego en Android 15 y `minSdk` es 34.
     */
    val available: Boolean = true,
    val loading: Boolean = true,
) {
    /** Sin libros y sin nada importandose: es una biblioteca vacia, no una en marcha. */
    val empty: Boolean get() = !loading && books.isEmpty() && !import.busy
}

/**
 * La biblioteca.
 *
 * No procesa nada: la importacion la lleva [BookImporter], que vive en el contenedor
 * porque tiene que sobrevivir a que esta pantalla se cierre. Aqui solo se observa.
 */
class LibraryViewModel(
    private val books: BookRepository,
    private val importer: BookImporter,
    private val covers: BookCoverStore,
    /** Borrar un libro no puede morir con la pantalla: ver `QuickNoteViewModel`. */
    private val appScope: CoroutineScope,
) : ViewModel() {

    val state: StateFlow<LibraryUiState> = combine(
        books.observeBooks(),
        importer.state,
    ) { library, import ->
        LibraryUiState(
            books = library,
            import = import,
            available = importer.available,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = LibraryUiState(available = importer.available),
    )

    fun import(uri: String) = importer.start(uri)

    /** La pantalla ya enseno el resultado de la ultima importacion. */
    fun acknowledgeImport() = importer.acknowledge()

    /**
     * Quita el libro y su portada.
     *
     * El texto se va solo por la clave ajena en cascada; la portada es un fichero y hay
     * que borrarla a mano, o quedaria una carpeta que no aparece en ninguna biblioteca.
     */
    fun delete(id: String) {
        appScope.launch {
            books.delete(id)
            covers.deleteFor(id)
        }
    }

    fun coverPath(relativePath: String): String = covers.absolutePath(relativePath)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
