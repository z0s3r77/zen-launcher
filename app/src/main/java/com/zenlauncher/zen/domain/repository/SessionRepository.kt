package com.zenlauncher.zen.domain.repository

import com.zenlauncher.zen.domain.model.ZenSession
import kotlinx.coroutines.flow.Flow

/**
 * Registro de sesiones terminadas.
 *
 * Es una interfaz, y no una clase de Room, porque la implementacion de v0.1 usa
 * SQLite directo: KSP (y por tanto Room) no es compatible con el Kotlin integrado de
 * AGP 9. Cuando esa incompatibilidad se resuelva, cambiar de motor es sustituir la
 * implementacion sin tocar dominio ni UI.
 */
interface SessionRepository {

    /** Emite de nuevo cada vez que se registra una sesion. */
    fun observeAll(): Flow<List<ZenSession>>

    suspend fun all(): List<ZenSession>

    suspend fun find(id: String): ZenSession?

    /**
     * Inserta si no existe ya una sesion con ese id. Es idempotente a proposito: el
     * cierre de una sesion puede llegar por la alarma y por la apertura de la app
     * casi a la vez.
     *
     * @return true si esta llamada fue la que la registro.
     */
    suspend fun recordIfAbsent(session: ZenSession): Boolean

    suspend fun deleteAll()
}
