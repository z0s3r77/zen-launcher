package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.notes.LinkExtractor
import org.junit.Assert.assertEquals
import org.junit.Test

class LinkExtractorTest {

    @Test
    fun `reconoce un enlace con esquema`() {
        assertEquals(
            listOf("https://ejemplo.es/articulo"),
            LinkExtractor.extract("Mira esto https://ejemplo.es/articulo y dime"),
        )
    }

    @Test
    fun `le pone esquema al que empieza por www`() {
        // Sin esquema, abrirlo depende de quien lo lea despues.
        assertEquals(
            listOf("https://www.ejemplo.es"),
            LinkExtractor.extract("apuntar www.ejemplo.es"),
        )
    }

    @Test
    fun `el punto final de la frase no forma parte del enlace`() {
        // "Mira esto: https://ejemplo.es." termina en un punto que es de la frase, y
        // arrastrarlo daba una direccion que no abre.
        assertEquals(
            listOf("https://ejemplo.es"),
            LinkExtractor.extract("Mira esto: https://ejemplo.es."),
        )
        assertEquals(
            listOf("https://ejemplo.es"),
            LinkExtractor.extract("¿Has visto https://ejemplo.es?"),
        )
    }

    @Test
    fun `un parentesis que es del enlace se queda`() {
        // Las direcciones de Wikipedia los llevan de verdad, y recortarlos las rompe.
        assertEquals(
            listOf("https://es.wikipedia.org/wiki/Aburrimiento_(psicologia)"),
            LinkExtractor.extract("https://es.wikipedia.org/wiki/Aburrimiento_(psicologia)"),
        )
    }

    @Test
    fun `un parentesis que cierra la frase no se queda`() {
        assertEquals(
            listOf("https://ejemplo.es/a"),
            LinkExtractor.extract("Lo apunte (ver https://ejemplo.es/a)"),
        )
    }

    @Test
    fun `una frase normal con puntos no se convierte en enlaces`() {
        // Es el motivo de no usar Patterns.WEB_URL: en un cuaderno personal, los falsos
        // positivos serian constantes y cada nota acabaria con adjuntos inventados.
        assertEquals(emptyList<String>(), LinkExtractor.extract("No me vale.Es lo que hay"))
        assertEquals(emptyList<String>(), LinkExtractor.extract("Comprar pan, leche y café."))
        assertEquals(emptyList<String>(), LinkExtractor.extract("Etc.es un asunto largo"))
    }

    @Test
    fun `un dominio con barra si cuenta como enlace`() {
        assertEquals(
            listOf("https://youtube.com/watch?v=abc"),
            LinkExtractor.extract("ver youtube.com/watch?v=abc luego"),
        )
    }

    @Test
    fun `el mismo enlace dos veces se adjunta una sola vez`() {
        // Pegar el mismo enlace arriba y abajo es normal al pensar en voz alta; la nota
        // acabaria con dos adjuntos identicos.
        assertEquals(
            listOf("https://ejemplo.es/a"),
            LinkExtractor.extract("https://ejemplo.es/a ... y otra vez https://ejemplo.es/a"),
        )
    }

    @Test
    fun `varios enlaces distintos salen en orden de aparicion`() {
        assertEquals(
            listOf("https://uno.es/a", "https://dos.es/b"),
            LinkExtractor.extract("primero https://uno.es/a y luego https://dos.es/b"),
        )
    }

    @Test
    fun `un texto sin enlaces no devuelve nada`() {
        assertEquals(
            emptyList<String>(),
            LinkExtractor.extract("Hemos perdido la capacidad de aburrirnos"),
        )
    }
}
