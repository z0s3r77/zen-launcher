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
 * soporta el DSL nuevo de AGP 9. El coste de escribir las tablas a mano es bajo, y
 * los repositorios aislan la decision para poder revertirla luego.
 */
internal class ZenDatabaseHelper(
    context: Context,
) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        createSessionsSchema(db)
        createNotesSchema(db)
        createReadingSchema(db)
    }

    /**
     * Migrar, nunca borrar: al otro lado de esta funcion hay sesiones que el usuario ya
     * ha hecho, notas que ya ha escrito y libros a medio leer. Cada version se aplica en
     * su propio bloque y sin `else`, para que un salto de v1 a v4 pase por todas.
     *
     * **Las funciones `create...Schema` crean siempre la forma ACTUAL de sus tablas**, no
     * la que tenian en la version que las estreno. Eso significa que quien llega desde
     * antes de la 3 recibe las tablas de Lectura ya completas, y los pasos posteriores
     * que solo **retocan** esas mismas tablas no pueden volver a aplicarse encima: el
     * `ALTER` de v4 fallaba con "duplicate column name" en cualquier telefono que
     * actualizara desde v1 o v2, y un fallo aqui deja el telefono sin pantalla de inicio.
     * De ahi [readingJustCreated]. Cualquier paso futuro que retoque tablas ya existentes
     * necesita la misma guarda.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v2 solo anade tablas nuevas: las sesiones de v1 se quedan intactas, y por
            // eso aqui no hay ni un ALTER ni un DROP sobre `sessions`.
            createNotesSchema(db)
        }
        // v3 tambien anade solo tablas: los libros son una funcion nueva y no tocan ni
        // una fila de lo que ya habia. Se crean con la forma actual, que ya incluye todo
        // lo que anadio v4.
        val readingJustCreated = oldVersion < 3
        if (readingJustCreated) {
            createReadingSchema(db)
        }
        if (oldVersion < 4 && !readingJustCreated) {
            // v4 es la unica migracion que **toca una tabla existente**, y por eso es un
            // ALTER con valor por defecto y no un DROP: al otro lado hay libros a medio
            // leer. Un libro de v3 se queda con desplazamiento 0, que es el principio del
            // parrafo por el que iba: se pierde media pagina de lectura, no el libro.
            db.execSQL(
                "ALTER TABLE $TABLE_BOOKS ADD COLUMN $COLUMN_LAST_OFFSET INTEGER NOT NULL DEFAULT 0",
            )
            createMarksSchema(db)
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        db.setForeignKeyConstraintsEnabled(true)
    }

    private fun createSessionsSchema(db: SQLiteDatabase) {
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

    /**
     * Las tablas de notas.
     *
     * `note_embeddings` se crea aqui aunque el indice semantico llegue mas tarde: crear
     * una tabla vacia hoy cuesta nada y evita una migracion v2 a v3 sobre una base de
     * datos que para entonces ya tendra notas del usuario dentro.
     */
    private fun createNotesSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_PROJECTS (
                $COLUMN_ID TEXT NOT NULL PRIMARY KEY,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                $COLUMN_DONE INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_NOTES (
                $COLUMN_ID TEXT NOT NULL PRIMARY KEY,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                $COLUMN_UPDATED_AT INTEGER NOT NULL,
                $COLUMN_BODY TEXT NOT NULL,
                $COLUMN_SEARCH_TEXT TEXT NOT NULL,
                $COLUMN_TITLE TEXT,
                $COLUMN_SUMMARY TEXT,
                $COLUMN_TAGS TEXT NOT NULL DEFAULT '',
                $COLUMN_STAGE TEXT NOT NULL,
                $COLUMN_PROJECT_ID TEXT,
                $COLUMN_ENRICHED_AT INTEGER,
                -- Borrar un proyecto NO borra sus notas: las suelta. Un proyecto que se
                -- abandona no convierte en basura las ideas que lo formaron.
                FOREIGN KEY ($COLUMN_PROJECT_ID) REFERENCES $TABLE_PROJECTS ($COLUMN_ID)
                    ON DELETE SET NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX idx_notes_created_at ON $TABLE_NOTES ($COLUMN_CREATED_AT DESC)",
        )
        db.execSQL(
            "CREATE INDEX idx_notes_project ON $TABLE_NOTES ($COLUMN_PROJECT_ID)",
        )
        // La cola de enriquecimiento pregunta siempre por lo mismo: lo que todavia no ha
        // pasado por el asistente, empezando por lo mas viejo.
        db.execSQL(
            """
            CREATE INDEX idx_notes_pending
                ON $TABLE_NOTES ($COLUMN_ENRICHED_AT, $COLUMN_CREATED_AT)
            """.trimIndent(),
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_ATTACHMENTS (
                $COLUMN_ID TEXT NOT NULL PRIMARY KEY,
                $COLUMN_NOTE_ID TEXT NOT NULL,
                $COLUMN_KIND TEXT NOT NULL,
                $COLUMN_VALUE TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                FOREIGN KEY ($COLUMN_NOTE_ID) REFERENCES $TABLE_NOTES ($COLUMN_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX idx_attachments_note ON $TABLE_ATTACHMENTS ($COLUMN_NOTE_ID)",
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_LINKS (
                -- La clave es la pareja sin direccion: A-B y B-A son la misma conexion,
                -- y con dos filas el usuario tendria que ignorarla dos veces.
                $COLUMN_PAIR_KEY TEXT NOT NULL PRIMARY KEY,
                $COLUMN_FROM_NOTE_ID TEXT NOT NULL,
                $COLUMN_TO_NOTE_ID TEXT NOT NULL,
                $COLUMN_SCORE REAL NOT NULL,
                $COLUMN_ORIGIN TEXT NOT NULL,
                $COLUMN_STATE TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                FOREIGN KEY ($COLUMN_FROM_NOTE_ID) REFERENCES $TABLE_NOTES ($COLUMN_ID)
                    ON DELETE CASCADE,
                FOREIGN KEY ($COLUMN_TO_NOTE_ID) REFERENCES $TABLE_NOTES ($COLUMN_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_links_from ON $TABLE_LINKS ($COLUMN_FROM_NOTE_ID)")
        db.execSQL("CREATE INDEX idx_links_to ON $TABLE_LINKS ($COLUMN_TO_NOTE_ID)")

        db.execSQL(
            """
            CREATE TABLE $TABLE_EMBEDDINGS (
                $COLUMN_NOTE_ID TEXT NOT NULL PRIMARY KEY,
                -- Que modelo lo genero: al cambiar de motor hay que reindexar, y
                -- comparar un vector lexico con uno neuronal daria un numero sin
                -- ningun significado en vez de un error.
                $COLUMN_MODEL TEXT NOT NULL,
                $COLUMN_DIM INTEGER NOT NULL,
                $COLUMN_VECTOR BLOB NOT NULL,
                $COLUMN_UPDATED_AT INTEGER NOT NULL,
                FOREIGN KEY ($COLUMN_NOTE_ID) REFERENCES $TABLE_NOTES ($COLUMN_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }

    /**
     * Las tablas de Lectura.
     *
     * Un libro se guarda **como texto ya entendido**, no como PDF: la ficha, sus bloques
     * en orden y su indice. El PDF original no se copia (ver `Book.sourceUri`), asi que
     * lo que ocupa un libro aqui es el texto que tiene, un par de megabytes para uno de
     * 350 paginas.
     */
    private fun createReadingSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_BOOKS (
                $COLUMN_ID TEXT NOT NULL PRIMARY KEY,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_AUTHOR TEXT,
                $COLUMN_SOURCE_URI TEXT NOT NULL,
                $COLUMN_COVER_PATH TEXT,
                $COLUMN_PAGE_COUNT INTEGER NOT NULL,
                $COLUMN_BLOCK_COUNT INTEGER NOT NULL,
                $COLUMN_IMPORTED_AT INTEGER NOT NULL,
                $COLUMN_LAST_READ_AT INTEGER,
                $COLUMN_LAST_BLOCK INTEGER NOT NULL DEFAULT 0,
                -- Dentro de que parrafo. Pasando pagina, un parrafo largo se parte por
                -- la mitad y "por donde ibas" es un punto dentro del bloque.
                $COLUMN_LAST_OFFSET INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        // La biblioteca se ordena siempre igual: lo ultimo que se estuvo leyendo arriba.
        db.execSQL(
            "CREATE INDEX idx_books_last_read ON $TABLE_BOOKS ($COLUMN_LAST_READ_AT DESC)",
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_BOOK_BLOCKS (
                $COLUMN_BOOK_ID TEXT NOT NULL,
                $COLUMN_BLOCK_INDEX INTEGER NOT NULL,
                $COLUMN_KIND TEXT NOT NULL,
                $COLUMN_LEVEL INTEGER NOT NULL DEFAULT 0,
                $COLUMN_PAGE INTEGER NOT NULL,
                $COLUMN_VALUE TEXT NOT NULL,
                -- La pareja libro-posicion es la identidad de un bloque: no hace falta
                -- un id propio, y asi el indice que se necesita para leerlo en orden es
                -- la propia clave primaria.
                PRIMARY KEY ($COLUMN_BOOK_ID, $COLUMN_BLOCK_INDEX),
                FOREIGN KEY ($COLUMN_BOOK_ID) REFERENCES $TABLE_BOOKS ($COLUMN_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent(),
        )

        createMarksSchema(db)

        db.execSQL(
            """
            CREATE TABLE $TABLE_BOOK_CHAPTERS (
                $COLUMN_BOOK_ID TEXT NOT NULL,
                $COLUMN_BLOCK_INDEX INTEGER NOT NULL,
                $COLUMN_TITLE TEXT NOT NULL,
                $COLUMN_LEVEL INTEGER NOT NULL,
                $COLUMN_PAGE INTEGER NOT NULL,
                PRIMARY KEY ($COLUMN_BOOK_ID, $COLUMN_BLOCK_INDEX),
                FOREIGN KEY ($COLUMN_BOOK_ID) REFERENCES $TABLE_BOOKS ($COLUMN_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent(),
        )
    }

    /**
     * Lo que el usuario deja escrito **encima** del libro: marcas y subrayados.
     *
     * Va en su propia funcion y no dentro de [createReadingSchema] porque son dos
     * versiones distintas del esquema: quien instalo la version anterior ya tiene las
     * tablas de libros y solo necesita estas.
     *
     * Un subrayado guarda **su texto copiado**, no solo las posiciones. Es la unica
     * duplicacion de datos que hay aqui y esta puesta a proposito: la lista de subrayados
     * es donde se repasa, y sin el texto habria que cargar el libro entero —miles de
     * bloques— para poder pintar una lista de diez lineas.
     */
    private fun createMarksSchema(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_BOOKMARKS (
                $COLUMN_ID TEXT NOT NULL PRIMARY KEY,
                $COLUMN_BOOK_ID TEXT NOT NULL,
                $COLUMN_BLOCK_INDEX INTEGER NOT NULL,
                $COLUMN_CHAR_OFFSET INTEGER NOT NULL DEFAULT 0,
                $COLUMN_VALUE TEXT NOT NULL,
                $COLUMN_PAGE INTEGER NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                FOREIGN KEY ($COLUMN_BOOK_ID) REFERENCES $TABLE_BOOKS ($COLUMN_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX idx_bookmarks_book ON $TABLE_BOOKMARKS ($COLUMN_BOOK_ID, $COLUMN_BLOCK_INDEX)",
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_HIGHLIGHTS (
                $COLUMN_ID TEXT NOT NULL PRIMARY KEY,
                $COLUMN_BOOK_ID TEXT NOT NULL,
                $COLUMN_BLOCK_INDEX INTEGER NOT NULL,
                $COLUMN_START INTEGER NOT NULL,
                $COLUMN_END INTEGER NOT NULL,
                $COLUMN_VALUE TEXT NOT NULL,
                -- null es "solo subrayado". Subrayar y anotar son la misma cosa con y
                -- sin texto detras, no dos funciones: ver `Highlight`.
                $COLUMN_NOTE TEXT,
                $COLUMN_PAGE INTEGER NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                FOREIGN KEY ($COLUMN_BOOK_ID) REFERENCES $TABLE_BOOKS ($COLUMN_ID)
                    ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX idx_highlights_book ON $TABLE_HIGHLIGHTS ($COLUMN_BOOK_ID, $COLUMN_BLOCK_INDEX)",
        )
    }

    companion object {
        const val DATABASE_NAME = "zen.db"

        /** v4: paginacion (posicion con desplazamiento), marcas de pagina y subrayados. */
        const val DATABASE_VERSION = 4

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

        const val TABLE_NOTES = "notes"
        const val TABLE_ATTACHMENTS = "note_attachments"
        const val TABLE_LINKS = "note_links"
        const val TABLE_EMBEDDINGS = "note_embeddings"
        const val TABLE_PROJECTS = "projects"

        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_UPDATED_AT = "updated_at"
        const val COLUMN_BODY = "body"

        /** Cuerpo mas etiquetas, normalizado. Es lo que mira el filtro literal. */
        const val COLUMN_SEARCH_TEXT = "search_text"
        const val COLUMN_TITLE = "title"
        const val COLUMN_SUMMARY = "summary"

        /** Lista separada por saltos de linea: se lee y se escribe siempre entera. */
        const val COLUMN_TAGS = "tags"
        const val COLUMN_STAGE = "stage"
        const val COLUMN_PROJECT_ID = "project_id"
        const val COLUMN_ENRICHED_AT = "enriched_at"

        const val COLUMN_NOTE_ID = "note_id"
        const val COLUMN_KIND = "kind"
        const val COLUMN_VALUE = "content"

        const val COLUMN_PAIR_KEY = "pair_key"
        const val COLUMN_FROM_NOTE_ID = "from_note_id"
        const val COLUMN_TO_NOTE_ID = "to_note_id"
        const val COLUMN_SCORE = "score"
        const val COLUMN_ORIGIN = "origin"
        const val COLUMN_STATE = "state"

        const val COLUMN_MODEL = "model"
        const val COLUMN_DIM = "dim"
        const val COLUMN_VECTOR = "vector"

        const val COLUMN_DONE = "done"

        const val TABLE_BOOKS = "books"
        const val TABLE_BOOK_BLOCKS = "book_blocks"
        const val TABLE_BOOK_CHAPTERS = "book_chapters"

        const val COLUMN_AUTHOR = "author"
        const val COLUMN_SOURCE_URI = "source_uri"
        const val COLUMN_COVER_PATH = "cover_path"
        const val COLUMN_PAGE_COUNT = "page_count"
        const val COLUMN_BLOCK_COUNT = "block_count"
        const val COLUMN_IMPORTED_AT = "imported_at"
        const val COLUMN_LAST_READ_AT = "last_read_at"
        const val COLUMN_LAST_BLOCK = "last_block"

        const val COLUMN_BOOK_ID = "book_id"

        /** `index` es palabra reservada de SQL, de ahi el nombre largo. */
        const val COLUMN_BLOCK_INDEX = "block_index"
        const val COLUMN_LEVEL = "level"
        const val COLUMN_PAGE = "page"

        const val TABLE_BOOKMARKS = "book_bookmarks"
        const val TABLE_HIGHLIGHTS = "book_highlights"

        const val COLUMN_LAST_OFFSET = "last_offset"
        const val COLUMN_CHAR_OFFSET = "char_offset"
        const val COLUMN_START = "start_offset"

        /** `end` es palabra reservada de SQL, de ahi el nombre largo. */
        const val COLUMN_END = "end_offset"
        const val COLUMN_NOTE = "note"
    }
}
