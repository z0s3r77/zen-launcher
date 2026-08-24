package com.zenlauncher.zen.presentation.breathe

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.breathing.BreathPhase
import com.zenlauncher.zen.domain.breathing.BreathingPattern
import com.zenlauncher.zen.domain.breathing.BreathingStep
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
 * Un minuto de respiracion guiada: cuatro segundos dentro, seis fuera, seis veces.
 *
 * El *por que* de esos numeros esta en [BreathingPattern] —resonancia a 0,1 Hz y
 * espiracion mas larga que la inspiracion—; aqui solo esta el como se ve.
 *
 * Es la unica pantalla de Zen donde algo se mueve sin que el dedo lo empuje, y no rompe
 * la regla: **el movimiento es el contenido**. Seguir una curva que sube y baja es lo
 * que permite respirar sin contar, y solo corre mientras el ejercicio corre; parado no
 * dibuja ni un fotograma. Lo que si se evita es todo lo demas: ni felicitacion al
 * terminar, ni racha, ni "llevas 3 dias seguidos". Se respira y se sale.
 *
 * ## El tiempo no sale del sistema de animacion
 *
 * El minuto se cuenta con el reloj de fotogramas ([withFrameMillis]), no con un
 * `Animatable` de 60 000 ms. Compose escala las animaciones con
 * `animator_duration_scale`, asi que a quien tenga las animaciones apagadas en opciones
 * de desarrollador —o desde accesibilidad— el ejercicio entero le duraria cero. Un
 * cronometro que depende de un ajuste de desarrollador no es un cronometro. Ver la
 * excepcion documentada en `ZenMotion`.
 */
@Composable
fun BreatheScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current

    var running by remember { mutableStateOf(false) }
    // `mutableLongState` y no un `State<BreathingStep>`: avanza en cada fotograma y solo
    // lo lee la curva, en fase de dibujo. Ver [BreathingWave].
    val elapsed = remember { mutableLongStateOf(0L) }
    // Lo que se lee en letras cambia como mucho una vez por segundo, asi que la pantalla
    // se recompone una vez por segundo aunque la curva vaya a sesenta.
    val step by remember { derivedStateOf { BreathingPattern.stepAt(elapsed.longValue) } }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect

        val start = withFrameMillis { it }
        var lastPhase: BreathPhase? = null

        while (true) {
            val frame = withFrameMillis { it }
            val now = (frame - start).coerceAtMost(BreathingPattern.TOTAL_MILLIS)
            elapsed.longValue = now

            // Un toque seco en cada cambio de fase, para poder cerrar los ojos: es la
            // unica forma de guiar sin mirar la pantalla. No usa `VIBRATE` —seria un
            // permiso nuevo— sino la respuesta tactil del sistema, que respeta el ajuste
            // de Android: quien la tiene apagada no siente nada y el ejercicio funciona
            // igual.
            val phase = BreathingPattern.stepAt(now).phase
            if (phase != lastPhase) {
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                lastPhase = phase
            }

            if (now >= BreathingPattern.TOTAL_MILLIS) break
        }

        running = false
    }

    // Sesenta segundos sin tocar la pantalla superan el tiempo de espera de muchos
    // dispositivos: sin esto, el telefono se apaga a mitad del ejercicio. Solo mientras
    // corre, y se suelta al salir de la pantalla.
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    // Volver arrastrando desde un borde, al primer intento: con las barras ocultas el
    // gesto de Android necesita dos. Ver `EdgeBackPolicy`.
    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.breathe_title),
            right = if (running) {
                stringResource(R.string.breathe_header_cycle, step.cycle, BreathingPattern.CYCLES)
            } else {
                stringResource(R.string.breathe_header_rate, BreathingPattern.BREATHS_PER_MINUTE)
            },
            leftAccent = running,
            onBack = onBack,
        )

        Spacer(Modifier.height(ZenSpacing.XLarge))

        PhaseHeadline(step = step, running = running)

        Spacer(Modifier.height(ZenSpacing.Large))

        BreathingWave(elapsedMillis = { elapsed.longValue })

        Spacer(Modifier.height(ZenSpacing.Large))

        ZenHairline()
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 15.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            MonoLabel(text = stringResource(R.string.breathe_remaining))
            MonoData(text = stringResource(R.string.breathe_seconds, step.remainingSeconds))
        }
        ZenHairline()

        // Todo el aire sobrante va aqui: lo que se sigue con los ojos queda arriba, y el
        // motivo baja hasta el boton en lugar de dejar un hueco en mitad de la pantalla.
        Spacer(Modifier.weight(1f).fillMaxWidth())

        // El motivo, una vez y en pequeño. Es lo que separa "una animacion bonita" de
        // "esto hace algo": quien lo lea una vez ya no necesita volver a leerlo, y por
        // eso vive al pie, no encima de la curva.
        MonoLabel(
            text = stringResource(R.string.breathe_notice_rhythm),
            color = ZenColors.Muted,
            maxLines = 6,
        )
        Spacer(Modifier.height(ZenSpacing.Small))
        MonoLabel(
            text = stringResource(R.string.breathe_notice_care),
            color = ZenColors.Dim,
            maxLines = 4,
        )
        Spacer(Modifier.height(ZenSpacing.Large))

        ZenHairline()
        ZenListRow(
            label = stringResource(controlLabel(running = running, finished = step.finished)),
            labelColor = if (running) ZenColors.Dim else ZenColors.Foreground,
            onClick = {
                if (running) {
                    running = false
                    // Se vuelve al principio en vez de dejar la curva a medias: una
                    // figura congelada a mitad se lee como un ejercicio pendiente.
                    elapsed.longValue = 0L
                } else {
                    elapsed.longValue = 0L
                    running = true
                }
            },
        )
        ZenHairline()
    }
}

/**
 * Que dice el boton segun el estado. Fuera del composable para poder fijarlo en un test
 * sin manejar el reloj de fotogramas.
 */
internal fun controlLabel(running: Boolean, finished: Boolean): Int = when {
    running -> R.string.breathe_stop
    finished -> R.string.breathe_again
    else -> R.string.breathe_start
}

/**
 * La orden, en grande, y los segundos que le quedan al lado.
 *
 * Parado no hay cifra: un "00" permanente junto a "PREPARADO" seria un dato con nada
 * detras. El alto lo fija la palabra, asi que la cifra puede faltar sin que la pantalla
 * se mueva al empezar.
 */
@Composable
private fun PhaseHeadline(step: BreathingStep, running: Boolean) {
    val phrase = when {
        !running && step.finished -> R.string.breathe_done
        !running -> R.string.breathe_ready
        step.phase == BreathPhase.INHALE -> R.string.breathe_inhale
        else -> R.string.breathe_exhale
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(phrase),
            style = ZenTextStyles.Title,
            color = ZenColors.Foreground,
        )
        if (running) {
            Text(
                text = "%02d".format(step.phaseRemainingSeconds),
                style = ZenTextStyles.Figure,
                // Gris y no blanco: la cifra acompaña, la orden manda.
                color = ZenColors.Tertiary,
            )
        }
    }
}
