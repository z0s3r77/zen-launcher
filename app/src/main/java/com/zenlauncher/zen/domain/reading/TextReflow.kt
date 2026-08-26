package com.zenlauncher.zen.domain.reading

/**
 * Convierte las paginas de un PDF en parrafos que se pueden reflowear.
 *
 * Puro y sin Android, como el analisis de la portada de noticias: se prueba contra
 * texto guardado, nunca contra un fichero real.
 *
 * El problema que resuelve: un PDF **no tiene parrafos**. Tiene lineas colocadas en una
 * hoja de tamano fijo, con la cabecera del libro arriba, el folio abajo y las palabras
 * partidas con guion donde se acababa el ancho de la caja. Ensenar eso tal cual en un
 * movil es exactamente lo que hace un visor de PDF y lo que aqui no se quiere.
 *
 * Nada de esto es exacto y no pretende serlo: lo que no se entiende **se descarta en
 * lugar de rellenarse**, que es la misma regla del analisis de noticias. Una cabecera de
 * mas que se cuela es una linea rara cada veinte paginas; un parrafo inventado es un
 * libro que no dice lo que dice.
 */
object TextReflow {

    /** Una linea fisica de la hoja, con la pagina de la que salio. */
    internal data class Line(val text: String, val page: Int, val first: Boolean, val last: Boolean)

    /**
     * Las paginas, ya sin cabeceras ni folios, convertidas en parrafos y titulos.
     *
     * @param skipPages paginas que no son cuerpo del libro —el indice impreso— y que no
     *   deben acabar como parrafos. Sin esto, el indice del propio libro aparece en el
     *   lector como una lista de titulos sueltos con numeros al final.
     */
    fun blocks(pages: List<PdfPageText>, skipPages: Set<Int> = emptySet()): List<BookBlock> {
        val lines = lines(pages.filterNot { it.page in skipPages })
        val clean = withoutRunningLines(lines, pages.size)
        return paragraphs(clean).mapIndexed { index, raw ->
            HeadingDetector.classify(raw.text, index, raw.page, raw.singleLine)
        }
    }

    /** Trocea cada pagina en lineas, marcando cual era la primera y cual la ultima. */
    internal fun lines(pages: List<PdfPageText>): List<Line> = buildList {
        for (page in pages) {
            val split = page.text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
            split.forEachIndexed { index, text ->
                add(
                    Line(
                        text = text,
                        page = page.page,
                        first = index == 0,
                        last = index == split.lastIndex,
                    ),
                )
            }
        }
    }

    /**
     * Quita lo que se repite arriba o abajo en muchas paginas: el titulo del libro, el
     * del capitulo y el folio.
     *
     * Se comparan **sin las cifras**, porque "El ser y la nada 87" y "El ser y la nada
     * 88" son la misma cabecera; si se compararan tal cual, cada pagina tendria una
     * cabecera distinta y ninguna se repetiria lo suficiente como para detectarse.
     *
     * El umbral es una fraccion de las paginas y no un numero fijo: un libro de 400
     * paginas y unos apuntes de 6 no se parecen en nada, y con un minimo absoluto de 3
     * un documento corto no puede perder lineas de verdad por casualidad.
     */
    internal fun withoutRunningLines(lines: List<Line>, pageCount: Int): List<Line> {
        val threshold = maxOf(MIN_REPEATS, (pageCount * RUNNING_FRACTION).toInt())
        val counts = mutableMapOf<String, Int>()
        for (line in lines) {
            if (!line.first && !line.last) continue
            val key = runningKey(line.text)
            if (key.isEmpty()) continue
            counts[key] = (counts[key] ?: 0) + 1
        }

        return lines.filterNot { line ->
            if (!line.first && !line.last) return@filterNot false
            // Un folio suelto se va siempre, se repita o no: "87" no es una linea de
            // texto en ningun libro, y en el ultimo capitulo puede aparecer pocas veces.
            if (isPageNumber(line.text)) return@filterNot true
            val key = runningKey(line.text)
            key.isNotEmpty() && (counts[key] ?: 0) >= threshold
        }
    }

    /** La linea sin cifras, en minusculas y sin espacios: lo que se compara. */
    private fun runningKey(text: String): String =
        text.filterNot { it.isDigit() || it.isWhitespace() }.lowercase()

    /** Solo cifras, solo numeros romanos, o cualquiera de los dos entre adornos. */
    internal fun isPageNumber(text: String): Boolean {
        val bare = text.trim().trim('-', '·', '—', '–', '[', ']', '(', ')', '|', ' ').trim()
        if (bare.isEmpty() || bare.length > MAX_PAGE_NUMBER_LENGTH) return false
        if (bare.all { it.isDigit() }) return true
        return ROMAN.matches(bare)
    }

    internal data class RawBlock(val text: String, val page: Int, val singleLine: Boolean)

    /**
     * Une las lineas en parrafos.
     *
     * La senal es el **ancho**: en un libro maquetado, todas las lineas de un parrafo
     * llegan al borde de la caja menos la ultima. Una linea corta cierra parrafo; una
     * larga significa que la siguiente es continuacion suya y se pega con un espacio.
     *
     * No se usa la puntuacion como senal principal porque en filosofia los parrafos
     * encadenan comas y dos puntos durante media pagina, y porque las abreviaturas
     * ("cfr.", "op. cit.") cerrarian parrafos donde no los hay.
     */
    internal fun paragraphs(lines: List<Line>): List<RawBlock> {
        if (lines.isEmpty()) return emptyList()
        val full = fullLineLength(lines)
        val out = mutableListOf<RawBlock>()
        val buffer = StringBuilder()
        var page = 0
        var lineCount = 0
        var pendingBreak = true

        fun flush() {
            val text = buffer.toString().trim()
            if (text.isNotEmpty()) out += RawBlock(text, page, lineCount <= 1)
            buffer.setLength(0)
            lineCount = 0
        }

        for (line in lines) {
            // Un titulo empieza parrafo aunque la linea anterior fuera larga: sin esto,
            // "3. Libertad" se pega al final del capitulo anterior y desaparece como
            // titulo, que es justo lo que se necesita para navegar.
            if (HeadingDetector.startsBlock(line.text)) {
                flush()
                pendingBreak = true
            }
            if (pendingBreak) {
                flush()
                page = line.page
            }
            append(buffer, line.text)
            lineCount++
            // Una linea acabada en guion **no puede cerrar parrafo**: por definicion es
            // media palabra. Se comprobaba solo el ancho, y como al extraer el texto la
            // linea pierde los espacios finales, una linea justificada acabada en guion
            // se medía corta y cortaba ahi: "liber-" quedaba como final de parrafo y
            // "tad no admite..." empezaba el siguiente. La palabra no volvia a unirse
            // nunca y el buscador no la encontraba en media pagina de cada dos.
            pendingBreak = !endsHyphenated(line.text) && isShort(line.text, full)
        }
        flush()
        return out
    }

    /**
     * Pega una linea a lo que ya hay, deshaciendo el guion de corte.
     *
     * El guion solo se quita si lo que hay antes y despues es minuscula: "liber-" +
     * "tad" es una palabra partida, pero "teorico-" + "Practico" o "1939-" + "1945" son
     * guiones de verdad y quitarlos cambiaria el texto del libro.
     */
    /** El guion de corte, normal o blando. Ver [append]. */
    private fun endsHyphenated(text: String): Boolean =
        text.endsWith('-') || text.endsWith('\u00AD')

    private fun append(buffer: StringBuilder, text: String) {
        if (buffer.isEmpty()) {
            buffer.append(text)
            return
        }
        val last = buffer.last()
        val cut = (last == '-' || last == '­') &&
            buffer.length >= 2 &&
            buffer[buffer.length - 2].isLowerCase() &&
            text.firstOrNull()?.isLowerCase() == true
        if (cut) {
            buffer.setLength(buffer.length - 1)
            buffer.append(text)
        } else {
            buffer.append(' ').append(text)
        }
    }

    /**
     * El ancho de una linea "llena", en caracteres.
     *
     * Percentil alto y no el maximo: una sola linea larguisima —una tabla mal extraida,
     * una nota al pie corrida— desplazaria el umbral y todo el libro pasaria a ser
     * parrafos de una linea.
     */
    internal fun fullLineLength(lines: List<Line>): Int {
        if (lines.isEmpty()) return 0
        val sorted = lines.map { it.text.length }.sorted()
        return sorted[(sorted.size * FULL_LINE_PERCENTILE).toInt().coerceAtMost(sorted.lastIndex)]
    }

    private fun isShort(text: String, full: Int): Boolean =
        full == 0 || text.length < full * SHORT_LINE_RATIO

    private const val RUNNING_FRACTION = 0.30
    private const val MIN_REPEATS = 3
    private const val MAX_PAGE_NUMBER_LENGTH = 8
    private const val FULL_LINE_PERCENTILE = 0.85
    private const val SHORT_LINE_RATIO = 0.78

    private val ROMAN = Regex("^[ivxlcdmIVXLCDM]+$")
}
