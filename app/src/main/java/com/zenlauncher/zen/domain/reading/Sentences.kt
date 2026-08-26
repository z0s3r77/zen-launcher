package com.zenlauncher.zen.domain.reading

/** Un trozo de texto. [end] es exclusivo. */
data class TextSpan(val start: Int, val end: Int) {
    val empty: Boolean get() = start >= end
}

/**
 * Frases dentro de un parrafo. Puro y sin Android.
 *
 * **La frase es la unidad con la que se subraya**, y es una decision, no una limitacion
 * tecnica. La alternativa era reimplementar la seleccion con manillas de arrastre: son
 * cientos de lineas fragiles, en un movil se arrastra tapando con el dedo justo lo que se
 * quiere marcar, y en un libro de filosofia lo que se subraya casi siempre **es** una
 * frase entera ("El hombre está condenado a ser libre"). Mantener pulsado coge la frase
 * de debajo del dedo, y `MÁS` va anadiendo las siguientes.
 *
 * No hay diccionario de abreviaturas: un punto solo cierra frase si detras viene un
 * espacio y luego una mayuscula, o si es el final del texto. Con eso, "cfr." y "op. cit."
 * no parten la frase, que es el caso que aparece de verdad en un libro academico.
 */
object Sentences {

    /** La frase que contiene [offset]. */
    fun at(text: String, offset: Int): TextSpan {
        if (text.isEmpty()) return TextSpan(0, 0)
        val position = offset.coerceIn(0, text.lastIndex)
        return TextSpan(start = startOfSentence(text, position), end = endOfSentence(text, position))
    }

    /**
     * Anade la frase siguiente. Si ya llega al final del parrafo, se queda como esta: el
     * subrayado no salta de bloque, porque un subrayado que cruzara parrafos no se podria
     * recortar por pagina sin partirse en dos cosas distintas.
     */
    fun extend(text: String, span: TextSpan): TextSpan {
        if (span.end >= text.length) return span
        val next = at(text, span.end)
        return if (next.end <= span.end) span else TextSpan(span.start, next.end)
    }

    private fun startOfSentence(text: String, position: Int): Int {
        var index = position
        while (index > 0) {
            if (closesSentence(text, index - 1)) return skipSpaces(text, index)
            index--
        }
        return 0
    }

    private fun endOfSentence(text: String, position: Int): Int {
        var index = position
        while (index < text.length) {
            // El cierre se lleva lo que va pegado detras del punto: «...libre.» acaba en
            // la comilla, no en el punto. Sin esto, subrayar una cita dejaba fuera su
            // propio cierre y la frase siguiente empezaba por un signo suelto.
            if (closesSentence(text, index)) {
                var end = index + 1
                while (end < text.length && text[end] in CLOSERS) end++
                return end
            }
            index++
        }
        return text.length
    }

    /**
     * El caracter de [index] cierra frase.
     *
     * El cierre puede llevar comillas o parentesis detras del punto —«...libre.» o
     * (...libre.)— y esos van dentro de la frase, no fuera.
     */
    private fun closesSentence(text: String, index: Int): Boolean {
        if (text[index] !in TERMINATORS && text[index] !in CLOSERS) return false
        if (text[index] in CLOSERS) {
            // Un cierre solo cuenta si lo que tiene delante ya cerraba frase.
            val before = index - 1
            if (before < 0 || text[before] !in TERMINATORS) return false
        }

        var next = index + 1
        if (next >= text.length) return true
        // Se salta lo que se pega al punto sin ser texto nuevo.
        while (next < text.length && text[next] in CLOSERS) next++
        if (next >= text.length) return true
        if (!text[next].isWhitespace()) return false

        val after = text.drop(next).firstOrNull { !it.isWhitespace() } ?: return true
        return after.isUpperCase() || after in OPENERS
    }

    private fun skipSpaces(text: String, from: Int): Int {
        var index = from
        while (index < text.length && text[index].isWhitespace()) index++
        return index
    }

    private val TERMINATORS = charArrayOf('.', '?', '!', '…')
    private val CLOSERS = charArrayOf('»', '"', '\'', ')', ']', '”')
    private val OPENERS = charArrayOf('«', '"', '¿', '¡', '(', '“')
}
