package com.zenlauncher.zen.domain.reading

/**
 * Por donde va la lectura. Puro y sin Android.
 *
 * La cuenta se lleva en **bloques**, no en paginas: el texto es reflowable, asi que la
 * pagina que se ve depende del tamano de letra que este puesto ahora. Guardar "pagina
 * 87" y subir el cuerpo dos escalones dejaria al usuario en otro sitio del libro sin
 * haber tocado nada.
 *
 * La pagina se sigue **ensenando** —"pagina 87 de 342"— porque es como se habla de un
 * libro y como se cita en clase. Solo que se deriva del bloque, en lugar de ser el dato.
 */
object ReadingProgress {

    /** De 0 a 1. Un libro sin bloques esta sin empezar, no terminado. */
    fun fraction(blockIndex: Int, blockCount: Int): Float {
        if (blockCount <= 1) return 0f
        return (blockIndex.toFloat() / (blockCount - 1)).coerceIn(0f, 1f)
    }

    /** El tanto por ciento redondeado, que es lo unico que se ensena en la biblioteca. */
    fun percent(blockIndex: Int, blockCount: Int): Int =
        (fraction(blockIndex, blockCount) * 100).toInt().coerceIn(0, 100)

    /**
     * En que capitulo cae un bloque.
     *
     * El **ultimo** capitulo que empieza antes o en ese bloque, no el mas cercano: si
     * estas en el bloque 400 y el capitulo 3 empieza en el 380 y el 4 en el 420, estas
     * en el 3 aunque el 4 quede mas cerca.
     */
    fun chapterAt(blockIndex: Int, chapters: List<BookChapter>): BookChapter? =
        chapters.lastOrNull { it.blockIndex <= blockIndex }

    /** La barra de progreso en caracteres, para leerla en voz alta y para la lista. */
    fun bar(blockIndex: Int, blockCount: Int, width: Int = BAR_WIDTH): String {
        val filled = (fraction(blockIndex, blockCount) * width).toInt().coerceIn(0, width)
        return FULL.repeat(filled) + EMPTY.repeat(width - filled)
    }

    const val BAR_WIDTH = 12

    private const val FULL = "█"
    private const val EMPTY = "░"
}
