package com.zenlauncher.zen.presentation.usage

import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.model.formatDurationCompact
import com.zenlauncher.zen.domain.usage.UsageFace
import com.zenlauncher.zen.domain.usage.UsageMood
import com.zenlauncher.zen.domain.usage.UsageReading
import com.zenlauncher.zen.presentation.components.MonoData
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.StatusMark
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing

/** Que dice la cara, en palabras. Ver [ZenHeaderStrip] y [UsageFace]. */
@StringRes
internal fun usageFaceDescription(face: UsageFace): Int = when (face) {
    UsageFace.BIEN -> R.string.usage_face_bien
    UsageFace.REGULAR -> R.string.usage_face_regular
    UsageFace.MAL -> R.string.usage_face_mal
    UsageFace.ALARMA -> R.string.usage_face_alarma
    UsageFace.DESCONOCIDO -> R.string.usage_face_desconocido
}

/**
 * La misma cara, en una palabra.
 *
 * **Todo resumen de Zen sale de aqui**, y no del escalon por tiempo: en la home el
 * glifo, donde solo caben dos caracteres, y la palabra en el resto. Asi el `:(` de la
 * franja y el rotulo de la pantalla de Uso no pueden contradecirse, que es justo lo que
 * pasaba cuando cada uno se calculaba por su cuenta: la cara triste porque una
 * aplicacion acaparaba, y debajo un "NORMAL" que salia solo del reloj.
 */
@StringRes
internal fun usageFaceLabel(face: UsageFace): Int = when (face) {
    UsageFace.BIEN -> R.string.usage_face_label_bien
    UsageFace.REGULAR -> R.string.usage_face_label_regular
    UsageFace.MAL -> R.string.usage_face_label_mal
    UsageFace.ALARMA -> R.string.usage_face_label_alarma
    UsageFace.DESCONOCIDO -> R.string.usage_face_label_desconocido
}

/**
 * El pulso del dia en la pantalla de inicio.
 *
 * **No es una fila permanente mas.** Vive bajo la misma regla que el mando del
 * reproductor —lo que no tiene nada detras no se pinta— y por eso solo aparece cuando
 * hay algo que decir: con el dia en calma o en normal, la home queda exactamente como
 * estaba. Un contador de uso siempre visible seria un marcador, y un marcador se juega;
 * ademas repetiria cincuenta veces al dia un dato que solo importa cuando se sale de
 * madre.
 *
 * Una sola linea, y las dos cifras que decidieron el escalon: el tiempo y las veces que
 * se ha cogido el telefono. La marca ambar nunca va sola —al lado se lee USO ALTO o
 * EXCESO—, para no depender del color.
 */
@Composable
fun UsagePulse(
    reading: UsageReading,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.usage_pulse_label),
                onClick = onOpen,
            )
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Medium),
    ) {
        StatusMark(active = true)
        MonoLabel(
            text = stringResource(usageFaceLabel(UsageMood.face(reading))),
            color = ZenColors.Tertiary,
        )
        Spacer(Modifier.weight(1f))
        MonoData(
            text = stringResource(
                R.string.usage_pulse_value,
                formatDurationCompact(reading.screenMillis),
                reading.unlocks,
            ),
            color = ZenColors.Foreground,
        )
    }
}
