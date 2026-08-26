package com.zenlauncher.zen.domain.reading

/**
 * Un libro importado, ya convertido en texto.
 *
 * **No guarda el PDF.** Guarda el `sourceUri` que devolvio el selector de documentos
 * —con permiso persistente, para poder volver a leerlo— y el texto ya extraido en la
 * base de datos. Un libro de filosofia de 350 paginas son unos dos megabytes de texto
 * frente a los diez o veinte del PDF, y el lector no vuelve a abrir el fichero nunca:
 * si el usuario mueve o borra el original, lo que ya se importo se sigue leyendo.
 *
 * @param lastPosition por donde iba la lectura. Es una posicion de texto y no una
 *   pagina a proposito: el texto es reflowable, asi que "pagina" depende del tamano de
 *   letra que tenga puesto ahora mismo y manana no significaria lo mismo.
 */
data class Book(
    val id: String,
    val title: String,
    val author: String?,
    val sourceUri: String,
    /** Ruta relativa a `filesDir` de la portada ya rasterizada, o null si no se pudo. */
    val coverPath: String?,
    val pageCount: Int,
    val blockCount: Int,
    val importedAtMillis: Long,
    val lastReadAtMillis: Long?,
    val lastPosition: ReadingPosition = ReadingPosition.Start,
) {
    /** Atajo: el bloque por el que va, que es de donde sale el porcentaje. */
    val lastBlockIndex: Int get() = lastPosition.blockIndex
}

/**
 * Que es un bloque de texto.
 *
 * Solo dos: lo que se lee y lo que titula. No hay cita, ni nota al pie, ni imagen: la
 * extraccion de texto de Android no da esa informacion y **inventarla seria peor que no
 * tenerla**, que es la misma regla que sigue el analisis de la portada de noticias.
 */
enum class BlockKind { HEADING, PARAGRAPH }

/**
 * Una unidad de texto del libro reconstruido.
 *
 * El indice es la posicion absoluta dentro del libro y es lo que ancla todo: el
 * progreso, los saltos del indice y los resultados de la busqueda. Nunca cambia; el
 * tamano de letra si.
 */
data class BookBlock(
    val index: Int,
    val kind: BlockKind,
    val text: String,
    /** Pagina del PDF de la que salio. Es lo que permite decir "pagina 87 de 342". */
    val page: Int,
    /** 1 capitulo, 2 seccion, 3 subseccion. Cero en los parrafos. */
    val level: Int = 0,
)

/**
 * Una entrada del indice navegable.
 *
 * Plano con un nivel, no un arbol de capitulos que contienen secciones. El arbol seria
 * fiel al modelo mental de un libro pero aqui no aporta nada: lo unico que se hace con
 * el indice es pintarlo con sangria y saltar a un bloque, y las dos cosas salen del
 * nivel. Un arbol obligaria ademas a decidir que hacer con una seccion que aparece
 * antes que su capitulo, que en un PDF real pasa.
 */
data class BookChapter(
    val title: String,
    val level: Int,
    /** A que bloque se salta. Es la unica forma de navegar que entiende el lector. */
    val blockIndex: Int,
    /** La pagina del PDF, para poder decir de donde viene. */
    val page: Int,
)

/**
 * El libro tal y como sale del analisis, todavia sin id ni fechas.
 *
 * Existe para que [BookBuilder] pueda ser puro: lo que devuelve no sabe nada de la base
 * de datos ni del reloj.
 */
data class BuiltBook(
    val title: String,
    val author: String?,
    val pageCount: Int,
    val blocks: List<BookBlock>,
    val chapters: List<BookChapter>,
) {
    /** Sin un solo parrafo no hay libro: es un PDF escaneado o vacio. */
    val readable: Boolean get() = blocks.any { it.kind == BlockKind.PARAGRAPH }
}
