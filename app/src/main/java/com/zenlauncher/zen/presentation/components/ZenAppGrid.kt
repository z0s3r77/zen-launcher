package com.zenlauncher.zen.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitDragOrCancellation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.apps.HomeAppOrder
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles

private const val COLUMNS = 2

/**
 * Una celda de la reticula.
 *
 * No es "una aplicacion instalada" a proposito: es **algo que se abre desde la pantalla
 * de inicio y se usa como si fuera una aplicacion**. Notas lo es. Vivia abajo, como una
 * fila igual que "Menu", y por eso se leia como una opcion de administracion en lugar de
 * como el sitio donde se escribe; ahora es una celda mas, con su numero, en la misma
 * lengua visual que WhatsApp o Spotify.
 *
 * La home **no crece** por esto: la fila de ancho completo que ocupaba Notas (64dp)
 * desaparece y en su lugar aparece media fila de reticula (60dp), y con un numero impar
 * de aplicaciones ni siquiera eso, porque Notas cae en el hueco que ya quedaba vacio.
 */
data class HomeTile(
    val label: String,
    val onClick: () -> Unit,
    val notifications: Int = 0,
    val onOpenNotifications: (() -> Unit)? = null,
)

/**
 * Rejilla de dos columnas para las aplicaciones de la pantalla de inicio.
 *
 * Sigue sin haber iconos: la rejilla es de **texto**, y existe solo porque ocho nombres
 * en una columna empujaban el reloj fuera de la pantalla y obligaban a desplazar para
 * llegar a lo mas usado. Con dos columnas todo entra en la zona del pulgar.
 *
 * Se construye con `Column` de `Row` y no con `LazyVerticalGrid`: son ocho elementos
 * como mucho y va dentro de una pantalla que ya se desplaza, donde una rejilla perezosa
 * anidada no puede medirse.
 *
 * @param movable cuantas celdas del principio se pueden reordenar. La reticula lleva al
 *   final celdas que **no** son aplicaciones —Notas y Lectura— y esas ni se mueven ni
 *   sirven de destino: su sitio es una decision de producto, no del usuario.
 * @param onMove destino y origen de un arrastre ya terminado. Null deja la reticula
 *   quieta, que es como estaba.
 */
@Composable
fun ZenAppGrid(
    tiles: List<HomeTile>,
    modifier: Modifier = Modifier,
    movable: Int = 0,
    onMove: ((from: Int, to: Int) -> Unit)? = null,
) {
    // Mover exige dos huecos que intercambiar: con una sola aplicacion en el inicio, la
    // pulsacion larga no hace nada en lugar de coger algo que no puede ir a ningun lado.
    val reorderable = onMove != null && movable > 1

    var dragging by remember { mutableStateOf<Int?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }
    // El centro de cada hueco, medido. Ver [HomeAppOrder.Slot] para por que medido y no
    // calculado a partir del alto de celda.
    val slots = remember { mutableStateMapOf<Int, HomeAppOrder.Slot>() }

    val from = dragging
    val target = when {
        from == null -> null
        else -> {
            val centers = (0 until movable).mapNotNull { slots[it] }
            // Hasta que estan medidos todos los huecos no hay a donde ir: quedarse es
            // la respuesta segura, y en el fotograma siguiente ya estan.
            if (centers.size == movable) {
                HomeAppOrder.slotAt(from, drag.x, drag.y, centers)
            } else {
                from
            }
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        tiles.chunked(COLUMNS).forEachIndexed { rowIndex, rowApps ->
            ZenHairline()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Alto intrinseco para que el filete vertical llegue de arriba abajo
                    // de la fila aunque una celda ocupe dos lineas por `fontScale`.
                    .height(IntrinsicSize.Min)
                    // La celda en la mano se dibuja encima de las demas, y las demas
                    // viven en otras filas: sin levantar la fila entera, la celda pasa
                    // por debajo de la de al lado en cuanto sale de la suya.
                    .zIndex(if (from != null && from / COLUMNS == rowIndex) 1f else 0f),
            ) {
                rowApps.forEachIndexed { columnIndex, tile ->
                    val index = rowIndex * COLUMNS + columnIndex
                    if (columnIndex > 0) {
                        Box(
                            Modifier
                                .width(ZenSpacing.Hairline)
                                .fillMaxHeight()
                                .background(ZenColors.Hairline),
                        )
                        Spacer(Modifier.width(ZenSpacing.Medium))
                    }
                    AppCell(
                        tile = tile,
                        index = index,
                        movable = reorderable && index < movable,
                        movableCount = movable,
                        dragging = from == index,
                        measuring = from != null,
                        // El numero que ensena la celda en la mano es **el destino**, no
                        // el hueco de donde salio: es el unico sitio donde cabe decir
                        // adonde va a caer, y cambia mientras el dedo se mueve.
                        slot = if (from == index) (target ?: index) else index,
                        dragOffset = if (from == index) drag else Offset.Zero,
                        onMove = onMove,
                        onDragStart = {
                            dragging = index
                            drag = Offset.Zero
                        },
                        onDrag = { drag += it },
                        onDragEnd = {
                            val to = target
                            dragging = null
                            drag = Offset.Zero
                            if (to != null && to != index) onMove?.invoke(index, to)
                        },
                        onDragCancel = {
                            dragging = null
                            drag = Offset.Zero
                        },
                        onSlotMeasured = { measured ->
                            // Un `SnapshotStateMap` avisa en cada escritura, tambien si
                            // el valor es el mismo: sin comparar, medir recomponia y
                            // recomponer volvia a medir, para siempre.
                            if (slots[index] != measured) slots[index] = measured
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Una fila impar deja el hueco vacio en lugar de estirar la celda: la
                // retícula tiene que seguir leyendose como retícula.
                if (rowApps.size < COLUMNS) Spacer(Modifier.weight((COLUMNS - rowApps.size).toFloat()))
            }
        }
    }
}

@Composable
private fun AppCell(
    tile: HomeTile,
    index: Int,
    movable: Boolean,
    movableCount: Int,
    dragging: Boolean,
    /** Hay **algun** arrastre en curso en la retícula, sea o no el de esta celda. */
    measuring: Boolean,
    slot: Int,
    dragOffset: Offset,
    onMove: ((from: Int, to: Int) -> Unit)?,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    onSlotMeasured: (HomeAppOrder.Slot) -> Unit,
    modifier: Modifier = Modifier,
) {
    val moveBack = stringResource(R.string.home_grid_move_back)
    val moveForward = stringResource(R.string.home_grid_move_forward)
    val moving = stringResource(R.string.home_grid_moving, slot + 1)

    Column(
        modifier = modifier
            // La celda en la mano se mueve con el dedo. `graphicsLayer` y no `offset`
            // porque no cambia la maquetacion: el hueco de donde salio se queda abierto,
            // que es lo que deja ver a donde vuelve si se suelta sin llegar a nada.
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
            }
            .clickable(role = Role.Button, onClick = tile.onClick)
            // Va **despues** de `clickable` a proposito: el nodo interior recibe la
            // pasada Main primero, asi que consumir aqui el "levanta el dedo" apaga el
            // toque de la celda. Sin esto, soltar encima abria la aplicacion que se
            // acababa de mover.
            .reorderDrag(
                enabled = movable,
                index = index,
                onDragStart = onDragStart,
                onDrag = onDrag,
                onDragEnd = onDragEnd,
                onDragCancel = onDragCancel,
            )
            // Medir los huecos **solo mientras hay un arrastre en curso**.
            //
            // Estaba puesto siempre, y `onGloballyPositioned` se dispara en cada pasada
            // de maquetacion: desde que la home se desplaza, arrastrar la columna hacia
            // abajo hacia que cada celda calculase su `boundsInRoot` y reservase un
            // `Slot` nuevo en cada fotograma, para nada —no habia ningun arrastre de
            // celda que necesitase saber donde estan los huecos—.
            //
            // La medida llega un fotograma tarde al empezar a arrastrar, y eso ya estaba
            // contemplado: hasta que estan medidos todos los huecos el destino es el
            // hueco de origen, que es la respuesta segura.
            .then(
                if (movable && measuring) {
                    Modifier.onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        onSlotMeasured(
                            HomeAppOrder.Slot(
                                // El centro **sin** el arrastre: es el hueco, no la
                                // celda. Con la celda en la mano medida donde esta,
                                // el destino se perseguiria a si mismo.
                                x = bounds.center.x - dragOffset.x,
                                y = bounds.center.y - dragOffset.y,
                            ),
                        )
                    }
                } else {
                    Modifier
                },
            )
            .then(
                if (movable) {
                    Modifier
                        // Arrastrar no existe para quien navega con un lector de
                        // pantalla. Las dos acciones dicen lo mismo con palabras y no
                        // dependen de poder apuntar a un sitio.
                        .semantics {
                            val actions = buildList {
                                if (index > 0) {
                                    add(CustomAccessibilityAction(moveBack) {
                                        onMove?.invoke(index, index - 1)
                                        onMove != null
                                    })
                                }
                                if (index < movableCount - 1) {
                                    add(CustomAccessibilityAction(moveForward) {
                                        onMove?.invoke(index, index + 1)
                                        onMove != null
                                    })
                                }
                            }
                            if (actions.isNotEmpty()) customActions = actions
                            // Mientras se arrastra, el estado se lee como texto: el
                            // numero que cambia en la celda es la misma informacion,
                            // pero un numero no se anuncia solo.
                            if (dragging) stateDescription = moving
                        }
                } else {
                    Modifier
                },
            )
            .then(
                // La celda en la mano pasa por encima de las demas: sin fondo propio se
                // leerian las dos a la vez. El marco de un pixel es el mismo de la marca
                // de avisos y del boton ZEN, no un adorno nuevo.
                if (dragging) {
                    Modifier
                        .background(ZenColors.Background)
                        .border(ZenSpacing.Hairline, ZenColors.Border)
                } else {
                    Modifier
                },
            )
            .heightIn(min = CELL_HEIGHT)
            .padding(end = ZenSpacing.Medium),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoLabel(
                text = "%02d".format(slot + 1),
                // Encendido solo mientras esta en la mano: el numero deja de ser el
                // rotulo de un hueco y pasa a ser lo que va a pasar al soltar.
                color = if (dragging) ZenColors.Foreground else ZenColors.Dim,
            )
            Spacer(Modifier.weight(1f))
            val onOpenNotifications = tile.onOpenNotifications
            if (tile.notifications > 0 && onOpenNotifications != null) {
                NotificationBadge(
                    count = tile.notifications,
                    label = tile.label,
                    onClick = onOpenNotifications,
                )
            }
        }
        Spacer(Modifier.height(ZenSpacing.XSmall))
        Text(
            text = tile.label,
            style = ZenTextStyles.Tile,
            color = ZenColors.Foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Mantener pulsada una celda y arrastrarla a otro hueco.
 *
 * **Pulsacion larga y no arrastre a secas**: la reticula esta llena de celdas que se
 * tocan cincuenta veces al dia, y un arrastre directo convertiria cualquier roce al
 * sacar el telefono del bolsillo en una pantalla de inicio reordenada. Ademas el
 * arrastre horizontal ya significa "volver" en el resto de Zen (ver `EdgeBackPolicy`):
 * exigir la pulsacion larga es lo que deja convivir los dos sin que ninguno adivine.
 *
 * Se consume todo lo que pasa despues de la pulsacion larga —el movimiento y el dedo
 * levantado— para que ni el toque de la celda ni el doble toque de `ZenScreen` lleguen a
 * dispararse.
 */
@Composable
private fun Modifier.reorderDrag(
    enabled: Boolean,
    index: Int,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier {
    // El bloque de `pointerInput` se queda con las funciones de la **primera**
    // composicion, y el destino del arrastre se recalcula en cada fotograma: sin esto,
    // soltar llamaba a un `onDragEnd` que todavia creia que no habia destino y no movia
    // nada nunca. Los `remember` van antes de la salida temprana: `enabled` cambia
    // cuando cambia el numero de aplicaciones del inicio.
    val start by rememberUpdatedState(onDragStart)
    val move by rememberUpdatedState(onDrag)
    val end by rememberUpdatedState(onDragEnd)
    val cancel by rememberUpdatedState(onDragCancel)

    if (!enabled) return this

    return pointerInput(index) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture

            start()
            var pointer = held.id
            while (true) {
                val change = awaitDragOrCancellation(pointer)
                if (change == null) {
                    // Otro gesto se llevo el dedo: se suelta donde estaba.
                    cancel()
                    break
                }
                if (change.changedToUpIgnoreConsumed()) {
                    change.consume()
                    end()
                    break
                }
                move(change.positionChange())
                change.consume()
                pointer = change.id
            }
        }
    }
}

/**
 * La marca de avisos: el numero de notificaciones pendientes de esa aplicacion.
 *
 * Es el **numero** y no un punto de color porque un punto solo dice "algo hay" y
 * obliga a abrir la aplicacion para saber si merece la pena; el numero cierra la
 * pregunta desde la pantalla de inicio. Va en un marco de un pixel, sin relleno y sin
 * ambar: el ambar esta reservado a las marcas de estado de 6dp.
 *
 * Tocarlo abre la lista **sin abrir la aplicacion**, que es justo la diferencia entre
 * mirar quien te escribio y caer dentro de la aplicacion veinte minutos.
 */
@Composable
private fun NotificationBadge(
    count: Int,
    label: String,
    onClick: () -> Unit,
) {
    val description = pluralStringResource(R.plurals.home_notifications_badge, count, count, label)
    Box(
        modifier = Modifier
            // El area tactil llega al minimo de accesibilidad sin que el marco crezca:
            // el resto de la celda sigue abriendo la aplicacion.
            .size(BADGE_TOUCH_TARGET)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .border(ZenSpacing.Hairline, ZenColors.Border)
                .padding(horizontal = ZenSpacing.XSmall),
        ) {
            MonoLabel(text = count.toString(), color = ZenColors.Foreground)
        }
    }
}

private val BADGE_TOUCH_TARGET = 40.dp

/**
 * 60dp: por encima del minimo tactil de 48dp y ocho celdas caben en una pantalla que ya
 * no se desplaza. Con 72dp la ultima fila quedaba fuera en un Nothing Phone (2a).
 */
private val CELL_HEIGHT = 60.dp
