package com.zenlauncher.zen.domain.scanner

import kotlin.math.abs
import kotlin.math.max

/**
 * Si el movil esta quieto, a partir del acelerometro.
 *
 * Puro y funcional —el estado entra y sale por la puerta— para poder probar la regla con
 * numeros escritos a mano en lugar de agitando un telefono.
 *
 * ### Se mide el cambio, no la fuerza
 *
 * La tentacion es comparar la magnitud del vector con la gravedad y llamar quieto a lo
 * que se acerque a 9,81. No sirve: un movil que se desplaza a velocidad constante mide
 * exactamente 9,81, y uno inclinado tambien. Lo que delata a la mano es la **variacion**
 * entre muestras seguidas, que es lo que se mide aqui.
 *
 * ### Y por que hay que sostenerlo
 *
 * Una sola muestra tranquila no significa nada: el temblor de la mano pasa por su punto
 * de inversion decenas de veces por segundo, y ahi la variacion es cero. Por eso hace
 * falta una racha de muestras seguidas por debajo del limite.
 */
object Stillness {

    /**
     * Variacion maxima entre dos muestras, en m/s2, para contar como quieto.
     *
     * Medio metro por segundo al cuadrado deja pasar el pulso de una mano apoyada y
     * descarta el movimiento de encuadrar. Es tolerante a proposito: la condicion de
     * quietud de verdad la ponen las esquinas, que no se mueven si la hoja no se mueve
     * (ver [CaptureDecision]); esto solo descarta el caso contrario, en el que las
     * esquinas se quedan clavadas en un borde equivocado mientras la mano viaja.
     */
    const val MAX_DELTA = 0.5f

    /** Muestras seguidas por debajo del limite. A ritmo de interfaz son un tercio de segundo. */
    const val REQUIRED_SAMPLES = 6

    /**
     * @param calm cuantas muestras seguidas llevan por debajo del limite.
     * @param seeded si ya hay una muestra anterior con la que comparar. Sin esto, la
     *   primera muestra se compararia contra un cero y daria un salto enorme, o peor,
     *   contra si misma y daria quietud instantanea nada mas abrir la camara.
     */
    data class Reading(
        val x: Float = 0f,
        val y: Float = 0f,
        val z: Float = 0f,
        val calm: Int = 0,
        val seeded: Boolean = false,
    ) {
        val still: Boolean get() = calm >= REQUIRED_SAMPLES
    }

    fun next(previous: Reading, x: Float, y: Float, z: Float): Reading {
        if (!previous.seeded) {
            return Reading(x = x, y = y, z = z, calm = 0, seeded = true)
        }

        // La mayor variacion de los tres ejes, no la suma: un giro sobre un solo eje
        // —girar la muneca— apenas mueve la suma y saca la hoja del encuadre igual.
        val delta = max(
            abs(x - previous.x),
            max(abs(y - previous.y), abs(z - previous.z)),
        )

        return Reading(
            x = x,
            y = y,
            z = z,
            // A cero al perderla, no un escalon menos: media racha guardada de un momento
            // que ya paso adelantaria el disparo en el siguiente.
            calm = if (delta <= MAX_DELTA) previous.calm + 1 else 0,
            seeded = true,
        )
    }
}
