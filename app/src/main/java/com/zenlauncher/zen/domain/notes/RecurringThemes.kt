package com.zenlauncher.zen.domain.notes

/** Una raiz que aparece en varias notas distintas. */
data class RecurringWord(val stem: String, val noteCount: Int)

/** Notas que estan conectadas entre si, directa o transitivamente. */
data class RecurringCluster(val noteIds: Set<String>)

/**
 * Detecta patrones a partir de datos que Zen ya calcula —raices normalizadas, grafo de
 * [NoteLink]—, nunca de afirmaciones psicologicas sobre el usuario. Pura y sin Android,
 * igual que [StatsCalculator][com.zenlauncher.zen.domain.stats.StatsCalculator].
 */
object RecurringThemes {

    /**
     * Raices que aparecen en al menos [minNotes] notas distintas, de mas a menos.
     *
     * Cuenta **notas**, no repeticiones: una raiz que sale diez veces en la misma nota
     * cuenta una, porque lo que importa es a cuantas ideas distintas vuelve el usuario,
     * no cuanto se repite dentro de una.
     */
    fun words(notes: List<Note>, minNotes: Int = DEFAULT_MIN_NOTES): List<RecurringWord> {
        val counts = mutableMapOf<String, Int>()
        notes.forEach { note ->
            TextNormalizer.stems(note.indexableText()).toSet().forEach { stem ->
                counts[stem] = (counts[stem] ?: 0) + 1
            }
        }
        return counts.filterValues { it >= minNotes }
            .map { (stem, count) -> RecurringWord(stem, count) }
            .sortedWith(compareByDescending<RecurringWord> { it.noteCount }.thenBy { it.stem })
    }

    /**
     * Grupos de notas conectadas entre si por conexiones **aceptadas**, de al menos
     * [minSize] notas.
     *
     * Solo cuentan las que el usuario confirmo, no las sugerencias sin responder: un
     * clúster construido con propuestas que nadie ha mirado todavia seria una suposicion
     * sobre una suposicion.
     */
    fun clusters(notes: List<Note>, links: List<NoteLink>, minSize: Int = DEFAULT_MIN_CLUSTER): List<RecurringCluster> {
        val noteIds = notes.mapTo(mutableSetOf()) { it.id }
        val adjacency = mutableMapOf<String, MutableSet<String>>()
        links.asSequence()
            .filter { it.state == LinkState.ACCEPTED }
            .filter { it.fromNoteId in noteIds && it.toNoteId in noteIds }
            .forEach { link ->
                adjacency.getOrPut(link.fromNoteId) { mutableSetOf() } += link.toNoteId
                adjacency.getOrPut(link.toNoteId) { mutableSetOf() } += link.fromNoteId
            }

        val visited = mutableSetOf<String>()
        val result = mutableListOf<RecurringCluster>()
        adjacency.keys.forEach { start ->
            if (start in visited) return@forEach
            val component = component(start, adjacency, visited)
            if (component.size >= minSize) result += RecurringCluster(component)
        }
        return result.sortedBy { it.noteIds.min() }
    }

    /** Componente conexa a partir de [start], por anchura. */
    private fun component(
        start: String,
        adjacency: Map<String, Set<String>>,
        visited: MutableSet<String>,
    ): Set<String> {
        val component = mutableSetOf<String>()
        val pending = ArrayDeque<String>()
        pending.add(start)
        visited += start
        while (pending.isNotEmpty()) {
            val current = pending.removeFirst()
            component += current
            adjacency[current].orEmpty().forEach { neighbor ->
                if (visited.add(neighbor)) pending.add(neighbor)
            }
        }
        return component
    }

    private const val DEFAULT_MIN_NOTES = 5
    private const val DEFAULT_MIN_CLUSTER = 3
}
