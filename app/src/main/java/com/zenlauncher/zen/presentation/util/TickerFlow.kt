package com.zenlauncher.zen.presentation.util

import com.zenlauncher.zen.core.ZenClock
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emite alineado al limite del periodo, no cada N ms desde que arranca.
 *
 * Importa para la bateria y para la exactitud: un reloj que actualiza "cada minuto"
 * desde un instante arbitrario ensena el minuto equivocado hasta medio minuto. Al
 * alinearlo, ademas, solo hay un despertar por unidad mostrada.
 *
 * Se recoge con `collectAsStateWithLifecycle`, asi que deja de emitir en cuanto la
 * pantalla se va a segundo plano.
 */
fun tickerFlow(periodMillis: Long, clock: ZenClock): Flow<Long> = flow {
    require(periodMillis > 0) { "El periodo debe ser positivo" }
    while (true) {
        val now = clock.wallTimeMillis()
        emit(now)
        delay(periodMillis - (now % periodMillis))
    }
}

const val ONE_SECOND_MILLIS = 1_000L
const val ONE_MINUTE_MILLIS = 60_000L
