package com.zenlauncher.zen.data.reading

import android.content.Context
import com.zenlauncher.zen.domain.reading.BookCoverStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Las portadas en `filesDir/lectura/<libro>/portada.jpg`. Una carpeta por libro. */
class FileBookCoverStore(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : BookCoverStore {

    private val appContext = context.applicationContext
    private val root = File(appContext.filesDir, ROOT_DIR)

    override suspend fun store(bookId: String, jpeg: ByteArray): String? = withContext(io) {
        // Degrada a null como todo lo demas: quedarse sin portada no puede impedir que
        // el libro se importe, que es lo unico que el usuario ha pedido.
        runCatching {
            val dir = File(root, bookId).apply { mkdirs() }
            File(dir, COVER_NAME).writeBytes(jpeg)
            "$ROOT_DIR/$bookId/$COVER_NAME"
        }.getOrNull()
    }

    override suspend fun deleteFor(bookId: String) {
        withContext(io) { File(root, bookId).deleteRecursively() }
    }

    override fun absolutePath(relativePath: String): String =
        File(appContext.filesDir, relativePath).absolutePath

    private companion object {
        const val ROOT_DIR = "lectura"
        const val COVER_NAME = "portada.jpg"
    }
}
