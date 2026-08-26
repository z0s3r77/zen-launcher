package com.zenlauncher.zen.domain.scanner

import kotlin.math.roundToInt

/**
 * Que tamano tiene cada hoja del PDF.
 *
 * Puro, y con test, porque es donde se pierde la proporcion sin que nadie se entere: el
 * PDF se mide en puntos (1/72 de pulgada) y la imagen en pixeles, y meter una imagen de
 * 1600x2200 en una hoja A4 "a lo ancho y a lo alto" la deja estirada. Aqui se ajusta al
 * mayor rectangulo con la proporcion de la imagen que cabe en A4, y **la hoja se pone en
 * horizontal si el escaneo lo esta**: un apaisado dentro de un A4 vertical deja dos
 * franjas blancas enormes y sale minusculo al imprimir.
 */
object PdfPageSize {

    /** A4 en puntos PDF, que es el tamano de papel de aqui. */
    const val A4_WIDTH_POINTS = 595
    const val A4_HEIGHT_POINTS = 842

    /**
     * @return ancho y alto de la hoja del PDF, en puntos.
     */
    fun forImage(imageWidth: Int, imageHeight: Int): Pair<Int, Int> {
        if (imageWidth <= 0 || imageHeight <= 0) return A4_WIDTH_POINTS to A4_HEIGHT_POINTS

        val landscape = imageWidth > imageHeight
        val sheetWidth = if (landscape) A4_HEIGHT_POINTS else A4_WIDTH_POINTS
        val sheetHeight = if (landscape) A4_WIDTH_POINTS else A4_HEIGHT_POINTS

        val imageRatio = imageWidth.toFloat() / imageHeight
        val sheetRatio = sheetWidth.toFloat() / sheetHeight

        // Se recorta la hoja hasta la proporcion de la imagen en lugar de dejar margenes:
        // el escaneo YA viene recortado al borde del papel, asi que un margen aqui es un
        // marco blanco alrededor de un folio blanco, y al imprimir encoge el documento.
        return if (imageRatio > sheetRatio) {
            sheetWidth to (sheetWidth / imageRatio).roundToInt().coerceAtLeast(1)
        } else {
            (sheetHeight * imageRatio).roundToInt().coerceAtLeast(1) to sheetHeight
        }
    }
}
