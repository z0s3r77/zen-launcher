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
    }

    /**
     * Migrar, nunca borrar: al otro lado de esta funcion hay sesiones que el usuario ya
     * ha hecho y notas que ya ha escrito. Cada version se aplica en su propio bloque y
     * sin `else`, para que un salto de v1 a v3 pase por las dos.
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            // v2 solo anade tablas nuevas: las sesiones de v1 se quedan intactas, y por
            // eso aqui no hay ni un ALTER ni un DROP sobre `sessions`.
            createNotesSchema(db)
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

    companion object {
        const val DATABASE_NAME = "zen.db"

        /** v2: tablas de notas, adjuntos, conexiones, proyectos e indice semantico. */
        const val DATABASE_VERSION = 2

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
    }
}
