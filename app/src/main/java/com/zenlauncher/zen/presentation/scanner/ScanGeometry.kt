package com.zenlauncher.zen.presentation.scanner

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.zenlauncher.zen.domain.scanner.Quad
import com.zenlauncher.zen.domain.scanner.ScanPoint

/**
 * Donde cae en pantalla una imagen que se ensena **entera y centrada**.
 *
 * Es el unico calculo que hace falta para que el marco coincida con la hoja. La vista
 * previa de la camara y la foto que se edita se dibujan las dos ajustadas al hueco sin
 * recortar, asi que casi siempre sobran bandas negras a los lados o arriba y abajo; las
 * esquinas, en cambio, vienen en fracciones **de la imagen**, no del hueco. Sin descontar
 * esas bandas el marco sale desplazado, y en la pantalla de ajustar esquinas eso significa
 * que el dedo no agarra donde toca.
 *
 * Se recorta y no se rellena (`fit`, no `fill`) a proposito: llenando el hueco, los bordes
 * de la hoja se saldrian de la pantalla justo cuando hay que agarrarlos.
 */
internal fun fittedRect(area: Size, imageAspect: Float): Rect {
    if (area.width <= 0f || area.height <= 0f || imageAspect <= 0f) {
        return Rect(Offset.Zero, area)
    }
    val areaAspect = area.width / area.height
    return if (imageAspect > areaAspect) {
        // La imagen es mas ancha que el hueco: toca los lados y sobra arriba y abajo.
        val height = area.width / imageAspect
        Rect(Offset(0f, (area.height - height) / 2f), Size(area.width, height))
    } else {
        val width = area.height * imageAspect
        Rect(Offset((area.width - width) / 2f, 0f), Size(width, area.height))
    }
}

/** De fraccion de imagen a pixeles de pantalla. */
internal fun ScanPoint.toOffset(rect: Rect): Offset =
    Offset(rect.left + x * rect.width, rect.top + y * rect.height)

/** De pixeles de pantalla a fraccion de imagen, recortado al interior. */
internal fun Offset.toScanPoint(rect: Rect): ScanPoint {
    if (rect.width <= 0f || rect.height <= 0f) return ScanPoint(0f, 0f)
    return ScanPoint(
        x = ((x - rect.left) / rect.width).coerceIn(0f, 1f),
        y = ((y - rect.top) / rect.height).coerceIn(0f, 1f),
    )
}

internal fun Quad.toOffsets(rect: Rect): List<Offset> = points.map { it.toOffset(rect) }
