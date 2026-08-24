package com.zenlauncher.zen.domain.notes

import com.zenlauncher.zen.core.ZenClock

/**
 * Mantiene el indice al dia y propone conexiones.
 *
 * Es lo unico que sabe a la vez del motor de vectores y del almacen. Ni la captura ni
 * las pantallas lo llaman en su camino critico: indexar ocurre **despues**, cuando ya
 * hay una nota guardada, y si no llega a ocurrir la nota se lee igual y el buscador
 * literal sigue encontrandola.
 */
class NoteIndexer(
    private val notes: NotesRepository,
    private val embedder: EmbeddingModel,
    private val clock: ZenClock,
) {

    /**
     * Indexa lo que falte y propone conexiones para lo recien indexado.
     *
     * Por tandas ([BATCH]) y no de golpe: al instalar la version con indice, un cuaderno
     * de mil notas se indexaria entero en el primer arranque. Con tandas, cada visita a
     * Notas adelanta un trozo y la pantalla nunca espera.
     *
     * @return cuantas notas se indexaron en esta pasada.
     */
    suspend fun sync(): Int {
        val pending = notes.notesWithoutEmbedding(embedder.id, BATCH)
        if (pending.isEmpty()) return 0

        pending.forEach { note ->
            notes.putEmbedding(note.id, embedder.id, embedder.embed(note.indexableText()))
        }

        // Las conexiones se calculan despues de indexar la tanda entera: una nota
        // recien indexada tiene que poder encontrar a las otras de su misma tanda.
        val vectors = notes.embeddings(embedder.id)
        val ignored = notes.ignoredPairs()
        pending.forEach { note -> propose(note.id, vectors, ignored) }

        return pending.size
    }

    /**
     * Notas parecidas a un texto suelto, ordenadas por parecido.
     *
     * Sirve para el buscador por significado y para "desarrollar una idea": en los dos
     * casos hay un texto que **todavia no es una nota** y se quiere saber a que se
     * parece de lo ya escrito.
     */
    suspend fun similarTo(text: String, limit: Int = SEARCH_LIMIT): List<ScoredNote> {
        if (text.isBlank()) return emptyList()
        return SemanticIndex.related(
            target = embedder.embed(text),
            candidates = notes.embeddings(embedder.id),
            // Mas permisivo que proponer una conexion: aqui el usuario **ha preguntado**,
            // asi que un resultado flojo es una respuesta regular. Una conexion que
            // nadie pidio y no viene a cuento ensena a ignorar la seccion entera.
            minScore = embedder.relatedThreshold * SEARCH_LENIENCY,
            limit = limit,
        )
    }

    /**
     * Guarda como propuestas las notas que se parecen a esta.
     *
     * Las propuestas nacen en [LinkState.PENDING]: el indice **sugiere**, y conectar dos
     * ideas es del usuario. El almacen ya se encarga de que esto no pise lo que el
     * usuario haya decidido antes sobre la misma pareja.
     */
    private suspend fun propose(
        noteId: String,
        vectors: Map<String, FloatArray>,
        ignored: Set<String>,
    ) {
        val target = vectors[noteId] ?: return
        val related = SemanticIndex.related(
            target = target,
            candidates = vectors,
            minScore = embedder.relatedThreshold,
            limit = MAX_LINKS_PER_NOTE,
            // Una nota fuera de sus propias conexiones: si no, lo primero que
            // encontraria seria ella misma, con un parecido perfecto.
            exclude = setOf(noteId),
        )

        related.forEach { scored ->
            val link = NoteLink(
                fromNoteId = noteId,
                toNoteId = scored.noteId,
                score = scored.score,
                origin = LinkOrigin.SUGGESTED,
                state = LinkState.PENDING,
                createdAtMillis = clock.wallTimeMillis(),
            )
            if (link.pairKey in ignored) return@forEach
            notes.putLink(link)
        }
    }

    private companion object {
        const val BATCH = 50
        const val MAX_LINKS_PER_NOTE = 5
        const val SEARCH_LIMIT = 5

        /** Buscar admite resultados mas flojos que proponer. Ver [similarTo]. */
        const val SEARCH_LENIENCY = 0.6f
    }
}

/**
 * Lo que de una nota entra en el indice.
 *
 * El cuerpo y lo que el asistente dedujo, igual que en el buscador literal: si una
 * etiqueta se ensena en la nota pero no cuenta para las conexiones, dos notas con la
 * misma etiqueta podrian no encontrarse nunca.
 */
internal fun Note.indexableText(): String =
    listOfNotNull(body, title, summary, tags.joinToString(" ").takeIf { it.isNotBlank() })
        .joinToString("\n")
