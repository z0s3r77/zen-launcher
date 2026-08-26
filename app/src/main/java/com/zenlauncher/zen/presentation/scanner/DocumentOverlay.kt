package com.zenlauncher.zen.presentation.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.domain.scanner.Quad
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing

/**
 * El marco de la hoja detectada, encima de la vista previa.
 *
 * **No se anima y no parpadea**: se dibuja donde esta la hoja y ya. Un marco que late o
 * que se desliza hasta su sitio en una pantalla que se mira apuntando con el pulso es
 * ruido encima de ruido, y ademas taparia el unico dato util, que es si las esquinas
 * estan donde deben.
 *
 * El estado se distingue por **contraste**, no por color: apagado mientras el encuadre no
 * sirve y encendido cuando va a disparar. El unico ambar de la aplicacion aparece aqui
 * como marca de 6dp en las cuatro esquinas y solo cuando esta listo, que es exactamente su
 * papel en el sistema visual: marca de estado, nunca relleno. Y nunca es la unica senal:
 * debajo hay un rotulo que dice lo mismo con palabras.
 */
@Composable
internal fun DocumentOverlay(
    quad: Quad?,
    imageAspect: Float,
    ready: Boolean,
    modifier: Modifier = Modifier,
) {
    if (quad == null) return

    val density = LocalDensity.current
    val strokeWidth = with(density) { OUTLINE_WIDTH.toPx() }
    val markSize = with(density) { ZenSpacing.StatusMark.toPx() }

    Canvas(modifier = modifier) {
        val rect = fittedRect(size, imageAspect)
        val corners = quad.toOffsets(rect)
        val color = if (ready) ZenColors.Foreground else ZenColors.Disabled

        drawQuadOutline(corners, color, strokeWidth)

        if (ready) {
            for (corner in corners) {
                drawRect(
                    color = ZenColors.Accent,
                    topLeft = Offset(corner.x - markSize / 2f, corner.y - markSize / 2f),
                    size = androidx.compose.ui.geometry.Size(markSize, markSize),
                )
            }
        }
    }
}

internal fun DrawScope.drawQuadOutline(
    corners: List<Offset>,
    color: Color,
    strokeWidth: Float,
) {
    if (corners.size != 4) return
    val path = Path().apply {
        moveTo(corners[0].x, corners[0].y)
        for (index in 1 until corners.size) lineTo(corners[index].x, corners[index].y)
        close()
    }
    drawPath(path, color = color, style = Stroke(width = strokeWidth))
}

/** Un filete, como todo lo demas del sistema, pero de 2dp para que se vea sobre la foto. */
private val OUTLINE_WIDTH = 2.dp

internal val CORNER_GRAB_RADIUS = 32.dp
internal val CORNER_HANDLE = 14.dp
