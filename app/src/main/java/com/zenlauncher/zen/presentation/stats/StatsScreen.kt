package com.zenlauncher.zen.presentation.stats

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.model.formatDurationCompact
import com.zenlauncher.zen.domain.stats.ZenStats
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles

/**
 * Registro, no marcador. Sin rachas, sin objetivos, sin comparaciones con la semana
 * pasada: solo si Zen se esta usando y cuanto.
 */
@Composable
fun StatsScreen(
    stats: ZenStats,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Volver arrastrando desde un borde, al primer intento: con las barras ocultas el
    // gesto de Android necesita dos. Ver `EdgeBackPolicy`.
    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.stats_title),
            right = "%02d".format(stats.totalCount),
            onBack = onBack,
        )

        if (stats.isEmpty) {
            Spacer(Modifier.height(ZenSpacing.XXLarge))
            MonoLabel(text = stringResource(R.string.stats_empty))
            return@ZenScreen
        }

        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Spacer(Modifier.height(ZenSpacing.Large))
            ZenHairline()

            StatRow(
                label = stringResource(R.string.stats_total_time),
                value = formatDurationCompact(stats.totalZenMillis),
            )
            ZenHairline()
            StatRow(
                label = stringResource(R.string.stats_completed),
                value = "%02d".format(stats.completedCount),
            )
            ZenHairline()
            StatRow(
                label = stringResource(R.string.stats_abandoned),
                value = "%02d".format(stats.abandonedCount),
                valueColor = ZenColors.Muted,
            )
            ZenHairline()
            StatRow(
                label = stringResource(R.string.stats_battery),
                value = if (stats.hasBatteryData) {
                    stringResource(R.string.stats_battery_value, stats.batteryConsumedPercent)
                } else {
                    stringResource(R.string.stats_battery_unavailable)
                },
            )
            ZenHairline()
            StatRow(
                label = stringResource(R.string.stats_longest),
                value = formatDurationCompact(stats.longestSessionMillis),
            )
            ZenHairline()
            StatRow(
                label = stringResource(R.string.stats_average),
                value = formatDurationCompact(stats.averageSessionMillis),
            )
            ZenHairline()
            StatRow(
                label = stringResource(R.string.stats_completion_rate),
                value = "${stats.completionRatePercent}%",
            )
            ZenHairline()

            Spacer(Modifier.height(ZenSpacing.Large))
            if (stats.hasBatteryData) {
                MonoLabel(
                    text = stringResource(
                        R.string.stats_battery_notice,
                        stats.batterySampleCount,
                    ),
                    color = ZenColors.Dim,
                    maxLines = 3,
                )
            }
            Spacer(Modifier.height(ZenSpacing.Medium).fillMaxWidth())
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = ZenColors.Foreground,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 26.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        MonoLabel(text = label)
        Text(text = value, style = ZenTextStyles.Figure, color = valueColor)
    }
}
