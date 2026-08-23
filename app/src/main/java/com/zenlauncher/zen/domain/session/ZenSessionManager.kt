package com.zenlauncher.zen.domain.session

import com.zenlauncher.zen.domain.model.ActiveSession
import com.zenlauncher.zen.domain.model.SessionProgress
import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.domain.model.ZenSession
import kotlinx.coroutines.flow.Flow

/**
 * Ciclo de vida de una sesion Zen.
 *
 * No sabe nada de como se refuerza la restriccion de aplicaciones: eso vive detras de
 * `AppRestrictionManager`, de modo que v0.2 pueda anadir Device Owner sin tocar esto.
 */
interface ZenSessionManager {

    val activeSession: Flow<ActiveSession?>

    suspend fun start(duration: ZenDuration): ActiveSession

    /** Termina la sesion en curso por decision del usuario. */
    suspend fun finishNow(): ZenSession?

    /**
     * Cierra la sesion si su tiempo ya vencio. Idempotente: se llama al arrancar, al
     * volver a primer plano y desde el receptor de la alarma, y solo la primera
     * registra.
     *
     * @return la sesion registrada, o null si no habia nada que cerrar.
     */
    suspend fun resolveExpired(): ZenSession?

    /** Progreso en un instante dado, usando el reloj inyectado. */
    fun progressNow(session: ActiveSession): SessionProgress
}
