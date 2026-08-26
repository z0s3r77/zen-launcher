package com.zenlauncher.zen.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
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
 */
@Composable
fun ZenAppGrid(
    tiles: List<HomeTile>,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        tiles.chunked(COLUMNS).forEachIndexed { rowIndex, rowApps ->
            ZenHairline()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Alto intrinseco para que el filete vertical llegue de arriba abajo
                    // de la fila aunque una celda ocupe dos lineas por `fontScale`.
                    .height(IntrinsicSize.Min),
            ) {
                rowApps.forEachIndexed { columnIndex, tile ->
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
                        index = rowIndex * COLUMNS + columnIndex + 1,
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
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = tile.onClick)
            .heightIn(min = CELL_HEIGHT)
            .padding(end = ZenSpacing.Medium),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonoLabel(text = "%02d".format(index), color = ZenColors.Dim)
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
