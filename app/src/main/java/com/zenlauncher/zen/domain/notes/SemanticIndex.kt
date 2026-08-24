package com.zenlauncher.zen.domain.notes

/**
 * Comparar vectores y quedarse con lo que de verdad se parece.
 *
 * Puro y sin estado: recibe los vectores ya calculados y devuelve un orden. Fuerza
 * bruta sobre todas las notas a proposito —nada de estructuras de vecinos aproximados—
 * porque con unos miles de notas son unos pocos millones de multiplicaciones, y un
 * indice aproximado anadiria un monton de codigo para ahorrar milisegundos que nadie
 * percibe.
 */
object SemanticIndex {

    /**
     * Semejanza entre dos vectores **ya normalizados**: su producto escalar.
     *
     * No se vuelve a dividir por las longitudes porque [EmbeddingModel.embed] promete
     * devolverlos de longitud 1. Se recorta a 0..1: los errores de coma flotante sacan
     * valores como 1,0000001, y un parecido mayor que "identico" no significa nada.
     */
    fun similarity(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var sum = 0f
        for (index in a.indices) sum += a[index] * b[index]
        return sum.coerceIn(0f, 1f)
    }

    /**
     * Lo mas parecido a [target], de mayor a menor.
     *
     * @param exclude ids que no se comparan. Es como una nota se queda fuera de sus
     *   propias conexiones: sin esto, lo primero que encontraria cada nota seria ella
     *   misma, con un parecido perfecto.
     */
    fun related(
        target: FloatArray,
        candidates: Map<String, FloatArray>,
        minScore: Float,
        limit: Int = DEFAULT_LIMIT,
        exclude: Set<String> = emptySet(),
    ): List<ScoredNote> =
        candidates.asSequence()
            .filter { it.key !in exclude }
            .map { (id, vector) -> ScoredNote(id, similarity(target, vector)) }
            .filter { it.score >= minScore }
            // Desempate por id para que dos notas con el mismo parecido salgan siempre
            // en el mismo orden: una lista que baila entre aperturas parece un fallo.
            .sortedWith(compareByDescending<ScoredNote> { it.score }.thenBy { it.noteId })
            .take(limit)
            .toList()

    private const val DEFAULT_LIMIT = 5
}

/** Una nota y cuanto se parece a lo que se estaba buscando. */
data class ScoredNote(
    val noteId: String,
    val score: Float,
)
