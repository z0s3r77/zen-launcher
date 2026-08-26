package com.zenlauncher.zen.domain.reading

import com.zenlauncher.zen.domain.notes.TextNormalizer

/**
 * Monta el libro entero a partir de las paginas del PDF. Puro y sin Android.
 *
 * Es el unico sitio donde se juntan las tres heuristicas —[TableOfContents],
 * [TextReflow] y [HeadingDetector]— y donde se decide con cual navegar. El orden
 * importa: el indice impreso se detecta **antes** de reconstruir el cuerpo, porque sus
 * paginas hay que sacarlas del cuerpo o el lector empieza el libro leyendo su propio
 * indice.
 */
object BookBuilder {

    fun build(document: PdfDocumentText): BuiltBook {
        val toc = TableOfContents.detect(document.pages)
        val blocks = TextReflow.blocks(document.pages, skipPages = toc.pages)
        val metadata = BookMetadata.detect(coverText(document.pages, toc.pages), document.fileName)

        return BuiltBook(
            title = metadata.title,
            author = metadata.author,
            pageCount = document.pages.size,
            blocks = blocks,
            chapters = chapters(blocks, toc),
        )
    }

    /**
     * El indice navegable.
     *
     * Se prefiere el indice impreso porque lo escribio quien hizo el libro: dice que
     * partes cuentan y como se llaman de verdad. Los titulos detectados en el cuerpo son
     * el plan B, y solo se usan si el impreso no aparecio o si casi ninguna de sus
     * entradas se pudo colocar —un indice que lleva a sitios equivocados es peor que no
     * tener indice—.
     */
    internal fun chapters(blocks: List<BookBlock>, toc: TocDetection): List<BookChapter> {
        val headings = blocks.filter { it.kind == BlockKind.HEADING }
        if (!toc.found) return headings.map { it.toChapter() }

        val fromToc = placeEntries(toc.entries, headings, blocks)
        val placedByTitle = fromToc.count { it.matchedTitle }
        // La mitad de las entradas colocadas por su titulo es el minimo para fiarse. Por
        // debajo, lo que hay es un indice mal leido o un libro cuyo cuerpo no se parece
        // a el (una recopilacion, unos apuntes con el indice de otra asignatura).
        if (placedByTitle * 2 < toc.entries.size) return headings.map { it.toChapter() }

        return fromToc.map { it.chapter }.distinctBy { it.blockIndex }.sortedBy { it.blockIndex }
    }

    private data class Placed(val chapter: BookChapter, val matchedTitle: Boolean)

    /**
     * Coloca cada entrada del indice en un bloque del cuerpo.
     *
     * Primero por el titulo, que es exacto. Lo que no aparece por titulo se coloca por
     * la pagina, y para eso hace falta saber cuanto se desplaza la numeracion impresa
     * respecto a la del PDF: la pagina 1 de un libro nunca es la hoja 1 del fichero,
     * porque delante van la cubierta, los creditos y el propio indice. El desplazamiento
     * se **mide** con las entradas que si se encontraron, en lugar de suponerlo.
     */
    private fun placeEntries(
        entries: List<TocEntry>,
        headings: List<BookBlock>,
        blocks: List<BookBlock>,
    ): List<Placed> {
        val byTitle = entries.associateWith { entry -> matchHeading(entry, headings) }
        val offsets = byTitle.mapNotNull { (entry, heading) ->
            heading?.let { it.page - entry.printedPage }
        }
        val offset = offsets.sorted().getOrNull(offsets.size / 2)

        return entries.mapNotNull { entry ->
            val heading = byTitle[entry]
            when {
                heading != null -> Placed(
                    BookChapter(entry.title, entry.level, heading.index, heading.page),
                    matchedTitle = true,
                )
                // Sin desplazamiento medido no se coloca a ojo: una entrada que lleva a
                // un parrafo cualquiera es peor que una entrada que no esta.
                offset != null -> blocks.firstOrNull { it.page >= entry.printedPage + offset }
                    ?.let { block ->
                        Placed(
                            BookChapter(entry.title, entry.level, block.index, block.page),
                            matchedTitle = false,
                        )
                    }
                else -> null
            }
        }
    }

    /**
     * Busca el titulo de una entrada entre los titulos del cuerpo.
     *
     * Se compara normalizado —sin acentos ni mayusculas, ver [TextNormalizer]— porque el
     * indice y el cuerpo no siempre coinciden: "LA CONCIENCIA" arriba del capitulo y
     * "La conciencia" en el indice son el mismo sitio. Se acepta que uno contenga al
     * otro porque el indice recorta subtitulos largos con puntos suspensivos.
     */
    internal fun matchHeading(entry: TocEntry, headings: List<BookBlock>): BookBlock? {
        val wanted = comparable(entry.title)
        if (wanted.length < MIN_MATCH_LENGTH) return null
        return headings.firstOrNull { comparable(it.text) == wanted }
            ?: headings.firstOrNull { heading ->
                val text = comparable(heading.text)
                text.length >= MIN_MATCH_LENGTH &&
                    (text.startsWith(wanted) || wanted.startsWith(text))
            }
    }

    /** Normalizado y sin la numeracion de delante, que el cuerpo no siempre repite. */
    private fun comparable(title: String): String =
        TextNormalizer.normalize(title.replace(NUMBERING, "")).trim()

    /**
     * La primera pagina con texto que no sea el indice: la portada.
     *
     * "Con texto" y no "la primera": muchos PDF empiezan por una imagen de cubierta que
     * no lleva ni una letra extraible, y de esa no se puede sacar ningun titulo.
     */
    private fun coverText(pages: List<PdfPageText>, tocPages: Set<Int>): String =
        pages.firstOrNull { it.page !in tocPages && it.text.count(Char::isLetter) >= MIN_COVER_LETTERS }
            ?.text
            .orEmpty()

    private fun BookBlock.toChapter() = BookChapter(
        title = text,
        level = level.coerceAtLeast(1),
        blockIndex = index,
        page = page,
    )

    private const val MIN_MATCH_LENGTH = 4
    private const val MIN_COVER_LETTERS = 10

    private val NUMBERING = Regex("^\\s*(?:\\d{1,3}(?:\\.\\d{1,3}){0,2}\\.?|[IVXLCDM]{1,7}\\.)\\s+")
}
