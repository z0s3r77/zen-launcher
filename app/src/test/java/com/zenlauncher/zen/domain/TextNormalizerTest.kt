package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.notes.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La normalizacion es la base de las tres cosas que buscan (filtro literal, indice
 * semantico y temas recurrentes), asi que se fija aqui con detalle: un cambio en el
 * criterio las descoloca a las tres a la vez.
 */
class TextNormalizerTest {

    @Test
    fun `quita acentos y mayusculas para que dictar y teclear encuentren lo mismo`() {
        // Al dictar salen las tildes; al teclear con prisa, no. Las dos formas tienen
        // que llegar al mismo texto o la nota dictada no se encuentra al escribirla.
        assertEquals("aburrimiento", TextNormalizer.normalize("Aburrimiento"))
        assertEquals("aburrimiento", TextNormalizer.normalize("aburrimiento"))
        // Los signos se quedan: normalize solo baja a minusculas y quita diacriticos.
        // Separar palabras es cosa de tokens(), y mezclar las dos responsabilidades
        // dejaria al buscador literal sin poder encontrar una frase entrecomillada.
        assertEquals("¿que perdemos?", TextNormalizer.normalize("¿Qué perdemos?"))
    }

    @Test
    fun `conserva la enye porque ano y anno no son la misma palabra`() {
        // Regresion: NFD parte la enye en "n" + tilde, y el filtro de diacriticos se
        // comia la parte que distingue la letra. En una nota personal, confundir esas
        // dos palabras es un fallo que se lee.
        assertEquals("un año raro", TextNormalizer.normalize("Un AÑO raro"))
        assertTrue(TextNormalizer.normalize("mañana").contains("ñ"))
        assertFalse(TextNormalizer.normalize("mañana") == "manana")
    }

    @Test
    fun `colapsa los espacios y recorta los bordes`() {
        assertEquals("una idea suelta", TextNormalizer.normalize("  una   idea\n\nsuelta  "))
    }

    @Test
    fun `las palabras vacias no entran porque conectarian notas que no tienen nada que ver`() {
        val tokens = TextNormalizer.tokens("La gente ya no sabe aburrirse con el movil")

        assertFalse(tokens.contains("con"))
        assertFalse(tokens.contains("que"))
        assertTrue(tokens.contains("gente"))
        assertTrue(tokens.contains("aburrirse"))
        assertTrue(tokens.contains("movil"))
    }

    @Test
    fun `descarta los signos y las palabras de menos de tres letras`() {
        val tokens = TextNormalizer.tokens("¿Y si el movil, en realidad, no es el problema?")

        assertFalse(tokens.any { it.length < 3 })
        assertFalse(tokens.any { it.contains(",") || it.contains("?") })
        assertTrue(tokens.contains("realidad"))
    }

    @Test
    fun `descarta los numeros sueltos, que no dicen de que va una idea`() {
        assertFalse(TextNormalizer.tokens("apuntar 2026 y 1500 ideas").contains("2026"))
        assertTrue(TextNormalizer.tokens("apuntar 2026 y 1500 ideas").contains("ideas"))
    }

    @Test
    fun `la misma familia de palabras cae en la misma raiz`() {
        // Es lo que permite que "hemos perdido el aburrimiento" conecte con "ya nadie
        // sabe aburrirse" sin compartir ni una palabra exacta.
        val raiz = TextNormalizer.stem("aburrimiento")

        assertEquals(raiz, TextNormalizer.stem("aburrirse"))
        assertEquals(raiz, TextNormalizer.stem("aburrido"))
        assertEquals(raiz, TextNormalizer.stem("aburrir"))
    }

    @Test
    fun `el pronombre pegado al verbo no impide reconocer la palabra`() {
        // Regresion encontrada probando en el dispositivo: "aburrirnos" se quedaba en
        // "aburrirn" mientras "aburrirse" daba "aburr", asi que dos notas que hablaban
        // exactamente de lo mismo puntuaban 0,00 y no se conectaban jamas. En castellano
        // el pronombre pegado aparece en cuanto alguien escribe como habla.
        val raiz = TextNormalizer.stem("aburrirse")

        assertEquals(raiz, TextNormalizer.stem("aburrirnos"))
        assertEquals(raiz, TextNormalizer.stem("aburrirme"))
        assertEquals(TextNormalizer.stem("quedar"), TextNormalizer.stem("quedarnos"))
        assertEquals(TextNormalizer.stem("plantear"), TextNormalizer.stem("plantearlo"))
    }

    @Test
    fun `no se come el final de las palabras que solo lo parecen`() {
        // Sin la guarda de que lo que queda termine en verbo, "manos" perderia su "nos"
        // y "pelo" su "lo", y el indice juntaria notas por palabras destrozadas.
        // "manos" pierde la "s" del plural, que es correcto, pero NO su "nos": si se lo
        // comiera quedaria "ma" y se juntaria con cualquier cosa.
        assertEquals(TextNormalizer.stem("mano"), TextNormalizer.stem("manos"))
        assertFalse(TextNormalizer.stem("manos") == "ma")
        assertEquals("pelo", TextNormalizer.stem("pelo"))
        assertEquals("caso", TextNormalizer.stem("caso"))
        assertFalse(TextNormalizer.stem("regalo") == "rega")
    }

    @Test
    fun `los verbos de relleno no cuentan como contenido`() {
        // Es un sustituto pobre del IDF. Medido: sin quitarlos, dos notas sin nada que
        // ver que solo compartian "pasa" puntuaban 0,286, por encima de pares que si
        // hablaban del mismo tema. Ver `LexicalEmbedderTest`.
        val tokens = TextNormalizer.tokens("Lo que pasa es que quiero ver como hacen esa cosa")

        assertFalse(tokens.contains("pasa"))
        assertFalse(tokens.contains("ver"))
        assertFalse(tokens.contains("cosa"))
        assertFalse(tokens.contains("hacen"))
    }

    @Test
    fun `no recorta tanto como para juntar palabras distintas`() {
        // El recorte agresivo es el riesgo real de un stemmer sin diccionario: si
        // "movil" y "movimiento" cayeran en la misma raiz, las conexiones dejarian de
        // significar nada.
        assertFalse(TextNormalizer.stem("movil") == TextNormalizer.stem("movimiento"))
        assertFalse(TextNormalizer.stem("casa") == TextNormalizer.stem("caso"))
    }

    @Test
    fun `las palabras cortas se dejan intactas`() {
        assertEquals("idea", TextNormalizer.stem("idea"))
        assertEquals("red", TextNormalizer.stem("red"))
    }

    @Test
    fun `un texto vacio no revienta ni inventa palabras`() {
        assertEquals("", TextNormalizer.normalize(""))
        assertEquals(emptyList<String>(), TextNormalizer.tokens("   "))
        assertEquals(emptyList<String>(), TextNormalizer.stems("¿? -- ,,"))
    }
}
