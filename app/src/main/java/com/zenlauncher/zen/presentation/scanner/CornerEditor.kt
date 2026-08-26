package com.zenlauncher.zen.presentation.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.scanner.Quad
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing

/**
 * Ajustar las cuatro esquinas a mano, sobre la foto original.
 *
 * Sobre la **original** y no sobre la ya recortada: hacia fuera del recorte hay imagen de
 * verdad, y ese es justo el movimiento que se hace cuando la deteccion se ha comido un
 * margen. Con la recortada debajo, arrastrar una esquina hacia fuera sacaria pixeles que
 * ya no existen.
 *
 * ### El arrastre solo se consume si se agarro una esquina
 *
 * `ZenScreen` usa el arrastre horizontal desde el borde para volver (ver `EdgeBackPolicy`),
 * asi que un hijo que se tragara todos los arrastres dejaria esta pantalla sin ese gesto.
 * Por eso no se usa `detectDragGestures`, que consume siempre: se espera el primer
 * contacto, se mira si cayo cerca de una esquina y **solo entonces** se consume. Un
 * arrastre que empieza en cualquier otro sitio de la foto sigue llegando a la pantalla y
 * el gesto de volver funciona igual.
 *
 * @param onPreview mientras el dedo se mueve. Solo redibuja el marco, no reprocesa nada.
 * @param onCommit al soltar. Aqui si se vuelve a enderezar la hoja, que es lo caro.
 */
@Composable
internal fun CornerEditor(
    imagePath: String,
    revision: Int,
    imageAspect: Float,
    quad: Quad,
    onCommit: (Quad) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val grabRadius = with(density) { CORNER_GRAB_RADIUS.toPx() }
    val handle = with(density) { CORNER_HANDLE.toPx() }
    val strokeWidth = with(density) { ZenSpacing.Hairline.toPx() * 2f }

    // El cuadrilatero que se esta arrastrando vive aqui y no en el ViewModel: son decenas
    // de cambios por segundo mientras el dedo se mueve, y cada uno seria una vuelta
    // entera por el estado de la pantalla para mover una esquina cuatro pixeles.
    var dragging by remember(quad) { mutableStateOf<Quad?>(null) }
    val shown = dragging ?: quad

    val description = stringResource(R.string.scanner_corners_description)

    Box(modifier = modifier) {
        ScanImage(
            path = imagePath,
            revision = revision,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = description }
                .pointerInput(imageAspect, quad) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val rect = fittedRect(
                            Size(size.width.toFloat(), size.height.toFloat()),
                            imageAspect,
                        )

                        val start = dragging ?: quad
                        val corners = start.toOffsets(rect)
                        val index = corners.indices.minByOrNull {
                            (corners[it] - down.position).getDistance()
                        } ?: return@awaitEachGesture

                        // Lejos de toda esquina: no se consume nada y el gesto sigue su
                        // camino hacia la pantalla, que es donde vive el volver.
                        if ((corners[index] - down.position).getDistance() > grabRadius) {
                            return@awaitEachGesture
                        }

                        down.consume()
                        var working = start
                        drag(down.id) { change ->
                            working = working.withCorner(index, change.position.toScanPoint(rect))
                            dragging = working
                            change.consume()
                        }
                        dragging = null
                        onCommit(working)
                    }
                },
        ) {
            val rect = fittedRect(size, imageAspect)
            val corners = shown.toOffsets(rect)

            drawQuadOutline(corners, ZenColors.Foreground, strokeWidth)

            // Las asas se dibujan pequenas —un cuadrado hueco de 14dp— pero se agarran
            // desde mucho mas lejos (32dp): un asa del tamano del area tactil taparia
            // justo la esquina de la hoja que hay que ver para colocarla.
            for (corner in corners) {
                drawRect(
                    color = ZenColors.Background,
                    topLeft = Offset(corner.x - handle / 2f, corner.y - handle / 2f),
                    size = Size(handle, handle),
                )
                drawRect(
                    color = ZenColors.Foreground,
                    topLeft = Offset(corner.x - handle / 2f, corner.y - handle / 2f),
                    size = Size(handle, handle),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth),
                )
            }
        }
    }
}
