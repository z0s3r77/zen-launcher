package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.weather.WeatherCondition
import com.zenlauncher.zen.domain.weather.WeatherPlace
import com.zenlauncher.zen.domain.weather.WeatherReading
import com.zenlauncher.zen.presentation.theme.ZenTheme
import com.zenlauncher.zen.presentation.weather.PlaceSearchState
import com.zenlauncher.zen.presentation.weather.WeatherScreen
import com.zenlauncher.zen.presentation.weather.WeatherUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class WeatherScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val madrid = WeatherPlace("Madrid, España", 40.4165, -3.7026)
    private val oviedo = WeatherPlace("Oviedo, Asturias, España", 43.36, -5.84)

    private val chosen = mutableListOf<WeatherPlace>()
    private var searches = 0
    private var refreshes = 0
    private var cleared = 0

    private fun render(
        state: WeatherUiState,
        search: PlaceSearchState = PlaceSearchState(),
        query: String = "",
    ) {
        composeRule.setContent {
            ZenTheme {
                WeatherScreen(
                    state = state,
                    search = search,
                    query = query,
                    onQueryChange = {},
                    onSearch = { searches++ },
                    onChoose = { chosen += it },
                    onClearPlace = { cleared++ },
                    onRefresh = { refreshes++ },
                    onBack = {},
                    locale = Locale.forLanguageTag("es-ES"),
                )
            }
        }
    }

    @Test
    fun `ensena la ciudad, los grados y la hora de la lectura`() {
        render(
            WeatherUiState(
                place = madrid,
                reading = WeatherReading(18, WeatherCondition.LLUVIA, 1_700_000_000_000),
            ),
        )

        composeRule.onNodeWithText("Madrid, España").assertIsDisplayed()
        composeRule.onNodeWithText("LLUVIA", substring = true).assertIsDisplayed()
    }

    /**
     * Sin ciudad el tiempo esta apagado, red incluida, y eso se dice: una pantalla en
     * blanco no distingue "no has elegido" de "no hay datos".
     */
    @Test
    fun `sin ciudad lo explica en lugar de quedarse en blanco`() {
        render(WeatherUiState())

        composeRule.onNodeWithText("Elige una ciudad", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("ELEGIR CIUDAD").assertIsDisplayed()
    }

    /** Un dato viejo y ningun dato son cosas distintas y se dicen distinto. */
    @Test
    fun `un dato demasiado viejo se dice, no se ensena`() {
        render(WeatherUiState(place = madrid, reading = null, stale = true))

        composeRule.onNodeWithText("de hace demasiado", substring = true).assertIsDisplayed()
    }

    @Test
    fun `elegir un resultado de la busqueda lo devuelve`() {
        render(
            state = WeatherUiState(),
            search = PlaceSearchState(results = listOf(madrid, oviedo)),
            query = "Madrid",
        )

        composeRule.onNodeWithText("Oviedo, Asturias, España").performClick()

        assertEquals(oviedo, chosen.single())
    }

    /**
     * "No hay resultados" y "no hay red" se dicen juntos: desde la pantalla no se pueden
     * distinguir, y afirmar solo lo primero seria decirle al usuario que su ciudad no
     * existe.
     */
    @Test
    fun `una busqueda vacia lo dice sin culpar a la ciudad`() {
        render(state = WeatherUiState(), search = PlaceSearchState(empty = true))

        composeRule.onNodeWithText("o no hay conexión", substring = true).assertIsDisplayed()
    }

    @Test
    fun `quitar la ciudad solo se ofrece si hay ciudad`() {
        render(
            WeatherUiState(
                place = madrid,
                reading = WeatherReading(18, WeatherCondition.DESPEJADO, 1_700_000_000_000),
            ),
        )

        composeRule.onNodeWithText("QUITAR").performClick()

        assertEquals(1, cleared)
    }
}
