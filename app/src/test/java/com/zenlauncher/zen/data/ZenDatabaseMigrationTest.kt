package com.zenlauncher.zen.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.zenlauncher.zen.data.db.SqliteSessionRepository
import com.zenlauncher.zen.data.notes.SqliteNotesRepository
import com.zenlauncher.zen.domain.notes.Note
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migracion de v1 (solo sesiones) a v2 (sesiones + notas).
 *
 * Al otro lado de `onUpgrade` hay sesiones que el usuario ya ha hecho. La forma facil de
 * migrar —soltar las tablas y volver a crearlas— le borraria el registro entero al
 * instalar la version con notas, y el registro es de las pocas cosas de Zen que se
 * acumulan con el tiempo. Este test existe para que esa forma facil no se cuele.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZenDatabaseMigrationTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    /**
     * Crea a mano una `zen.db` con el esquema exacto de v1 y una sesion dentro, que es
     * el estado del que parte cualquiera que ya tuviera Zen instalado.
     */
    private fun createVersion1Database() {
        val file = context.getDatabasePath("zen.db")
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            """
            CREATE TABLE sessions (
                id TEXT NOT NULL PRIMARY KEY,
                started_at INTEGER NOT NULL,
                ended_at INTEGER NOT NULL,
                planned_duration INTEGER NOT NULL,
                actual_duration INTEGER NOT NULL,
                initial_battery INTEGER NOT NULL,
                final_battery INTEGER NOT NULL,
                initial_charging INTEGER NOT NULL,
                final_charging INTEGER NOT NULL,
                outcome TEXT NOT NULL,
                restricted_count INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_sessions_started_at ON sessions (started_at DESC)")
        db.insert(
            "sessions",
            null,
            ContentValues().apply {
                put("id", "de-la-v1")
                put("started_at", 1_000L)
                put("ended_at", 1_800_000L)
                put("planned_duration", 1_800_000L)
                put("actual_duration", 1_799_000L)
                put("initial_battery", 84)
                put("final_battery", 80)
                put("initial_charging", 0)
                put("final_charging", 0)
                put("outcome", "COMPLETED")
                put("restricted_count", 4)
            },
        )
        db.version = 1
        db.close()
    }

    @Test
    fun `al subir a v2 las sesiones de v1 siguen ahi`() = runTest {
        createVersion1Database()

        val sessions = SqliteSessionRepository(context, UnconfinedTestDispatcher())

        val recuperada = sessions.find("de-la-v1")
        assertNotNull("la migracion borro el registro de sesiones", recuperada)
        assertEquals(1_800_000L, recuperada!!.plannedDurationMillis)
        assertEquals(84, recuperada.initialBatteryPercent)
    }

    @Test
    fun `al subir a v2 las tablas de notas quedan utilizables`() = runTest {
        createVersion1Database()

        val notes = SqliteNotesRepository(context, UnconfinedTestDispatcher())
        notes.save(
            Note(
                id = "primera",
                createdAtMillis = 2_000L,
                updatedAtMillis = 2_000L,
                body = "La primera nota despues de actualizar",
            ),
        )

        assertEquals("La primera nota despues de actualizar", notes.note("primera")?.body)
    }

    @Test
    fun `una instalacion nueva crea las dos mitades del esquema de golpe`() = runTest {
        // Sin pasar por v1: onCreate tiene que dejar las sesiones y las notas listas.
        val notes = SqliteNotesRepository(context, UnconfinedTestDispatcher())
        val sessions = SqliteSessionRepository(context, UnconfinedTestDispatcher())

        notes.save(Note("a", 1_000L, 1_000L, "Una idea"))

        assertEquals("Una idea", notes.note("a")?.body)
        assertEquals(emptyList<Any>(), sessions.all())
    }
}
