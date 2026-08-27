package com.zenlauncher.zen.presentation

import app.cash.turbine.test
import com.zenlauncher.zen.domain.scanner.CaptureDecision
import com.zenlauncher.zen.domain.scanner.ExportResult
import com.zenlauncher.zen.domain.scanner.GrayFrame
import com.zenlauncher.zen.domain.scanner.Quad
import com.zenlauncher.zen.domain.scanner.ScanError
import com.zenlauncher.zen.domain.scanner.ScanFilter
import com.zenlauncher.zen.domain.scanner.ScanPhase
import com.zenlauncher.zen.domain.scanner.ScanPoint
import com.zenlauncher.zen.fakes.FakeDocumentDetector
import com.zenlauncher.zen.fakes.FakeDocumentProcessor
import com.zenlauncher.zen.fakes.FakeScanExporter
import com.zenlauncher.zen.fakes.FakeScanWorkspace
import com.zenlauncher.zen.fakes.FakeTextRecognizer
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.presentation.scanner.ScannerViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScannerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val encuadrada = Quad(
        topLeft = ScanPoint(0.12f, 0.10f),
        topRight = ScanPoint(0.88f, 0.10f),
        bottomRight = ScanPoint(0.88f, 0.90f),
        bottomLeft = ScanPoint(0.12f, 0.90f),
    )

    private val ajustada = Quad(
        topLeft = ScanPoint(0.05f, 0.05f),
        topRight = ScanPoint(0.95f, 0.05f),
        bottomRight = ScanPoint(0.95f, 0.95f),
        bottomLeft = ScanPoint(0.05f, 0.95f),
    )

    @Test
    fun `sin vision por computador el escaner lo dice en vez de abrir la camara`() = runTest {
        // En un telefono donde la libreria nativa no carga, esto tiene que terminar en un
        // rotulo: Zen es la pantalla de inicio y no se puede caer.
        val detector = FakeDocumentDetector(available = false)
        val viewModel = build(detector = detector)

        viewModel.onCameraReady(hasFlash = true)

        assertEquals(ScanPhase.ERROR, viewModel.state.value.phase)
        assertEquals(ScanError.VISION_UNAVAILABLE, viewModel.state.value.error)
    }

    @Test
    fun `sostener la hoja quieta acaba pidiendo una foto`() = runTest {
        val detector = FakeDocumentDetector(liveQuad = encuadrada)
        val viewModel = build(detector = detector)
        viewModel.onCameraReady(hasFlash = false)

        viewModel.captureRequests.test {
            repeat(CaptureDecision.REQUIRED_STEADY_FRAMES + 1) {
                viewModel.onFrame(frame(), deviceStill = true)
            }
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(ScanPhase.CAPTURING, viewModel.state.value.phase)
    }

    @Test
    fun `en manual no dispara solo por mucho que se sostenga`() = runTest {
        val viewModel = build(detector = FakeDocumentDetector(liveQuad = encuadrada))
        viewModel.onCameraReady(hasFlash = false)
        viewModel.toggleAutoCapture()

        repeat(CaptureDecision.REQUIRED_STEADY_FRAMES * 3) {
            viewModel.onFrame(frame(), deviceStill = true)
        }

        assertEquals(ScanPhase.READY_TO_CAPTURE, viewModel.state.value.phase)
    }

    @Test
    fun `mientras se captura los frames se tiran sin mirarlos`() = runTest {
        // Detectar debajo de una pantalla de revision es gastar procesador para nada, y en
        // el proceso del launcher eso se nota en la bateria.
        val detector = FakeDocumentDetector(liveQuad = encuadrada)
        val viewModel = build(detector = detector)
        viewModel.onCameraReady(hasFlash = false)

        viewModel.requestCapture()
        val analizadosAntes = detector.frames

        repeat(5) { viewModel.onFrame(frame(), deviceStill = true) }

        assertEquals(analizadosAntes, detector.frames)
    }

    @Test
    fun `dos frames seguidos no piden dos fotos de la misma hoja`() = runTest {
        // Regresion del cierre atomico: el hilo de analisis mira quince veces por segundo
        // mientras la captura corre en otro, asi que comprobar y marcar tienen que ser una
        // sola operacion. Sin el, la racha se sigue cumpliendo en los frames siguientes y
        // se dispararian varias fotos de la misma hoja.
        val viewModel = build(detector = FakeDocumentDetector(liveQuad = encuadrada))
        viewModel.onCameraReady(hasFlash = false)

        viewModel.captureRequests.test {
            repeat(CaptureDecision.REQUIRED_STEADY_FRAMES * 3) {
                viewModel.onFrame(frame(), deviceStill = true)
            }

            awaitItem()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `una foto se endereza, se filtra y queda lista para revisar`() = runTest {
        val detector = FakeDocumentDetector(liveQuad = encuadrada, photoQuad = ajustada)
        val procesador = FakeDocumentProcessor()
        val almacen = FakeScanWorkspace()
        val viewModel = build(detector = detector, processor = procesador, workspace = almacen)

        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 90)
        advanceUntilIdle()

        val estado = viewModel.state.value
        assertEquals(ScanPhase.EDITING, estado.phase)
        assertEquals(1, estado.document.pageCount)

        val pagina = estado.editingPage!!
        // El modo documento es el de partida: es el que hace que una foto parezca escaneo.
        assertEquals(ScanFilter.DOCUMENT, pagina.filter)
        // Se usa el cuadrilatero de la foto entera, no el de la vista previa: veinte veces
        // mas pixeles dan esquinas mucho mas finas.
        assertEquals(ajustada, pagina.quad)
        // Los tres ficheros de la pagina, cada uno por su razon. Ver `ScanPage`.
        assertEquals(3, almacen.files.size)
    }

    @Test
    fun `sin hoja en la foto se entra a editar igual, avisando`() = runTest {
        // Mover cuatro esquinas es mas rapido que repetir la foto, asi que no se descarta.
        val detector = FakeDocumentDetector(liveQuad = null, photoQuad = null)
        val viewModel = build(detector = detector)

        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 0)
        advanceUntilIdle()

        val estado = viewModel.state.value
        assertEquals(ScanPhase.EDITING, estado.phase)
        assertEquals(ScanError.NO_DOCUMENT, estado.error)
        assertNotNull(estado.editingPage)
    }

    @Test
    fun `una hoja diminuta se avisa pero se deja editar`() = runTest {
        // Disparando a mano el usuario manda: se endereza igual y se le dice que va a
        // salir ilegible, en vez de descartarle la foto de algo que ya no esta delante.
        val minuscula = Quad(
            topLeft = ScanPoint(0.44f, 0.44f),
            topRight = ScanPoint(0.56f, 0.44f),
            bottomRight = ScanPoint(0.56f, 0.60f),
            bottomLeft = ScanPoint(0.44f, 0.60f),
        )
        val viewModel = build(detector = FakeDocumentDetector(photoQuad = minuscula))

        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 0)
        advanceUntilIdle()

        val estado = viewModel.state.value
        assertEquals(ScanError.DOCUMENT_TOO_SMALL, estado.error)
        assertEquals(1, estado.document.pageCount)
        assertEquals(ScanPhase.EDITING, estado.phase)
    }

    @Test
    fun `una foto movida no es un error sino una propiedad de la pagina`() = runTest {
        // Se guarda igual y se revisa igual; lo que se dice va al lado de la pagina a la
        // que le pasa, no como un error suelto que taparia al siguiente.
        val viewModel = build(processor = FakeDocumentProcessor(sharpness = 0.05f))

        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 0)
        advanceUntilIdle()

        val estado = viewModel.state.value
        assertTrue(estado.editingPage!!.blurry)
        assertNull(estado.error)
        assertEquals(1, estado.document.pageCount)
    }

    @Test
    fun `si la deteccion sobre la foto falla se usa la de la pantalla`() = runTest {
        // Es lo que el usuario estaba viendo cuando decidio disparar: es la mejor pista
        // que queda, y desde luego mejor que un recorte por defecto.
        val detector = FakeDocumentDetector(liveQuad = encuadrada, photoQuad = null)
        val viewModel = build(detector = detector)
        viewModel.onCameraReady(hasFlash = false)
        repeat(3) { viewModel.onFrame(frame(), deviceStill = true) }

        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 0)
        advanceUntilIdle()

        assertEquals(encuadrada, viewModel.state.value.editingPage!!.quad)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `cambiar de filtro no vuelve a enderezar`() = runTest {
        // Rehacer la perspectiva al cambiar de modo seria repetir lo mas caro para nada, y
        // ademas degradaria la imagen en cada cambio.
        val procesador = FakeDocumentProcessor()
        val viewModel = build(processor = procesador)

        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 0)
        advanceUntilIdle()
        val enderezadosTrasCapturar = procesador.rectifyCalls

        viewModel.setFilter(ScanFilter.BLACK_AND_WHITE)
        advanceUntilIdle()

        assertEquals(enderezadosTrasCapturar, procesador.rectifyCalls)
        assertEquals(ScanFilter.BLACK_AND_WHITE, viewModel.state.value.editingPage!!.filter)
        assertEquals(ScanFilter.BLACK_AND_WHITE, procesador.lastFilter)
    }

    @Test
    fun `mover una esquina si vuelve a enderezar, y desde la foto original`() = runTest {
        val procesador = FakeDocumentProcessor()
        val viewModel = build(processor = procesador)

        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 0)
        advanceUntilIdle()
        val antes = procesador.rectifyCalls

        viewModel.setQuad(ajustada)
        advanceUntilIdle()

        assertEquals(antes + 1, procesador.rectifyCalls)
        assertEquals(ajustada, procesador.lastQuad)
    }

    @Test
    fun `reprocesar sube la revision para que la pantalla no ensene lo anterior`() = runTest {
        // Regresion: las tres rutas de una pagina no cambian al reprocesarla, asi que sin
        // este numero la pantalla —que recuerda el bitmap por su ruta— seguiria ensenando
        // el filtro anterior.
        val viewModel = build()
        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 0)
        advanceUntilIdle()
        val inicial = viewModel.state.value.editingPage!!.revision

        viewModel.setFilter(ScanFilter.GRAYSCALE)
        advanceUntilIdle()

        assertEquals(inicial + 1, viewModel.state.value.editingPage!!.revision)
    }

    @Test
    fun `recortar de nuevo tira el texto reconocido`() = runTest {
        // Es el OCR de un encuadre que ya no existe: dejarlo puesto seria ensenar el texto
        // de otra imagen como si fuera el de esta.
        val viewModel = build()
        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 0)
        advanceUntilIdle()

        viewModel.runOcr()
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.editingPage!!.text)

        viewModel.setQuad(ajustada)
        advanceUntilIdle()

        assertNull(viewModel.state.value.editingPage!!.text)
    }

    @Test
    fun `cambiar solo de filtro conserva el texto`() = runTest {
        // El recorte no ha cambiado, asi que el texto sigue siendo el de esta hoja. Volver
        // a leerlo costaria un segundo largo por nada.
        val reconocedor = FakeTextRecognizer()
        val viewModel = build(recognizer = reconocedor)
        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 0)
        advanceUntilIdle()

        viewModel.runOcr()
        advanceUntilIdle()

        viewModel.setFilter(ScanFilter.HIGH_CONTRAST)
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.editingPage!!.text)
        assertEquals(1, reconocedor.reads)
    }

    @Test
    fun `un reconocedor que revienta no tumba el launcher`() = runTest {
        // Regresion: el reconocedor vive en el contenedor, o sea uno para todo el
        // proceso, y `onCleared` lo cierra al salir del escaner. Con el cliente en un
        // `by lazy`, cerrado quedaba cerrado para siempre pero `available` seguia
        // diciendo true: la segunda visita al escaner llamaba a `process` sobre un
        // detector cerrado, ML Kit lanzaba de forma sincrona y la excepcion subia por
        // `viewModelScope` sin que nadie la cogiera. El proceso del launcher moria y el
        // telefono se quedaba sin pantalla de inicio.
        val reconocedor = FakeTextRecognizer()
        val viewModel = build(recognizer = reconocedor)
        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 0)
        advanceUntilIdle()

        // Alguien lo cerro antes (otra visita al escaner que ya termino).
        reconocedor.close()

        viewModel.runOcr()
        advanceUntilIdle()

        // Se dice que el OCR fallo, y el documento y la aplicacion siguen en pie.
        val estado = viewModel.state.value
        assertEquals(ScanError.OCR_FAILED, estado.error)
        assertEquals(1, estado.document.pageCount)
        assertFalse(estado.ocrRunning)
    }

    @Test
    fun `un OCR fallido no se lleva por delante el documento`() = runTest {
        val viewModel = build(recognizer = FakeTextRecognizer(result = null))
        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 0)
        advanceUntilIdle()

        viewModel.runOcr()
        advanceUntilIdle()

        val estado = viewModel.state.value
        assertEquals(ScanError.OCR_FAILED, estado.error)
        assertEquals(1, estado.document.pageCount)
        assertFalse(estado.ocrRunning)
    }

    @Test
    fun `varias paginas se acumulan en orden`() = runTest {
        val viewModel = build()

        repeat(3) { indice ->
            viewModel.onPhotoTaken("foto$indice".toByteArray(), rotationDegrees = 0)
            advanceUntilIdle()
            viewModel.confirmPage()
        }

        val estado = viewModel.state.value
        assertEquals(3, estado.document.pageCount)
        assertEquals(ScanPhase.DETECTING, estado.phase)
        assertNull(estado.editingPageId)
    }

    @Test
    fun `el PDF se pide con todas las paginas y en su orden`() = runTest {
        val exportador = FakeScanExporter()
        val viewModel = build(exporter = exportador)

        repeat(2) {
            viewModel.onPhotoTaken("foto$it".toByteArray(), rotationDegrees = 0)
            advanceUntilIdle()
            viewModel.confirmPage()
        }

        viewModel.savePdf()
        advanceUntilIdle()

        assertEquals(
            viewModel.state.value.document.pages.map { it.id },
            exportador.pdfPages!!.map { it.id },
        )
        assertTrue(viewModel.state.value.export is ExportResult.Saved)
    }

    @Test
    fun `sin sitio en el disco se dice, y el documento sigue ahi`() = runTest {
        val viewModel = build(exporter = FakeScanExporter(failure = ScanError.OUT_OF_SPACE))
        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 0)
        advanceUntilIdle()

        viewModel.savePdf()
        advanceUntilIdle()

        val estado = viewModel.state.value
        assertEquals(ScanError.OUT_OF_SPACE, estado.error)
        assertEquals(1, estado.document.pageCount)
        assertEquals(ScanPhase.EDITING, estado.phase)
    }

    @Test
    fun `repetir una pagina la borra del disco y vuelve a la camara`() = runTest {
        val almacen = FakeScanWorkspace()
        val viewModel = build(workspace = almacen)
        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 0)
        advanceUntilIdle()
        val id = viewModel.state.value.editingPage!!.id

        viewModel.retakePage()
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.document.pageCount)
        assertEquals(ScanPhase.DETECTING, viewModel.state.value.phase)
        assertTrue(almacen.deleted.contains(id))
    }

    @Test
    fun `repetir deja disparar otra vez`() = runTest {
        // Regresion del cierre atomico: si `retakePage` no lo soltara, el escaner se
        // quedaria mudo despues de descartar una pagina y no habria forma de saber por que.
        val viewModel = build(detector = FakeDocumentDetector(liveQuad = encuadrada))
        viewModel.onCameraReady(hasFlash = false)
        viewModel.requestCapture()
        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 0)
        advanceUntilIdle()

        viewModel.retakePage()
        advanceUntilIdle()

        viewModel.captureRequests.test {
            repeat(CaptureDecision.REQUIRED_STEADY_FRAMES + 1) {
                viewModel.onFrame(frame(), deviceStill = true)
            }
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `una captura que falla no deja el escaner colgado`() = runTest {
        val viewModel = build(detector = FakeDocumentDetector(liveQuad = encuadrada))
        viewModel.onCameraReady(hasFlash = false)
        viewModel.requestCapture()

        viewModel.onCaptureFailed()

        assertEquals(ScanPhase.DETECTING, viewModel.state.value.phase)
        assertEquals(ScanError.CAPTURE_FAILED, viewModel.state.value.error)

        viewModel.captureRequests.test {
            repeat(CaptureDecision.REQUIRED_STEADY_FRAMES + 1) {
                viewModel.onFrame(frame(), deviceStill = true)
            }
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `si no se puede escribir en el disco no se crea media pagina`() = runTest {
        val viewModel = build(workspace = FakeScanWorkspace(failWrites = true))

        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 0)
        advanceUntilIdle()

        val estado = viewModel.state.value
        assertEquals(0, estado.document.pageCount)
        assertEquals(ScanError.SAVE_FAILED, estado.error)
        assertEquals(ScanPhase.DETECTING, estado.phase)
    }

    @Test
    fun `salir del escaner se lleva lo que no se guardo`() = runTest {
        // Cada escaneo abandonado son fotos a resolucion completa que no aparecen en
        // ninguna pantalla y que nadie va a ir a buscar.
        val almacen = FakeScanWorkspace()
        val viewModel = build(workspace = almacen)
        viewModel.onPhotoTaken("foto".toByteArray(), rotationDegrees = 0)
        advanceUntilIdle()

        viewModel.discardAll()
        advanceUntilIdle()

        assertEquals(1, almacen.cleared)
        assertTrue(almacen.files.isEmpty())
    }

    @Test
    fun `sin reconocedor de texto no se ofrece el OCR`() = runTest {
        val viewModel = build(recognizer = FakeTextRecognizer(available = false))
        assertFalse(viewModel.state.value.ocrAvailable)
    }

    private fun frame() = GrayFrame(
        luma = ByteArray(16),
        width = 4,
        height = 4,
        rowStride = 4,
        rotationDegrees = 0,
    )

    private fun TestScope.build(
        detector: FakeDocumentDetector = FakeDocumentDetector(photoQuad = encuadrada),
        processor: FakeDocumentProcessor = FakeDocumentProcessor(),
        recognizer: FakeTextRecognizer = FakeTextRecognizer(),
        workspace: FakeScanWorkspace = FakeScanWorkspace(),
        exporter: FakeScanExporter = FakeScanExporter(),
    ) = ScannerViewModel(
        detector = detector,
        processor = processor,
        recognizer = recognizer,
        workspace = workspace,
        exporter = exporter,
        appScope = this,
    )
}
