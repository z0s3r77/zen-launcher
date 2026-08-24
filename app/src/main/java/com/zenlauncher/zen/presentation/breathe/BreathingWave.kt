package com.zenlauncher.zen.presentation.breathe

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.domain.breathing.BreathingPattern
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing

/**
 * El minuto entero dibujado como una curva: alto = pulmones llenos, bajo = vacios, y el
 * eje horizontal es el tiempo.
 *
 * Se ve **de una vez**, no una animacion que se descubre sola: la parte tenue es lo que
 * queda por respirar y la trazada en blanco lo ya hecho, asi que la misma figura dice
 * el ritmo, en que punto de la respiracion estas y cuanto falta para terminar. Es la
 * razon de que no haya ademas una barra de progreso: seria el mismo dato dos veces.
 *
 * Lo unico que se mueve es la marca, y se mueve porque el ejercicio esta corriendo: en
 * reposo la curva esta ahi quieta, entera y sin trazar, y no se dibuja ni un fotograma
 * de mas. Es la misma regla del ecualizador del reproductor.
 *
 * ## Por que un lambda y no un `Float`
 *
 * [elapsedMillis] se lee **dentro del dibujo**, no en la composicion. La marca avanza a
 * sesenta fotogramas por segundo; si el valor entrara como parametro, cada fotograma
 * recompondria la pantalla entera para volver a colocar dos palabras que solo cambian
 * una vez por segundo. Leyendolo aqui, cada fotograma invalida el dibujo y nada mas.
 */
@Composable
internal fun BreathingWave(
    elapsedMillis: () -> Long,
    modifier: Modifier = Modifier,
    height: Dp = WAVE_HEIGHT,
) {
    Canvas(
        // Decorativa para un lector de pantalla: al lado se lee INSPIRA o ESPIRA con
        // todas las letras, y describir una curva punto por punto no ayuda a nadie.
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { },
    ) {
        val hairline = ZenSpacing.Hairline.toPx()
        val marker = MARKER_SIZE.toPx()
        // Hueco arriba para que la marca no se corte en el pico, y abajo para las
        // divisiones entre respiraciones.
        val top = marker / 2f
        val bottom = size.height - TICK_ZONE.toPx()
        val span = bottom - top

        fun xAt(millis: Long): Float =
            size.width * (millis.toFloat() / BreathingPattern.TOTAL_MILLIS)

        fun yAt(millis: Long): Float =
            bottom - BreathingPattern.amplitudeAt(millis) * span

        // Linea de base: el suelo del que sale cada respiracion.
        drawLine(
            color = ZenColors.Hairline,
            start = Offset(0f, bottom),
            end = Offset(size.width, bottom),
            strokeWidth = hairline,
        )

        // Una division por respiracion terminada. Van por debajo de la linea de base
        // para no cruzarse con la curva justo donde toca el suelo.
        for (cycle in 1 until BreathingPattern.CYCLES) {
            val x = xAt(BreathingPattern.CYCLE_MILLIS * cycle)
            drawLine(
                color = ZenColors.Faint,
                start = Offset(x, bottom),
                end = Offset(x, bottom + TICK_LENGTH.toPx()),
                strokeWidth = hairline,
            )
        }

        val elapsed = elapsedMillis().coerceIn(0L, BreathingPattern.TOTAL_MILLIS)

        drawPathBetween(0L, BreathingPattern.TOTAL_MILLIS, ::xAt, ::yAt) { path ->
            drawPath(path, ZenColors.Faint, style = Stroke(width = hairline))
        }

        if (elapsed > 0L) {
            drawPathBetween(0L, elapsed, ::xAt, ::yAt) { path ->
                drawPath(path, ZenColors.Foreground, style = Stroke(width = TRACE_WIDTH.toPx()))
            }
        }

        // Marca cuadrada, como el resto de indicadores de estado de Zen. Sube y baja
        // con el aire: seguirla con el rabillo del ojo es seguir el ritmo sin leer.
        val x = xAt(elapsed)
        val y = yAt(elapsed)
        drawRect(
            color = ZenColors.Foreground,
            topLeft = Offset(x - marker / 2f, y - marker / 2f),
            size = Size(marker, marker),
        )
    }
}

/**
 * Traza la curva entre dos instantes muestreandola. [SAMPLES] puntos en sesenta
 * segundos es un punto cada ~375 ms, mas que de sobra para que el ojo la lea como una
 * curva y no como una linea quebrada.
 */
private inline fun DrawScope.drawPathBetween(
    fromMillis: Long,
    toMillis: Long,
    xAt: (Long) -> Float,
    yAt: (Long) -> Float,
    draw: (Path) -> Unit,
) {
    val path = Path()
    path.moveTo(xAt(fromMillis), yAt(fromMillis))
    val step = BreathingPattern.TOTAL_MILLIS / SAMPLES
    var millis = fromMillis + step
    while (millis < toMillis) {
        path.lineTo(xAt(millis), yAt(millis))
        millis += step
    }
    // El ultimo punto va exacto: si no, el trazo se quedaria hasta la muestra anterior
    // y la marca iria por delante de su propia linea.
    path.lineTo(xAt(toMillis), yAt(toMillis))
    draw(path)
}

private const val SAMPLES = 160L

private val WAVE_HEIGHT = 132.dp
private val TRACE_WIDTH = 2.dp
private val MARKER_SIZE = ZenSpacing.StatusMark
private val TICK_ZONE = 8.dp
private val TICK_LENGTH = 4.dp
