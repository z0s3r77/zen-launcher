package com.zenlauncher.zen.domain.notes

/**
 * Donde viven las imagenes de las notas.
 *
 * Es una interfaz para que el ViewModel de captura no sepa nada de `ContentResolver` ni
 * de ficheros, y para poder probar el guardado sin tocar disco.
 */
interface AttachmentStore {

    /**
     * Copia una imagen elegida en el selector del sistema al almacenamiento propio de
     * Zen y devuelve el adjunto ya listo para guardar.
     *
     * **Copia y no referencia.** La URI que devuelve el selector deja de valer al poco
     * y apunta a un fichero que el usuario puede borrar de la galeria: una nota que
     * pierde su foto por limpiar el carrete no es un sitio donde guardar ideas.
     *
     * @return null si no se pudo leer la imagen. Degradar: la nota se guarda sin ella.
     */
    suspend fun storeImage(noteId: String, sourceUri: String): NoteAttachment?

    /** Borra las imagenes de una nota. Se usa al borrarla y al descartar la captura. */
    suspend fun deleteFor(noteId: String)

    /** Ruta absoluta de un adjunto, para pintarlo. */
    fun absolutePath(relativePath: String): String
}
