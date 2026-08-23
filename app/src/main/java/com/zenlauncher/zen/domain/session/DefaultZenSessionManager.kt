package com.zenlauncher.zen.domain.session

import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.domain.apps.AppRestrictionManager
import com.zenlauncher.zen.domain.battery.BatteryReader
import com.zenlauncher.zen.domain.model.ActiveSession
import com.zenlauncher.zen.domain.model.SessionOutcome
import com.zenlauncher.zen.domain.model.SessionProgress
import com.zenlauncher.zen.domain.model.SessionProgressCalculator
import com.zenlauncher.zen.domain.model.ZenDuration
import com.zenlauncher.zen.domain.model.ZenSession
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import com.zenlauncher.zen.domain.repository.SessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

class DefaultZenSessionManager(
    private val preferences: PreferencesRepository,
    private val sessions: SessionRepository,
    private val battery: BatteryReader,
    private val restrictions: AppRestrictionManager,
    private val alarms: SessionAlarmScheduler,
    private val clock: ZenClock,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) : ZenSessionManager {

    /**
     * El cierre puede llegar a la vez desde la alarma y desde la UI al volver a primer
     * plano. El mutex serializa esas dos entradas dentro del proceso; la idempotencia
     * real la garantiza `recordIfAbsent`, que ademas cubre el caso de dos procesos.
     */
    private val finishLock = Mutex()

    override val activeSession: Flow<ActiveSession?> = preferences.activeSession

    override suspend fun start(duration: ZenDuration): ActiveSession = finishLock.withLock {
        val status = battery.current()
        val restricted = preferences.currentRestrictedPackages()
        val session = ActiveSession(
            id = idFactory(),
            startedAtWallMillis = clock.wallTimeMillis(),
            startedAtElapsedMillis = clock.elapsedRealtimeMillis(),
            plannedDurationMillis = duration.millis,
            initialBatteryPercent = status.percent,
            initialCharging = status.charging,
            restrictedAppsCount = restricted.size,
        )
        // Persistir antes de cualquier efecto: si el proceso muere aqui, al volver a
        // abrir la sesion sigue en pie y el tiempo se calcula igual.
        preferences.putActiveSession(session)
        restrictions.enforce(session)
        alarms.schedule(session)
        session
    }

    override suspend fun finishNow(): ZenSession? = finishLock.withLock {
        val active = preferences.currentActiveSession() ?: return@withLock null
        finish(active)
    }

    override suspend fun resolveExpired(): ZenSession? = finishLock.withLock {
        val active = preferences.currentActiveSession() ?: return@withLock null
        if (!progressNow(active).isExpired) return@withLock null
        finish(active)
    }

    override fun progressNow(session: ActiveSession): SessionProgress =
        SessionProgressCalculator.progress(
            session = session,
            nowWallMillis = clock.wallTimeMillis(),
            nowElapsedMillis = clock.elapsedRealtimeMillis(),
        )

    /** Debe invocarse siempre con [finishLock] tomado. */
    private suspend fun finish(active: ActiveSession): ZenSession {
        val progress = progressNow(active)
        val outcome =
            if (progress.isExpired) SessionOutcome.COMPLETED else SessionOutcome.ABANDONED

        // Una sesion completada dura lo planificado aunque se cierre tarde: si el aviso
        // llega con retraso, no queremos inflar el tiempo registrado.
        val actualDuration = when (outcome) {
            SessionOutcome.COMPLETED -> active.plannedDurationMillis
            SessionOutcome.ABANDONED -> progress.elapsedMillis
        }

        val status = battery.current()
        val record = ZenSession(
            id = active.id,
            startedAtMillis = active.startedAtWallMillis,
            endedAtMillis = active.startedAtWallMillis + actualDuration,
            plannedDurationMillis = active.plannedDurationMillis,
            actualDurationMillis = actualDuration,
            initialBatteryPercent = active.initialBatteryPercent,
            finalBatteryPercent = status.percent,
            initialCharging = active.initialCharging,
            finalCharging = status.charging,
            outcome = outcome,
            restrictedAppsCount = active.restrictedAppsCount,
        )

        sessions.recordIfAbsent(record)
        // Se marca aunque el registro ya existiera: el usuario sigue sin haber visto
        // el resumen de esta sesion.
        preferences.setPendingSummary(record.id)
        // El orden importa: soltar restricciones y alarma aunque el registro ya
        // existiera, para no dejar el dispositivo en un estado a medias.
        alarms.cancel()
        restrictions.release()
        preferences.clearActiveSession()
        return record
    }
}
