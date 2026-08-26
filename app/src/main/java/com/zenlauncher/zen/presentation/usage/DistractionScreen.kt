package com.zenlauncher.zen.presentation.usage

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.model.formatDurationCompact
import com.zenlauncher.zen.domain.usage.CompulsionKind
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenListRow
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenTagButton
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles

@StringRes
internal fun distractionKindLabel(kind: CompulsionKind): Int = when (kind) {
    CompulsionKind.ARRASTRE -> R.string.distraction_kind_arrastre
    CompulsionKind.REPETICION -> R.string.distraction_kind_repeticion
    CompulsionKind.PICOTEO -> R.string.distraction_kind_picoteo
}

@StringRes
internal fun distractionBody(kind: CompulsionKind): Int = when (kind) {
    CompulsionKind.ARRASTRE -> R.string.distraction_body_arrastre
    CompulsionKind.REPETICION -> R.string.distraction_body_repeticion
    CompulsionKind.PICOTEO -> R.string.distraction_body_picoteo
}

/**
 * El aviso: lo que acabas de hacer, en numeros, y dos cosas que Zen sabe hacer en su
 * lugar.
 *
 * **Sustituye a la pantalla entera, como la sesion activa**, y no es una notificacion ni
 * una capa encima: llega al volver a la pantalla de inicio, cuando el usuario ya cerro
 * lo que estaba haciendo. Interrumpir *dentro* de otra aplicacion seria hacer
 * exactamente lo que Zen critica.
 *
 * **No bloquea nada.** Se sale con "Seguir como estaba", con el gesto de atras y
 * arrastrando desde el borde: tres salidas, y ninguna castiga. Un aviso que no deja
 * pasar se aprende a odiar, y lo unico que consigue es que se desinstale el launcher.
 *
 * Las cifras van primero y el texto despues, y no al reves: lo que hace que alguien se
 * pare es reconocer el dato, no leer un sermon. Por eso tampoco hay ejercicio de
 * castigo: las dos salidas —respirar un minuto, empezar una sesion— son las dos cosas
 * que Zen ya sabe hacer, no una penitencia inventada para este momento.
 */
@Composable
fun DistractionScreen(
    state: DistractionUiState,
    onBreathe: () -> Unit,
    onStartSession: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val compulsion = state.compulsion

    BackHandler(enabled = true) { onDismiss() }

    ZenScreen(modifier = modifier, onSwipeBack = onDismiss) {
        ZenHeaderStrip(
            left = stringResource(R.string.distraction_title),
            right = stringResource(distractionKindLabel(compulsion.kind)),
            leftAccent = true,
        )

        Spacer(Modifier.height(ZenSpacing.XLarge))

        Text(
            // En picoteo no sobra una aplicacion, sobra el salto: no hay nombre que
            // poner y poner el de la ultima seria senalar a la equivocada.
            text = state.appLabel ?: stringResource(R.string.distraction_scattered),
            style = ZenTextStyles.Title,
            color = ZenColors.Foreground,
        )

        Spacer(Modifier.height(ZenSpacing.Medium))

        Row(horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Small)) {
            // En un arrastre la apertura es una sola: escribir "1 APERTURA" al lado de
            // "48m" no anade un dato, anade ruido.
            if (compulsion.kind != CompulsionKind.ARRASTRE) {
                DataTag(
                    text = pluralStringResource(
                        R.plurals.usage_openings,
                        compulsion.openings,
                        compulsion.openings,
                    ),
                )
            }
            DataTag(text = formatDurationCompact(compulsion.foregroundMillis))
            // La ventana solo dice algo cuando hay una cuenta que encuadrar —"7
            // aperturas EN 30 MIN"—. En un arrastre la duracion **es** el hecho, y
            // "48m EN 60 MIN" se lee como dos datos que se contradicen.
            if (compulsion.kind != CompulsionKind.ARRASTRE) {
                DataTag(
                    text = stringResource(R.string.distraction_window, compulsion.windowMinutes),
                )
            }
        }

        Spacer(Modifier.height(ZenSpacing.Large))

        Text(
            text = stringResource(distractionBody(compulsion.kind)),
            style = ZenTextStyles.Body,
            color = ZenColors.Secondary,
        )

        Spacer(Modifier.weight(1f).fillMaxWidth())

        Column(verticalArrangement = Arrangement.spacedBy(ZenSpacing.Small)) {
            ZenTagButton(
                text = stringResource(R.string.distraction_breathe),
                onClick = onBreathe,
                onClickLabel = stringResource(R.string.distraction_breathe_label),
                modifier = Modifier.fillMaxWidth(),
                stretch = true,
            )
            ZenTagButton(
                text = stringResource(R.string.distraction_session),
                onClick = onStartSession,
                onClickLabel = stringResource(R.string.distraction_session_label),
                modifier = Modifier.fillMaxWidth(),
                stretch = true,
            )
        }

        Spacer(Modifier.height(ZenSpacing.Medium))
        ZenHairline()
        ZenListRow(
            label = stringResource(R.string.distraction_dismiss),
            labelColor = ZenColors.Muted,
            onClick = onDismiss,
        )
    }
}

/** La cifra dentro de un marco de 1px: el mismo marco de los botones, sin el toque. */
@Composable
private fun DataTag(text: String) {
    Box(
        modifier = Modifier
            .border(ZenSpacing.Hairline, ZenColors.Border)
            .padding(horizontal = ZenSpacing.Small, vertical = ZenSpacing.XSmall),
    ) {
        MonoLabel(text = text, color = ZenColors.Tertiary)
    }
}
