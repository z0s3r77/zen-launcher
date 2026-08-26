package com.zenlauncher.zen.data.reading

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import com.zenlauncher.zen.domain.reading.PdfDocumentText
import com.zenlauncher.zen.domain.reading.PdfPageText
import com.zenlauncher.zen.domain.reading.PdfTextSource
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Extraccion de texto con el PDF del propio Android. **Sin ninguna libreria.**
 *
 * `PdfRenderer.Page.getTextContents()` llego en Android 15 (API 35) y hace exactamente
 * lo que hace falta: devuelve el texto de la pagina, ya ordenado. Antes de esto, sacar
 * texto de un PDF en Android obligaba a empotrar PdfBox o iText —varios megabytes y una
 * dependencia mas en el arranque del launcher— para reimplementar lo que el sistema ya
 * trae. Se comprobo sobre `android.jar`, no de memoria.
 *
 * `minSdk` es 34, asi que hay un escalon de una version en el que Zen instala y esto no
 * funciona: ver [available]. El dispositivo objetivo es Android 16.
 *
 * **Nada sale del telefono y nada lanza.** Es la unica funcion de Zen que abre ficheros
 * del usuario, y un PDF corrupto no puede dejar el telefono sin pantalla de inicio.
 */
class AndroidPdfTextSource(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : PdfTextSource {

    private val appContext = context.applicationContext

    override val available: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    override suspend fun read(
        uri: String,
        onProgress: (page: Int, total: Int) -> Unit,
    ): PdfDocumentText? = withContext(io) {
        if (!available) return@withContext null
        withRenderer(uri) { renderer ->
            val total = renderer.pageCount
            val pages = ArrayList<PdfPageText>(total)
            for (index in 0 until total) {
                // Importar un libro de 400 paginas tarda; si el usuario se va, se para.
                // Sin esto, la importacion cancelada seguiria leyendo hojas hasta el
                // final dentro del proceso del launcher.
                coroutineContext.ensureActive()
                pages += PdfPageText(page = index, text = textOf(renderer, index))
                onProgress(index + 1, total)
            }
            PdfDocumentText(pages = pages, fileName = displayName(uri))
        }
    }

    override suspend fun renderCover(uri: String, maxEdgePx: Int): ByteArray? = withContext(io) {
        if (!available) return@withContext null
        withRenderer(uri) { renderer ->
            if (renderer.pageCount == 0) return@withRenderer null
            renderer.openPage(0).use { page ->
                val scale = minOf(
                    maxEdgePx.toFloat() / page.width,
                    maxEdgePx.toFloat() / page.height,
                    1f,
                )
                val bitmap = Bitmap.createBitmap(
                    (page.width * scale).toInt().coerceAtLeast(1),
                    (page.height * scale).toInt().coerceAtLeast(1),
                    Bitmap.Config.ARGB_8888,
                )
                // El bitmap nace transparente y `render` **compone encima** en lugar de
                // sustituir: una portada con fondo blanco de papel saldria con las letras
                // sobre nada, y al pintarla sobre el negro de Zen no se leeria.
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, COVER_QUALITY, out)
                bitmap.recycle()
                out.toByteArray()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun textOf(renderer: PdfRenderer, index: Int): String =
        // Solo se puede tener una pagina abierta a la vez, de ahi el `use` por pagina.
        runCatching {
            renderer.openPage(index).use { page ->
                page.textContents.joinToString(separator = "\n") { it.text }
            }
        }.getOrDefault("")

    /**
     * Abre el documento y garantiza que se cierra, pase lo que pase.
     *
     * Se intenta primero con el descriptor que da el proveedor. `PdfRenderer` **exige un
     * fichero por el que poder saltar hacia atras y hacia delante**, y hay proveedores
     * —Drive, algunas aplicaciones de correo— que entregan una tuberia, que no lo es. En
     * ese caso se copia a la cache y se abre la copia; la copia se borra siempre.
     */
    private fun <T> withRenderer(uri: String, body: (PdfRenderer) -> T?): T? {
        var copy: File? = null
        return runCatching {
            val descriptor = open(uri) ?: run {
                copy = copyToCache(uri) ?: return@runCatching null
                ParcelFileDescriptor.open(copy, ParcelFileDescriptor.MODE_READ_ONLY)
            }
            descriptor.use { PdfRenderer(it).use(body) }
        }.getOrNull().also { copy?.delete() }
    }

    private fun open(uri: String): ParcelFileDescriptor? =
        runCatching {
            appContext.contentResolver.openFileDescriptor(Uri.parse(uri), "r")
        }.getOrNull()

    private fun copyToCache(uri: String): File? = runCatching {
        val file = File.createTempFile("lectura", ".pdf", appContext.cacheDir)
        appContext.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
            file.outputStream().use(input::copyTo)
        } ?: return null
        file
    }.getOrNull()

    /**
     * El nombre del fichero elegido.
     *
     * Es lo unico parecido a un metadato que da el sistema: `PdfRenderer` no expone el
     * diccionario `/Info` de un PDF, asi que ni el titulo ni el autor se pueden leer de
     * ahi. Ver [com.zenlauncher.zen.domain.reading.BookMetadata].
     */
    private fun displayName(uri: String): String = runCatching {
        appContext.contentResolver.query(Uri.parse(uri), null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) cursor.getString(column) else null
        }
    }.getOrNull().orEmpty()

    private companion object {
        const val COVER_QUALITY = 80
    }
}
