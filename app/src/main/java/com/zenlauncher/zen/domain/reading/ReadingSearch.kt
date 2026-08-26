package com.zenlauncher.zen.domain.reading

import com.zenlauncher.zen.domain.notes.TextNormalizer

/** Un sitio del libro donde aparece lo que se busco. */
data class ReadingHit(
    val blockIndex: Int,
    val page: Int,
    /** Un trozo de alrededor, para reconocer el sitio sin tener que saltar. */
    val snippet: String,
)

/**
 * Buscar dentro de un libro. Puro y sin Android.
 *
 * Busqueda **literal y normalizada**, no semantica: en un libro se busca una palabra o
 * una cita que se recuerda, no algo parecido. El indice semantico de Notas resuelve otra
 * pregunta —"¿que escribi yo sobre esto?"— y traerlo aqui obligaria a vectorizar miles
 * de parrafos por libro para responder peor.
 *
 * Corre sobre los bloques que el lector ya tiene en memoria, asi que no toca SQLite: un
 * libro entero son unos dos megabytes de texto y ya estan cargados para poder pintarlos.
 */
object ReadingSearch {

    fun find(blocks: List<BookBlock>, query: String, limit: Int = MAX_HITS): List<ReadingHit> {
        val needle = TextNormalizer.normalize(query)
        if (needle.length < MIN_QUERY_LENGTH) return emptyList()

        return blocks.asSequence()
            .mapNotNull { block ->
                val position = TextNormalizer.normalize(block.text).indexOf(needle)
                if (position < 0) {
                    null
                } else {
                    ReadingHit(
                        blockIndex = block.index,
                        page = block.page,
                        snippet = snippet(block.text, position, needle.length),
                    )
                }
            }
            .take(limit)
            .toList()
    }

    /**
     * El trozo de texto alrededor del hallazgo.
     *
     * La posicion viene del texto **normalizado**, que puede tener otra longitud que el
     * original —los espacios se colapsan—, asi que se acota a lo que hay: sin esto, un
     * parrafo con espacios dobles cortaria fuera de rango y reventaria la busqueda.
     */
    internal fun snippet(text: String, position: Int, length: Int): String {
        val start = (position - CONTEXT).coerceIn(0, text.length)
        val end = (position + length + CONTEXT).coerceIn(start, text.length)
        val core = text.substring(start, end).trim()
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < text.length) "…" else ""
        return "$prefix$core$suffix"
    }

    private const val MIN_QUERY_LENGTH = 2
    private const val CONTEXT = 60
    private const val MAX_HITS = 60
}
