package com.zenlauncher.zen.fakes

import com.zenlauncher.zen.domain.reading.BlockKind
import com.zenlauncher.zen.domain.reading.BookBlock
import com.zenlauncher.zen.domain.reading.PageMeasurer

/**
 * Una pantalla de mentira con lineas de ancho fijo.
 *
 * [CHARS_PER_LINE] caracteres por linea y [LINE_HEIGHT] pixeles por linea, asi que con
 * `pageHeight = 200` caben diez lineas justas y las cuentas del test se pueden hacer a
 * mano. Es lo que permite probar el reparto en paginas sin Compose ni dispositivo.
 */
class FakePageMeasurer(
    private val blocks: List<BookBlock>,
    private val charsPerLine: Int = CHARS_PER_LINE,
) : PageMeasurer {

    override fun spacingBefore(blockIndex: Int): Float =
        if (blocks[blockIndex].kind == BlockKind.HEADING) HEADING_GAP else PARAGRAPH_GAP

    override fun height(blockIndex: Int, start: Int, end: Int): Float =
        linesIn(end - start) * LINE_HEIGHT

    override fun cut(blockIndex: Int, start: Int, available: Float, atLeastOneLine: Boolean): Int {
        val fits = (available / LINE_HEIGHT).toInt()
        val lines = if (fits <= 0 && atLeastOneLine) 1 else fits
        if (lines <= 0) return start
        return minOf(start + lines * charsPerLine, blocks[blockIndex].text.length)
    }

    private fun linesIn(length: Int): Int =
        if (length <= 0) 0 else (length + charsPerLine - 1) / charsPerLine

    companion object {
        const val CHARS_PER_LINE = 40
        const val LINE_HEIGHT = 20f
        const val PARAGRAPH_GAP = 10f
        const val HEADING_GAP = 30f
    }
}
