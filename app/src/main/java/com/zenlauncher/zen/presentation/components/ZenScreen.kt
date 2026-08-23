package com.zenlauncher.zen.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.zenlauncher.zen.domain.system.EdgeBackPolicy
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing

/**
 * Contenedor de pantalla.
 *
 * Usa `WindowInsets.safeDrawing` en lugar de margenes fijos porque targetSdk 36 impone
 * edge-to-edge: la barra de estado y la de gestos se dibujan sobre el contenido, y en
 * un Nothing Phone (2a) con barra de gestos los numeros grandes quedarian cortados.
 */
@Composable
fun ZenScreen(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = ZenSpacing.ScreenHorizontal,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    onSwipeBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val doubleTapToLock = LocalDoubleTapToLock.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .edgeBack(onSwipeBack)
            .background(ZenColors.Background)
            // Doble toque en el fondo para apagar la pantalla. Las filas y botones
            // consumen sus propios toques, asi que `detectTapGestures` —que exige un
            // primer contacto sin consumir— no se dispara encima de ellos: el gesto
            // queda restringido a las zonas vacias, que es justo lo que se busca.
            .pointerInput(doubleTapToLock) {
                detectTapGestures(onDoubleTap = { doubleTapToLock() })
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(
                start = horizontalPadding,
                end = horizontalPadding,
                top = ZenSpacing.ScreenTop,
                bottom = ZenSpacing.ScreenBottom,
            ),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/**
 * Arrastrar desde un borde lateral hacia dentro para volver.
 *
 * Zen reconoce el gesto **por su cuenta** en lugar de esperar al de Android, porque con
 * las barras ocultas el sistema se queda el primer deslizamiento para sacarlas y solo
 * el segundo llega como "atras": el usuario tenia que deslizar dos veces, siempre. Los
 * eventos si llegan a la aplicacion durante ese primer deslizamiento, asi que aqui se
 * responde al primero. Ver [EdgeBackPolicy].
 *
 * No se consume nada en la pasada Initial: el toque inicial solo se **observa**, para no
 * cambiar el comportamiento de ningun otro gesto de la pantalla.
 */
@Composable
private fun Modifier.edgeBack(onBack: (() -> Unit)?): Modifier {
    if (onBack == null) return this

    val density = LocalDensity.current
    val edge = with(density) { EdgeBackPolicy.EDGE_DP.dp.toPx() }
    val threshold = with(density) { EdgeBackPolicy.THRESHOLD_DP.dp.toPx() }
    val touchDownX = remember { mutableFloatStateOf(0f) }

    return this
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                touchDownX.floatValue = down.position.x
            }
        }
        .pointerInput(onBack, edge, threshold) {
            var accumulated = 0f
            detectHorizontalDragGestures(
                onDragStart = { accumulated = 0f },
                onDragEnd = {
                    val goes = EdgeBackPolicy.goesBack(
                        startX = touchDownX.floatValue,
                        width = size.width.toFloat(),
                        edge = edge,
                        dragged = accumulated,
                        threshold = threshold,
                    )
                    if (goes) onBack()
                },
            ) { change, dragAmount ->
                accumulated += dragAmount
                change.consume()
            }
        }
}
