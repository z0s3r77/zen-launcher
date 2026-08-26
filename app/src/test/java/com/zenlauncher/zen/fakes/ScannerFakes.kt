package com.zenlauncher.zen.fakes

import com.zenlauncher.zen.domain.scanner.Detection
import com.zenlauncher.zen.domain.scanner.DocumentDetector
import com.zenlauncher.zen.domain.scanner.DocumentProcessor
import com.zenlauncher.zen.domain.scanner.ExportResult
import com.zenlauncher.zen.domain.scanner.GrayFrame
import com.zenlauncher.zen.domain.scanner.Quad
import com.zenlauncher.zen.domain.scanner.RecognizedText
import com.zenlauncher.zen.domain.scanner.RecognizedWord
import com.zenlauncher.zen.domain.scanner.ScanError
import com.zenlauncher.zen.domain.scanner.ScanExporter
import com.zenlauncher.zen.domain.scanner.ScanFile
import com.zenlauncher.zen.domain.scanner.ScanFilter
import com.zenlauncher.zen.domain.scanner.ScanPage
import com.zenlauncher.zen.domain.scanner.ScanWorkspace
import com.zenlauncher.zen.domain.scanner.TextRecognizer

/**
 * Los dobles del escaner, aparte del resto igual que `ReadingFakes`.
 *
 * Estan por la misma razon que aquellos: **la mitad de lo que hay que probar es el camino
 * en el que algo no se puede hacer**. Un telefono sin OpenCV, una camara que no devuelve
 * la foto, un disco lleno o un OCR que falla no se pueden provocar con las
 * implementaciones de verdad, y son justo los casos en los que un launcher no se puede
 * permitir caerse.
 */

/** Detecta lo que se le diga, o nada. */
class FakeDocumentDetector(
    override var available: Boolean = true,
    /** Lo que devuelve por cada frame de la vista previa. */
    var liveQuad: Quad? = null,
    /** Lo que devuelve sobre la foto ya tomada. */
    var photoQuad: Quad? = null,
    var imageAspect: Float = 0.75f,
) : DocumentDetector {

    var frames = 0
        private set
    var photoLookups = 0
        private set
    var closed = false
        private set

    override fun detect(frame: GrayFrame): Detection? {
        frames++
        return liveQuad?.let { Detection(it, imageAspect) }
    }

    override suspend fun detectInPhoto(jpeg: ByteArray): Detection? {
        photoLookups++
        return photoQuad?.let { Detection(it, imageAspect) }
    }

    override fun close() {
        closed = true
    }
}

/**
 * Procesa marcando los bytes en lugar de mover pixeles.
 *
 * Cada paso deja su huella en el contenido, asi que un test puede comprobar **por donde
 * ha pasado** una pagina sin decodificar ninguna imagen.
 */
class FakeDocumentProcessor(
    override var available: Boolean = true,
    var sharpness: Float = 1f,
    var failRectify: Boolean = false,
) : DocumentProcessor {

    var rectifyCalls = 0
        private set
    var filterCalls = 0
        private set
    var lastQuad: Quad? = null
    var lastQuarterTurns: Int = 0
    var lastFilter: ScanFilter? = null

    override suspend fun upright(jpeg: ByteArray, rotationDegrees: Int): ByteArray? =
        if (rotationDegrees == 0) jpeg else "derecha:".toByteArray() + jpeg

    override suspend fun rectify(jpeg: ByteArray, quad: Quad, quarterTurns: Int): ByteArray? {
        rectifyCalls++
        lastQuad = quad
        lastQuarterTurns = quarterTurns
        return if (failRectify) null else "recta:".toByteArray() + jpeg
    }

    override suspend fun applyFilter(rectified: ByteArray, filter: ScanFilter): ByteArray? {
        filterCalls++
        lastFilter = filter
        return "$filter:".toByteArray() + rectified
    }

    override suspend fun sharpness(jpeg: ByteArray): Float = sharpness
}

/** Lee lo que se le diga, o falla. */
class FakeTextRecognizer(
    override var available: Boolean = true,
    var result: RecognizedText? = RecognizedText(
        text = "Texto de prueba",
        words = listOf(RecognizedWord("Texto", 0.1f, 0.1f, 0.3f, 0.15f)),
    ),
) : TextRecognizer {

    var reads = 0
        private set
    var closed = false
        private set

    override suspend fun read(imagePath: String): RecognizedText? {
        reads++
        return result
    }

    override fun close() {
        closed = true
    }
}

/** Un almacen en memoria, con las mismas rutas que el de verdad. */
class FakeScanWorkspace(var failWrites: Boolean = false) : ScanWorkspace {

    val files = LinkedHashMap<String, ByteArray>()
    var cleared = 0
        private set
    val deleted = mutableListOf<String>()

    override suspend fun write(pageId: String, file: ScanFile, bytes: ByteArray): String? {
        if (failWrites) return null
        val path = "$pageId/${file.name}"
        files[path] = bytes
        return path
    }

    override suspend fun read(path: String): ByteArray? = files[path]

    override suspend fun deletePage(pageId: String) {
        deleted += pageId
        files.keys.filter { it.startsWith("$pageId/") }.forEach(files::remove)
    }

    override suspend fun clear() {
        cleared++
        files.clear()
    }
}

/** Guarda apuntando lo que le llega, o devuelve el fallo que se le pida. */
class FakeScanExporter(var failure: ScanError? = null) : ScanExporter {

    var images = 0
        private set
    var pdfPages: List<ScanPage>? = null
        private set

    override suspend fun exportImage(page: ScanPage): ExportResult {
        images++
        return failure?.let { ExportResult.Failed(it) }
            ?: ExportResult.Saved(location = "Pictures/Zen", displayName = "Escaneo.jpg")
    }

    override suspend fun exportPdf(pages: List<ScanPage>): ExportResult {
        pdfPages = pages
        return failure?.let { ExportResult.Failed(it) }
            ?: ExportResult.Saved(location = "Documents/Zen", displayName = "Escaneo.pdf")
    }
}
