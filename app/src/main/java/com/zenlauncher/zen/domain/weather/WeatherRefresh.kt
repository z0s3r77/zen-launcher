package com.zenlauncher.zen.domain.weather

/**
 * Cuando volver a preguntar y hasta cuando fiarse de lo traido. Funciones puras.
 *
 * **Zen no sondea.** Igual que el uso del movil, el tiempo se relee al volver a la
 * pantalla de inicio: el momento en que alguien puede leer el dato y el momento en que
 * se pide son el mismo. Un latido periodico gastaria bateria y red para refrescar un
 * numero que nadie esta mirando.
 *
 * Vuelta a la home no significa peticion. Se entra y se sale de la pantalla de inicio
 * decenas de veces al dia y el tiempo no cambia entre dos de ellas, asi que entre
 * peticion y peticion hay media hora de silencio.
 */
object WeatherRefresh {

    /** Lo que se espera entre dos peticiones, se vuelva a la home las veces que sea. */
    const val INTERVAL_MILLIS: Long = 30 * 60_000L

    /**
     * A partir de aqui el dato deja de ensenarse.
     *
     * Sin este limite, un telefono que pasa el fin de semana sin red seguiria ensenando
     * los grados del viernes con la misma cara que los de ahora. Seis horas es el punto
     * en que la temperatura ya no se parece: cubre una noche sin cobertura, no cubre un
     * dia entero.
     */
    const val STALE_MILLIS: Long = 6 * 60 * 60_000L

    /**
     * @param lastAttemptAtMillis cuando se intento por ultima vez, con red o sin ella, o
     *   null si aun no se intento nunca. Es el **intento** y no el acierto: contando
     *   solo los aciertos, un telefono sin cobertura pediria en cada vuelta a la home.
     */
    fun shouldRefresh(lastAttemptAtMillis: Long?, nowMillis: Long): Boolean {
        if (lastAttemptAtMillis == null) return true
        // El reloj de pared puede ir hacia atras (cambio de hora, ajuste por red). Sin
        // esto, un salto atras dejaria el tiempo congelado hasta alcanzar la marca vieja.
        if (nowMillis < lastAttemptAtMillis) return true
        return nowMillis - lastAttemptAtMillis >= INTERVAL_MILLIS
    }

    fun isStale(reading: WeatherReading, nowMillis: Long): Boolean =
        nowMillis - reading.observedAtMillis >= STALE_MILLIS
}
