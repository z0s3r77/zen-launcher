package com.zenlauncher.zen.domain.scanner

/**
 * Una pagina escaneada.
 *
 * Guarda **tres** ficheros y no uno, y cada uno esta por algo:
 *
 * - `originalPath`: la foto tal como salio de la camara. Es lo que permite volver a
 *   mover las esquinas media hora despues sin haber perdido nada. Sin ella, arrastrar
 *   una esquina hacia fuera sacaria pixeles que ya no existen.
 * - `rectifiedPath`: la hoja enderezada y recortada, **sin filtro**. De aqui salen todos
 *   los modos, asi que cambiar de filtro nunca degrada lo anterior.
 * - `renderedPath`: la enderezada con el modo puesto. Es lo que se ve, lo que se guarda
 *   como imagen y lo que va al PDF.
 */
data class ScanPage(
    val id: String,
    val originalPath: String,
    val rectifiedPath: String,
    val renderedPath: String,
    val quad: Quad,
    val quarterTurns: Int,
    val filter: ScanFilter,
    /** Nitidez de la foto de origen, 0..1. Por debajo de [BLURRY_LIMIT] se avisa. */
    val sharpness: Float,
    val text: RecognizedText? = null,
    /**
     * Sube cada vez que se reescribe `renderedPath`.
     *
     * Las tres rutas de una pagina no cambian al reprocesarla —se sobrescribe el mismo
     * fichero—, asi que sin esto la pantalla, que recuerda el bitmap por su ruta, seguiria
     * ensenando el filtro anterior despues de cambiarlo. Es un numero y no una ruta nueva
     * por pagina para no ir dejando ficheros sueltos en la cache con cada retoque.
     */
    val revision: Int = 0,
) {
    val blurry: Boolean get() = sharpness in 0f..BLURRY_LIMIT

    companion object {
        /**
         * Por debajo de esto la foto se considera movida.
         *
         * Se calibro mirando el dispositivo, no en un test: la varianza del laplaciano
         * depende de cuanta tinta hay en la hoja, y un folio con dos lineas da menos
         * varianza que una pagina llena aunque las dos esten nitidas. Por eso solo
         * **avisa** y no descarta nada.
         */
        const val BLURRY_LIMIT = 0.12f
    }
}

/**
 * El documento que se esta montando: una o varias paginas en orden.
 *
 * Es un dato, no un servicio. Vive en el ViewModel mientras dura la pantalla y se
 * convierte en ficheros al guardar; que sea inmutable es lo que deja rehacer una pagina
 * concreta sin tocar las demas.
 */
data class ScanDocument(
    val pages: List<ScanPage> = emptyList(),
) {
    val empty: Boolean get() = pages.isEmpty()
    val pageCount: Int get() = pages.size

    fun replace(page: ScanPage): ScanDocument =
        copy(pages = pages.map { if (it.id == page.id) page else it })

    fun add(page: ScanPage): ScanDocument = copy(pages = pages + page)

    fun remove(pageId: String): ScanDocument =
        copy(pages = pages.filterNot { it.id == pageId })

    fun page(pageId: String?): ScanPage? = pages.firstOrNull { it.id == pageId }

    // No hay un "todo el texto del documento". El texto se lee y se copia **por pagina**,
    // que es la unidad que el usuario tiene delante cuando lo pide; un boton que copiara
    // seis paginas seguidas dejaria en el portapapeles algo que nadie ha visto entero.
}
