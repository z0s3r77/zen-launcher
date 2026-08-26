package com.zenlauncher.zen.data.notes

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.zenlauncher.zen.data.db.ZenDatabaseHelper
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_BODY
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_CREATED_AT
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_DIM
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_DONE
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_ENRICHED_AT
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_FROM_NOTE_ID
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_ID
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_KIND
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_MODEL
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_NOTE_ID
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_ORIGIN
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_PAIR_KEY
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_PROJECT_ID
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_SCORE
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_SEARCH_TEXT
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_STAGE
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_STATE
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_SUMMARY
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_TAGS
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_TITLE
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_TO_NOTE_ID
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_UPDATED_AT
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_VECTOR
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.COLUMN_VALUE
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.TABLE_ATTACHMENTS
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.TABLE_EMBEDDINGS
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.TABLE_LINKS
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.TABLE_NOTES
import com.zenlauncher.zen.data.db.ZenDatabaseHelper.Companion.TABLE_PROJECTS
import com.zenlauncher.zen.domain.notes.AttachmentKind
import com.zenlauncher.zen.domain.notes.LinkOrigin
import com.zenlauncher.zen.domain.notes.LinkState
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.domain.notes.NoteAttachment
import com.zenlauncher.zen.domain.notes.NoteLink
import com.zenlauncher.zen.domain.notes.NoteStage
import com.zenlauncher.zen.domain.notes.NotesRepository
import com.zenlauncher.zen.domain.notes.Project
import com.zenlauncher.zen.domain.notes.TextNormalizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Notas sobre la misma base de datos que las sesiones.
 *
 * Mismo patron que [com.zenlauncher.zen.data.db.SqliteSessionRepository]: las lecturas
 * en vivo se reemiten al recibir una invalidacion, en lugar de montar observadores de
 * SQLite. Con una base de datos de un solo proceso, releer una lista de notas cuesta
 * menos que mantener la maquinaria para calcular que cambio.
 */
class SqliteNotesRepository(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : NotesRepository {

    private val helper = ZenDatabaseHelper(context.applicationContext)

    /** extraBufferCapacity para que emitir un cambio nunca suspenda al escritor. */
    private val invalidations = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    override fun observeNotes(): Flow<List<Note>> = reemitting { allNotes() }

    override fun observeNote(id: String): Flow<Note?> = reemitting { note(id) }

    override fun observeLinks(noteId: String): Flow<List<NoteLink>> = reemitting { links(noteId) }

    override fun observeProjects(): Flow<List<Project>> = reemitting { projects() }

    override suspend fun note(id: String): Note? = withContext(io) {
        val note = helper.readableDatabase.query(
            TABLE_NOTES,
            null,
            "$COLUMN_ID = ?",
            arrayOf(id),
            null,
            null,
            null,
            "1",
        ).use { if (it.moveToFirst()) it.toNote() else null } ?: return@withContext null

        note.copy(attachments = attachmentsFor(listOf(id))[id].orEmpty())
    }

    override suspend fun save(note: Note) {
        withContext(io) {
            val db = helper.writableDatabase
            db.beginTransaction()
            try {
                upsertNote(db, note)
                // Los adjuntos se reescriben enteros: son pocos por nota y calcular el
                // diferencial costaria mas codigo del que ahorra.
                db.delete(TABLE_ATTACHMENTS, "$COLUMN_NOTE_ID = ?", arrayOf(note.id))
                note.attachments.forEach { attachment ->
                    db.insertWithOnConflict(
                        TABLE_ATTACHMENTS,
                        null,
                        attachment.toContentValues(note.id),
                        SQLiteDatabase.CONFLICT_REPLACE,
                    )
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
        invalidations.tryEmit(Unit)
    }

    /**
     * Inserta, y si ya existia esa nota **actualiza** en lugar de reemplazar.
     *
     * `CONFLICT_REPLACE` seria un DELETE seguido de un INSERT, y las claves foraneas en
     * cascada se llevarian por delante los adjuntos y **todas las conexiones** de la
     * nota. Es decir: editar una nota borraria en silencio los enlaces que el usuario
     * habia aceptado con otras notas. Un UPDATE deja intacto lo que cuelga de ella.
     */
    private fun upsertNote(db: SQLiteDatabase, note: Note) {
        val values = note.toContentValues()
        val updated = db.update(TABLE_NOTES, values, "$COLUMN_ID = ?", arrayOf(note.id))
        if (updated == 0) {
            db.insertWithOnConflict(TABLE_NOTES, null, values, SQLiteDatabase.CONFLICT_ABORT)
        }
    }

    override suspend fun delete(id: String) {
        withContext(io) {
            // Adjuntos, conexiones y vector caen solos por ON DELETE CASCADE.
            helper.writableDatabase.delete(TABLE_NOTES, "$COLUMN_ID = ?", arrayOf(id))
        }
        invalidations.tryEmit(Unit)
    }

    override suspend fun search(query: String): List<Note> = withContext(io) {
        val needle = TextNormalizer.normalize(query)
        if (needle.isBlank()) return@withContext allNotes()

        // El texto ya se guarda normalizado en `search_text`, asi que el LIKE compara
        // manzanas con manzanas: buscar "aburrimiento" encuentra "aburrimiento" aunque
        // la nota se dictara con tilde. ESCAPE porque un guion bajo o un porcentaje
        // escritos en la busqueda son literales para el usuario, no comodines.
        val escaped = needle.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        readNotes(
            selection = "$COLUMN_SEARCH_TEXT LIKE ? ESCAPE '\\'",
            selectionArgs = arrayOf("%$escaped%"),
        )
    }

    override suspend fun pendingEnrichment(limit: Int): List<Note> = withContext(io) {
        readNotes(
            selection = "$COLUMN_ENRICHED_AT IS NULL",
            selectionArgs = null,
            orderBy = "$COLUMN_CREATED_AT ASC",
            limit = limit.toString(),
        )
    }

    override suspend fun putLink(link: NoteLink) {
        withContext(io) {
            // Una propuesta del indice NO pisa lo que ya haya: si el usuario descarto
            // esa pareja, reproponerla con estado PENDING la resucitaba y la conexion
            // volvia a la pantalla como si nunca la hubiera visto. Lo que decide el
            // usuario (aceptar, ignorar, conectar a mano) si escribe siempre.
            val conflict = if (link.isFreshSuggestion) {
                SQLiteDatabase.CONFLICT_IGNORE
            } else {
                SQLiteDatabase.CONFLICT_REPLACE
            }
            helper.writableDatabase.insertWithOnConflict(
                TABLE_LINKS,
                null,
                link.toContentValues(),
                conflict,
            )
        }
        invalidations.tryEmit(Unit)
    }

    override fun observePendingLinks(): Flow<List<NoteLink>> = reemitting { pendingLinks() }

    private fun pendingLinks(): List<NoteLink> =
        helper.readableDatabase.query(
            TABLE_LINKS,
            null,
            "$COLUMN_STATE = ?",
            arrayOf(LinkState.PENDING.name),
            null,
            null,
            "$COLUMN_SCORE DESC",
        ).use { cursor ->
            buildList(cursor.count) {
                while (cursor.moveToNext()) add(cursor.toLink())
            }
        }

    override fun observeAcceptedLinks(): Flow<List<NoteLink>> = reemitting { acceptedLinks() }

    private fun acceptedLinks(): List<NoteLink> =
        helper.readableDatabase.query(
            TABLE_LINKS,
            null,
            "$COLUMN_STATE = ?",
            arrayOf(LinkState.ACCEPTED.name),
            null,
            null,
            null,
        ).use { cursor ->
            buildList(cursor.count) {
                while (cursor.moveToNext()) add(cursor.toLink())
            }
        }

    override suspend fun ignoredPairs(): Set<String> = withContext(io) {
        helper.readableDatabase.query(
            TABLE_LINKS,
            arrayOf(COLUMN_PAIR_KEY),
            "$COLUMN_STATE = ?",
            arrayOf(LinkState.IGNORED.name),
            null,
            null,
            null,
        ).use { cursor ->
            buildSet(cursor.count) {
                while (cursor.moveToNext()) add(cursor.getString(0))
            }
        }
    }

    override suspend fun putEmbedding(noteId: String, model: String, vector: FloatArray) {
        withContext(io) {
            helper.writableDatabase.insertWithOnConflict(
                TABLE_EMBEDDINGS,
                null,
                ContentValues().apply {
                    put(COLUMN_NOTE_ID, noteId)
                    put(COLUMN_MODEL, model)
                    put(COLUMN_DIM, vector.size)
                    put(COLUMN_VECTOR, vector.toBlob())
                    put(COLUMN_UPDATED_AT, System.currentTimeMillis())
                },
                // Reindexar una nota pisa su vector: es la misma nota, no una segunda.
                SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        // Los vectores no cambian nada de lo que se ve: no se invalida la lectura en
        // vivo. Emitir aqui repintaria la lista entera cada vez que se indexa una nota,
        // que es justo lo que pasa al arrancar con notas viejas sin indexar.
    }

    override suspend fun embeddings(model: String): Map<String, FloatArray> = withContext(io) {
        helper.readableDatabase.query(
            TABLE_EMBEDDINGS,
            arrayOf(COLUMN_NOTE_ID, COLUMN_VECTOR),
            "$COLUMN_MODEL = ?",
            arrayOf(model),
            null,
            null,
            null,
        ).use { cursor ->
            buildMap(cursor.count) {
                while (cursor.moveToNext()) {
                    put(cursor.getString(0), cursor.getBlob(1).toFloatArray())
                }
            }
        }
    }

    override suspend fun notesWithoutEmbedding(model: String, limit: Int): List<Note> =
        withContext(io) {
            // NOT EXISTS y no LEFT JOIN: no hace falta traer ninguna columna de la tabla
            // de vectores, solo saber si hay fila para esa nota con ese motor.
            readNotes(
                selection = """
                NOT EXISTS (
                    SELECT 1 FROM $TABLE_EMBEDDINGS
                    WHERE $TABLE_EMBEDDINGS.$COLUMN_NOTE_ID = $TABLE_NOTES.$COLUMN_ID
                      AND $TABLE_EMBEDDINGS.$COLUMN_MODEL = ?
                )
                """.trimIndent(),
                selectionArgs = arrayOf(model),
                orderBy = "$COLUMN_CREATED_AT ASC",
                limit = limit.toString(),
            )
        }

    /**
     * El vector como bytes, en orden little-endian **explicito**.
     *
     * `ByteBuffer` usa big-endian por defecto y ARM es little-endian: sin fijarlo, un
     * cambio de plataforma leeria los mismos bytes como numeros distintos y las
     * conexiones se volverian aleatorias sin que nada fallara de forma visible.
     */
    private fun FloatArray.toBlob(): ByteArray {
        val buffer = ByteBuffer.allocate(size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        forEach(buffer::putFloat)
        return buffer.array()
    }

    private fun ByteArray.toFloatArray(): FloatArray {
        val buffer = ByteBuffer.wrap(this).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(size / Float.SIZE_BYTES) { buffer.getFloat() }
    }

    override suspend fun saveProject(project: Project) {
        withContext(io) {
            // Mismo motivo que `upsertNote`: `CONFLICT_REPLACE` es un DELETE seguido de
            // un INSERT, y `notes.project_id` tiene `ON DELETE SET NULL` en cascada. Al
            // editar un proyecto ya existente (por ejemplo, marcarlo terminado) ese
            // DELETE intermedio soltaba todas sus notas antes de que el INSERT llegara a
            // devolverles el id: la nota se quedaba sin proyecto y la cola de "marcar
            // terminado" no encontraba nada que actualizar. Un UPDATE deja intacta la
            // referencia.
            val db = helper.writableDatabase
            val values = project.toContentValues()
            val updated = db.update(TABLE_PROJECTS, values, "$COLUMN_ID = ?", arrayOf(project.id))
            if (updated == 0) {
                db.insertWithOnConflict(TABLE_PROJECTS, null, values, SQLiteDatabase.CONFLICT_ABORT)
            }
        }
        invalidations.tryEmit(Unit)
    }

    override suspend fun deleteProject(id: String) {
        withContext(io) {
            // Las notas del proyecto se sueltan, no se borran: ON DELETE SET NULL.
            helper.writableDatabase.delete(TABLE_PROJECTS, "$COLUMN_ID = ?", arrayOf(id))
        }
        invalidations.tryEmit(Unit)
    }

    override suspend fun assignToProject(noteId: String, projectId: String?) {
        withContext(io) {
            val values = ContentValues().apply {
                if (projectId == null) putNull(COLUMN_PROJECT_ID) else put(COLUMN_PROJECT_ID, projectId)
            }
            helper.writableDatabase.update(TABLE_NOTES, values, "$COLUMN_ID = ?", arrayOf(noteId))
        }
        invalidations.tryEmit(Unit)
    }

    override suspend fun notesInProject(projectId: String): List<Note> = withContext(io) {
        readNotes(
            selection = "$COLUMN_PROJECT_ID = ?",
            selectionArgs = arrayOf(projectId),
        )
    }

    // --- Lectura ---

    private suspend fun allNotes(): List<Note> = withContext(io) { readNotes(null, null) }

    private fun links(noteId: String): List<NoteLink> =
        helper.readableDatabase.query(
            TABLE_LINKS,
            null,
            "$COLUMN_FROM_NOTE_ID = ? OR $COLUMN_TO_NOTE_ID = ?",
            arrayOf(noteId, noteId),
            null,
            null,
            "$COLUMN_SCORE DESC",
        ).use { cursor ->
            buildList(cursor.count) {
                while (cursor.moveToNext()) add(cursor.toLink())
            }
        }

    private fun projects(): List<Project> =
        helper.readableDatabase.query(
            TABLE_PROJECTS,
            null,
            null,
            null,
            null,
            null,
            "$COLUMN_CREATED_AT DESC",
        ).use { cursor ->
            buildList(cursor.count) {
                while (cursor.moveToNext()) add(cursor.toProject())
            }
        }

    /**
     * Lee notas y les engancha sus adjuntos en **dos** consultas, no en una por nota:
     * con cien notas en pantalla, la version ingenua son ciento una consultas.
     */
    private fun readNotes(
        selection: String?,
        selectionArgs: Array<String>?,
        orderBy: String = "$COLUMN_CREATED_AT DESC",
        limit: String? = null,
    ): List<Note> {
        val notes = helper.readableDatabase.query(
            TABLE_NOTES,
            null,
            selection,
            selectionArgs,
            null,
            null,
            orderBy,
            limit,
        ).use { cursor ->
            buildList(cursor.count) {
                while (cursor.moveToNext()) add(cursor.toNote())
            }
        }
        if (notes.isEmpty()) return notes

        val attachments = attachmentsFor(notes.map { it.id })
        return notes.map { it.copy(attachments = attachments[it.id].orEmpty()) }
    }

    private fun attachmentsFor(noteIds: List<String>): Map<String, List<NoteAttachment>> {
        if (noteIds.isEmpty()) return emptyMap()
        val placeholders = noteIds.joinToString(",") { "?" }
        return helper.readableDatabase.query(
            TABLE_ATTACHMENTS,
            null,
            "$COLUMN_NOTE_ID IN ($placeholders)",
            noteIds.toTypedArray(),
            null,
            null,
            "$COLUMN_CREATED_AT ASC",
        ).use { cursor ->
            buildList(cursor.count) {
                while (cursor.moveToNext()) add(cursor.toAttachment())
            }
        }.groupBy { it.noteId }
    }

    /** Vuelve a leer en cada invalidacion, empezando por leer una vez al suscribirse. */
    private fun <T> reemitting(read: suspend () -> T): Flow<T> =
        invalidations
            .onStart { emit(Unit) }
            .let { source -> flow { source.collect { emit(read()) } } }
            .flowOn(io)

    // --- Conversiones ---

    private fun Note.toContentValues() = ContentValues().apply {
        put(COLUMN_ID, id)
        put(COLUMN_CREATED_AT, createdAtMillis)
        put(COLUMN_UPDATED_AT, updatedAtMillis)
        put(COLUMN_BODY, body)
        // El texto de busqueda se calcula al guardar y no al buscar: normalizar cien
        // notas en cada pulsacion del teclado convertiria el filtro en una espera.
        put(COLUMN_SEARCH_TEXT, TextNormalizer.normalize(searchableText()))
        put(COLUMN_TITLE, title)
        put(COLUMN_SUMMARY, summary)
        put(COLUMN_TAGS, tags.joinToString(TAG_SEPARATOR))
        put(COLUMN_STAGE, stage.name)
        put(COLUMN_PROJECT_ID, projectId)
        put(COLUMN_ENRICHED_AT, enrichedAtMillis)
    }

    /**
     * Lo que entra en el filtro literal: el cuerpo, mas lo que el asistente dedujo.
     *
     * Las etiquetas y el resumen tambien buscan porque son palabras que el usuario ve
     * en la nota; si se ensenan y no encuentran, el buscador parece roto.
     */
    private fun Note.searchableText(): String =
        listOfNotNull(body, title, summary, tags.joinToString(" ")).joinToString("\n")

    private fun NoteAttachment.toContentValues(noteId: String) = ContentValues().apply {
        put(COLUMN_ID, id)
        put(COLUMN_NOTE_ID, noteId)
        put(COLUMN_KIND, kind.name)
        put(COLUMN_VALUE, value)
        put(COLUMN_CREATED_AT, createdAtMillis)
    }

    private fun NoteLink.toContentValues() = ContentValues().apply {
        put(COLUMN_PAIR_KEY, pairKey)
        put(COLUMN_FROM_NOTE_ID, fromNoteId)
        put(COLUMN_TO_NOTE_ID, toNoteId)
        put(COLUMN_SCORE, score)
        put(COLUMN_ORIGIN, origin.name)
        put(COLUMN_STATE, state.name)
        put(COLUMN_CREATED_AT, createdAtMillis)
    }

    private fun Project.toContentValues() = ContentValues().apply {
        put(COLUMN_ID, id)
        put(COLUMN_TITLE, title)
        put(COLUMN_CREATED_AT, createdAtMillis)
        put(COLUMN_DONE, if (done) 1 else 0)
    }

    private fun Cursor.toNote() = Note(
        id = getString(getColumnIndexOrThrow(COLUMN_ID)),
        createdAtMillis = getLong(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
        updatedAtMillis = getLong(getColumnIndexOrThrow(COLUMN_UPDATED_AT)),
        body = getString(getColumnIndexOrThrow(COLUMN_BODY)),
        title = getStringOrNull(COLUMN_TITLE),
        summary = getStringOrNull(COLUMN_SUMMARY),
        tags = getString(getColumnIndexOrThrow(COLUMN_TAGS))
            .split(TAG_SEPARATOR)
            .filter { it.isNotBlank() },
        stage = NoteStage.fromStorage(getString(getColumnIndexOrThrow(COLUMN_STAGE))),
        projectId = getStringOrNull(COLUMN_PROJECT_ID),
        enrichedAtMillis = getLongOrNull(COLUMN_ENRICHED_AT),
    )

    private fun Cursor.toAttachment() = NoteAttachment(
        id = getString(getColumnIndexOrThrow(COLUMN_ID)),
        noteId = getString(getColumnIndexOrThrow(COLUMN_NOTE_ID)),
        kind = AttachmentKind.fromStorage(getString(getColumnIndexOrThrow(COLUMN_KIND))),
        value = getString(getColumnIndexOrThrow(COLUMN_VALUE)),
        createdAtMillis = getLong(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
    )

    private fun Cursor.toLink() = NoteLink(
        fromNoteId = getString(getColumnIndexOrThrow(COLUMN_FROM_NOTE_ID)),
        toNoteId = getString(getColumnIndexOrThrow(COLUMN_TO_NOTE_ID)),
        score = getFloat(getColumnIndexOrThrow(COLUMN_SCORE)),
        origin = LinkOrigin.fromStorage(getString(getColumnIndexOrThrow(COLUMN_ORIGIN))),
        state = LinkState.fromStorage(getString(getColumnIndexOrThrow(COLUMN_STATE))),
        createdAtMillis = getLong(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
    )

    private fun Cursor.toProject() = Project(
        id = getString(getColumnIndexOrThrow(COLUMN_ID)),
        title = getString(getColumnIndexOrThrow(COLUMN_TITLE)),
        createdAtMillis = getLong(getColumnIndexOrThrow(COLUMN_CREATED_AT)),
        done = getInt(getColumnIndexOrThrow(COLUMN_DONE)) == 1,
    )

    private fun Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    private fun Cursor.getLongOrNull(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    private companion object {
        /**
         * Las etiquetas se guardan en una sola columna separadas por saltos de linea.
         * No hay tabla aparte porque siempre se leen y se escriben con la nota entera:
         * una tabla de union solo anadiria una consulta a cada lectura.
         */
        const val TAG_SEPARATOR = "\n"
    }
}
