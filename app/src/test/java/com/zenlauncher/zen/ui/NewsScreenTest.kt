package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.news.NewsEdition
import com.zenlauncher.zen.domain.news.NewsHeadline
import com.zenlauncher.zen.domain.news.NewsPoint
import com.zenlauncher.zen.presentation.news.NewsScreen
import com.zenlauncher.zen.presentation.news.NewsUiState
import com.zenlauncher.zen.presentation.theme.ZenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class NewsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val AHORA = 1_700_000_000_000L

    private val opened = mutableListOf<String>()
    private var refreshes = 0
    private var backs = 0

    private fun punto(numero: Int) = NewsPoint(
        index = "%02d".format(numero),
        title = "Titular del punto $numero",
        summary = "Resumen del punto $numero",
        url = "https://noticiasdoxa.es/cluster/$numero/",
        section = "Sección $numero",
    )

    private fun portada(
        puntos: Int = 7,
        fetchedAtMillis: Long = AHORA,
    ) = NewsEdition(
        headline = NewsHeadline(
            title = "Lo que pasa hoy en el mundo",
            subtitle = "El parrafo que explica de que va el dia.",
        ),
        points = (1..puntos).map { punto(it) },
        fetchedAtMillis = fetchedAtMillis,
        editionLabel = "2026-08-25",
    )

    /**
     * Baja hasta lo que se quiere mirar.
     *
     * La portada es una lista perezosa: lo que queda por debajo del pliegue **no esta
     * compuesto**, asi que buscarlo sin desplazarse no falla porque este mal pintado,
     * falla porque todavia no existe.
     */
    private fun scrollTo(text: String) {
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(text, substring = true))
    }

    private fun render(state: NewsUiState) {
        composeRule.setContent {
            ZenTheme {
                NewsScreen(
                    state = state,
                    onOpenLink = { opened += it },
                    onRefresh = { refreshes++ },
                    onBack = { backs++ },
                    nowMillis = AHORA,
                    locale = Locale.forLanguageTag("es-ES"),
                )
            }
        }
    }

    @Test
    fun `ensena el titular y su parrafo`() {
        render(NewsUiState(edition = portada(), fromToday = true))

        composeRule.onNodeWithText("Lo que pasa hoy en el mundo").assertIsDisplayed()
        composeRule.onNodeWithText("El parrafo que explica de que va el dia.").assertIsDisplayed()
    }

    /**
     * Los siete, con su numero y su resumen. Siete y no "los que quepan": es lo que se
     * viene a leer, y un punto que no se pinta es una noticia que no existe.
     */
    @Test
    fun `ensena los siete puntos con numero, titulo y resumen`() {
        render(NewsUiState(edition = portada(), fromToday = true))

        (1..7).forEach { numero ->
            scrollTo("Titular del punto $numero")
            composeRule.onNodeWithText("Titular del punto $numero").assertIsDisplayed()
            composeRule.onNodeWithText("Resumen del punto $numero").assertIsDisplayed()
            composeRule.onNodeWithText("%02d".format(numero)).assertIsDisplayed()
        }
    }

    /** Ninguna cifra sin salida: cada punto lleva a su noticia entera. */
    @Test
    fun `tocar un punto abre su enlace`() {
        render(NewsUiState(edition = portada(), fromToday = true))

        composeRule.onNodeWithText("Titular del punto 3").performClick()

        assertEquals(listOf("https://noticiasdoxa.es/cluster/3/"), opened)
    }

    /**
     * Regresion de la regla de toda la aplicacion: un dato viejo con la misma cara que
     * uno de ahora es una mentira. Si lo que se lee no es de hoy, se dice.
     */
    @Test
    fun `una portada de otro dia lo dice`() {
        render(NewsUiState(edition = portada(fetchedAtMillis = AHORA - 86_400_000L)))

        composeRule.onNodeWithText("PORTADA DE OTRO DÍA").assertIsDisplayed()
    }

    @Test
    fun `la portada de hoy no se anuncia como vieja`() {
        render(NewsUiState(edition = portada(), fromToday = true))

        composeRule.onNodeWithText("PORTADA DE OTRO DÍA").assertDoesNotExist()
    }

    /** Sin nada bajado no se deja la pantalla en blanco: se explica por que. */
    @Test
    fun `sin portada lo explica en lugar de quedarse en blanco`() {
        render(NewsUiState(failed = true))

        composeRule.onNodeWithText("No hay ninguna portada", substring = true).assertIsDisplayed()
    }

    @Test
    fun `mientras baja lo dice`() {
        render(NewsUiState(downloading = true))

        composeRule.onNodeWithText("DESCARGANDO LA PORTADA…").assertIsDisplayed()
    }

    @Test
    fun `actualizar avisa a quien lo pidio`() {
        render(NewsUiState(edition = portada(), fromToday = true))

        scrollTo("ACTUALIZAR")
        composeRule.onNodeWithText("ACTUALIZAR").performClick()

        assertEquals(1, refreshes)
    }

    /** De donde sale lo que se lee: Zen no firma como suyo el trabajo de otro. */
    @Test
    fun `dice de que sitio salio la portada`() {
        render(NewsUiState(edition = portada(), fromToday = true))

        scrollTo("LA DOXA")
        composeRule.onNodeWithText("LA DOXA · NOTICIASDOXA.ES").assertIsDisplayed()
    }
}
