package com.zenlauncher.zen.presentation.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.model.formatDurationCompact
import com.zenlauncher.zen.domain.usage.UsagePressure
import com.zenlauncher.zen.domain.usage.WeeklyUsage
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.util.ZenDateFormats
import java.util.Locale

/** Alto de la zona de barras. Siete columnas caben de sobra en el ancho de un movil. */
private val CHART_HEIGHT = 132.dp

/** Ancho de cada barra dentro de su columna: deja aire y no se lee como un bloque solido. */
private const val BAR_WIDTH_FRACTION = 0.44f

/** Un dia sin medir deja este mun~on en la base, no una barra a cero. */
private val UNMEASURED_STUB = 2.dp

/**
 * La semana en barras.
 *
 * **Es la unica grafica de Zen y existe porque la pregunta que contesta no cabe en un
 * numero**: "¿esto va a mas o a menos?" se responde de un vistazo con siete barras y no
 * con siete cifras en fila. Todo lo demas de la aplicacion sigue siendo texto.
 *
 * Monocroma y sin ejes: la unica referencia es **la linea del umbral de USO ALTO**, y
 * esa es justo la que convierte el dibujo en informacion —los dias que la pasan se ven
 * sin contar nada—. Sin ella, siete barras relativas entre si no dicen si el conjunto
 * esta bien o mal, solo cual fue el peor.
 *
 * La escala nunca baja del umbral, asi que en una semana tranquila las barras salen
 * bajas de verdad en lugar de estirarse hasta arriba y aparentar un problema que no
 * existe.
 *
 * No se anima ni al entrar ni al cambiar: es un registro, no un panel.
 */
@Composable
fun UsageWeekChart(
    week: WeeklyUsage,
    modifier: Modifier = Modifier,
    locale: Locale = Locale.getDefault(),
) {
    val highMillis = UsagePressure.HIGH_MINUTES * 60_000L
    val scale = maxOf(week.busiestMillis, highMillis).coerceAtLeast(1L)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
            Row(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Small),
            ) {
                week.days.forEachIndexed { index, day ->
                    val isToday = index == week.days.lastIndex
                    val description = if (day.measured) {
                        "${ZenDateFormats.weekdayInitial(day.dayStartMillis, locale)} " +
                            formatDurationCompact(day.screenMillis)
                    } else {
                        stringResource(R.string.usage_day_unmeasured)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            // Una grafica no se lee sola: cada barra dice en voz alta su
                            // dia y su tiempo, que es la regla de que todo estado se
                            // pueda leer como texto y no solo por la forma.
                            .clearAndSetSemantics { contentDescription = description },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(BAR_WIDTH_FRACTION)
                                .height(
                                    if (day.measured) {
                                        CHART_HEIGHT * (day.screenMillis.toFloat() / scale)
                                    } else {
                                        UNMEASURED_STUB
                                    },
                                )
                                .background(
                                    when {
                                        !day.measured -> ZenColors.Faint
                                        isToday -> ZenColors.Foreground
                                        else -> ZenColors.Disabled
                                    },
                                ),
                        )
                    }
                }
            }

            // La linea del umbral, medida desde abajo. Es lo que da sentido a las
            // alturas: sin referencia, siete barras solo se comparan entre ellas.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(y = -(CHART_HEIGHT * (highMillis.toFloat() / scale)))
                    .fillMaxWidth()
                    .height(ZenSpacing.Hairline)
                    .background(ZenColors.Border),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Small),
        ) {
            week.days.forEachIndexed { index, day ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    MonoLabel(
                        text = ZenDateFormats.weekdayInitial(day.dayStartMillis, locale),
                        color = if (index == week.days.lastIndex) {
                            ZenColors.Foreground
                        } else {
                            ZenColors.Dim
                        },
                    )
                }
            }
        }

        MonoLabel(
            text = stringResource(R.string.usage_chart_high),
            color = ZenColors.Disabled,
        )
    }
}
