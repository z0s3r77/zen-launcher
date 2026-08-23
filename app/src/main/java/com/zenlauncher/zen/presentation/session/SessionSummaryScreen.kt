package com.zenlauncher.zen.presentation.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.model.ZenSession
import com.zenlauncher.zen.domain.model.formatDurationClock
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
 * Resumen. Registra lo ocurrido sin felicitar ni renir: una sesion abandonada se
 * muestra con el mismo tono que una completada, solo cambia el dato.
 */
@Composable
fun SessionSummaryScreen(
    session: ZenSession,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Volver arrastrando desde un borde, al primer intento: con las barras ocultas el
    // gesto de Android necesita dos. Ver `EdgeBackPolicy`.
    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.summary_title),
            right = stringResource(
                if (session.completed) R.string.summary_completed
                else R.string.summary_abandoned,
            ),
        )

        Spacer(Modifier.height(ZenSpacing.XXLarge))

        Text(
            text = formatDurationClock(session.actualDurationMillis),
            style = ZenTextStyles.Timer,
            color = ZenColors.Foreground,
        )
        Spacer(Modifier.height(ZenSpacing.Small))
        MonoLabel(text = stringResource(R.string.summary_actual_duration))

        Spacer(Modifier.height(ZenSpacing.XXLarge))

        Column {
            ZenHairline()
            SummaryRow(
                label = stringResource(R.string.summary_planned),
                value = formatDurationClock(session.plannedDurationMillis),
            )
            ZenHairline()
            SummaryRow(
                label = stringResource(R.string.summary_started),
                value = ZenDateFormats.time(session.startedAtMillis),
            )
            ZenHairline()
            SummaryRow(
                label = stringResource(R.string.summary_ended),
                value = ZenDateFormats.time(session.endedAtMillis),
            )
            ZenHairline()
            SummaryRow(
                label = stringResource(R.string.summary_battery),
                // Cuando la medida no es fiable se dice, en lugar de inventar un cero.
                value = session.batteryConsumedPercent
                    ?.let { stringResource(R.string.summary_battery_value, it) }
                    ?: stringResource(R.string.summary_battery_unavailable),
            )
            ZenHairline()
            SummaryRow(
                label = stringResource(R.string.summary_blocked),
                value = "%02d".format(session.restrictedAppsCount),
            )
            ZenHairline()
        }

        Spacer(Modifier.height(ZenSpacing.XXLarge))

        ZenHairline(color = ZenColors.Border)
        ZenListRow(
            label = stringResource(R.string.summary_back),
            onClick = onBack,
        )
        ZenHairline()
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MonoLabel(text = label)
        MonoData(text = value)
    }
}
