package com.zenlauncher.zen.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite directo en lugar de Room.
 *
 * Room necesita KSP y KSP no es compatible con el Kotlin integrado de AGP 9
 * ("KSP is not compatible with Android Gradle Plugin's built-in Kotlin"), y renunciar
 * al Kotlin integrado tampoco es viable porque `org.jetbrains.kotlin.android` no
 * soporta el DSL nuevo de AGP 9. Con una sola tabla el coste de escribirla a mano es
 * bajo, y `SessionRepository` aisla la decision para poder revertirla luego.
 */
internal class ZenDatabaseHelper(
    context: Context,
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_SESSIONS (
                $COLUMN_ID TEXT NOT NULL PRIMARY KEY,
                $COLUMN_STARTED_AT INTEGER NOT NULL,
                $COLUMN_ENDED_AT INTEGER NOT NULL,
                $COLUMN_PLANNED_DURATION INTEGER NOT NULL,
                $COLUMN_ACTUAL_DURATION INTEGER NOT NULL,
                $COLUMN_INITIAL_BATTERY INTEGER NOT NULL,
                $COLUMN_FINAL_BATTERY INTEGER NOT NULL,
                $COLUMN_INITIAL_CHARGING INTEGER NOT NULL,
                $COLUMN_FINAL_CHARGING INTEGER NOT NULL,
                $COLUMN_OUTCOME TEXT NOT NULL,
                $COLUMN_RESTRICTED_COUNT INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX idx_sessions_started_at ON $TABLE_SESSIONS ($COLUMN_STARTED_AT DESC)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // v0.1 es la primera version publicada: todavia no hay migraciones que aplicar.
        // Cuando las haya, este metodo debe migrar, nunca borrar.
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    companion object {
        const val DATABASE_NAME = "zen.db"
        const val DATABASE_VERSION = 1

        const val TABLE_SESSIONS = "sessions"
        const val COLUMN_ID = "id"
        const val COLUMN_STARTED_AT = "started_at"
        const val COLUMN_ENDED_AT = "ended_at"
        const val COLUMN_PLANNED_DURATION = "planned_duration"
        const val COLUMN_ACTUAL_DURATION = "actual_duration"
        const val COLUMN_INITIAL_BATTERY = "initial_battery"
        const val COLUMN_FINAL_BATTERY = "final_battery"
        const val COLUMN_INITIAL_CHARGING = "initial_charging"
        const val COLUMN_FINAL_CHARGING = "final_charging"
        const val COLUMN_OUTCOME = "outcome"
        const val COLUMN_RESTRICTED_COUNT = "restricted_count"
    }
}
