package com.zenlauncher.zen.data.scanner

import android.content.Context
import com.zenlauncher.zen.domain.scanner.ScanFile
import com.zenlauncher.zen.domain.scanner.ScanWorkspace
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Las paginas a medio escanear, en `cacheDir/escaner/<pagina>/`.
 *
 * En `cacheDir` y no en `filesDir`, al reves que las portadas de Lectura, y es
 * deliberado: una portada es parte de un libro que el usuario decidio guardar, y esto son
 * fotos a resolucion completa de un escaneo que a lo mejor abandona a medias. Al estar en
 * la cache, si el disco se llena el sistema puede tirarlas el mismo en lugar de avisar al
 * usuario de que se ha quedado sin sitio por culpa del launcher.
 *
 * No hace falta ningun permiso: es almacenamiento privado de la aplicacion.
 */
class FileScanWorkspace(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : ScanWorkspace {

    private val appContext = context.applicationContext
    private val root = File(appContext.cacheDir, ROOT_DIR)

    override suspend fun write(pageId: String, file: ScanFile, bytes: ByteArray): String? =
        withContext(io) {
            runCatching {
                val directory = File(root, pageId).apply { mkdirs() }
                val target = File(directory, file.fileName())
                target.writeBytes(bytes)
                target.absolutePath
            }.getOrNull()
        }

    override suspend fun read(path: String): ByteArray? = withContext(io) {
        runCatching { File(path).readBytes() }.getOrNull()
    }

    override suspend fun deletePage(pageId: String) {
        withContext(io) { runCatching { File(root, pageId).deleteRecursively() } }
    }

    override suspend fun clear() {
        withContext(io) { runCatching { root.deleteRecursively() } }
    }

    private fun ScanFile.fileName(): String = when (this) {
        ScanFile.ORIGINAL -> "original.jpg"
        ScanFile.RECTIFIED -> "recta.jpg"
        ScanFile.RENDERED -> "final.jpg"
    }

    private companion object {
        const val ROOT_DIR = "escaner"
    }
}
