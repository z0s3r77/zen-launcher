package com.zenlauncher.zen.presentation.notifications

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.notifications.AppNotification
import com.zenlauncher.zen.domain.notifications.NotificationGroup
import com.zenlauncher.zen.presentation.components.MonoData
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles
import com.zenlauncher.zen.presentation.util.ZenDateFormats

/**
 * El panel de notificaciones, escrito en el idioma de Zen.
 *
 * Es una pantalla aparte y no una fila mas en la pantalla de inicio: alli solo esta el
 * numero, quieto, y hay que ir a buscar esto. Sin iconos, sin colores de marca, sin
 * botones de responder y sin acciones rapidas —lo que ensena es **quien** te escribio y
 * **que** dice—; para cualquier otra cosa se abre la aplicacion.
 *
 * [focusPackage] llega cuando se entro tocando la marca de una aplicacion concreta: se
 * ensena solo esa, porque quien toca el "3" de WhatsApp va a WhatsApp y no al panel
 * entero.
 */
@Composable
fun NotificationsScreen(
    state: NotificationsUiState,
    onOpenApp: (String) -> Unit,
    onGrantAccess: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    focusPackage: String? = null,
) {
    val groups = remember(state.groups, focusPackage) {
        if (focusPackage == null) state.groups
        else state.groups.filter { it.packageName == focusPackage }
    }
    val total = groups.sumOf { it.count }

    // Volver arrastrando desde un borde, al primer intento: con las barras ocultas el
    // gesto de Android necesita dos. Ver `EdgeBackPolicy`.
    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.notifications_title),
            right = "%02d".format(total),
            onBack = onBack,
        )

        if (!state.hasAccess) {
            Spacer(Modifier.height(ZenSpacing.Large))
            MonoLabel(
                text = stringResource(R.string.notifications_no_access),
                maxLines = MAX_NOTICE_LINES,
            )
            Spacer(Modifier.height(ZenSpacing.Medium))
            ZenHairline()
            ZenListRow(
                label = stringResource(R.string.notifications_grant),
                onClick = onGrantAccess,
            )
            ZenHairline()
            return@ZenScreen
        }

        if (groups.isEmpty()) {
            Spacer(Modifier.height(ZenSpacing.Large))
            MonoLabel(text = stringResource(R.string.notifications_empty))
            return@ZenScreen
        }

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            groups.forEach { group ->
                item(key = "cabecera:${group.packageName}") {
                    Spacer(Modifier.height(ZenSpacing.Medium))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MonoLabel(text = group.label.uppercase(), color = ZenColors.Muted)
                        MonoLabel(text = "%02d".format(group.count))
                    }
                    Spacer(Modifier.height(ZenSpacing.Small))
                    ZenHairline(color = ZenColors.Border)
                }

                items(
                    count = group.notifications.size,
                    key = { index -> group.notifications[index].key },
                ) { index ->
                    NotificationRow(
                        notification = group.notifications[index],
                        onClick = { onOpenApp(group.packageName) },
                    )
                    ZenHairline()
                }
            }
        }
    }
}

/**
 * Titulo, texto y hora. Dos lineas de texto como mucho: si el mensaje no cabe, se abre
 * la aplicacion; una pantalla de inicio que deja leerlo entero es una que te retiene.
 */
@Composable
private fun NotificationRow(
    notification: AppNotification,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = ZenSpacing.Row)
            .padding(vertical = ZenSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (notification.title.isNotBlank()) {
                Text(
                    text = notification.title,
                    style = ZenTextStyles.ListItem,
                    color = ZenColors.Foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (notification.text.isNotBlank()) {
                Spacer(Modifier.height(ZenSpacing.XSmall))
                Text(
                    text = notification.text,
                    style = ZenTextStyles.Tile,
                    color = ZenColors.Muted,
                    maxLines = MAX_TEXT_LINES,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(ZenSpacing.Medium))
        MonoData(text = ZenDateFormats.time(notification.postTime), color = ZenColors.Dim)
    }
}

private const val MAX_TEXT_LINES = 2
private const val MAX_NOTICE_LINES = 4
