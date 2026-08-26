package com.zenlauncher.zen.domain.reading

/**
 * Un trozo de bloque que cabe en una pagina. [end] es exclusivo.
 *
 * Un parrafo de filosofia ocupa media pagina o mas, asi que **hay que poder partirlo**:
 * una pagina hecha solo de bloques enteros dejaria medias paginas en blanco constantes,
 * o obligaria a desplazar dentro de la pagina, que es justo lo que se queria quitar.
 */
data class PageFragment(val blockIndex: Int, val start: Int, val end: Int)

/**
 * Una pagina compuesta.
 *
 * @param end donde empieza la siguiente. Con [end] fuera del libro, esta es la ultima.
 */
data class ReaderPage(
    val fragments: List<PageFragment>,
    val start: ReadingPosition,
    val end: ReadingPosition,
) {
    val empty: Boolean get() = fragments.isEmpty()
}

/**
 * Mide el texto ya con el estilo y el ancho de pagina puestos.
 *
 * Es la frontera con Compose, y existe por la misma razon que [PdfTextSource]: partir el
 * texto en paginas es una **decision** —cuanto cabe, donde se corta, cuanto aire va
 * delante de un titulo— y las decisiones se prueban sin Android. La implementacion de
 * verdad usa `TextMeasurer`; los tests usan una de mentira con lineas de ancho fijo.
 */
interface PageMeasurer {

    /** Aire que precede al bloque. Se ignora cuando el bloque **abre** la pagina. */
    fun spacingBefore(blockIndex: Int): Float

    /** Alto en pixeles del texto de [blockIndex] entre [start] y [end] (exclusivo). */
    fun height(blockIndex: Int, start: Int, end: Int): Float

    /**
     * El mayor corte tal que `[start, corte)` quepa en [available], siempre en un final
     * de linea. Devuelve [start] si no cabe ni una linea.
     *
     * @param atLeastOneLine fuerza una linea aunque no quepa. Sirve para la primera
     *   linea de una pagina: sin esto, un bloque cuya primera linea no entra dejaria la
     *   pagina vacia y el lector no avanzaria nunca.
     */
    fun cut(blockIndex: Int, start: Int, available: Float, atLeastOneLine: Boolean): Int
}

/**
 * Reparte el libro en paginas. Puro: todo lo que sabe de la pantalla se lo pregunta a
 * [PageMeasurer].
 *
 * **Las paginas no se calculan todas de golpe.** Un libro de 350 paginas obligaria a
 * medir miles de parrafos al abrirlo y otra vez con cada cambio de tamano de letra, y
 * eso son segundos de espera dentro del proceso del launcher. Se calcula la pagina que
 * se esta mirando y punto; ir hacia atras se resuelve midiendo un poco antes (ver
 * [previous]) en lugar de guardando una pila, que se quedaria vacia en cuanto alguien
 * saltara desde el indice.
 *
 * Por eso el numero que se ensena sigue siendo **la pagina del PDF** y no la pagina
 * compuesta: es estable, no cambia al tocar el cuerpo de letra y es la que se cita en
 * clase.
 */
object Paginator {

    fun page(
        from: ReadingPosition,
        blocks: List<BookBlock>,
        pageHeight: Float,
        measurer: PageMeasurer,
    ): ReaderPage {
        if (blocks.isEmpty() || pageHeight <= 0f) {
            return ReaderPage(emptyList(), from, from)
        }

        val start = clamp(from, blocks)
        val fragments = mutableListOf<PageFragment>()
        var index = start.blockIndex
        var offset = start.charOffset

        var used = 0f
        while (index <= blocks.lastIndex) {
            val text = blocks[index].text
            val gap = if (fragments.isEmpty()) 0f else measurer.spacingBefore(index)
            val available = pageHeight - used - gap
            if (available <= 0f) break

            val full = measurer.height(index, offset, text.length)
            if (full <= available) {
                fragments += PageFragment(index, offset, text.length)
                used += gap + full
                index++
                offset = 0
                continue
            }

            val cut = measurer.cut(index, offset, available, atLeastOneLine = fragments.isEmpty())
            when {
                cut > offset -> {
                    fragments += PageFragment(index, offset, cut)
                    offset = cut
                }
                // Ni una linea entra y la pagina esta vacia: se mete el bloque entero
                // antes que devolver una pagina sin nada. Una pagina vacia no avanza, y
                // el lector se quedaria clavado ahi para siempre.
                fragments.isEmpty() -> {
                    fragments += PageFragment(index, offset, text.length)
                    index++
                    offset = 0
                }
            }
            break
        }

        val end = if (index > blocks.lastIndex) {
            ReadingPosition(blocks.size, 0)
        } else {
            ReadingPosition(index, offset)
        }
        return ReaderPage(fragments = fragments, start = start, end = end)
    }

    /**
     * La pagina que termina donde empieza [before].
     *
     * No hay pila de paginas visitadas a proposito: se vaciaria en cuanto alguien saltara
     * desde el indice o desde una marca, y ahi es justo donde uno quiere retroceder una
     * pagina. Se vuelve a medir un poco por detras y se avanza hasta alcanzar [before],
     * que cuesta unas pocas medidas y funciona igual se venga de donde se venga.
     */
    fun previous(
        before: ReadingPosition,
        blocks: List<BookBlock>,
        pageHeight: Float,
        measurer: PageMeasurer,
    ): ReaderPage {
        if (blocks.isEmpty() || before <= ReadingPosition.Start) {
            return page(ReadingPosition.Start, blocks, pageHeight, measurer)
        }

        var current = page(anchorBefore(before, blocks, pageHeight, measurer), blocks, pageHeight, measurer)
        while (current.end < before) {
            val next = page(current.end, blocks, pageHeight, measurer)
            // Si deja de avanzar, se para: sin esto una pagina que no cabe daria un
            // bucle infinito dentro de la pantalla de inicio del telefono.
            if (next.end <= current.end) break
            current = next
        }
        return current
    }

    /**
     * Un punto de partida bastante por detras de [before] como para que la pagina que se
     * componga desde ahi llegue hasta el. Acotado: se retrocede por alto medido, no por
     * un numero de bloques a ojo, y nunca mas de [LOOKBACK] paginas.
     */
    private fun anchorBefore(
        before: ReadingPosition,
        blocks: List<BookBlock>,
        pageHeight: Float,
        measurer: PageMeasurer,
    ): ReadingPosition {
        var index = before.blockIndex.coerceIn(0, blocks.lastIndex)
        var accumulated = 0f
        while (index > 0 && accumulated < pageHeight * LOOKBACK) {
            index--
            accumulated += measurer.spacingBefore(index) +
                measurer.height(index, 0, blocks[index].text.length)
        }
        return ReadingPosition(index, 0)
    }

    private fun clamp(position: ReadingPosition, blocks: List<BookBlock>): ReadingPosition {
        val index = position.blockIndex.coerceIn(0, blocks.lastIndex)
        val offset = position.charOffset.coerceIn(0, blocks[index].text.length)
        return ReadingPosition(index, offset)
    }

    private const val LOOKBACK = 2f
}
