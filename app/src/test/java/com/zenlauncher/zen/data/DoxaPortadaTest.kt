package com.zenlauncher.zen.data

import com.zenlauncher.zen.data.news.DoxaPortada
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * El analizador de la portada, sobre una portada de verdad guardada en
 * `test/resources`. Sin red: un test que salga a internet falla el dia que no hay wifi
 * y ademas comprueba el sitio, no el codigo.
 */
class DoxaPortadaTest {

    private val AHORA = 1_700_000_000_000L

    private fun portada(): String = checkNotNull(
        javaClass.classLoader?.getResourceAsStream("portada-doxa.html"),
    ) { "falta la portada de prueba" }.bufferedReader().use { it.readText() }

    @Test
    fun `saca el titular y su parrafo`() {
        val edition = DoxaPortada.parse(portada(), AHORA)
        assertNotNull(edition)
        edition!!

        assertEquals(
            "España afronta crisis en Ceuta por entrada masiva de migrantes y huelga " +
                "indefinida en Airbus",
            edition.headline.title,
        )
        assertTrue(edition.headline.subtitle.startsWith("Tres semanas después"))
    }

    /** Siete: ni los treinta y una historias del sitio ni las seis primeras. */
    @Test
    fun `saca exactamente los siete primeros puntos`() {
        val edition = DoxaPortada.parse(portada(), AHORA)!!

        assertEquals(DoxaPortada.MAX_POINTS, edition.points.size)
        assertEquals("01", edition.points.first().index)
        assertEquals("07", edition.points.last().index)
    }

    @Test
    fun `cada punto trae numero, seccion, titulo, resumen y enlace absoluto`() {
        val first = DoxaPortada.parse(portada(), AHORA)!!.points.first()

        assertEquals("01", first.index)
        assertEquals("Política", first.section)
        assertEquals(
            "Crisis en Ceuta tras entrada masiva de migrantes: hacinamiento, " +
                "enfermedades y tensión sanitaria",
            first.title,
        )
        assertTrue(first.summary.startsWith("Los residentes de Ceuta"))
        // El sitio escribe la ruta; lo que sale de aqui tiene que poder abrirse tal cual.
        assertEquals("https://noticiasdoxa.es/cluster/1095/", first.url)
    }

    /**
     * La portada tiene mas listas de titulares debajo —"Asuntos que siguen"—, y esas no
     * son los puntos. Sin este test, un patron mas suelto se las llevaria por delante y
     * la pantalla ensenaria siete cosas que no son las siete primeras.
     */
    @Test
    fun `no se lleva los titulares de las secciones de abajo`() {
        val edition = DoxaPortada.parse(portada(), AHORA)!!

        assertTrue(edition.points.none { it.url.contains("/asunto/") })
        assertTrue(edition.points.all { it.url.startsWith("https://noticiasdoxa.es/cluster/") })
    }

    @Test
    fun `lee la fecha de la edicion que declara el sitio`() {
        assertEquals("2026-08-25", DoxaPortada.parse(portada(), AHORA)!!.editionLabel)
    }

    @Test
    fun `guarda la hora de descarga que se le pasa`() {
        assertEquals(AHORA, DoxaPortada.parse(portada(), AHORA)!!.fetchedAtMillis)
    }

    /**
     * El dia que el sitio cambie de marcado, esto devuelve null y la pantalla dice que
     * no se pudo leer. Media portada —un titular sin puntos— no se ensena.
     */
    @Test
    fun `sin puntos no hay portada`() {
        val html = """
            <section class="estado"><div class="estado-lede"><h2>Un titular</h2>
            <p class="nota-deck">Una bajada</p></div></section>
        """.trimIndent()

        assertNull(DoxaPortada.parse(html, AHORA))
    }

    @Test
    fun `sin titular no hay portada`() {
        val html = """
            <li class="toca-punto"><span class="banda-num">01</span>
            <h3 class="toca-titulo"><a href="/cluster/1/">Algo</a></h3>
            <p class="toca-porque">Por esto</p></li>
        """.trimIndent()

        assertNull(DoxaPortada.parse(html, AHORA))
    }

    /**
     * Lo que sale de aqui acaba en un `ACTION_VIEW`. Un `href` con otro esquema o de
     * otro dominio se descarta en lugar de entregarselo al sistema: Zen enlaza a la
     * noticia que resumio, no a lo que aparezca en el atributo.
     */
    @Test
    fun `descarta los puntos cuyo enlace no es del sitio`() {
        val edition = DoxaPortada.parse(conEnlaces(), AHORA)

        // Los tres primeros son basura; el unico que sobrevive es el del sitio.
        assertEquals(1, edition!!.points.size)
        assertEquals("https://noticiasdoxa.es/cluster/9/", edition.points.single().url)
    }

    @Test
    fun `traduce las entidades y deja una sola linea`() {
        val html = """
            <section class="estado"><div class="estado-lede">
            <h2>Uno &amp; otro</h2>
            <p class="nota-deck">Con
            salto   de&nbsp;linea&hellip;</p></div></section>
            <li class="toca-punto"><span class="banda-num">01</span>
            <h3 class="toca-titulo"><a href="/cluster/1/">T&iacute;tulo</a></h3>
            <p class="toca-porque">Sube un 5&#37; y baja</p></li>
        """.trimIndent()

        val edition = DoxaPortada.parse(html, AHORA)!!

        assertEquals("Uno & otro", edition.headline.title)
        assertEquals("Con salto de linea…", edition.headline.subtitle)
        assertEquals("Sube un 5% y baja", edition.points.single().summary)
        // Una entidad con nombre que no esta en la tabla se deja tal cual: borrarla se
        // comeria un caracter sin que nadie pueda notarlo.
        assertEquals("T&iacute;tulo", edition.points.single().title)
    }

    private fun conEnlaces(): String = """
        <section class="estado"><div class="estado-lede"><h2>Titular</h2>
        <p class="nota-deck">Bajada</p></div></section>
        <ol class="toca">
        <li class="toca-punto"><span class="banda-num">01</span>
        <h3 class="toca-titulo"><a href="javascript:alert(1)">Uno</a></h3>
        <p class="toca-porque">Resumen</p></li>
        <li class="toca-punto"><span class="banda-num">02</span>
        <h3 class="toca-titulo"><a href="//otrodominio.com/x/">Dos</a></h3>
        <p class="toca-porque">Resumen</p></li>
        <li class="toca-punto"><span class="banda-num">03</span>
        <h3 class="toca-titulo"><a href="https://otrodominio.com/x/">Tres</a></h3>
        <p class="toca-porque">Resumen</p></li>
        <li class="toca-punto"><span class="banda-num">04</span>
        <h3 class="toca-titulo"><a href="/cluster/9/">Cuatro</a></h3>
        <p class="toca-porque">Resumen</p></li>
        </ol>
    """.trimIndent()
}
