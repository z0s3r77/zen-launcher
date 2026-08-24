package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.notes.LexicalEmbedder
import com.zenlauncher.zen.domain.notes.SemanticIndex
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * El motor de nivel 0.
 *
 * Los casos son frases reales del tipo que se apunta en Zen, no palabras sueltas: lo que
 * hay que fijar es que dos ideas parecidas caigan juntas y dos ideas distintas no, y eso
 * solo se ve con frases. El umbral de [LexicalEmbedder.relatedThreshold] esta ajustado
 * sobre estos casos, asi que cambiarlo hara fallar los que deben separarse.
 */
class LexicalEmbedderTest {

    private val embedder = LexicalEmbedder()

    private suspend fun similarity(a: String, b: String): Float =
        SemanticIndex.similarity(embedder.embed(a), embedder.embed(b))

    @Test
    fun `el vector sale con longitud uno`() = runTest {
        // Es lo que permite que comparar sea un producto escalar, y que una nota larga
        // no se parezca a todo solo por tener mas palabras.
        val vector = embedder.embed("Hemos perdido la capacidad de aburrirnos")

        var sum = 0.0
        for (value in vector) sum += value.toDouble() * value

        assertTrue("longitud ${abs(sum)}", abs(sum - 1.0) < 0.0001)
        assertEquals(512, vector.size)
    }

    @Test
    fun `un texto identico se parece a si mismo del todo`() = runTest {
        val texto = "Quiero hacer un video sobre la atencion"

        assertTrue(similarity(texto, texto) > 0.999f)
    }

    @Test
    fun `un texto vacio no revienta y no se parece a nada`() = runTest {
        assertEquals(0f, similarity("", "Una idea cualquiera"), 0.0001f)
        assertEquals(0f, similarity("¿? ...", "Una idea cualquiera"), 0.0001f)
    }

    @Test
    fun `el pronombre pegado al verbo no rompe la conexion`() = runTest {
        // Regresion encontrada en el dispositivo, con estas dos notas exactas: daban
        // 0,00 porque "aburrirnos" no reducia a la misma raiz que "aburrirse".
        val score = similarity(
            "Hemos perdido la capacidad de aburrirnos",
            "Ya nadie sabe aburrirse, el movil se lo come todo",
        )

        assertTrue("parecido $score", score >= embedder.relatedThreshold)
    }

    @Test
    fun `el umbral separa de verdad los pares reales`() = runTest {
        // El umbral esta ajustado sobre estos pares, no a ojo. Si un cambio los solapa,
        // el arreglo es la lista de palabras vacias, no bajar el umbral.
        val deben = listOf(
            "Hemos perdido la capacidad de aburrirnos" to "Ya nadie sabe aburrirse, el movil se lo come todo",
            "Quiero hacer un video sobre por que nos cuesta estar solos" to "La soledad y por que nos cuesta tanto",
            "Quiero aprender Rust" to "Me interesa la programacion de sistemas, quiza Rust",
            "El algoritmo compite por nuestra atencion" to "Las aplicaciones estan disenadas para robarte la atencion",
        )
        val noDeben = listOf(
            "Hemos perdido la capacidad de aburrirnos" to "Comprar pan, leche y pilas para el mando",
            "Lo que pasa es que el tema de la casa es que no" to "Lo que pasa es que el asunto de la moto es que si",
            "Llamar al dentista el martes" to "El algoritmo compite por nuestra atencion",
            "Renovar el seguro del coche" to "Quiero aprender Rust",
        )

        val peorVerdadero = deben.minOf { similarity(it.first, it.second) }
        val peorFalso = noDeben.maxOf { similarity(it.first, it.second) }

        assertTrue("verdadero mas flojo: $peorVerdadero", peorVerdadero >= embedder.relatedThreshold)
        assertTrue("falso mas fuerte: $peorFalso", peorFalso < embedder.relatedThreshold)
    }

    @Test
    fun `dos formas de decir lo mismo se reconocen por la familia de palabras`() = runTest {
        // Es lo que este motor si sabe hacer: no comparten ni una palabra exacta, pero
        // "aburrimiento" y "aburrirse" comparten raiz.
        val score = similarity(
            "Hemos perdido el aburrimiento",
            "Ya nadie sabe aburrirse",
        )

        assertTrue("parecido $score", score >= embedder.relatedThreshold)
    }

    @Test
    fun `dos notas del mismo tema se encuentran`() = runTest {
        val score = similarity(
            "Quiero hacer un video sobre por que nos cuesta estar solos",
            "Un video sobre la soledad y por que nos cuesta",
        )

        assertTrue("parecido $score", score >= embedder.relatedThreshold)
    }

    @Test
    fun `dos notas sin nada que ver no se conectan`() = runTest {
        // Una conexion que no viene a cuento ensena a ignorar la seccion entera.
        val score = similarity(
            "Hemos perdido la capacidad de aburrirnos",
            "Comprar pan, leche y pilas para el mando",
        )

        assertTrue("parecido $score", score < embedder.relatedThreshold)
    }

    @Test
    fun `las palabras vacias no conectan notas ajenas`() = runTest {
        // Sin quitarlas, dos notas que no tienen nada que ver se pareceria un 60% solo
        // por compartir "que", "de" y "el".
        val score = similarity(
            "Lo que pasa es que el tema de la casa es que no",
            "Lo que pasa es que el asunto de la moto es que si",
        )

        assertTrue("parecido $score", score < embedder.relatedThreshold)
    }

    @Test
    fun `repetir una palabra no convierte la nota en ese unico tema`() = runTest {
        // Frecuencia sublineal: sin ella, una nota que repite "video" diez veces se
        // parece a cualquier nota que mencione un video de pasada.
        val obsesiva = "Video video video video video video video video"
        val depasada = "Grabar un video sobre la atencion, el aburrimiento y el movil"
        val otra = "Grabar algo sobre la atencion, el aburrimiento y el movil"

        assertTrue(similarity(depasada, otra) > similarity(depasada, obsesiva))
    }

    @Test
    fun `el orden de las palabras cuenta algo, pero no lo es todo`() = runTest {
        // Los pares de raices contiguas aportan; que aporten poco es deliberado, basta
        // una palabra en medio para romperlos.
        val score = similarity(
            "quiero aprender rust orientado a sistemas",
            "sistemas aprender rust quiero orientado a",
        )

        assertTrue("parecido $score", score > 0.7f)
        assertTrue("parecido $score", score < 0.999f)
    }

    @Test
    fun `el id lleva version del calculo, no solo del motor`() {
        // Este test existe para que cambiar el algoritmo sin subir la version haga
        // ruido. `NoteIndexer` solo reindexa lo que no tiene vector de ESTE id: al
        // arreglar el stemmer de los pronombres pegados, las notas ya indexadas en el
        // dispositivo se quedaron con las raices rotas y no se conectaban con nada,
        // porque el id seguia siendo el mismo. Si tocas tokens, raices, palabras
        // vacias, pesos o dimensiones, sube la version aqui.
        assertEquals("lexico-v2", embedder.id)
        assertEquals(512, embedder.dimensions)
    }

    @Test
    fun `el limite conocido esta fijado aqui a proposito`() = runTest {
        // Dos ideas que hablan de lo mismo sin compartir ni una palabra NO se conectan.
        // No es un fallo que arreglar en este motor: es lo que resolvera el modelo
        // neuronal detras de la misma interfaz. Cuando llegue, este test cambia.
        val score = similarity(
            "Ya no sabemos aburrirnos",
            "El movil se ha comido todos los momentos muertos",
        )

        assertTrue("parecido $score", score < embedder.relatedThreshold)
    }
}
