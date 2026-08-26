package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.weather.WeatherCondition
import com.zenlauncher.zen.domain.weather.WeatherReading
import com.zenlauncher.zen.domain.weather.WeatherRefresh
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherRefreshTest {

    private val ahora = 1_700_000_000_000L

    @Test
    fun `sin ningun intento previo se pide`() {
        assertTrue(WeatherRefresh.shouldRefresh(lastAttemptAtMillis = null, nowMillis = ahora))
    }

    /**
     * A la pantalla de inicio se vuelve decenas de veces al dia y el tiempo no cambia
     * entre dos de ellas. Sin esta espera, cada vuelta seria una peticion a la red.
     */
    @Test
    fun `volver a la home antes de la media hora no pide nada`() {
        val hace10min = ahora - 10 * 60_000L

        assertFalse(WeatherRefresh.shouldRefresh(hace10min, ahora))
    }

    @Test
    fun `pasada la media hora se vuelve a pedir`() {
        val justo = ahora - WeatherRefresh.INTERVAL_MILLIS

        assertTrue(WeatherRefresh.shouldRefresh(justo, ahora))
    }

    /**
     * Regresion: el reloj de pared puede ir hacia atras —cambio de hora, ajuste por
     * red—. Comparando solo la resta, un salto atras dejaba el tiempo congelado hasta
     * que el reloj alcanzase otra vez la marca vieja, que puede ser una hora despues.
     */
    @Test
    fun `si el reloj salta hacia atras se pide en lugar de congelarse`() {
        val enElFuturo = ahora + 60 * 60_000L

        assertTrue(WeatherRefresh.shouldRefresh(enElFuturo, ahora))
    }

    @Test
    fun `un dato recien traido no esta viejo`() {
        val lectura = WeatherReading(18, WeatherCondition.DESPEJADO, ahora - 60_000L)

        assertFalse(WeatherRefresh.isStale(lectura, ahora))
    }

    /**
     * Un telefono sin cobertura seguiria ensenando los grados de anoche con la misma
     * cara que los de ahora. A partir de aqui se deja de ensenar.
     */
    @Test
    fun `pasadas las seis horas el dato deja de ensenarse`() {
        val lectura = WeatherReading(18, WeatherCondition.DESPEJADO, ahora - WeatherRefresh.STALE_MILLIS)

        assertTrue(WeatherRefresh.isStale(lectura, ahora))
    }
}
