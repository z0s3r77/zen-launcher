package com.zenlauncher.zen.domain.usage

/**
 * Cuando se puede parar al usuario y cuando hay que callarse.
 *
 * Es la parte mas delicada de todo esto. Una aplicacion que existe para dejar de
 * reclamar atencion no puede convertirse ella misma en la interrupcion: un aviso que
 * salta cada vez que vuelves a la pantalla de inicio se aprende a descartar en dos dias
 * y a partir de ahi no dice nada. Por eso todo lo que hay aqui son razones para **no**
 * avisar.
 */
object DistractionPolicy {

    /**
     * Minutos de silencio tras avisar una vez.
     *
     * Hora y media es el orden de magnitud de "otro rato del dia", no de "otra vez lo
     * mismo": el patron que disparo el aviso sigue ahi durante un buen rato despues de
     * ensenarlo —las aperturas ya ocurrieron y no se borran—, asi que sin espera el
     * aviso reaparecia cada vez que se volvia a la home.
     */
    const val COOLDOWN_MINUTES = 90

    /**
     * @param sessionActive durante una sesion Zen no se interrumpe nunca. Quien esta en
     *   una sesion ya tomo la decision que este aviso pretende provocar.
     * @param lastShownAtMillis cuando se enseno por ultima vez, o null si nunca.
     */
    fun shouldInterrupt(
        compulsion: Compulsion?,
        lastShownAtMillis: Long?,
        nowMillis: Long,
        sessionActive: Boolean,
    ): Boolean {
        if (compulsion == null) return false
        if (sessionActive) return false
        if (lastShownAtMillis == null) return true

        val since = nowMillis - lastShownAtMillis
        // Un reloj que salta hacia atras —cambio de hora, viaje, ajuste de red— dejaria
        // `since` negativo y con `since >= espera` bloquearia el aviso hasta que el
        // reloj alcanzase la marca vieja. Se trata como "hace mucho": la marca guardada
        // ya no significa nada.
        if (since < 0) return true

        return since >= COOLDOWN_MINUTES * 60_000L
    }
}
