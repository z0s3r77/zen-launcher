package com.zenlauncher.zen.domain.reading

/**
 * Un fragmento subrayado, con o sin nota.
 *
 * Subrayar y anotar son **la misma cosa** con y sin texto detras, no dos funciones: en un
 * libro se subraya, y a veces al lado se escribe algo. Separarlas obligaria a elegir
 * antes de saber si vas a tener algo que decir, y dejaria dos listas que hablan del mismo
 * párrafo.
 *
 * @param start posicion dentro del bloque; [end] es exclusivo.
 * @param text el texto subrayado, copiado. Se guarda copiado a proposito: la lista de
 *   subrayados se lee sin cargar el libro entero, y ese es el sitio donde uno repasa.
 * @param note null cuando es solo subrayado. Vacio y null son lo mismo aqui.
 */
data class Highlight(
    val id: String,
    val bookId: String,
    val blockIndex: Int,
    val start: Int,
    val end: Int,
    val text: String,
    val note: String?,
    val page: Int,
    val createdAtMillis: Long,
) {
    val position: ReadingPosition get() = ReadingPosition(blockIndex, start)

    val hasNote: Boolean get() = !note.isNullOrBlank()
}

/** Un subrayado colocado dentro de un trozo de pagina, con posiciones **relativas**. */
data class HighlightSpan(val id: String, val start: Int, val end: Int, val hasNote: Boolean)

/**
 * Coloca los subrayados dentro de un trozo de pagina. Puro.
 *
 * Hace falta porque un subrayado vive en coordenadas del **bloque** y el texto que se
 * pinta es un **trozo** del bloque: al partir un parrafo entre dos paginas, un subrayado
 * que cruce el corte tiene que salir a medias en cada una. Sin recortarlo, pintarlo daria
 * posiciones fuera de rango y la pagina reventaria.
 */
object HighlightSpans {

    fun inFragment(fragment: PageFragment, highlights: List<Highlight>): List<HighlightSpan> =
        highlights.asSequence()
            .filter { it.blockIndex == fragment.blockIndex }
            .mapNotNull { highlight ->
                val start = maxOf(highlight.start, fragment.start)
                val end = minOf(highlight.end, fragment.end)
                if (start >= end) {
                    null
                } else {
                    HighlightSpan(
                        id = highlight.id,
                        start = start - fragment.start,
                        end = end - fragment.start,
                        hasNote = highlight.hasNote,
                    )
                }
            }
            .sortedBy { it.start }
            .toList()

    /** El subrayado que hay en una posicion del bloque, si lo hay. Para poder tocarlo. */
    fun at(blockIndex: Int, offset: Int, highlights: List<Highlight>): Highlight? =
        highlights.firstOrNull {
            it.blockIndex == blockIndex && offset >= it.start && offset < it.end
        }
}
