package com.zenlauncher.zen.domain.reading

/**
 * Que bloques de texto son titulos. Puro y sin Android.
 *
 * Un PDF no marca sus titulos: lo unico que los distingue en la hoja es que estan en
 * otro cuerpo, y el cuerpo se pierde al extraer el texto. Lo que queda son senales de
 * forma —la linea esta sola, es corta, va en mayusculas, empieza por un numero de
 * capitulo— y ninguna vale por si sola.
 *
 * Es deliberadamente **conservador**: un titulo que se queda en parrafo es una entrada
 * de menos en el indice, y el libro se sigue leyendo entero. Un parrafo convertido en
 * titulo sale en el indice como una frase suelta a la que no lleva a ninguna parte y
 * ensucia la unica forma de navegar que hay.
 */
object HeadingDetector {

    /**
     * Si esta linea empieza algo nuevo pase lo que pase.
     *
     * Se consulta **antes** de pegar la linea al parrafo anterior (ver [TextReflow]):
     * un titulo suele venir detras de una linea larga —la ultima de la pagina del
     * capitulo anterior— y sin esto se pegaria a ella y dejaria de existir.
     */
    fun startsBlock(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_HEADING_LENGTH) return false
        if (NAMED.containsMatchIn(trimmed)) return true
        return NUMBERED.containsMatchIn(trimmed) || ROMAN_NUMBERED.containsMatchIn(trimmed)
    }

    /**
     * Convierte un bloque crudo en el bloque definitivo del libro.
     *
     * @param singleLine si el bloque venia de una sola linea de la hoja. Es la senal mas
     *   fuerte que queda: un titulo esta solo en su linea por definicion, y una frase
     *   corta que ademas estaba sola es casi siempre un titulo.
     */
    fun classify(text: String, index: Int, page: Int, singleLine: Boolean): BookBlock {
        val level = headingLevel(text, singleLine)
        return if (level > 0) {
            BookBlock(index = index, kind = BlockKind.HEADING, text = text, page = page, level = level)
        } else {
            BookBlock(index = index, kind = BlockKind.PARAGRAPH, text = text, page = page)
        }
    }

    /** 0 si no es titulo. 1 capitulo, 2 seccion, 3 subseccion. */
    fun headingLevel(text: String, singleLine: Boolean): Int {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_HEADING_LENGTH) return 0

        NUMBERED.find(trimmed)?.let { match ->
            // "3" es capitulo, "3.1" seccion, "3.1.2" subseccion. Se cuentan los puntos
            // interiores, no el final: "3." y "3" son lo mismo.
            val digits = match.groupValues[1].trimEnd('.')
            return (digits.count { it == '.' } + 1).coerceAtMost(MAX_LEVEL)
        }
        if (NAMED.containsMatchIn(trimmed) || ROMAN_NUMBERED.containsMatchIn(trimmed)) return 1

        // Sin numeracion hace falta que estuviera sola en su linea: si no, cualquier
        // frase corta en mayusculas dentro de un parrafo pasaria por titulo.
        if (!singleLine) return 0
        if (endsLikeSentence(trimmed)) return 0
        if (isAllCaps(trimmed)) return 1
        // Corta, sola, empieza en mayuscula y no termina en punto. Es lo mas debil que
        // se acepta y por eso pide todas las condiciones a la vez.
        val starts = trimmed.first().isUpperCase()
        return if (starts && trimmed.length <= SHORT_HEADING_LENGTH) 1 else 0
    }

    /**
     * Termina como una frase: punto, interrogacion, cierre de comillas...
     *
     * Los dos puntos **no** cuentan: "ÍNDICE:" o "Introducción:" son titulos, y en
     * castellano academico un titulo con dos puntos y subtitulo es de lo mas comun.
     */
    private fun endsLikeSentence(text: String): Boolean =
        text.last() in SENTENCE_ENDINGS

    /**
     * Sin una sola minuscula, y con al menos dos letras.
     *
     * Se miran solo las letras: "3. LA CONCIENCIA" lleva cifras y un punto, y contarlos
     * dejaria fuera justo los titulos mejor marcados que hay.
     */
    private fun isAllCaps(text: String): Boolean {
        val letters = text.filter(Char::isLetter)
        return letters.length >= MIN_CAPS_LETTERS && letters.none(Char::isLowerCase)
    }

    private const val MAX_HEADING_LENGTH = 90
    private const val SHORT_HEADING_LENGTH = 48
    private const val MIN_CAPS_LETTERS = 2
    private const val MAX_LEVEL = 3

    private val SENTENCE_ENDINGS = charArrayOf('.', '?', '!', ';', ',', '»', '"', '’')

    /**
     * Las palabras que abren division en un libro academico en castellano.
     *
     * Van con `\b` al final para no casar dentro de otra palabra, y se aceptan en
     * mayusculas o en capital porque las dos formas aparecen en el mismo libro.
     */
    private val NAMED = Regex(
        "^(cap[íi]tulo|parte|libro|secci[óo]n|ap[ée]ndice|introducci[óo]n|pr[óo]logo" +
            "|pre[áa]mbulo|prefacio|ep[íi]logo|conclusi[óo]n(es)?|bibliograf[íi]a" +
            "|anexo|nota preliminar)\\b",
        RegexOption.IGNORE_CASE,
    )

    /** "3.", "3.1", "12.4.2 Titulo": la cifra y luego algo que no es otra cifra. */
    private val NUMBERED = Regex("^(\\d{1,3}(?:\\.\\d{1,3}){0,2}\\.?)\\s+\\p{L}")

    /** "IV. El problema del ser". El punto es obligatorio: sin el, "I" o "V" casarian solos. */
    private val ROMAN_NUMBERED = Regex("^[IVXLCDM]{1,7}\\.\\s+\\p{L}")
}
