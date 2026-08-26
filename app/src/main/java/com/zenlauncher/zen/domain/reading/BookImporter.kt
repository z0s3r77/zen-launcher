package com.zenlauncher.zen.domain.reading

import com.zenlauncher.zen.core.ZenClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Importar un PDF: leerlo, entenderlo y guardarlo.
 *
 * Vive en el dominio y no en la capa de datos porque no toca Android: recibe el lector
 * de PDF, el almacen y el reloj por la puerta, igual que `NoteIndexer` o
 * `SeedEssentialFavourites`. Se puede probar entero con un lector de mentira.
 *
 * Vive en el contenedor y **no en un ViewModel** a proposito. Un libro de 400 paginas
 * tarda decenas de segundos en procesarse, y en un launcher el usuario se va a la
 * pantalla de inicio en cuanto ve que aquello va para largo: si la importacion colgara
 * del ciclo de vida de la pantalla, volver mataria el trabajo a medias y no habria
 * ninguna senal de por que el libro no aparecio. Al estar aqui, la pantalla se puede
 * cerrar, abrir y cerrar otra vez, y siempre encuentra el estado real.
 *
 * Es tambien la razon de que el estado sea un [StateFlow] y no un evento: quien vuelve a
 * la biblioteca a mitad de proceso tiene que ver por donde va, no perderse el aviso.
 */
class BookImporter(
    private val pdf: PdfTextSource,
    private val books: BookRepository,
    private val covers: BookCoverStore,
    private val clock: ZenClock,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow<ImportState>(ImportState.Idle)
    val state: StateFlow<ImportState> = _state.asStateFlow()

    private var job: Job? = null

    /** Si este telefono puede importar libros. Se pregunta antes de ofrecer el boton. */
    val available: Boolean get() = pdf.available

    /**
     * Empieza a importar. Una segunda llamada mientras hay una en marcha **no hace nada**:
     * dos importaciones a la vez en el proceso del launcher son dos PDF enteros en
     * memoria, y el sistema mata al launcher antes que a nadie.
     */
    fun start(uri: String) {
        if (job?.isActive == true) return
        if (!pdf.available) {
            _state.value = ImportState.Failed(ImportFailure.UNSUPPORTED)
            return
        }

        // El estado se marca **antes** de lanzar la corrutina, no dentro. Lanzarla no la
        // ejecuta: hasta que el planificador le da paso, `start` ya ha vuelto y el
        // estado seguiria en Idle, asi que tocar ANADIR LIBRO no cambiaria nada durante
        // ese hueco y ademas `acknowledge` podria borrar un aviso de una importacion que
        // acaba de empezar.
        _state.value = ImportState.Reading(page = 0, total = 0)

        job = scope.launch {
            val document = pdf.read(uri) { page, total ->
                _state.value = ImportState.Reading(page = page, total = total)
            }
            if (document == null) {
                _state.value = ImportState.Failed(ImportFailure.UNREADABLE)
                return@launch
            }

            _state.value = ImportState.Building
            val built = BookBuilder.build(document)
            if (!built.readable) {
                // Ni un parrafo: es un escaneo. Se dice tal cual en lugar de crear un
                // libro vacio que al abrirlo no ensena nada y parece un fallo de Zen.
                _state.value = ImportState.Failed(ImportFailure.NO_TEXT)
                return@launch
            }

            val id = UUID.randomUUID().toString()
            val cover = pdf.renderCover(uri, COVER_MAX_EDGE_PX)?.let { covers.store(id, it) }

            books.save(
                book = Book(
                    id = id,
                    title = built.title,
                    author = built.author,
                    sourceUri = uri,
                    coverPath = cover,
                    pageCount = built.pageCount,
                    blockCount = built.blocks.size,
                    importedAtMillis = clock.wallTimeMillis(),
                    lastReadAtMillis = null,
                    lastPosition = ReadingPosition.Start,
                ),
                blocks = built.blocks,
                chapters = built.chapters,
            )

            _state.value = ImportState.Done(bookId = id, title = built.title)
        }
    }

    /**
     * La pantalla ya vio el resultado.
     *
     * Sin esto, el aviso de "importado" o el motivo del fallo se quedarian para siempre
     * encima de la biblioteca, y volver a entrar manana enseñaria el error de ayer.
     */
    fun acknowledge() {
        if (!_state.value.busy) _state.value = ImportState.Idle
    }

    private companion object {
        /**
         * 512 px de lado. La portada se pinta a un tercio del ancho de la pantalla en una
         * lista: mas resolucion no se ve, y son bytes en el disco del launcher.
         */
        const val COVER_MAX_EDGE_PX = 512
    }
}
