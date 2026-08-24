package com.zenlauncher.zen.domain.breathing

import kotlin.math.PI
import kotlin.math.cos

/** Las dos mitades de una respiracion. No hay apnea: ver [BreathingPattern]. */
enum class BreathPhase { INHALE, EXHALE }

/**
 * Lo que la pantalla necesita saber en un instante dado, **sin la amplitud**.
 *
 * La amplitud se deja fuera a proposito: cambia en cada fotograma, y meterla aqui
 * obligaria a recomponer la pantalla sesenta veces por segundo para actualizar dos
 * palabras que solo cambian una vez por segundo. La curva la lee aparte, en la fase de
 * dibujo, con [BreathingPattern.amplitudeAt].
 */
data class BreathingStep(
    val phase: BreathPhase,
    /** Respiracion en curso, de 1 a [BreathingPattern.CYCLES]. */
    val cycle: Int,
    /** Segundos que quedan de la fase actual, redondeados hacia arriba. */
    val phaseRemainingSeconds: Int,
    /** Segundos que quedan del minuto entero. */
    val remainingSeconds: Int,
    val finished: Boolean,
)

/**
 * El patron guiado: **cuatro segundos dentro, seis fuera, seis veces**.
 *
 * Es una funcion pura del tiempo transcurrido, no una maquina de estados con un
 * temporizador dentro. La pantalla lleva la cuenta del reloj y pregunta aqui; asi el
 * patron se puede probar entero sin Android, sin esperar un minuto y sin fotogramas.
 *
 * ## Por que estos numeros
 *
 * - **Seis respiraciones por minuto (0,1 Hz)** es la llamada *frecuencia de resonancia*
 *   del sistema cardiovascular: a ese ritmo la respiracion entra en fase con la onda de
 *   presion arterial y la variabilidad de la frecuencia cardiaca alcanza su maximo, con
 *   la ganancia barorrefleja mas alta que se mide a cualquier otro ritmo (Vaschillo et
 *   al. 2002; Lehrer y Gevirtz, *Front. Psychol.* 2014). No es un ritmo "bonito": es el
 *   que mas mueve la fisiologia por respiracion, y por eso es el que usa el
 *   biofeedback de HRV.
 * - **La espiracion mas larga que la inspiracion (6:4)** porque el freno vagal sobre el
 *   corazon actua al soltar el aire: el pulso baja durante la espiracion y sube durante
 *   la inspiracion. Alargar la espiracion es lo que inclina el equilibrio hacia el lado
 *   parasimpatico —el de "descansar"— en lugar de limitarse a respirar despacio
 *   (revision de Zaccaro et al., *Front. Hum. Neurosci.* 2018; Laborde et al. 2022).
 * - **Sin apneas.** Los patrones con retencion (el 4-4-4-4 "en caja") bajan el ritmo a
 *   costa de aguantar el aire, y a quien llega agitado o con ansiedad la retencion le
 *   sube la sensacion de ahogo. El cociente entre inspirar y espirar es lo que hace el
 *   trabajo; la apnea no hace falta.
 * - **Un minuto** porque es la dosis que de verdad se toma. El efecto sobre la
 *   variabilidad cardiaca aparece dentro de las primeras respiraciones lentas, no al
 *   cabo de veinte minutos, y los ensayos que comparan practicas breves diarias
 *   (Balban et al., *Cell Reports Medicine* 2023) encuentran mejoras de animo con cinco
 *   minutos al dia. Seis ciclos entran justos en 60 000 ms: el minuto no es un corte
 *   arbitrario a mitad de una respiracion.
 *
 * Esto **no es un tratamiento** y la pantalla no mide nada: no hay sensor de pulso en
 * juego, asi que Zen guia el ritmo y no promete un resultado.
 */
object BreathingPattern {

    const val INHALE_MILLIS = 4_000L
    const val EXHALE_MILLIS = 6_000L
    const val CYCLE_MILLIS = INHALE_MILLIS + EXHALE_MILLIS

    /** Seis ciclos de diez segundos: el minuto cierra al final de una espiracion. */
    const val CYCLES = 6
    const val TOTAL_MILLIS = CYCLE_MILLIS * CYCLES

    /** Solo para rotular: 60 000 / [CYCLE_MILLIS]. */
    const val BREATHS_PER_MINUTE = 6

    private const val MILLIS_PER_SECOND = 1_000L

    /**
     * Altura de la curva en `elapsedMillis`, de 0 (pulmones vacios) a 1 (llenos).
     *
     * Medio coseno en cada fase en lugar de una rampa recta: la rampa obliga a arrancar
     * y a frenar el aire de golpe en cada extremo, y lo que se sigue con el cuerpo es
     * una curva que entra y sale suave. Ademas deja la union entre inspirar y espirar
     * sin pico: la pendiente llega a cero justo en el cambio.
     */
    fun amplitudeAt(elapsedMillis: Long): Float {
        val clamped = elapsedMillis.coerceIn(0L, TOTAL_MILLIS)
        val inCycle = clamped % CYCLE_MILLIS
        return if (inCycle < INHALE_MILLIS) {
            val t = inCycle.toDouble() / INHALE_MILLIS
            ((1.0 - cos(PI * t)) / 2.0).toFloat()
        } else {
            val t = (inCycle - INHALE_MILLIS).toDouble() / EXHALE_MILLIS
            ((1.0 + cos(PI * t)) / 2.0).toFloat()
        }
    }

    /** Lo que se lee en pantalla en `elapsedMillis`. */
    fun stepAt(elapsedMillis: Long): BreathingStep {
        val clamped = elapsedMillis.coerceIn(0L, TOTAL_MILLIS)
        val finished = clamped >= TOTAL_MILLIS
        val inCycle = clamped % CYCLE_MILLIS

        val phase = if (inCycle < INHALE_MILLIS) BreathPhase.INHALE else BreathPhase.EXHALE
        val phaseRemaining = if (phase == BreathPhase.INHALE) {
            INHALE_MILLIS - inCycle
        } else {
            CYCLE_MILLIS - inCycle
        }

        return BreathingStep(
            phase = phase,
            // Al terminar, `inCycle` vuelve a 0 y el ciclo calculado seria el septimo:
            // se acota para que la ultima lectura siga siendo "6 de 6".
            cycle = (clamped / CYCLE_MILLIS).toInt().coerceIn(0, CYCLES - 1) + 1,
            // Hacia arriba: durante el primer milisegundo de una inspiracion de cuatro
            // segundos se lee "4", no "3". Un contador que empieza por debajo del total
            // hace dudar de si se ha perdido un segundo.
            phaseRemainingSeconds = ceilSeconds(if (finished) 0L else phaseRemaining),
            remainingSeconds = ceilSeconds(TOTAL_MILLIS - clamped),
            finished = finished,
        )
    }

    private fun ceilSeconds(millis: Long): Int =
        ((millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND).toInt()
}
