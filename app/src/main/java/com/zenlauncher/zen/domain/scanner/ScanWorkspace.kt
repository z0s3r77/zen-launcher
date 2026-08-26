package com.zenlauncher.zen.domain.scanner

/** Cual de los tres ficheros de una pagina. Ver [ScanPage]. */
enum class ScanFile { ORIGINAL, RECTIFIED, RENDERED }

/**
 * Donde viven las paginas mientras se escanean.
 *
 * Es almacenamiento **privado** de la aplicacion: un escaneo a medias no tiene por que
 * aparecer en la galeria del telefono, y solo sale de aqui cuando el usuario guarda
 * (ver [ScanExporter]). Por eso no hace falta ningun permiso de almacenamiento.
 *
 * Se limpia sola al terminar: dejar una carpeta con fotos a resolucion completa de cada
 * intento abandonado llenaria el disco sin que nada lo mencione en ninguna pantalla.
 */
interface ScanWorkspace {

    suspend fun write(pageId: String, file: ScanFile, bytes: ByteArray): String?

    suspend fun read(path: String): ByteArray?

    suspend fun deletePage(pageId: String)

    /** Borra todo lo que quedo de escaneos anteriores. Se llama al abrir el escaner. */
    suspend fun clear()
}

/** Lo que paso al intentar guardar. */
sealed interface ExportResult {
    /** @param location donde quedo, en palabras, para poder decirlo en pantalla. */
    data class Saved(val location: String, val displayName: String) : ExportResult

    data class Failed(val error: ScanError) : ExportResult
}

/**
 * Sacar el resultado del escaner al telefono.
 *
 * Escribe por `MediaStore`, que es la API moderna y **no necesita ningun permiso** para
 * los ficheros que crea la propia aplicacion: Zen no pide almacenamiento, igual que no
 * lo pide el selector de documentos de Lectura ni el de fotos de Notas.
 */
interface ScanExporter {

    /** Una pagina como imagen, a Imagenes/Zen. */
    suspend fun exportImage(page: ScanPage): ExportResult

    /**
     * Todas las paginas como un PDF, a Documentos/Zen: una pagina del PDF por escaneo.
     *
     * Si las paginas traen texto reconocido, va dentro como **capa seleccionable** encima
     * de la imagen y en tinta invisible, no como un texto suelto al final.
     */
    suspend fun exportPdf(pages: List<ScanPage>): ExportResult
}
