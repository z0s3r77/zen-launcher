package com.zenlauncher.zen.presentation.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.zenlauncher.zen.presentation.components.ZenListRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.model.ActiveSession
import com.zenlauncher.zen.domain.model.formatDurationClock
import com.zenlauncher.zen.presentation.components.MonoData
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles
import com.zenlauncher.zen.presentation.util.ZenDateFormats

/**
 * Pantalla de sesion activa.
 *
 * Esta pantalla esta disenada para ser **aburrida**: sin barra de progreso, sin
 * animaciones, sin frases motivacionales y sin nada que cambie salvo dos cifras. El
 * cronometro va en gris (#D5D5D2), no en blanco, para que no atraiga la mirada.
 */
private const val MILLIS_PER_MINUTE = 60_000L

@Composable
fun ActiveSessionScreen(
    state: SessionUiState,
    session: ActiveSession,
    confirming: Boolean,
    pinned: Boolean = false,
    onRequestFinish: () -> Unit,
    onCancelFinish: () -> Unit,
    onConfirmFinish: () -> Unit,
    onTimerReachedZero: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = state.progress

    LaunchedEffect(progress?.isExpired) {
        if (progress?.isExpired == true) onTimerReachedZero()
    }

    ZenScreen(modifier = modifier) {
        // La hora de pared no aparece aqui a proposito: los indicadores en vivo viven
        // solo en la pantalla de inicio, y en una sesion lo unico que importa es cuanto
        // queda. En su sitio va lo que se planifico, que no cambia.
        ZenHeaderStrip(
            left = stringResource(R.string.session_header_active),
            right = stringResource(
                R.string.session_header_planned,
                session.plannedDurationMillis / MILLIS_PER_MINUTE,
            ),
            leftAccent = true,
        )

        Spacer(Modifier.height(ZenSpacing.XXLarge))

        MonoLabel(text = stringResource(R.string.session_remaining))
        Spacer(Modifier.height(ZenSpacing.Medium))
        Text(
            text = formatDurationClock(progress?.remainingMillis ?: 0L),
            style = ZenTextStyles.Timer,
            color = ZenColors.Tertiary,
        )

        Spacer(Modifier.height(ZenSpacing.XXLarge))

        Column {
            ZenHairline()
            DataRow(
                label = stringResource(R.string.session_elapsed),
                value = formatDurationClock(progress?.elapsedMillis ?: 0L),
            )
            ZenHairline()
            DataRow(
                label = stringResource(R.string.session_battery),
                value = if (state.battery.isKnown) "${state.battery.percent}%" else "—",
            )
            ZenHairline()
            DataRow(
                label = stringResource(R.string.session_started),
                value = ZenDateFormats.time(session.startedAtWallMillis),
            )
            ZenHairline()
            DataRow(
                label = stringResource(R.string.session_blocked),
                value = "%02d".format(session.restrictedAppsCount),
            )
            ZenHairline()
        }

        if (pinned) {
            Spacer(Modifier.height(ZenSpacing.Medium))
            MonoLabel(text = stringResource(R.string.session_pinned))
            Spacer(Modifier.height(ZenSpacing.XSmall))
            // Se dice como salir: ocultar la salida que Android garantiza de todas
            // formas solo consigue que el usuario se sienta atrapado.
            MonoLabel(
                text = stringResource(R.string.session_pinned_exit),
                color = ZenColors.Dim,
                maxLines = 3,
            )
        }

        if (progress?.clockAnomaly == true) {
            Spacer(Modifier.height(ZenSpacing.Medium))
            MonoLabel(
                text = stringResource(R.string.session_clock_anomaly),
                color = ZenColors.Muted,
            )
        }

        Spacer(Modifier.height(ZenSpacing.XXLarge))

        ZenHairline()
        ZenListRow(
            label = stringResource(R.string.session_finish),
            labelColor = ZenColors.Dim,
            onClick = onRequestFinish,
        )
        ZenHairline()
    }

    if (confirming) {
        FinishConfirmationDialog(
            elapsed = formatDurationClock(progress?.elapsedMillis ?: 0L),
            onDismiss = onCancelFinish,
            onConfirm = onConfirmFinish,
        )
    }
}

@Composable
private fun DataRow(label: String, value: String) {
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

/**
 * Confirmacion antes de abandonar. Es la friccion deliberada del diseno: el momento de
 * maxima tentacion no deberia resolverse con un toque distraido.
 */
@Composable
private fun FinishConfirmationDialog(
    elapsed: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ZenColors.Background,
        titleContentColor = ZenColors.Foreground,
        textContentColor = ZenColors.Secondary,
        title = {
            Text(
                text = stringResource(R.string.session_finish_confirm_title),
                style = ZenTextStyles.ListItem,
            )
        },
        text = {
            Text(
                text = stringResource(R.string.session_finish_confirm_body, elapsed),
                style = ZenTextStyles.Body,
            )
        },
        // El peso visual va en SEGUIR, no en TERMINAR: la opcion que preserva la
        // sesion debe ser la facil, y abandonar debe costar un gesto deliberado.
        confirmButton = {
            TextButton(onClick = onDismiss) {
                MonoLabel(
                    text = stringResource(R.string.session_finish_confirm_no),
                    color = ZenColors.Foreground,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onConfirm) {
                MonoLabel(
                    text = stringResource(R.string.session_finish_confirm_yes),
                    color = ZenColors.Dim,
                )
            }
        },
    )
}
