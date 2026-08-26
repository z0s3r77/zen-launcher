package com.zenlauncher.zen.domain.reading

/** Una linea del indice impreso, ya entendida. */
data class TocEntry(
    val title: String,
    val level: Int,
    /** La pagina **impresa** en el indice, que casi nunca es la del PDF. */
    val printedPage: Int,
)

/** Lo encontrado y en que paginas estaba, para poder no meterlas en el cuerpo. */
data class TocDetection(
    val entries: List<TocEntry> = emptyList(),
    val pages: Set<Int> = emptySet(),
) {
    val found: Boolean get() = entries.isNotEmpty()
}

/**
 * Busca el indice impreso del libro. Puro y sin Android.
 *
 * **No se da por supuesto ningun formato.** Hay libros con puntos suspensivos hasta el
 * numero, otros con tabulaciones, otros con la numeracion delante y otros sin ella; los
 * apuntes de clase a veces no tienen indice en absoluto. Todo aqui es heuristica y todo
 * puede fallar sin romper nada: si no se encuentra indice, [BookBuilder] navega con los
 * titulos que detecto en el cuerpo, y si tampoco hay, el lector ofrece saltar por
 * paginas. **Siempre hay una forma de moverse por el libro.**
 */
object TableOfContents {

    /**
     * @param pages el documento entero. Se mira solo el principio y el final: un indice
     *   en mitad del libro no existe, y recorrer 400 paginas buscandolo solo puede
     *   producir falsos positivos —una bibliografia numerada se parece mucho a un
     *   indice—.
     */
    fun detect(pages: List<PdfPageText>): TocDetection {
        val candidates = pages.take(HEAD_PAGES) + pages.takeLast(TAIL_PAGES)
        val titled = candidates.firstOrNull { hasTocTitle(it.text) }
        val start = titled ?: candidates.firstOrNull { looksLikeToc(it.text) } ?: return TocDetection()

        // El indice puede seguir en las paginas siguientes, y se para en cuanto una deja
        // de parecerlo: sin ese corte, el primer capitulo entero se leeria como indice.
        val used = mutableListOf(start)
        var next = pages.indexOfFirst { it.page == start.page } + 1
        while (next < pages.size && used.size < MAX_TOC_PAGES && looksLikeToc(pages[next].text)) {
            used += pages[next]
            next++
        }

        val entries = used.flatMap { entriesIn(it.text) }
        if (entries.size < MIN_ENTRIES || !mostlyAscending(entries)) return TocDetection()
        return TocDetection(entries = entries, pages = used.map { it.page }.toSet())
    }

    private fun hasTocTitle(text: String): Boolean =
        text.split('\n').any { TITLE.matches(it.trim()) } && looksLikeToc(text)

    /** Bastantes lineas de la pagina terminan en un numero de pagina. */
    internal fun looksLikeToc(text: String): Boolean {
        val lines = text.split('\n').map { it.trim() }.filter { it.length >= MIN_LINE_LENGTH }
        if (lines.size < MIN_ENTRIES) return false
        val hits = lines.count { parse(it) != null }
        return hits >= MIN_ENTRIES && hits * 2 >= lines.size
    }

    internal fun entriesIn(text: String): List<TocEntry> =
        text.split('\n').mapNotNull { parse(it.trim()) }

    /**
     * Una linea del indice.
     *
     * Se prueban dos formas y en este orden: primero la que lleva puntos conductores,
     * que no admite duda, y despues la de "titulo, espacios, numero". La segunda es la
     * que puede equivocarse —"Kant 1781" es un titulo, no una entrada— y por eso exige
     * que el titulo termine en letra y tenga cuerpo suficiente.
     */
    internal fun parse(line: String): TocEntry? {
        if (line.length < MIN_LINE_LENGTH) return null
        val match = LEADER.find(line) ?: TRAILING.find(line) ?: return null
        val page = match.groupValues[2].toIntOrNull() ?: return null
        if (page <= 0 || page > MAX_PRINTED_PAGE) return null

        val raw = match.groupValues[1].trim().trimEnd('.', '·', '…', ' ', '\t')
        if (raw.count(Char::isLetter) < MIN_TITLE_LETTERS) return null

        // La numeracion se queda **dentro** del titulo: "3.1 Determinismo" es como se
        // llama esa seccion en el libro, y quitarsela dejaria tres entradas seguidas
        // llamadas "Determinismo", "Eleccion" y "Libertad" sin decir de que capitulo
        // son. Lo unico que se saca de ella es el nivel.
        val level = NUMBERING.find(raw)?.let { match ->
            (match.groupValues[1].trimEnd('.').count { char -> char == '.' } + 1)
                .coerceAtMost(MAX_LEVEL)
        } ?: 1

        return TocEntry(title = raw, level = level, printedPage = page)
    }

    /**
     * Los numeros de pagina de un indice suben.
     *
     * No se exige que suban siempre: una entrada mal leida da un numero absurdo, y
     * tirar el indice entero por una linea rota seria perder la unica forma comoda de
     * navegar. Con dos de cada tres subiendo, es un indice.
     */
    internal fun mostlyAscending(entries: List<TocEntry>): Boolean {
        if (entries.size < 2) return false
        val pairs = entries.zipWithNext()
        val ordered = pairs.count { (a, b) -> b.printedPage >= a.printedPage }
        return ordered * 3 >= pairs.size * 2
    }

    private const val HEAD_PAGES = 25
    private const val TAIL_PAGES = 10
    private const val MAX_TOC_PAGES = 8
    private const val MIN_ENTRIES = 3
    private const val MIN_LINE_LENGTH = 6
    private const val MIN_TITLE_LETTERS = 3
    private const val MAX_PRINTED_PAGE = 9999
    private const val MAX_LEVEL = 3

    /**
     * Como se titula un indice. Se usa con `matches`, asi que casa la linea entera: una
     * linea que solo dice "ÍNDICE" es el rotulo del indice, pero "el indice de refraccion"
     * dentro de un parrafo no lo es.
     */
    private val TITLE = Regex(
        "[\\p{Pd}\\s]*(?:[íi]ndice(?:\\s+general)?|contenidos?|sumario" +
            "|tabla\\s+de\\s+contenidos?|contents)[\\s.:]*",
        RegexOption.IGNORE_CASE,
    )

    /** "Introducción ......... 5" */
    private val LEADER = Regex("^(.*?)[.·…\\u2026\\s]{3,}(\\d{1,4})$")

    /** "3. Libertad   67". El titulo tiene que acabar en letra o en cierre. */
    private val TRAILING = Regex("^(.*?[\\p{L}\\p{N})\\]»\"'])[\\s\\t]+(\\d{1,4})$")

    private val NUMBERING = Regex("^(\\d{1,3}(?:\\.\\d{1,3}){0,2}\\.?)\\s+")
}
