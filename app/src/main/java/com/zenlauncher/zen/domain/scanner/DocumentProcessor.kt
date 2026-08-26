package com.zenlauncher.zen.domain.scanner

/**
 * Los modos de la imagen ya enderezada.
 *
 * Cinco y no mas, porque cada uno responde a una situacion distinta de verdad. El que
 * importa es [DOCUMENT]: es el que hace que una foto de un folio parezca un escaneo.
 */
enum class ScanFilter {
    /** La foto tal cual, solo enderezada y recortada. */
    ORIGINAL,

    /**
     * El modo del escaner.
     *
     * Divide la imagen por su propia iluminacion —estimada con un desenfoque muy ancho—,
     * lo que quita a la vez la sombra de la mano, el degradado de la lampara y el gris
     * del papel; despues estira el contraste dejando el fondo en blanco y el texto donde
     * estaba. Es una division y no una resta: la sombra multiplica la luz que llega al
     * papel, no la descuenta.
     */
    DOCUMENT,

    /** Dos tonos, con umbral adaptativo. Para fotocopiar y para que el PDF pese poco. */
    BLACK_AND_WHITE,

    /** Como [DOCUMENT] pero apretando mas: para lapiz flojo o tinta muy clara. */
    HIGH_CONTRAST,

    /** Gris sin tocar niveles. Para fotos y diagramas, donde el blanco puro se come el dibujo. */
    GRAYSCALE,
}

/**
 * Enderezar, recortar y mejorar. La otra frontera con OpenCV.
 *
 * Trabaja con JPEG en los dos sentidos, igual que `BookCoverStore` recibe los bytes de
 * la portada: asi el dominio nunca ve un `Bitmap` y la unica capa que sabe de graficos de
 * Android es `data/scanner`.
 *
 * **El original no se toca nunca.** [rectify] devuelve la hoja enderezada *sin* filtro y
 * eso es lo que se guarda; [applyFilter] parte siempre de ahi. Encadenar filtros sobre el
 * resultado del anterior dejaria al usuario sin vuelta atras en cuanto probara dos.
 */
interface DocumentProcessor {

    val available: Boolean

    /**
     * Deja la foto **derecha** y devuelve el JPEG girado.
     *
     * Se hace una sola vez, nada mas capturar, y todo lo que viene despues —detectar,
     * enderezar, filtrar, el OCR y el PDF— trabaja ya sobre una imagen derecha. La
     * alternativa era arrastrar los grados por seis firmas distintas, y basta olvidarlos
     * en una para que el recorte salga girado sin ningun error visible.
     *
     * Tambien es donde se decide **ignorar la etiqueta EXIF** de la foto y girar a mano
     * por los grados que da la camara: quien lee el JPEG despues (ML Kit, el PDF, la
     * galeria del telefono) puede honrar o no esa etiqueta, y una imagen ya girada de
     * verdad se ve igual en todos.
     */
    suspend fun upright(jpeg: ByteArray, rotationDegrees: Int): ByteArray?

    /**
     * Endereza la hoja marcada por `quad` dentro de `jpeg` y la gira `quarterTurns`
     * cuartos de vuelta. Devuelve el JPEG enderezado y sin filtrar, o null si falla.
     */
    suspend fun rectify(jpeg: ByteArray, quad: Quad, quarterTurns: Int): ByteArray?

    /** Aplica un modo sobre una imagen **ya enderezada**. */
    suspend fun applyFilter(rectified: ByteArray, filter: ScanFilter): ByteArray?

    /**
     * Como de nitida esta una foto, entre 0 y 1: la varianza del laplaciano normalizada.
     *
     * Sirve para avisar de que salio movida antes de que el usuario descubra que no se
     * lee. No bloquea nada —una foto borrosa de algo que ya no esta delante es mejor que
     * ninguna—, solo se dice.
     */
    suspend fun sharpness(jpeg: ByteArray): Float
}
