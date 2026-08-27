package com.zenlauncher.zen.presentation.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.presentation.theme.ZenColors

/**
 * Ecualizador de cuatro barras: el mismo vocabulario que el medidor de bateria, pero en
 * vertical y en movimiento.
 *
 * **No reacciona al sonido real, y no puede.** Leer la onda exige `Visualizer`, que pide
 * el permiso `RECORD_AUDIO` —el microfono— y que ademas ya no captura la mezcla global
 * del dispositivo. Pedir el microfono para animar cuatro barras seria un intercambio
 * pesimo. Lo que si es cierto es lo unico que promete: se mueve mientras suena algo y se
 * queda quieto en cuanto se pausa.
 *
 * Cada barra lleva un periodo distinto y deliberadamente no multiplo de los demas, para
 * que el conjunto no se sincronice en un vaiven de sierra reconocible.
 *
 * Parado no anima **nada**: sin sesion sonando no hay ni un fotograma de mas, que es lo
 * que se espera de una pantalla que esta encendida todo el dia.
 */
@Composable
fun PlaybackEqualizer(
    playing: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "ecualizador")

    Row(
        // Decorativo: la misma fila ya dice SONANDO o EN PAUSA con todas las letras, y
        // un lector de pantalla no gana nada anunciando cuatro barras.
        modifier = modifier
            .height(MAX_BAR_HEIGHT)
            .clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
        verticalAlignment = Alignment.Bottom,
    ) {
        BAR_PERIODS_MILLIS.forEachIndexed { index, period ->
            val level: State<Float>? = if (playing) {
                transition.animateFloat(
                    initialValue = REST_LEVEL,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = period, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse,
                        // El desfase inicial evita que las cuatro salgan del mismo sitio.
                        initialStartOffset = BAR_OFFSETS[index],
                    ),
                    label = "barra",
                )
            } else {
                null
            }

            // La barra mide **siempre** lo mismo y se escala. Antes el alto era
            // `MAX_BAR_HEIGHT * level` con `level` leido en la composicion: cada
            // fotograma recomponia las cuatro barras y remedia la fila, la columna y —por
            // la cadena de medida— el cuerpo de la pantalla de inicio, a 120 Hz mientras
            // sonara algo. Leyendo el valor dentro de `graphicsLayer`, la animacion se
            // queda en la capa: ni composicion ni medida, solo dibujo.
            Box(
                Modifier
                    .width(BAR_WIDTH)
                    .height(MAX_BAR_HEIGHT)
                    .graphicsLayer {
                        // El origen abajo: la barra crece hacia arriba, como estaba.
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        scaleY = level?.value ?: REST_LEVEL
                    }
                    .background(if (playing) ZenColors.Foreground else ZenColors.Faint),
            )
        }
    }
}

private val BAR_PERIODS_MILLIS = listOf(520, 380, 660, 460)
private val BAR_OFFSETS = listOf(
    androidx.compose.animation.core.StartOffset(0),
    androidx.compose.animation.core.StartOffset(160),
    androidx.compose.animation.core.StartOffset(80),
    androidx.compose.animation.core.StartOffset(240),
)

/** En reposo las barras no desaparecen: quedan como un filete, igual que la bateria. */
private const val REST_LEVEL = 0.2f

private val BAR_WIDTH = 3.dp
private val BAR_GAP = 2.dp
private val MAX_BAR_HEIGHT = 14.dp
