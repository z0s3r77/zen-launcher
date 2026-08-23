package com.zenlauncher.zen.data.db

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_ACTUAL_DURATION
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_ENDED_AT
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_FINAL_BATTERY
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_FINAL_CHARGING
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_ID
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_INITIAL_BATTERY
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_INITIAL_CHARGING
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_OUTCOME
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_PLANNED_DURATION
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_RESTRICTED_COUNT
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_STARTED_AT
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.TABLE_SESSIONS
import com.zenlauncher.zen.domain.model.SessionOutcome
import com.zenlauncher.zen.domain.model.ZenSession
import com.zenlauncher.zen.domain.repository.SessionRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

class SqliteSessionRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : SessionRepository {

    private val helper = ZenDatabaseHelper(context.applicationContext)

    /** extraBufferCapacity para que emitir un cambio nunca suspenda al escritor. */
    private val invalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    override fun observeAll(): Flow<List<ZenSession>> =
        invalidations
            .onStart { emit(Unit) }
            .let { source -> flow { source.collect { emit(all()) } } }
            .flowOn(io)

    override suspend fun all(): List<ZenSession> = withContext(io) {
        helper.readableDatabase.query(
            TABLE_SESSIONS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_STARTED_AT DESC",
        ).use { cursor ->
            buildList(cursor.count) {
                while (cursor.moveToNext()) add(cursor.toSession())
            }
        }
    }

    override suspend fun find(id: String): ZenSession? = withContext(io) {
        helper.readableDatabase.query(
            TABLE_SESSIONS,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toSession() else null
        }
    }

    override suspend fun recordIfAbsent(session: ZenSession): Boolean = withContext(io) {
        val db = helper.writableDatabase
        // CONFLICT_IGNORE hace la insercion idempotente en la propia base de datos, no
        // solo dentro del proceso: la alarma y la UI pueden intentarlo a la vez.
        val rowId = db.insertWithOnConflict(
            TABLE_SESSIONS,
            null,
            session.toContentValues(),
            SQLiteDatabase.CONFLICT_IGNORE,
        )
        val inserted = rowId != -1L
        if (inserted) invalidations.tryEmit(Unit)
        inserted
    }

    override suspend fun deleteAll() {
        withContext(io) {
            helper.writableDatabase.delete(TABLE_SESSIONS, null, null)
        }
        invalidations.tryEmit(Unit)
    }

    private fun ZenSession.toContentValues() = ContentValues().apply {
        put(COLUMN_ID, id)
        put(COLUMN_STARTED_AT, startedAtMillis)
        put(COLUMN_ENDED_AT, endedAtMillis)
        put(COLUMN_PLANNED_DURATION, plannedDurationMillis)
        put(COLUMN_ACTUAL_DURATION, actualDurationMillis)
        put(COLUMN_INITIAL_BATTERY, initialBatteryPercent)
        put(COLUMN_FINAL_BATTERY, finalBatteryPercent)
        put(COLUMN_INITIAL_CHARGING, if (initialCharging) 1 else 0)
        put(COLUMN_FINAL_CHARGING, if (finalCharging) 1 else 0)
        put(COLUMN_OUTCOME, outcome.name)
        put(COLUMN_RESTRICTED_COUNT, restrictedAppsCount)
    }

    private fun Cursor.toSession() = ZenSession(
        id = getString(getColumnIndexOrThrow(COLUMN_ID)),
        startedAtMillis = getLong(getColumnIndexOrThrow(COLUMN_STARTED_AT)),
        endedAtMillis = getLong(getColumnIndexOrThrow(COLUMN_ENDED_AT)),
        plannedDurationMillis = getLong(getColumnIndexOrThrow(COLUMN_PLANNED_DURATION)),
        actualDurationMillis = getLong(getColumnIndexOrThrow(COLUMN_ACTUAL_DURATION)),
        initialBatteryPercent = getInt(getColumnIndexOrThrow(COLUMN_INITIAL_BATTERY)),
        finalBatteryPercent = getInt(getColumnIndexOrThrow(COLUMN_FINAL_BATTERY)),
        initialCharging = getInt(getColumnIndexOrThrow(COLUMN_INITIAL_CHARGING)) == 1,
        finalCharging = getInt(getColumnIndexOrThrow(COLUMN_FINAL_CHARGING)) == 1,
        outcome = SessionOutcome.fromStorage(getString(getColumnIndexOrThrow(COLUMN_OUTCOME))),
        restrictedAppsCount = getInt(getColumnIndexOrThrow(COLUMN_RESTRICTED_COUNT)),
    )
}
