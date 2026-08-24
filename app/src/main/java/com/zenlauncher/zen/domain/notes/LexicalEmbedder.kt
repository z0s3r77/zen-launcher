package com.zenlauncher.zen.domain.notes

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Motor de nivel 0: vectores de texto sin ningun modelo, en Kotlin puro.
 *
 * Reparte las raices del texto en un vector de tamano fijo con el truco del hashing.
 * Cuesta microsegundos, no pesa nada en el APK y se prueba entero en la JVM.
 *
 * **Lo que sabe hacer**: relacionar notas que comparten vocabulario o familia de
 * palabras, aunque esten escritas distinto. "Hemos perdido el aburrimiento" y "ya nadie
 * sabe aburrirse" caen juntas porque comparten raiz (ver [TextNormalizer.stem]).
 *
 * **Lo que no sabe hacer, y hay que decirlo**: relacionar dos ideas que no comparten
 * ni una palabra. "Aburrirse" y "los momentos muertos" hablan de lo mismo y para este
 * motor son desconocidas. Eso lo resuelve un modelo neuronal detras de la misma
 * interfaz; hasta entonces, el limite es este y no se disimula.
 */
class LexicalEmbedder : EmbeddingModel {

    override val id: String = ID

    override val dimensions: Int = DIMENSIONS

    /**
     * Ajustado con datos, no a ojo.
     *
     * Estuvo en 0,32 y **no se disparaba casi nunca**: probando en el dispositivo, dos
     * notas que hablaban claramente del mismo tema se quedaban en 0,21 y no llegaban a
     * proponerse. Con notas reales —mas largas que un par de palabras— compartir un
     * termino distintivo da un coseno bajo, porque el resto del vocabulario no coincide.
     *
     * Medido sobre los pares de `LexicalEmbedderTest`: los que deben conectarse van de
     * 0,21 hacia arriba y los que no, todos a 0,00. El umbral se pone en medio con
     * margen por los dos lados. Si vuelve a haber solapamiento, el arreglo es la lista
     * de palabras vacias —no bajar esto—: una conexion que no viene a cuento ensena a
     * ignorar la seccion entera, mientras que una que falta solo deja una nota sin
     * compania.
     */
    override val relatedThreshold: Float = 0.18f

    override suspend fun embed(text: String): FloatArray {
        val stems = TextNormalizer.stems(text)
        val vector = FloatArray(DIMENSIONS)
        if (stems.isEmpty()) return vector

        // Frecuencia bruta primero: hay que saber cuantas veces sale cada raiz antes de
        // poder amortiguar las que se repiten.
        val counts = stems.groupingBy { it }.eachCount()

        counts.forEach { (stem, count) ->
            // Frecuencia sublineal: que una palabra salga diez veces no hace la nota
            // diez veces mas sobre ese tema. Sin esto, una nota que repite una palabra
            // se parece a todo lo que la mencione una vez.
            add(vector, stem, weight = 1f + ln(count.toFloat()))
        }

        // Pares de raices contiguas: "aprender rust" dice algo que "aprender" y "rust"
        // por separado no dicen. Pesan menos que las palabras sueltas porque son mas
        // fragiles: basta una palabra en medio para que el par no aparezca.
        stems.zipWithNext { first, second ->
            add(vector, "$first $second", weight = BIGRAM_WEIGHT)
        }

        return normalized(vector)
    }

    /**
     * Suma la raiz en su cubo, con signo.
     *
     * El signo sale de un segundo hash. Dos raices distintas que caigan en el mismo
     * cubo —pasa, con miles de palabras y cientos de cubos— se suman a ciegas y fingen
     * un parecido que no existe; con signo, la mitad de esas colisiones se restan y el
     * error se cancela en lugar de acumularse.
     */
    private fun add(vector: FloatArray, token: String, weight: Float) {
        val hash = token.hashCode()
        val bucket = (hash and Int.MAX_VALUE) % DIMENSIONS
        val sign = if (token.reversed().hashCode() and 1 == 0) 1f else -1f
        vector[bucket] += sign * weight
    }

    /**
     * Longitud 1, para que comparar dos vectores sea un producto escalar y una nota
     * larga no se parezca a todo solo por tener mas palabras.
     */
    private fun normalized(vector: FloatArray): FloatArray {
        var sum = 0.0
        for (value in vector) sum += value.toDouble() * value
        val length = sqrt(sum).toFloat()
        if (length == 0f) return vector
        for (index in vector.indices) vector[index] /= length
        return vector
    }

    private companion object {
        /**
         * El id lleva version, y **hay que subirla al tocar como se calcula el vector**.
         *
         * No identifica a la familia de motores sino a *este* calculo exacto: tokens,
         * raices, palabras vacias, pesos y dimensiones. `NoteIndexer` solo reindexa lo
         * que no tiene vector **de este id**, asi que cambiar el algoritmo sin subir la
         * version deja los vectores viejos ahi para siempre, calculados con las reglas
         * de antes y comparandose con los nuevos.
         *
         * Pasó de verdad: al arreglar el stemmer de los pronombres pegados, en el
         * dispositivo las notas ya indexadas se quedaron con las raices rotas y no se
         * conectaban con nada. Subir la version es lo unico que las rehace.
         *
         * v2: pronombres enclitricos y palabras de relleno (ver [TextNormalizer]).
         */
        const val ID = "lexico-v2"

        /**
         * 512 cubos: con menos, dos palabras sin relacion caen juntas demasiado a
         * menudo y aparecen conexiones inventadas; con mas, cada nota ocupa el doble en
         * la base de datos sin que las conexiones mejoren de forma apreciable.
         */
        const val DIMENSIONS = 512

        const val BIGRAM_WEIGHT = 0.5f
    }
}
