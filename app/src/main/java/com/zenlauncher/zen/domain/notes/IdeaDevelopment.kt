package com.zenlauncher.zen.domain.notes

/**
 * Lo que se le devuelve a quien esta desarrollando una idea.
 *
 * Cada campo es independiente y **puede venir vacio**: la pantalla solo pinta la
 * seccion cuya lista o pregunta no esta vacia. Nunca se rellena un campo con algo
 * generico solo para que la seccion tenga contenido.
 */
data class IdeaPrompts(
    val centralQuestion: String? = null,
    val approaches: List<String> = emptyList(),
    val questions: List<String> = emptyList(),
)

/**
 * Genera preguntas y enfoques a partir de una idea suelta.
 *
 * Misma frontera que [EmbeddingModel]: hoy [HeuristicIdeaDevelopmentModel] no razona,
 * solo ancla plantillas a senales reales del texto; el dia que haya un LLM local detras,
 * cambia la implementacion y no quien la llama.
 */
interface IdeaDevelopmentModel {
    fun generate(idea: String, relatedCount: Int): IdeaPrompts
}

/**
 * Heuristica pura: sin red, sin modelo, sin relleno generico.
 *
 * Cada categoria de [CATEGORIES] solo aparece si alguna de sus raices gatillo esta de
 * verdad en la idea, y cada pregunta de [questions] solo si la senal que la condiciona
 * esta presente. Una idea sin ninguna raiz reconocida y sin notas relacionadas devuelve
 * [IdeaPrompts] vacio entero: es preferible no decir nada a inventar una pregunta que
 * serviria igual para cualquier idea.
 */
class HeuristicIdeaDevelopmentModel : IdeaDevelopmentModel {

    override fun generate(idea: String, relatedCount: Int): IdeaPrompts {
        if (idea.isBlank()) return IdeaPrompts()
        return IdeaPrompts(
            centralQuestion = centralQuestion(idea),
            approaches = approaches(idea),
            questions = questions(idea, relatedCount),
        )
    }

    /**
     * Si la idea ya termina en interrogacion, no hay nada que anadir: repetir lo que el
     * usuario ya escribio no es una pregunta central, es un eco.
     */
    private fun centralQuestion(idea: String): String? {
        if (idea.trim().endsWith("?")) return null
        val dominant = dominantStem(TextNormalizer.stems(idea)) ?: return null
        return "¿Qué papel juega «$dominant» en esta idea?"
    }

    /** La raiz que mas se repite; a igualdad, la que aparece primero. */
    private fun dominantStem(stems: List<String>): String? {
        if (stems.isEmpty()) return null
        val counts = stems.groupingBy { it }.eachCount()
        val max = counts.values.max()
        return stems.first { counts.getValue(it) == max }
    }

    private fun approaches(idea: String): List<String> {
        val stems = TextNormalizer.stems(idea).toSet()
        return CATEGORIES.filter { category -> category.triggerStems.any { it in stems } }
            .map { it.label }
    }

    private fun questions(idea: String, relatedCount: Int): List<String> {
        val result = mutableListOf<String>()

        // Ya la planteo como pregunta: lo que falta no es formularla, es saber que
        // respuesta busca.
        if (idea.trim().endsWith("?")) {
            result += "Ya la planteaste como pregunta: ¿qué respuesta esperas encontrar?"
        }

        // La negacion se busca en las palabras tal cual, no en las raices: "sin" es una
        // palabra vacia para el indice pero aqui es justo la senal que se busca.
        if (hasNegation(idea)) {
            result += "¿Qué te estás dejando fuera al centrarte en lo que no quieres?"
        }

        if (relatedCount >= RELATED_QUESTION_THRESHOLD) {
            result += "Ya tienes $relatedCount notas relacionadas: ¿qué tienen en común?"
        }

        return result
    }

    private fun hasNegation(idea: String): Boolean {
        val words = TextNormalizer.normalize(idea).split(NON_WORD)
        return words.any { it in NEGATION_WORDS }
    }

    private class ApproachCategory(val label: String, triggers: List<String>) {
        /** Raices, no palabras: para que "programar" dispare igual que "programacion". */
        val triggerStems: Set<String> =
            triggers.map { TextNormalizer.stem(TextNormalizer.normalize(it)) }.toSet()
    }

    private companion object {
        val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

        val NEGATION_WORDS = setOf("no", "nunca", "jamas", "tampoco", "nadie", "ningun", "ninguna", "sin")

        const val RELATED_QUESTION_THRESHOLD = 3

        val CATEGORIES = listOf(
            ApproachCategory(
                "Tecnológico",
                listOf(
                    "aplicacion", "codigo", "software", "algoritmo", "dato", "internet",
                    "digital", "programa", "programar", "tecnologia", "ordenador", "robot",
                    "automatizar",
                ),
            ),
            ApproachCategory(
                "Psicológico",
                listOf(
                    "miedo", "ansiedad", "emocion", "mente", "pensamiento", "habito",
                    "motivacion", "autoestima", "estres", "animo", "confianza",
                ),
            ),
            ApproachCategory(
                "Social",
                listOf(
                    "amigo", "familia", "comunidad", "grupo", "relacion", "equipo",
                    "sociedad", "vecino", "gente",
                ),
            ),
            ApproachCategory(
                "Creativo",
                listOf(
                    "arte", "diseño", "musica", "escribir", "pintura", "creatividad",
                    "historia", "dibujo", "cancion",
                ),
            ),
        )
    }
}
