package com.zenlauncher.zen.presentation.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zenlauncher.zen.domain.scanner.CaptureDecision
import com.zenlauncher.zen.domain.scanner.CaptureHint
import com.zenlauncher.zen.domain.scanner.DocumentDetector
import com.zenlauncher.zen.domain.scanner.DocumentProcessor
import com.zenlauncher.zen.domain.scanner.ExportResult
import com.zenlauncher.zen.domain.scanner.GrayFrame
import com.zenlauncher.zen.domain.scanner.Quad
import com.zenlauncher.zen.domain.scanner.ScanDocument
import com.zenlauncher.zen.domain.scanner.ScanError
import com.zenlauncher.zen.domain.scanner.ScanExporter
import com.zenlauncher.zen.domain.scanner.ScanFile
import com.zenlauncher.zen.domain.scanner.ScanFilter
import com.zenlauncher.zen.domain.scanner.ScanPage
import com.zenlauncher.zen.domain.scanner.ScanPhase
import com.zenlauncher.zen.domain.scanner.ScanWorkspace
import com.zenlauncher.zen.domain.scanner.TextRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class ScannerUiState(
    val phase: ScanPhase = ScanPhase.INITIALIZING,
    val error: ScanError? = null,
    /** Lo que se ve por la camara ahora mismo: cuadrilatero y por que no dispara. */
    val live: CaptureDecision.State = CaptureDecision.State(),
    /**
     * Ancho entre alto del frame que analiza la camara, ya girado.
     *
     * Lo necesita el marco que se dibuja encima de la vista previa: las esquinas vienen
     * en fracciones del frame, y la vista previa se ensena entera y centrada, con bandas
     * a los lados o arriba. Sin esta proporcion no se sabe donde empiezan las bandas y el
     * marco queda desplazado respecto a la hoja.
     */
    val frameAspect: Float = DEFAULT_FRAME_ASPECT,
    val document: ScanDocument = ScanDocument(),
    val editingPageId: String? = null,
    val autoCapture: Boolean = true,
    val torchOn: Boolean = false,
    val hasFlash: Boolean = false,
    val ocrAvailable: Boolean = false,
    val ocrRunning: Boolean = false,
    val export: ExportResult? = null,
) {
    val editingPage: ScanPage? get() = document.page(editingPageId)
    val hint: CaptureHint get() = live.hint

    companion object {
        /** Hasta que llegue el primer frame. 3:4 es la proporcion del analisis en vertical. */
        const val DEFAULT_FRAME_ASPECT = 0.75f
    }
}

/**
 * El escaner.
 *
 * Reparte el trabajo en tres ritmos muy distintos, y esa es la decision que sostiene el
 * rendimiento entero:
 *
 * 1. **Por frame**, en el hilo de analisis de CameraX: solo detectar y decidir. No se
 *    reserva nada grande, no se toca el disco y no se pasa por el hilo principal.
 * 2. **Por captura**: enderezar, filtrar y guardar. Caro, pero pasa una vez y el usuario
 *    ya esta esperando.
 * 3. **A peticion**: OCR y exportar. Solo si alguien los pide.
 *
 * La camara no vive aqui —necesita el ciclo de vida de la pantalla— sino en el
 * composable, que pide fotos cuando este ViewModel las reclama por [captureRequests].
 * Asi la decision de cuando disparar se puede probar sin una camara delante.
 */
class ScannerViewModel(
    private val detector: DocumentDetector,
    private val processor: DocumentProcessor,
    private val recognizer: TextRecognizer,
    private val workspace: ScanWorkspace,
    private val exporter: ScanExporter,
    /** Guardar y limpiar no pueden morir con la pantalla. Ver `QuickNoteViewModel`. */
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ScannerUiState(ocrAvailable = recognizer.available),
    )
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    /**
     * Peticiones de foto hacia la pantalla.
     *
     * `DROP_OLDEST` con capacidad uno: si por lo que sea se encolaran dos, la segunda
     * dispararia una foto de mas nada mas volver de la primera.
     */
    private val _captureRequests = MutableSharedFlow<Unit>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val captureRequests: SharedFlow<Unit> = _captureRequests.asSharedFlow()

    /**
     * Cierra la puerta desde el hilo de analisis.
     *
     * Es un `AtomicBoolean` y no un campo del estado porque quien lo consulta es el hilo
     * de CameraX quince veces por segundo mientras la corrutina de captura corre en otro:
     * comprobar y marcar tienen que ser una sola operacion, o dos frames seguidos piden
     * dos fotos de la misma hoja.
     */
    private val capturing = AtomicBoolean(false)

    private var processJob: Job? = null

    /** La camara arranco. Sin esto la pantalla se quedaria en INITIALIZING para siempre. */
    fun onCameraReady(hasFlash: Boolean) {
        if (!detector.available) {
            _state.update { it.copy(phase = ScanPhase.ERROR, error = ScanError.VISION_UNAVAILABLE) }
            return
        }
        _state.update {
            it.copy(phase = ScanPhase.DETECTING, error = null, hasFlash = hasFlash)
        }
    }

    fun onCameraFailed(error: ScanError) {
        _state.update { it.copy(phase = ScanPhase.ERROR, error = error) }
    }

    /**
     * Un frame. **Corre en el hilo de analisis de CameraX**, no en el principal.
     *
     * @param deviceStill lo que dice el acelerometro. Ver `Stillness`.
     */
    fun onFrame(frame: GrayFrame, deviceStill: Boolean) {
        val current = _state.value
        // Mientras se captura, se procesa o se edita, los frames se tiran sin mirarlos:
        // detectar debajo de una pantalla de revision es gastar procesador para nada.
        if (current.phase != ScanPhase.DETECTING &&
            current.phase != ScanPhase.DOCUMENT_DETECTED &&
            current.phase != ScanPhase.READY_TO_CAPTURE
        ) {
            return
        }
        if (capturing.get()) return

        val detection = detector.detect(frame)
        val next = CaptureDecision.next(
            previous = current.live,
            detected = detection?.quad,
            imageAspect = detection?.imageAspect ?: 1f,
            deviceStill = deviceStill,
        )

        _state.update {
            it.copy(
                live = next,
                phase = next.phase,
                frameAspect = detection?.imageAspect ?: it.frameAspect,
            )
        }

        if (next.readyToCapture && current.autoCapture) requestCapture()
    }

    /** El boton de disparar, y tambien lo que llama la captura automatica. */
    fun requestCapture() {
        if (!capturing.compareAndSet(false, true)) return
        _state.update { it.copy(phase = ScanPhase.CAPTURING) }
        _captureRequests.tryEmit(Unit)
    }

    fun toggleAutoCapture() {
        _state.update { it.copy(autoCapture = !it.autoCapture) }
    }

    fun setTorch(enabled: Boolean) {
        _state.update { it.copy(torchOn = enabled) }
    }

    /** La camara no pudo dar la foto. Se vuelve a mirar en lugar de quedarse colgado. */
    fun onCaptureFailed() {
        capturing.set(false)
        _state.update {
            it.copy(
                phase = ScanPhase.DETECTING,
                error = ScanError.CAPTURE_FAILED,
                live = CaptureDecision.State(),
            )
        }
    }

    /**
     * La foto, recien tomada.
     *
     * Aqui empieza todo lo caro, y por eso va en su propia corrutina: enderezar y filtrar
     * una foto de doce megapixeles son cientos de milisegundos, y bloquear el hilo
     * principal en la pantalla de inicio del telefono no es una opcion.
     */
    fun onPhotoTaken(jpeg: ByteArray, rotationDegrees: Int) {
        processJob?.cancel()
        processJob = viewModelScope.launch {
            _state.update { it.copy(phase = ScanPhase.PROCESSING, error = null) }

            val pageId = UUID.randomUUID().toString()
            val upright = processor.upright(jpeg, rotationDegrees) ?: jpeg
            val originalPath = workspace.write(pageId, ScanFile.ORIGINAL, upright)
            if (originalPath == null) {
                fail(ScanError.SAVE_FAILED)
                return@launch
            }

            // Se vuelve a detectar sobre la foto entera y no se reusa el cuadrilatero de
            // la vista previa: la foto tiene veinte veces mas pixeles, asi que las
            // esquinas salen mucho mas finas. Si aqui no se encuentra nada, se cae al que
            // habia en pantalla, que al menos es donde el usuario vio la hoja.
            val detected = processor.let { detector.detectInPhoto(upright)?.quad }
            val fallback = _state.value.live.quad
            val quad = detected ?: fallback ?: Quad.inset(MANUAL_MARGIN)

            val page = buildPage(
                pageId = pageId,
                originalPath = originalPath,
                original = upright,
                quad = quad,
                quarterTurns = 0,
                filter = ScanFilter.DOCUMENT,
                revision = 0,
            )
            if (page == null) {
                workspace.deletePage(pageId)
                fail(ScanError.CAPTURE_FAILED)
                return@launch
            }

            capturing.set(false)
            _state.update {
                it.copy(
                    phase = ScanPhase.EDITING,
                    document = it.document.add(page),
                    editingPageId = page.id,
                    live = CaptureDecision.State(),
                    // Los dos avisos entran a editar igual en lugar de descartar la foto:
                    // el cuadrilatero ya esta puesto y mover cuatro esquinas es mas rapido
                    // que repetir la captura.
                    error = when {
                        detected == null && fallback == null -> ScanError.NO_DOCUMENT
                        // Una hoja que ocupa una miseria del encuadre se endereza igual,
                        // pero sale con tan pocos pixeles que no se lee. Se dice antes de
                        // que el usuario lo descubra guardando el PDF.
                        quad.areaFraction < TINY_COVERAGE -> ScanError.DOCUMENT_TOO_SMALL
                        else -> null
                    },
                )
            }
        }
    }

    /** Mueve una esquina y rehace la pagina. Lo llama la pantalla **al soltar el dedo**. */
    fun setQuad(quad: Quad) {
        val page = _state.value.editingPage ?: return
        reprocess(page.copy(quad = quad.clampedToImage()))
    }

    fun setFilter(filter: ScanFilter) {
        val page = _state.value.editingPage ?: return
        if (page.filter == filter) return
        // Solo se vuelve a filtrar: la perspectiva no ha cambiado, asi que rehacer el
        // enderezado seria repetir lo mas caro para nada.
        reprocess(page.copy(filter = filter), rectifyAgain = false)
    }

    fun rotate() {
        val page = _state.value.editingPage ?: return
        reprocess(page.copy(quarterTurns = (page.quarterTurns + 1) % 4))
    }

    /**
     * Vuelve a enderezar, filtrar y guardar la pagina.
     *
     * @param rectifyAgain false cuando solo cambia el filtro. El fichero enderezado no se
     *   toca —es el original limpio del que salen todos los modos—, que es lo que deja
     *   volver de un filtro a otro sin degradar nada.
     */
    private fun reprocess(page: ScanPage, rectifyAgain: Boolean = true) {
        processJob?.cancel()
        processJob = viewModelScope.launch {
            _state.update { it.copy(phase = ScanPhase.PROCESSING) }

            val original = if (rectifyAgain) workspace.read(page.originalPath) else null
            if (rectifyAgain && original == null) {
                fail(ScanError.CAPTURE_FAILED)
                return@launch
            }

            val updated = buildPage(
                pageId = page.id,
                originalPath = page.originalPath,
                original = original,
                quad = page.quad,
                quarterTurns = page.quarterTurns,
                filter = page.filter,
                revision = page.revision + 1,
                rectifiedPath = page.rectifiedPath.takeIf { !rectifyAgain },
                sharpness = page.sharpness,
                text = page.text,
            )
            if (updated == null) {
                _state.update { it.copy(phase = ScanPhase.EDITING, error = ScanError.CAPTURE_FAILED) }
                return@launch
            }

            _state.update {
                it.copy(
                    phase = ScanPhase.EDITING,
                    document = it.document.replace(updated),
                    error = null,
                )
            }
        }
    }

    /**
     * Endereza, filtra y escribe los dos ficheros derivados de una pagina.
     *
     * @param rectifiedPath cuando se pasa, se reusa el enderezado que ya habia en lugar de
     *   rehacerlo. Es el caso de cambiar solo de filtro.
     */
    private suspend fun buildPage(
        pageId: String,
        originalPath: String,
        original: ByteArray?,
        quad: Quad,
        quarterTurns: Int,
        filter: ScanFilter,
        revision: Int,
        rectifiedPath: String? = null,
        sharpness: Float = -1f,
        text: com.zenlauncher.zen.domain.scanner.RecognizedText? = null,
    ): ScanPage? {
        val rectifiedBytes: ByteArray
        val rectifiedFile: String
        if (rectifiedPath != null) {
            rectifiedBytes = workspace.read(rectifiedPath) ?: return null
            rectifiedFile = rectifiedPath
        } else {
            val source = original ?: return null
            rectifiedBytes = processor.rectify(source, quad, quarterTurns) ?: return null
            rectifiedFile = workspace.write(pageId, ScanFile.RECTIFIED, rectifiedBytes) ?: return null
        }

        val rendered = processor.applyFilter(rectifiedBytes, filter) ?: rectifiedBytes
        val renderedFile = workspace.write(pageId, ScanFile.RENDERED, rendered) ?: return null

        val measured = if (sharpness >= 0f) sharpness else original?.let { processor.sharpness(it) } ?: 1f

        return ScanPage(
            id = pageId,
            originalPath = originalPath,
            rectifiedPath = rectifiedFile,
            renderedPath = renderedFile,
            quad = quad,
            quarterTurns = quarterTurns,
            filter = filter,
            sharpness = measured,
            // El texto reconocido se tira al cambiar el recorte o el filtro: es texto de
            // una imagen que ya no existe, y dejarlo puesto seria ensenar el OCR de un
            // encuadre anterior como si fuera el de este.
            text = if (rectifiedPath != null) text else null,
            revision = revision,
        )
    }

    /** Da por buena la pagina y vuelve a la camara a por la siguiente. */
    fun confirmPage() {
        capturing.set(false)
        _state.update {
            it.copy(
                phase = ScanPhase.DETECTING,
                editingPageId = null,
                live = CaptureDecision.State(),
                error = null,
            )
        }
    }

    /** Descarta la pagina que se esta revisando y vuelve a la camara. */
    fun retakePage() {
        val page = _state.value.editingPage
        capturing.set(false)
        _state.update {
            it.copy(
                phase = ScanPhase.DETECTING,
                document = page?.let { current -> it.document.remove(current.id) } ?: it.document,
                editingPageId = null,
                live = CaptureDecision.State(),
                error = null,
            )
        }
        page?.let { current -> appScope.launch { workspace.deletePage(current.id) } }
    }

    fun openPage(pageId: String) {
        if (_state.value.document.page(pageId) == null) return
        _state.update { it.copy(phase = ScanPhase.EDITING, editingPageId = pageId, error = null) }
    }

    fun deletePage(pageId: String) {
        _state.update {
            it.copy(
                document = it.document.remove(pageId),
                editingPageId = if (it.editingPageId == pageId) null else it.editingPageId,
                phase = if (it.editingPageId == pageId) ScanPhase.DETECTING else it.phase,
            )
        }
        appScope.launch { workspace.deletePage(pageId) }
    }

    /** Lee el texto de la pagina que se esta revisando. Nunca bloquea nada de lo demas. */
    fun runOcr() {
        val page = _state.value.editingPage ?: return
        if (!recognizer.available || _state.value.ocrRunning) return

        viewModelScope.launch {
            _state.update { it.copy(ocrRunning = true) }
            val text = recognizer.read(page.renderedPath)
            _state.update { current ->
                val target = current.document.page(page.id)
                current.copy(
                    ocrRunning = false,
                    document = target?.let { current.document.replace(it.copy(text = text)) }
                        ?: current.document,
                    error = if (text == null) ScanError.OCR_FAILED else current.error,
                )
            }
        }
    }

    fun saveImage() {
        val page = _state.value.editingPage ?: _state.value.document.pages.lastOrNull() ?: return
        export { exporter.exportImage(page) }
    }

    fun savePdf() {
        val pages = _state.value.document.pages
        if (pages.isEmpty()) return
        export { exporter.exportPdf(pages) }
    }

    /**
     * Guardar corre en [appScope] y no en el del ViewModel.
     *
     * Escribir un PDF de seis paginas tarda segundos, y en un launcher el usuario se va a
     * la pantalla de inicio en cuanto ve que aquello va para largo. Colgado de la pantalla,
     * volver mataria la escritura a medias y dejaria un PDF truncado en la carpeta de
     * documentos. Es la misma razon por la que `BookImporter` vive en el contenedor.
     */
    private fun export(block: suspend () -> ExportResult) {
        _state.update { it.copy(phase = ScanPhase.SAVING, export = null) }
        appScope.launch {
            val result = block()
            _state.update {
                it.copy(
                    phase = if (it.editingPageId != null) ScanPhase.EDITING else ScanPhase.DETECTING,
                    export = result,
                    error = (result as? ExportResult.Failed)?.error,
                )
            }
        }
    }

    /** La pantalla ya enseno el resultado de guardar o el ultimo error. */
    fun acknowledge() {
        _state.update { it.copy(export = null, error = null) }
    }

    /**
     * Salir del escaner: se borra todo lo que no se guardo.
     *
     * En [appScope] porque la pantalla ya no existe cuando esto corre. Sin ello, cada
     * escaneo abandonado dejaria fotos a resolucion completa en la cache del launcher que
     * no aparecen en ninguna pantalla y que nadie va a ir a buscar.
     */
    fun discardAll() {
        capturing.set(false)
        appScope.launch { workspace.clear() }
    }

    override fun onCleared() {
        detector.close()
        recognizer.close()
    }

    private fun fail(error: ScanError) {
        capturing.set(false)
        _state.update {
            it.copy(phase = ScanPhase.DETECTING, error = error, live = CaptureDecision.State())
        }
    }

    private companion object {
        /** Margen del cuadrilatero de partida cuando no se detecto nada. */
        const val MANUAL_MARGIN = 0.08f

        /**
         * Por debajo de esto la hoja ocupaba tan poco que enderezarla no da nada legible.
         *
         * Es la mitad de lo que exige la captura automatica: disparando a mano el usuario
         * manda, asi que esto avisa en vez de impedir. Ver [CaptureDecision.MIN_COVERAGE].
         */
        const val TINY_COVERAGE = CaptureDecision.MIN_COVERAGE / 2f
    }
}
