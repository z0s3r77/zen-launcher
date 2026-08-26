package com.zenlauncher.zen.presentation.usage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.model.formatDurationCompact
import com.zenlauncher.zen.domain.usage.UsageMood
import com.zenlauncher.zen.presentation.components.MonoData
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles

/**
 * El uso del movil de hoy. Registro, no marcador: igual que la pantalla de sesiones, no
 * hay rachas, ni objetivos, ni comparacion con ayer. Comparar con ayer convierte el dato
 * en una puntuacion, y una puntuacion se juega.
 *
 * Solo el dia en curso. Guardar el historico obligaria a escribir en disco lo que hace
 * el usuario con cada aplicacion, y eso es exactamente lo que Zen no quiere tener: lo
 * leido vive en memoria mientras el proceso existe y se va con el.
 */
@Composable
fun UsageScreen(
    state: UsageUiState,
    onBack: () -> Unit,
    onGrantAccess: () -> Unit,
    onOpenWeek: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.usage_title),
            right = stringResource(usageFaceLabel(UsageMood.face(state.reading))),
            leftAccent = state.reading.worthShowing,
            onBack = onBack,
        )

        if (!state.hasAccess) {
            UsageAccessNotice(onGrantAccess = onGrantAccess)
            return@ZenScreen
        }

        // `weight(1f)` y no solo `verticalScroll`: el area de desplazamiento tiene que
        // ocupar **lo que queda** bajo la franja de cabecera, no medirse por su
        // contenido. Sin el peso funciona mientras el contenido quepa, y el dia que no
        // quepa el contenedor se mide mas alto que la pantalla y su recorte deja de caer
        // donde deberia.
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(ZenSpacing.Large))
            ZenHairline()

            Figure(
                label = stringResource(R.string.usage_screen_time),
                value = formatDurationCompact(state.reading.screenMillis),
            )
            ZenHairline()
            Figure(
                label = stringResource(R.string.usage_unlocks),
                value = "%02d".format(state.reading.unlocks),
            )
            ZenHairline()

            // La semana entra por aqui y no por el menu: quien mira cuanto lleva hoy es
            // exactamente quien se pregunta si hoy fue raro o es lo de siempre, y esa la
            // contesta la semana. Se lee ya el dato en la propia fila.
            ZenListRow(
                label = stringResource(R.string.usage_week),
                onClick = onOpenWeek,
                trailing = { MonoLabel(text = stringResource(R.string.usage_week_title)) },
            )
            ZenHairline()

            Spacer(Modifier.height(ZenSpacing.Large))

            if (state.apps.isEmpty()) {
                MonoLabel(text = stringResource(R.string.usage_empty))
                Spacer(Modifier.height(ZenSpacing.Medium).fillMaxWidth())
                return@Column
            }

            MonoLabel(text = stringResource(R.string.usage_apps_section))
            ZenHairline()
            // Solo las que se llevan algo. Una lista de cuarenta aplicaciones con dos
            // minutos cada una no dice nada y convierte la pantalla en un inventario.
            state.apps.take(MAX_APPS).forEach { app ->
                ZenListRow(
                    label = app.label,
                    labelColor = ZenColors.Tertiary,
                    trailing = {
                        MonoData(
                            text = stringResource(
                                R.string.usage_app_summary,
                                formatDurationCompact(app.foregroundMillis),
                                pluralStringResource(
                                    R.plurals.usage_app_openings,
                                    app.openings,
                                    app.openings,
                                ),
                            ),
                        )
                    },
                )
                ZenHairline()
            }
            Spacer(Modifier.height(ZenSpacing.Medium).fillMaxWidth())
        }
    }
}

/**
 * Sin acceso concedido no se ensena un cero: se ensena que no hay medida.
 *
 * La diferencia importa. Un cero dice "hoy no has usado el movil" y seria mentira; esto
 * dice que Zen no puede verlo, que se concede a mano y que se quita en el mismo sitio.
 */
@Composable
internal fun UsageAccessNotice(onGrantAccess: () -> Unit) {
    Spacer(Modifier.height(ZenSpacing.XXLarge))
    MonoLabel(text = stringResource(R.string.usage_no_access))
    Spacer(Modifier.height(ZenSpacing.Small))
    Text(
        text = stringResource(R.string.usage_no_access_body),
        style = ZenTextStyles.Body,
        color = ZenColors.Secondary,
    )
    Spacer(Modifier.height(ZenSpacing.Large))
    ZenHairline()
    ZenListRow(
        label = stringResource(R.string.usage_grant),
        onClick = onGrantAccess,
        trailing = { MonoLabel(text = stringResource(R.string.usage_revoke)) },
    )
    ZenHairline()
}

@Composable
private fun Figure(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        MonoLabel(text = label)
        Text(text = value, style = ZenTextStyles.Figure, color = ZenColors.Foreground)
    }
}

/** Tope de filas por aplicacion. Ver el comentario de arriba. */
private const val MAX_APPS = 10
