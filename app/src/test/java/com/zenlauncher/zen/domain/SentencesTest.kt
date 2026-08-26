package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.reading.Sentences
import com.zenlauncher.zen.domain.reading.TextSpan
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La frase es la unidad con la que se subraya, asi que si esto se equivoca el usuario
 * marca media frase o dos.
 */
class SentencesTest {

    private fun texto(span: TextSpan, of: String) = of.substring(span.start, span.end)

    private val parrafo =
        "El ser es y el no ser no es. El hombre está condenado a ser libre. " +
            "No hay salida."

    @Test
    fun `coge la frase de debajo del dedo`() {
        val span = Sentences.at(parrafo, parrafo.indexOf("condenado"))

        assertEquals("El hombre está condenado a ser libre.", texto(span, parrafo))
    }

    @Test
    fun `la primera y la ultima frase tambien salen enteras`() {
        assertEquals("El ser es y el no ser no es.", texto(Sentences.at(parrafo, 2), parrafo))
        assertEquals("No hay salida.", texto(Sentences.at(parrafo, parrafo.length - 3), parrafo))
    }

    /**
     * El caso que aparece de verdad en un libro academico. Sin la condicion de que
     * detras venga una mayuscula, "cfr." partiria la frase en dos y subrayar cogeria
     * cuatro letras.
     */
    @Test
    fun `una abreviatura no parte la frase`() {
        val con = "La libertad, cfr. op. cit. pág. 41, no admite excusa. Y ya está."

        val span = Sentences.at(con, con.indexOf("admite"))

        assertEquals("La libertad, cfr. op. cit. pág. 41, no admite excusa.", texto(span, con))
    }

    @Test
    fun `el cierre de comillas entra en la frase, no en la siguiente`() {
        val con = "Dijo: «el hombre está condenado a ser libre.» Y se quedó tan ancho."

        val span = Sentences.at(con, con.indexOf("condenado"))

        assertEquals("Dijo: «el hombre está condenado a ser libre.»", texto(span, con))
    }

    @Test
    fun `una interrogacion cierra frase igual que un punto`() {
        val con = "¿Qué es el ser? Nadie lo sabe."

        assertEquals("¿Qué es el ser?", texto(Sentences.at(con, 5), con))
        assertEquals("Nadie lo sabe.", texto(Sentences.at(con, 20), con))
    }

    @Test
    fun `un parrafo sin puntuacion es una sola frase`() {
        val con = "un texto sin puntuacion ninguna"

        assertEquals(TextSpan(0, con.length), Sentences.at(con, 5))
    }

    @Test
    fun `ampliar anade la frase siguiente`() {
        val primera = Sentences.at(parrafo, 2)

        val ampliada = Sentences.extend(parrafo, primera)

        assertEquals(
            "El ser es y el no ser no es. El hombre está condenado a ser libre.",
            texto(ampliada, parrafo),
        )
    }

    /**
     * Ampliar al final del parrafo no hace nada: un subrayado no salta de bloque, porque
     * al partir la pagina no se podria recortar sin convertirse en dos cosas distintas.
     */
    @Test
    fun `ampliar al final del parrafo se queda como esta`() {
        val ultima = Sentences.at(parrafo, parrafo.length - 3)

        assertEquals(ultima, Sentences.extend(parrafo, ultima))
    }

    @Test
    fun `un parrafo vacio no revienta`() {
        assertEquals(TextSpan(0, 0), Sentences.at("", 0))
    }
}
