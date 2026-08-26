package com.zenlauncher.zen.data.scanner

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.util.Log
import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.domain.scanner.ExportResult
import com.zenlauncher.zen.domain.scanner.PdfPageSize
import com.zenlauncher.zen.domain.scanner.RecognizedWord
import com.zenlauncher.zen.domain.scanner.ScanError
import com.zenlauncher.zen.domain.scanner.ScanExporter
import com.zenlauncher.zen.domain.scanner.ScanNaming
import com.zenlauncher.zen.domain.scanner.ScanPage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.OutputStream
import java.util.Calendar
import java.util.TimeZone

/**
 * Sacar el escaneo al telefono: la imagen a Imagenes/Zen y el PDF a Documentos/Zen.
 *
 * Por `MediaStore`, que es la API vigente y **no necesita ningun permiso** para lo que
 * escribe la propia aplicacion. Zen no pide almacenamiento, igual que no lo pide el
 * selector de documentos de Lectura ni el de fotos de Notas: el permiso se evita
 * eligiendo la API correcta, no pidiendolo "por si acaso".
 *
 * Se escribe con `IS_PENDING` a 1 y se libera al terminar. Sin eso, la galeria del
 * telefono ensena el fichero mientras todavia se esta escribiendo, y un PDF de seis
 * paginas a medio escribir aparece como un documento roto.
 */
class AndroidScanExporter(
    context: Context,
    private val clock: ZenClock,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ScanExporter {

    private val appContext = context.applicationContext

    override suspend fun exportImage(page: ScanPage): ExportResult = withContext(io) {
        val bytes = runCatching { File(page.renderedPath).readBytes() }.getOrNull()
            ?: return@withContext ExportResult.Failed(ScanError.SAVE_FAILED)

        spaceProblem(bytes.size.toLong())?.let { return@withContext it }

        val name = ScanNaming.imageName(stamp(), pageNumber = 1, totalPages = 1)
        write(
            collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            name = name,
            mimeType = "image/jpeg",
            relativePath = "${Environment.DIRECTORY_PICTURES}/$ALBUM",
            location = "${Environment.DIRECTORY_PICTURES}/$ALBUM",
        ) { stream -> stream.write(bytes) }
    }

    override suspend fun exportPdf(pages: List<ScanPage>): ExportResult = withContext(io) {
        if (pages.isEmpty()) return@withContext ExportResult.Failed(ScanError.SAVE_FAILED)

        // Se estima el sitio con el tamano de las imagenes de partida: el PDF pesa algo
        // menos, asi que la comprobacion peca de prudente, que es lo que se quiere.
        val estimate = pages.sumOf { runCatching { File(it.renderedPath).length() }.getOrDefault(0L) }
        spaceProblem(estimate)?.let { return@withContext it }

        val name = ScanNaming.pdfName(stamp())
        write(
            collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            name = name,
            mimeType = "application/pdf",
            relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$ALBUM",
            location = "${Environment.DIRECTORY_DOCUMENTS}/$ALBUM",
        ) { stream -> renderPdf(pages, stream) }
    }

    /**
     * Una hoja del PDF por escaneo, en orden.
     *
     * Las paginas se decodifican **de una en una** y se reciclan al terminar. Un escaneo
     * son unos 16 MB de bitmap; seis a la vez son cien, y esto corre en el proceso de la
     * pantalla de inicio. Ver `LauncherMemory`.
     */
    private fun renderPdf(pages: List<ScanPage>, stream: OutputStream) {
        val document = PdfDocument()
        try {
            pages.forEachIndexed { index, page ->
                val bitmap = BitmapFactory.decodeFile(page.renderedPath) ?: return@forEachIndexed
                try {
                    val (width, height) = PdfPageSize.forImage(bitmap.width, bitmap.height)
                    val info = PdfDocument.PageInfo.Builder(width, height, index + 1).create()
                    val sheet = document.startPage(info)
                    drawPage(sheet.canvas, bitmap, page.text?.words.orEmpty(), width, height)
                    document.finishPage(sheet)
                } finally {
                    bitmap.recycle()
                }
            }
            document.writeTo(stream)
        } finally {
            document.close()
        }
    }

    /**
     * La imagen y, si hay OCR, la capa de texto seleccionable.
     *
     * **El texto se pinta primero y la imagen encima**, y no al reves con tinta
     * transparente. `PdfDocument` dibuja a traves de Skia, que no expone el modo de
     * renderizado invisible del formato PDF (el modo 3, que es como lo hacen las
     * herramientas de OCR de escritorio) y ademas puede descartar del todo un trazo con
     * alfa cero. Pintando debajo, el texto queda igual de escondido a la vista y sigue
     * estando en la capa de texto del documento, que es de donde lo saca el visor al
     * seleccionar o al buscar.
     *
     * Cada palabra se dibuja en su sitio y con el cuerpo que le corresponde a su caja, no
     * con un tamano fijo: si las posiciones no coinciden con lo que se ve, seleccionar
     * arrastrando devuelve palabras de otra linea.
     */
    private fun drawPage(
        canvas: Canvas,
        bitmap: Bitmap,
        words: List<RecognizedWord>,
        width: Int,
        height: Int,
    ) {
        if (words.isNotEmpty()) {
            val paint = Paint().apply {
                isAntiAlias = true
                color = Color.BLACK
            }
            for (word in words) {
                val boxHeight = word.height * height
                val boxWidth = word.width * width
                if (boxHeight <= 0f || boxWidth <= 0f) continue

                paint.textSize = boxHeight
                val measured = paint.measureText(word.text)
                // Se aprieta o se estira el cuerpo para que la palabra ocupe su caja: es
                // lo que hace que el rectangulo de seleccion caiga sobre las letras que
                // se ven, y no dos milimetros mas alla.
                if (measured > 0f) paint.textScaleX = boxWidth / measured

                canvas.drawText(
                    word.text,
                    word.left * width,
                    // La base de la linea, no el borde de arriba: `drawText` mide desde
                    // ahi, y usar el borde subiria cada palabra su propio alto.
                    word.bottom * height,
                    paint,
                )
                paint.textScaleX = 1f
            }
        }

        canvas.drawBitmap(bitmap, null, Rect(0, 0, width, height), null)
    }

    /**
     * Escribe en `MediaStore` y siempre deja el registro consistente.
     *
     * Si algo falla a mitad, la entrada a medio escribir se borra: dejarla con
     * `IS_PENDING` a 1 la esconde de la galeria pero la deja ocupando sitio para siempre,
     * y el usuario no tiene ninguna forma de verla para borrarla.
     */
    private fun write(
        collection: Uri,
        name: String,
        mimeType: String,
        relativePath: String,
        location: String,
        body: (OutputStream) -> Unit,
    ): ExportResult {
        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        var uri: Uri? = null
        return try {
            uri = resolver.insert(collection, values)
                ?: return ExportResult.Failed(ScanError.SAVE_FAILED)

            resolver.openOutputStream(uri)?.use(body)
                ?: return ExportResult.Failed(ScanError.SAVE_FAILED)

            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null,
            )
            ExportResult.Saved(location = location, displayName = name)
        } catch (error: Throwable) {
            Log.w(TAG, "No se pudo guardar $name", error)
            uri?.let { runCatching { resolver.delete(it, null, null) } }
            ExportResult.Failed(
                if (error is java.io.IOException && error.isOutOfSpace()) {
                    ScanError.OUT_OF_SPACE
                } else {
                    ScanError.SAVE_FAILED
                },
            )
        }
    }

    /**
     * Se comprueba el sitio **antes** de empezar.
     *
     * Quedarse sin disco a mitad de escribir un PDF deja un fichero truncado que el visor
     * abre en blanco, y el usuario cree que Zen genera PDF rotos. Avisar antes es la
     * diferencia entre un problema que tiene arreglo y uno que parece un fallo.
     */
    private fun spaceProblem(needed: Long): ExportResult.Failed? {
        val free = runCatching {
            val directory = Environment.getExternalStorageDirectory() ?: return null
            StatFs(directory.path).availableBytes
        }.getOrNull() ?: return null

        return if (free < needed + SPACE_MARGIN_BYTES) {
            ExportResult.Failed(ScanError.OUT_OF_SPACE)
        } else {
            null
        }
    }

    /**
     * El reloj de pared desmenuzado en hora local, que es lo que espera [ScanNaming].
     *
     * La zona horaria se lee aqui y no dentro de `ScanNaming` a proposito: es un dato del
     * dispositivo, y con ella dentro esa funcion dejaria de ser pura y de poder probarse.
     */
    private fun stamp(): ScanNaming.Stamp {
        val calendar = Calendar.getInstance(TimeZone.getDefault())
        calendar.timeInMillis = clock.wallTimeMillis()
        return ScanNaming.Stamp(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH) + 1,
            day = calendar.get(Calendar.DAY_OF_MONTH),
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE),
            second = calendar.get(Calendar.SECOND),
        )
    }

    private fun java.io.IOException.isOutOfSpace(): Boolean =
        message?.contains("ENOSPC", ignoreCase = true) == true ||
            message?.contains("No space", ignoreCase = true) == true

    private companion object {
        const val TAG = "ZenScanner"
        const val ALBUM = "Zen"

        /** Colchon para no dejar el telefono al borde de llenarse. */
        const val SPACE_MARGIN_BYTES = 8L * 1024 * 1024
    }
}
