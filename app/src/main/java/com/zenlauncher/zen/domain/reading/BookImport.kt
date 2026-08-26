package com.zenlauncher.zen.domain.reading

/** Por que no se pudo importar. Cada motivo se dice con palabras distintas al usuario. */
enum class ImportFailure {
    /**
     * El telefono no sabe extraer texto de un PDF: Android anterior al 15. Es el unico
     * motivo que no depende del fichero elegido, y por eso ni siquiera se llega a abrir
     * el selector de documentos cuando pasa.
     */
    UNSUPPORTED,

    /** El fichero no se pudo abrir: no es un PDF, esta corrupto o lleva contrasena. */
    UNREADABLE,

    /**
     * Se abrio y no tiene ni una letra: es un escaneo, paginas de imagen.
     *
     * Es el caso que resolveria el OCR, y por eso se distingue de [UNREADABLE] en lugar
     * de meterlos en un "no se pudo" generico: al usuario se le puede decir exactamente
     * que le pasa a su fichero.
     */
    NO_TEXT,
}

/**
 * En que va la importacion.
 *
 * Vive en el dominio y no en la pantalla porque **la importacion no muere con la
 * pantalla**: leer 400 hojas tarda, y quien se va a la home a mirar la hora tiene que
 * encontrarla terminada al volver.
 */
sealed interface ImportState {

    /** No hay ninguna importacion en marcha ni nada que contar sobre la ultima. */
    data object Idle : ImportState

    /** Sacando el texto. `total` es el numero de paginas del PDF. */
    data class Reading(val page: Int, val total: Int) : ImportState

    /** Ya esta todo el texto: ahora se busca el indice y se rehacen los parrafos. */
    data object Building : ImportState

    data class Done(val bookId: String, val title: String) : ImportState

    data class Failed(val reason: ImportFailure) : ImportState

    val busy: Boolean get() = this is Reading || this is Building
}
