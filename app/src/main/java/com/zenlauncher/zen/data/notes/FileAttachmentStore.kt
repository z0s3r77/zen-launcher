package com.zenlauncher.zen.data.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.domain.notes.AttachmentKind
import com.zenlauncher.zen.domain.notes.AttachmentStore
import com.zenlauncher.zen.domain.notes.NoteAttachment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Imagenes en el almacenamiento privado de la aplicacion, una carpeta por nota.
 *
 * `filesDir` y no la galeria: lo que se apunta en Zen no tiene por que aparecer en el
 * carrete ni en ninguna otra aplicacion, y ahi dentro nada externo puede borrarlo.
 */
class FileAttachmentStore(
    context: Context,
    private val clock: ZenClock,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : AttachmentStore {

    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, ROOT_DIR)

    override suspend fun storeImage(noteId: String, sourceUri: String): NoteAttachment? =
        withContext(io) {
            // Todo el camino degrada a null: una foto que no se puede leer no puede
            // impedir que se guarde la idea que el usuario acaba de escribir.
            runCatching {
                val uri = Uri.parse(sourceUri)
                val bitmap = decodeDownscaled(uri) ?: return@runCatching null

                val dir = File(root, noteId).apply { mkdirs() }
                val file = File(dir, "${UUID.randomUUID()}.jpg")
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                bitmap.recycle()

                NoteAttachment(
                    id = UUID.randomUUID().toString(),
                    noteId = noteId,
                    kind = AttachmentKind.IMAGE,
                    value = "$ROOT_DIR/$noteId/${file.name}",
                    createdAtMillis = clock.wallTimeMillis(),
                )
            }.getOrNull()
        }

    override suspend fun deleteFor(noteId: String) {
        withContext(io) {
            File(root, noteId).deleteRecursively()
        }
    }

    override fun absolutePath(relativePath: String): String =
        File(appContext.filesDir, relativePath).absolutePath

    /**
     * Decodifica reduciendo el tamano en la propia lectura.
     *
     * Una foto del Phone (2a) son 50 megapixeles: descomprimirla entera para guardarla
     * son cientos de megabytes de bitmap en memoria, dentro del proceso del **launcher**.
     * `inSampleSize` la reduce mientras se lee, asi que el pico nunca llega a existir.
     *
     * [MAX_EDGE] es de sobra para releer una nota en un movil, y evita que apuntar diez
     * ideas con foto se coma un giga de almacenamiento.
     */
    private fun decodeDownscaled(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        } ?: return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
        }
        return appContext.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    private companion object {
        const val ROOT_DIR = "notas"
        const val MAX_EDGE = 2048
        const val JPEG_QUALITY = 85

        /** Potencia de dos, que es lo unico que `inSampleSize` respeta de verdad. */
        fun sampleSize(width: Int, height: Int): Int {
            var sample = 1
            var longest = maxOf(width, height)
            while (longest / 2 >= MAX_EDGE) {
                longest /= 2
                sample *= 2
            }
            return sample
        }
    }
}
