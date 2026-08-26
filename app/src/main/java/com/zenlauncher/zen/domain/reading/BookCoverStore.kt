package com.zenlauncher.zen.domain.reading

/**
 * Donde vive la portada rasterizada de un libro.
 *
 * Misma frontera y mismas razones que [com.zenlauncher.zen.domain.notes.AttachmentStore]:
 * el almacenamiento privado de la aplicacion, no la galeria. Una pagina de un libro del
 * usuario no tiene por que aparecer en el carrete del telefono.
 */
interface BookCoverStore {

    /** Guarda el JPEG y devuelve su ruta **relativa** a `filesDir`, o null si no pudo. */
    suspend fun store(bookId: String, jpeg: ByteArray): String?

    suspend fun deleteFor(bookId: String)

    /**
     * La ruta absoluta para poder pintarla.
     *
     * Se guarda relativa y se resuelve aqui, no al reves: la ruta de `filesDir` puede
     * cambiar entre instalaciones y restauraciones, y una ruta absoluta guardada en la
     * base de datos dejaria las portadas apuntando a un sitio que ya no existe.
     */
    fun absolutePath(relativePath: String): String
}
