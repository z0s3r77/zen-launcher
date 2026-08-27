package com.zenlauncher.zen.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.zenlauncher.zen.data.db.SqliteSessionRepository
import com.zenlauncher.zen.data.db.ZenDatabaseHelper
import com.zenlauncher.zen.data.notes.SqliteNotesRepository
import com.zenlauncher.zen.data.reading.SqliteBookRepository
import com.zenlauncher.zen.domain.model.SessionOutcome
import com.zenlauncher.zen.domain.model.ZenSession
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.domain.reading.Book
import com.zenlauncher.zen.domain.reading.Bookmark
import com.zenlauncher.zen.domain.reading.ReadingPosition
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migracion entre versiones del esquema: v1 (solo sesiones), v2 (+ notas), v3 (+ libros),
 * v4 (+ posicion dentro del parrafo, marcas y subrayados).
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

    /**
     * v3 anade Lectura. Mismo compromiso que v2: al otro lado hay sesiones hechas y
     * notas escritas, y ninguna de las dos cosas puede desaparecer por instalar la
     * version que trae los libros.
     */
    @Test
    fun `al subir a v3 no se pierde nada y los libros quedan utilizables`() = runTest {
        createVersion1Database()

        val sessions = SqliteSessionRepository(context, UnconfinedTestDispatcher())
        val notes = SqliteNotesRepository(context, UnconfinedTestDispatcher())
        val books = SqliteBookRepository(context, UnconfinedTestDispatcher())

        notes.save(Note("nota", 2_000L, 2_000L, "Una idea de antes"))
        books.save(
            book = Book(
                id = "libro",
                title = "El ser y la nada",
                author = "Jean-Paul Sartre",
                sourceUri = "content://documentos/1",
                coverPath = null,
                pageCount = 342,
                blockCount = 0,
                importedAtMillis = 3_000L,
                lastReadAtMillis = null,
            ),
            blocks = emptyList(),
            chapters = emptyList(),
        )

        assertNotNull("la migracion a v3 borro el registro de sesiones", sessions.find("de-la-v1"))
        assertEquals("Una idea de antes", notes.note("nota")?.body)
        assertEquals("El ser y la nada", books.book("libro")?.title)
    }

    /**
     * Crea a mano una `zen.db` con el esquema de Lectura tal y como era en v3 y un libro
     * a medio leer dentro, que es el estado del que parte cualquiera que ya tuviera la
     * version anterior instalada.
     *
     * Solo se recrea la mitad de Lectura porque es la unica que v4 toca: v4 es la primera
     * migracion que **altera una tabla existente** en lugar de anadir tablas nuevas, y es
     * justo eso lo que hay que probar.
     */
    private fun createVersion3ReadingDatabase() {
        val file = context.getDatabasePath("zen.db")
        file.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            """
            CREATE TABLE books (
                id TEXT NOT NULL PRIMARY KEY,
                title TEXT NOT NULL,
                author TEXT,
                source_uri TEXT NOT NULL,
                cover_path TEXT,
                page_count INTEGER NOT NULL,
                block_count INTEGER NOT NULL,
                imported_at INTEGER NOT NULL,
                last_read_at INTEGER,
                last_block INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE book_blocks (
                book_id TEXT NOT NULL,
                block_index INTEGER NOT NULL,
                kind TEXT NOT NULL,
                level INTEGER NOT NULL DEFAULT 0,
                page INTEGER NOT NULL,
                content TEXT NOT NULL,
                PRIMARY KEY (book_id, block_index),
                FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE book_chapters (
                book_id TEXT NOT NULL,
                block_index INTEGER NOT NULL,
                title TEXT NOT NULL,
                level INTEGER NOT NULL,
                page INTEGER NOT NULL,
                PRIMARY KEY (book_id, block_index),
                FOREIGN KEY (book_id) REFERENCES books (id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.insert(
            "books",
            null,
            ContentValues().apply {
                put("id", "de-la-v3")
                put("title", "El ser y la nada")
                put("author", "Jean-Paul Sartre")
                put("source_uri", "content://documentos/1")
                put("page_count", 342)
                put("block_count", 900)
                put("imported_at", 1_000L)
                put("last_read_at", 2_000L)
                put("last_block", 87)
            },
        )
        db.version = 3
        db.close()
    }

    /**
     * v4 anade la columna del desplazamiento dentro del parrafo. Al otro lado hay libros a
     * medio leer: un libro de v3 tiene que seguir estando y seguir abriendose por el
     * parrafo por el que iba. Se pierde media pagina de lectura, no el libro.
     */
    @Test
    fun `al subir a v4 los libros a medio leer siguen ahi`() = runTest {
        createVersion3ReadingDatabase()

        val books = SqliteBookRepository(context, UnconfinedTestDispatcher())

        val recuperado = books.book("de-la-v3")
        assertNotNull("la migracion a v4 borro los libros", recuperado)
        assertEquals("El ser y la nada", recuperado!!.title)
        assertEquals(87, recuperado.lastPosition.blockIndex)
        // Lo que no habia no se inventa: un libro de v3 empieza el parrafo por el principio.
        assertEquals(0, recuperado.lastPosition.charOffset)
    }

    @Test
    fun `al subir a v4 se puede marcar y subrayar`() = runTest {
        createVersion3ReadingDatabase()

        val books = SqliteBookRepository(context, UnconfinedTestDispatcher())
        books.addBookmark(
            Bookmark(
                id = "m1",
                bookId = "de-la-v3",
                position = ReadingPosition(87, 40),
                snippet = "El ser es y el no ser no es.",
                page = 12,
                createdAtMillis = 3_000L,
            ),
        )

        assertEquals(1, books.observeBookmarks("de-la-v3").first().size)
    }

    @Test
    fun `una instalacion nueva crea las tres partes del esquema de golpe`() = runTest {
        // Sin pasar por v1: onCreate tiene que dejar sesiones, notas y libros listos.
        val notes = SqliteNotesRepository(context, UnconfinedTestDispatcher())
        val sessions = SqliteSessionRepository(context, UnconfinedTestDispatcher())
        val books = SqliteBookRepository(context, UnconfinedTestDispatcher())

        notes.save(Note("a", 1_000L, 1_000L, "Una idea"))

        assertEquals("Una idea", notes.note("a")?.body)
        assertEquals(emptyList<Any>(), sessions.all())
        assertEquals(null, books.book("no-existe"))
    }

    /**
     * Regresion: los tres repositorios viven sobre el **mismo** fichero, `zen.db`, y cada
     * uno construia su propio `SQLiteOpenHelper`. Eso son tres pools de conexiones a la
     * misma base de datos —cache de paginas y sentencias preparadas duplicadas en memoria
     * nativa del proceso del launcher— y, en primera instalacion, tres candidatos a
     * ejecutar `onCreate` a la vez sobre unos `CREATE TABLE` que no llevan
     * `IF NOT EXISTS`.
     *
     * Compartiendo el ayudante, los tres escriben y leen por la misma conexion.
     */
    @Test
    fun `los tres repositorios comparten una sola conexion a zen db`() = runTest {
        val helper = ZenDatabaseHelper(context)
        val sessions = SqliteSessionRepository(helper, UnconfinedTestDispatcher())
        val notes = SqliteNotesRepository(helper, UnconfinedTestDispatcher())
        val books = SqliteBookRepository(helper, UnconfinedTestDispatcher())

        notes.save(Note("nota", 1_000L, 1_000L, "Una idea"))
        sessions.recordIfAbsent(
            ZenSession(
                id = "sesion",
                startedAtMillis = 1_000L,
                endedAtMillis = 61_000L,
                plannedDurationMillis = 60_000L,
                actualDurationMillis = 60_000L,
                initialBatteryPercent = 84,
                finalBatteryPercent = 83,
                initialCharging = false,
                finalCharging = false,
                outcome = SessionOutcome.COMPLETED,
                restrictedAppsCount = 0,
            ),
        )

        // Los tres siguen viendo lo suyo por la misma conexion.
        assertEquals(1, notes.observeNotes().first().size)
        assertEquals(1, sessions.all().size)
        assertTrue(books.observeBooks().first().isEmpty())
    }
}
