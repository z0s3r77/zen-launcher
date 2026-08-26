package com.zenlauncher.zen.data

import com.zenlauncher.zen.data.news.NewsJson
import com.zenlauncher.zen.domain.news.NewsEdition
import com.zenlauncher.zen.domain.news.NewsHeadline
import com.zenlauncher.zen.domain.news.NewsPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `org.json` es una clase de Android, asi que esto necesita Robolectric aunque el codigo
 * no toque ni pantalla ni ficheros.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NewsJsonTest {

    private val portada = NewsEdition(
        headline = NewsHeadline(
            title = "España afronta crisis en Ceuta",
            subtitle = "Tres semanas después de la entrada, la ciudad sigue en crisis.",
        ),
        points = listOf(
            NewsPoint(
                index = "01",
                title = "Crisis en Ceuta: hacinamiento y tensión sanitaria",
                summary = "Los residentes ven alterada su vida diaria.",
                url = "https://noticiasdoxa.es/cluster/1095/",
                section = "Política",
            ),
            NewsPoint(
                index = "02",
                title = "Huelga indefinida en Airbus",
                summary = "Más de 11.000 empleados dejan de trabajar.",
                url = "https://noticiasdoxa.es/cluster/1132/",
            ),
        ),
        fetchedAtMillis = 1_700_000_000_000L,
        editionLabel = "2026-08-25",
    )

    @Test
    fun `lo escrito se vuelve a leer igual`() {
        assertEquals(portada, NewsJson.decode(NewsJson.encode(portada)))
    }

    /** Sin seccion es un caso normal: no todos los puntos la traen. */
    @Test
    fun `un punto sin seccion se lee sin seccion, no con una vacia`() {
        val leida = NewsJson.decode(NewsJson.encode(portada))!!

        assertNull(leida.points[1].section)
    }

    /**
     * Unos bytes corruptos en el fichero de preferencias no pueden dejar el telefono
     * sin pantalla de inicio: se lee null y se vuelve a bajar.
     */
    @Test
    fun `una cadena que no es json no revienta`() {
        assertNull(NewsJson.decode("{esto no es json"))
        assertNull(NewsJson.decode(""))
    }

    /** Media portada guardada no es una portada: sin puntos, se baja de nuevo. */
    @Test
    fun `sin puntos se lee como si no hubiera nada`() {
        assertNull(NewsJson.decode("""{"v":1,"titulo":"Algo","puntos":[]}"""))
    }

    /** Lo escrito por un formato que no se reconoce se tira; solo cuesta una descarga. */
    @Test
    fun `otra version del formato se descarta`() {
        val json = NewsJson.encode(portada).replace("\"v\":1", "\"v\":99")

        assertNull(NewsJson.decode(json))
    }
}
