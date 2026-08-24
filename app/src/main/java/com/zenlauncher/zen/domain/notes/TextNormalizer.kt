package com.zenlauncher.zen.domain.notes

import java.text.Normalizer

/**
 * Normalizacion de texto en castellano, pura y sin Android.
 *
 * Es la base de las tres cosas que buscan: el filtro literal, el indice semantico y la
 * deteccion de temas. Vive en el dominio y no en la capa de datos para que las tres
 * usen exactamente el mismo criterio: si el buscador y el indice separaran las palabras
 * de forma distinta, una nota podria encontrarse escribiendo y no aparecer como
 * conexion de si misma.
 */
object TextNormalizer {

    /**
     * Minusculas y sin acentos, con los espacios colapsados.
     *
     * Sin quitar acentos, buscar "aburrimiento" no encontraria lo dictado con tilde ni
     * al reves: al teclear a toda prisa las tildes no se ponen y al dictar siempre
     * aparecen. La enye **se conserva**: en castellano no es una "n" con adorno, y
     * confundir "ano" con "anno" en una nota personal es un fallo que se nota.
     */
    fun normalize(text: String): String {
        val lower = text.lowercase()
        // La enye se aparta antes de descomponer y se repone despues: NFD la partiria
        // en "n" + tilde y el filtro de diacriticos se comeria justo la parte que
        // distingue la letra.
        val guarded = lower.replace(NTILDE, NTILDE_GUARD)
        val decomposed = Normalizer.normalize(guarded, Normalizer.Form.NFD)
        val stripped = decomposed.replace(DIACRITICS, "")
        return stripped.replace(NTILDE_GUARD, NTILDE).replace(WHITESPACE, " ").trim()
    }

    /**
     * Palabras utiles de un texto: normalizadas, sin signos, sin vacias y sin las
     * demasiado cortas.
     *
     * Las palabras vacias se quitan porque son las que mas se repiten en cualquier
     * texto: sin filtrarlas, dos notas que no tienen nada que ver se pareceria un 60%
     * solo por compartir "que", "de" y "el".
     */
    fun tokens(text: String): List<String> =
        normalize(text)
            .split(NON_WORD)
            .filter { it.length >= MIN_TOKEN_LENGTH && it !in STOPWORDS && !it.all(Char::isDigit) }

    /**
     * Raiz aproximada de una palabra.
     *
     * No es un lematizador: recorta los sufijos flexivos mas comunes del castellano
     * para que "aburrirse", "aburrimiento" y "aburrido" caigan en el mismo cubo. Un
     * lematizador de verdad necesitaria un diccionario empotrado, y aqui el coste de
     * equivocarse es una conexion de menos, no un dato mal guardado.
     */
    fun stem(token: String): String {
        if (token.length <= STEM_MIN_LENGTH) return token
        val base = withoutEnclitic(token)
        return stripSuffix(base)
    }

    /**
     * Quita el pronombre pegado al final de un infinitivo o un gerundio.
     *
     * Regresion encontrada probando en el dispositivo: "aburrirnos" se quedaba en
     * "aburrirn" mientras "aburrirse" daba "aburr", asi que dos notas que hablaban
     * exactamente de lo mismo puntuaban 0,00 y no se conectaban nunca. En castellano
     * esto no es un caso raro: "quedarnos", "hacerlo", "decirte" y "planteandolo"
     * aparecen en cuanto alguien escribe como habla.
     *
     * Solo se quita si lo que queda **termina en verbo** (`-ar`, `-er`, `-ir`, `-ando`,
     * `-iendo`). Sin esa guarda, "manos" se comeria su "nos", "menos" su "nos" y
     * "pelo" su "lo", y el indice empezaria a juntar notas por palabras destrozadas.
     */
    private fun withoutEnclitic(token: String): String {
        for (pronoun in ENCLITICS) {
            if (!token.endsWith(pronoun)) continue
            val base = token.dropLast(pronoun.length)
            if (base.length >= STEM_MIN_LENGTH && VERB_ENDINGS.any(base::endsWith)) return base
        }
        return token
    }

    private fun stripSuffix(token: String): String {
        if (token.length <= STEM_MIN_LENGTH) return token
        for (suffix in SUFFIXES) {
            if (token.length - suffix.length >= STEM_MIN_LENGTH && token.endsWith(suffix)) {
                return token.dropLast(suffix.length)
            }
        }
        return token
    }

    /** Atajo: las raices de un texto, en orden de aparicion y con repeticiones. */
    fun stems(text: String): List<String> = tokens(text).map(::stem)

    /**
     * Pronombres que se pegan detras del verbo, de mas largo a mas corto para que
     * "selo" se pruebe antes que "lo".
     */
    private val ENCLITICS = listOf(
        "melas", "melos", "selas", "selos", "telas", "telos", "noslas", "noslos",
        "mela", "melo", "sela", "selo", "tela", "telo", "nosla", "noslo",
        "nos", "les", "las", "los", "me", "te", "se", "le", "la", "lo", "os",
    ).sortedByDescending { it.length }

    private val VERB_ENDINGS = listOf("ando", "iendo", "ar", "er", "ir")

    private const val NTILDE = "ñ"

    /**
     * Marca temporal que ocupa el sitio de la enye durante la descomposicion. Es un
     * caracter de control que no puede aparecer en un texto escrito ni dictado, asi
     * que no puede chocar con el contenido de una nota.
     */
    private const val NTILDE_GUARD = "\u0001"

    private const val MIN_TOKEN_LENGTH = 3
    private const val STEM_MIN_LENGTH = 4

    private val DIACRITICS = Regex("\\p{Mn}+")
    private val WHITESPACE = Regex("\\s+")
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    /**
     * Sufijos ordenados de mas largo a mas corto: probar "amiento" antes que "o" evita
     * que "aburrimiento" se quede en "aburrimient".
     */
    private val SUFFIXES = listOf(
        "amiento", "imiento", "aciones", "iciones",
        "andose", "iendose", "arse", "erse", "irse",
        "aban", "ando", "iendo", "aron", "eron",
        "ado", "ada", "ados", "adas", "ido", "ida", "idos", "idas",
        "ciones", "cion", "mente", "idades", "idad", "ismo", "ista",
        "aba", "ian", "ias", "emos", "amos", "imos",
        "ar", "er", "ir", "es", "os", "as", "an", "en", "a", "o", "e", "s",
    ).sortedByDescending { it.length }

    /**
     * Palabras vacias del castellano. Lista corta a proposito: cada palabra que se
     * quita es una palabra que ya no puede conectar dos notas, y "problema" o "tiempo"
     * dicen mucho de una idea aunque sean frecuentes.
     */
    private val STOPWORDS = setOf(
        "que", "los", "las", "del", "por", "con", "una", "uno", "unos", "unas", "para",
        "como", "pero", "sus", "sin", "sobre", "esta", "este", "esto", "estos", "estas",
        "son", "era", "eran", "fue", "han", "hay", "haber", "muy", "mas", "menos",
        "todo", "toda", "todos", "todas", "otro", "otra", "otros", "otras",
        "cuando", "donde", "porque", "aunque", "entre", "hasta", "desde", "tambien",
        "algo", "alguien", "nada", "nadie", "cada", "ser", "estar", "tener", "hacer",
        "mismo", "misma", "solo", "sola", "asi", "aqui", "alli", "ahora",
        "mis", "tus", "nos", "les",

        // Verbos y muletillas de altisima frecuencia y contenido casi nulo.
        //
        // Medido: sin ellos, las notas que NO deben conectarse puntuaban hasta 0,286
        // —dos notas sin nada que ver que solo compartian "pasa"— mientras que las que
        // si hablan del mismo tema bajaban hasta 0,187. Los dos rangos se solapaban y
        // NINGUN umbral los separaba. Quitandolos, todo lo que no debe conectar cae a
        // cero y queda sitio de sobra para el umbral.
        //
        // Es un sustituto pobre del IDF: en vez de medir que palabras son frecuentes en
        // el cuaderno del usuario —lo que obligaria a recalcular todos los vectores
        // cada vez que se escribe una nota—, se da por hecho que estas lo son siempre.
        "pasa", "pasar", "pasan", "paso", "pasado", "pasa",
        "dice", "decir", "dicen", "dijo", "digo",
        "puede", "poder", "puedo", "pueden", "podemos", "podria",
        "quiere", "querer", "quiero", "quieren",
        "sabe", "saber", "sabemos", "saben",
        "vamos", "van", "vas", "voy",
        "dar", "dan", "das", "doy",
        "poner", "pone", "ponen", "pongo",
        "tiene", "tienen", "tengo", "teniendo",
        "hace", "hacen", "hago", "haciendo", "hecho",
        "vez", "veces", "cosa", "cosas", "tipo", "manera", "forma",
        "ver", "viendo", "visto",
        "ir", "iba", "fui", "fuera",
        "estan", "estoy", "esa", "ese", "eso", "esos", "esas",
        "les", "ella", "ellos", "ellas", "usted",
    )
}
