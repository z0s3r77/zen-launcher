package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.scanner.Stillness
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Si el movil esta quieto, a partir del acelerometro. */
class StillnessTest {

    @Test
    fun `la primera muestra solo siembra y nunca declara quietud`() {
        // Regresion de la que protege `seeded`: comparando la primera muestra contra un
        // cero saldria un salto enorme, y comparandola consigo misma saldria quietud
        // instantanea nada mas abrir la camara.
        val primera = Stillness.next(Stillness.Reading(), 0f, 9.81f, 0f)

        assertTrue(primera.seeded)
        assertEquals(0, primera.calm)
        assertFalse(primera.still)
    }

    @Test
    fun `un movil apoyado acaba declarandose quieto`() {
        var lectura = Stillness.Reading()
        repeat(Stillness.REQUIRED_SAMPLES + 1) {
            lectura = Stillness.next(lectura, 0.01f, 9.80f, 0.02f)
        }
        assertTrue(lectura.still)
    }

    @Test
    fun `una racha corta no basta`() {
        var lectura = Stillness.next(Stillness.Reading(), 0f, 9.81f, 0f)
        repeat(Stillness.REQUIRED_SAMPLES - 1) {
            lectura = Stillness.next(lectura, 0f, 9.81f, 0f)
        }
        assertFalse(lectura.still)
    }

    @Test
    fun `un movil quieto pero inclinado tambien cuenta como quieto`() {
        // Es la razon de medir el cambio y no la distancia a la gravedad: un movil
        // inclinado sobre un atril mide muy lejos de 9,81 en cada eje y esta clavado.
        var lectura = Stillness.Reading()
        repeat(Stillness.REQUIRED_SAMPLES + 1) {
            lectura = Stillness.next(lectura, 6.9f, 6.9f, 0.1f)
        }
        assertTrue(lectura.still)
    }

    @Test
    fun `un movimiento a velocidad constante no se cuela como quietud`() {
        // Mide exactamente la gravedad mientras se desplaza; lo que lo delata es el
        // arranque y la parada, o sea la variacion.
        var lectura = Stillness.Reading()
        repeat(Stillness.REQUIRED_SAMPLES + 1) { paso ->
            val sacudida = if (paso % 2 == 0) 1.5f else -1.5f
            lectura = Stillness.next(lectura, sacudida, 9.81f, 0f)
        }
        assertFalse(lectura.still)
    }

    @Test
    fun `un giro sobre un solo eje rompe la quietud`() {
        // Se mira el mayor de los tres ejes y no la suma: girar la muneca apenas mueve la
        // suma y saca la hoja del encuadre igual.
        var lectura = Stillness.Reading()
        repeat(Stillness.REQUIRED_SAMPLES + 1) {
            lectura = Stillness.next(lectura, 0f, 9.81f, 0f)
        }
        assertTrue(lectura.still)

        val girado = Stillness.next(lectura, 0f, 9.81f, 3f)
        assertFalse(girado.still)
        assertEquals(0, girado.calm)
    }
}
