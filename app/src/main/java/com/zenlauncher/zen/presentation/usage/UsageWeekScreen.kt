package com.zenlauncher.zen.presentation.usage

import androidx.annotation.StringRes
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
import com.zenlauncher.zen.domain.usage.PatternAction
import com.zenlauncher.zen.domain.usage.PatternKind
import com.zenlauncher.zen.domain.usage.UsagePatterns
import com.zenlauncher.zen.domain.usage.WeekVerdict
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenTagButton
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles
import java.util.Locale

@StringRes
internal fun verdictLabel(verdict: WeekVerdict): Int = when (verdict) {
    WeekVerdict.BAJO_CONTROL -> R.string.usage_verdict_control
    WeekVerdict.ATENCION -> R.string.usage_verdict_atencion
    WeekVerdict.FUERA_DE_MANO -> R.string.usage_verdict_fuera
}

/**
 * La semana: la grafica, las tres cifras que la resumen, el veredicto y el patron.
 *
 * **El veredicto va antes que el patron a proposito.** La pregunta con la que alguien
 * entra aqui es "¿lo tengo controlado?", y esa se contesta con una palabra; el detalle
 * de por que viene despues, para quien quiera leerlo. Al reves, la pantalla obligaba a
 * interpretar tres observaciones para deducir la respuesta.
 *
 * **Y no hay consejo sin cifra ni cifra sin salida.** Cada observacion lleva el numero
 * que la sostiene, y la que tiene arreglo dentro de Zen lleva su boton: restringir esa
 * aplicacion. Una recomendacion que no lleva a ninguna parte es un sermon.
 */
@Composable
fun UsageWeekScreen(
    state: WeekUiState,
    onBack: () -> Unit,
    onOpenRestricted: () -> Unit,
    onGrantAccess: () -> Unit,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
) {
    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.usage_week_title),
            // Cuantos dias sostienen lo que hay debajo, y no el veredicto ni la media:
            // los dos se leen enteros mas abajo, y ponerlos aqui era decir lo mismo dos
            // veces en la misma pantalla. Este dato no esta en ningun otro sitio y es el
            // que dice cuanto hay que fiarse del resto. La marca ambar de la izquierda
            // es la que avisa desde arriba.
            right = if (state.hasAccess && !state.loading) {
                stringResource(
                    R.string.usage_week_days,
                    state.week.measuredDays.size,
                    state.week.days.size,
                )
            } else {
                ""
            },
            leftAccent = state.verdict != WeekVerdict.BAJO_CONTROL,
            onBack = onBack,
        )

        if (!state.hasAccess && !state.loading) {
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

            if (state.loading) {
                MonoLabel(text = stringResource(R.string.usage_week_loading))
                return@Column
            }

            if (!state.week.hasData) {
                MonoLabel(text = stringResource(R.string.usage_week_empty), maxLines = 3)
                return@Column
            }

            UsageWeekChart(week = state.week, locale = locale)

            Spacer(Modifier.height(ZenSpacing.Large))
            ZenHairline()
            WeekFigure(
                label = stringResource(R.string.usage_week_average),
                value = formatDurationCompact(state.week.averageMillis),
            )
            ZenHairline()
            WeekFigure(
                label = stringResource(R.string.usage_week_total),
                value = formatDurationCompact(state.week.totalMillis),
            )
            ZenHairline()
            WeekFigure(
                label = stringResource(R.string.usage_week_unlocks),
                value = "%02d".format(state.week.averageUnlocks),
            )
            ZenHairline()

            Spacer(Modifier.height(ZenSpacing.Large))
            Verdict(state = state)

            Spacer(Modifier.height(ZenSpacing.Large))
            Patterns(state = state, onOpenRestricted = onOpenRestricted)

            Spacer(Modifier.height(ZenSpacing.Large))
            // Por que la ventana es la que es y por que hay dias vacios: es una
            // consecuencia directa de no guardar nada, y se explica en vez de disimularse.
            MonoLabel(
                text = stringResource(R.string.usage_week_notice),
                color = ZenColors.Disabled,
                maxLines = 5,
            )
            Spacer(Modifier.height(ZenSpacing.Medium).fillMaxWidth())
        }
    }
}

/** La respuesta en una palabra, arriba del todo del detalle. */
@Composable
private fun Verdict(state: WeekUiState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        MonoLabel(text = stringResource(R.string.usage_verdict_label))
        Text(
            text = stringResource(verdictLabel(state.verdict)),
            style = ZenTextStyles.Figure,
            color = ZenColors.Foreground,
        )
    }
}

@Composable
private fun Patterns(state: WeekUiState, onOpenRestricted: () -> Unit) {
    MonoLabel(text = stringResource(R.string.usage_patterns_section))
    Spacer(Modifier.height(ZenSpacing.Small))
    ZenHairline()

    if (state.patterns.isEmpty()) {
        // Se distingue "no hay patron" de "todavia no se puede saber": llamar ladrona a
        // una aplicacion porque ayer viste una serie seria adivinar, y decir que todo va
        // bien con un solo dia medido tambien.
        val message = if (state.week.measuredDays.size < UsagePatterns.MIN_DAYS) {
            R.string.usage_patterns_too_soon
        } else {
            R.string.usage_patterns_none
        }
        Spacer(Modifier.height(ZenSpacing.Medium))
        MonoLabel(text = stringResource(message), color = ZenColors.Muted, maxLines = 3)
        Spacer(Modifier.height(ZenSpacing.Medium))
        ZenHairline()
        return
    }

    state.patterns.forEach { row ->
        val pattern = row.pattern
        val name = row.label ?: pattern.packageName.orEmpty()
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = ZenSpacing.Medium)) {
            Text(
                text = when (pattern.kind) {
                    PatternKind.LADRONA ->
                        stringResource(R.string.usage_pattern_ladrona, name, pattern.value)

                    PatternKind.REPETIDA ->
                        stringResource(R.string.usage_pattern_repetida, name, pattern.value)

                    PatternKind.SUBIENDO ->
                        stringResource(R.string.usage_pattern_subiendo, pattern.value)
                },
                style = ZenTextStyles.Body,
                color = ZenColors.Foreground,
            )
            Spacer(Modifier.height(ZenSpacing.Small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (pattern.dailyMillis > 0L) {
                    MonoLabel(
                        text = stringResource(
                            R.string.usage_pattern_daily,
                            formatDurationCompact(pattern.dailyMillis),
                        ),
                    )
                }
                Spacer(Modifier.weight(1f))
                if (pattern.action == PatternAction.RESTRINGIR) {
                    ZenTagButton(
                        text = stringResource(R.string.usage_pattern_restrict),
                        onClick = onOpenRestricted,
                        onClickLabel = stringResource(R.string.usage_pattern_restrict_label),
                    )
                }
            }
        }
        ZenHairline()
    }
}

@Composable
private fun WeekFigure(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        MonoLabel(text = label)
        Text(text = value, style = ZenTextStyles.Figure, color = ZenColors.Foreground)
    }
}
