package com.zenlauncher.zen.fakes

import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.domain.notes.NoteAttachment
import com.zenlauncher.zen.domain.notes.LinkState
import com.zenlauncher.zen.domain.notes.NoteLink
import com.zenlauncher.zen.domain.notes.NotesRepository
import com.zenlauncher.zen.domain.notes.Project
import com.zenlauncher.zen.domain.notes.TextNormalizer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Almacen de notas en memoria.
 *
 * El filtro imita el del almacen real (texto normalizado, cuerpo mas lo que el
 * asistente dedujo) para que los tests de ViewModel no pasen con un criterio que la
 * base de datos no comparte. Lo que fija el criterio de verdad es
 * `SqliteNotesRepositoryTest`, sobre SQLite.
 */
class FakeNotesRepository(
    initial: List<Note> = emptyList(),
) : NotesRepository {

    private val notes = MutableStateFlow(initial.sortedByDescending { it.createdAtMillis })
    private val links = MutableStateFlow<List<NoteLink>>(emptyList())
    private val projects = MutableStateFlow<List<Project>>(emptyList())

    val saved: List<Note> get() = notes.value
    var deleted = mutableListOf<String>()
        private set

    override fun observeNotes(): Flow<List<Note>> = notes

    override fun observeNote(id: String): Flow<Note?> =
        notes.map { all -> all.firstOrNull { it.id == id } }

    override suspend fun note(id: String): Note? = notes.value.firstOrNull { it.id == id }

    override suspend fun save(note: Note) {
        notes.value = (notes.value.filterNot { it.id == note.id } + note)
            .sortedByDescending { it.createdAtMillis }
    }

    override suspend fun delete(id: String) {
        deleted += id
        notes.value = notes.value.filterNot { it.id == id }
    }

    override suspend fun search(query: String): List<Note> {
        val needle = TextNormalizer.normalize(query)
        if (needle.isBlank()) return notes.value
        return notes.value.filter { note ->
            val haystack = listOfNotNull(
                note.body,
                note.title,
                note.summary,
                note.tags.joinToString(" "),
            ).joinToString("\n")
            TextNormalizer.normalize(haystack).contains(needle)
        }
    }

    override suspend fun pendingEnrichment(limit: Int): List<Note> =
        notes.value.filter { it.enrichedAtMillis == null }
            .sortedBy { it.createdAtMillis }
            .take(limit)

    override fun observeLinks(noteId: String): Flow<List<NoteLink>> =
        links.map { all -> all.filter { it.fromNoteId == noteId || it.toNoteId == noteId } }

    override suspend fun putLink(link: NoteLink) {
        val existing = links.value.firstOrNull { it.pairKey == link.pairKey }
        // Misma regla que el almacen real: una propuesta del indice no pisa lo que el
        // usuario ya decidio sobre esa pareja.
        if (existing != null && link.isFreshSuggestion) return
        links.value = links.value.filterNot { it.pairKey == link.pairKey } + link
    }

    override fun observePendingLinks(): Flow<List<NoteLink>> =
        links.map { all -> all.filter { it.state == LinkState.PENDING } }

    override fun observeAcceptedLinks(): Flow<List<NoteLink>> =
        links.map { all -> all.filter { it.state == LinkState.ACCEPTED } }

    override suspend fun ignoredPairs(): Set<String> =
        links.value.filter { it.state == LinkState.IGNORED }
            .map { it.pairKey }
            .toSet()

    private val vectors = mutableMapOf<String, MutableMap<String, FloatArray>>()

    override suspend fun putEmbedding(noteId: String, model: String, vector: FloatArray) {
        vectors.getOrPut(model) { mutableMapOf() }[noteId] = vector
    }

    override suspend fun embeddings(model: String): Map<String, FloatArray> =
        vectors[model].orEmpty()

    override suspend fun notesWithoutEmbedding(model: String, limit: Int): List<Note> {
        val indexed = vectors[model].orEmpty().keys
        return notes.value.filterNot { it.id in indexed }
            .sortedBy { it.createdAtMillis }
            .take(limit)
    }

    override fun observeProjects(): Flow<List<Project>> = projects

    override suspend fun saveProject(project: Project) {
        projects.value = projects.value.filterNot { it.id == project.id } + project
    }

    override suspend fun deleteProject(id: String) {
        projects.value = projects.value.filterNot { it.id == id }
        notes.value = notes.value.map { if (it.projectId == id) it.copy(projectId = null) else it }
    }

    override suspend fun assignToProject(noteId: String, projectId: String?) {
        notes.value = notes.value.map {
            if (it.id == noteId) it.copy(projectId = projectId) else it
        }
    }

    override suspend fun notesInProject(projectId: String): List<Note> =
        notes.value.filter { it.projectId == projectId }
}

/**
 * Almacen de imagenes en memoria: apunta que se pidio guardar y que se pidio borrar,
 * sin tocar disco.
 */
class FakeAttachmentStore(
    private var failing: Boolean = false,
) : com.zenlauncher.zen.domain.notes.AttachmentStore {

    val deletedFor = mutableListOf<String>()
    private var counter = 0

    fun failNext() {
        failing = true
    }

    override suspend fun storeImage(noteId: String, sourceUri: String): NoteAttachment? {
        if (failing) return null
        counter++
        return NoteAttachment(
            id = "img-$counter",
            noteId = noteId,
            kind = com.zenlauncher.zen.domain.notes.AttachmentKind.IMAGE,
            value = "notas/$noteId/$counter.jpg",
            createdAtMillis = 1_000L + counter,
        )
    }

    override suspend fun deleteFor(noteId: String) {
        deletedFor += noteId
    }

    override fun absolutePath(relativePath: String): String = "/datos/$relativePath"
}

/** Atajo para los tests: una nota con lo minimo puesto. */
fun testNote(
    id: String,
    body: String = "Una idea suelta",
    createdAt: Long = 1_000L,
    title: String? = null,
    tags: List<String> = emptyList(),
    enrichedAt: Long? = null,
) = Note(
    id = id,
    createdAtMillis = createdAt,
    updatedAtMillis = createdAt,
    body = body,
    title = title,
    tags = tags,
    enrichedAtMillis = enrichedAt,
)
