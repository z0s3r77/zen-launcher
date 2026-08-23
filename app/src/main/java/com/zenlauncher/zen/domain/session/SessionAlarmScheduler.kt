package com.zenlauncher.zen.domain.session

import com.zenlauncher.zen.domain.model.ActiveSession

/**
 * Programa un unico aviso para el final de la sesion.
 *
 * Es una alarma, no un servicio en primer plano: Zen no necesita estar viva mientras
 * corre el tiempo, porque el estado se deriva de marcas de tiempo persistidas. La
 * alarma solo existe para poder avisar cuando el usuario no esta mirando.
 */
interface SessionAlarmScheduler {
    fun schedule(session: ActiveSession)

    fun cancel()
}
