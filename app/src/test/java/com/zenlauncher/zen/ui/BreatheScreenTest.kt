package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.presentation.breathe.BreatheScreen
import com.zenlauncher.zen.presentation.theme.ZenTheme
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class BreatheScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var backs = 0

    @Before
    fun setUp() {
        // El ejercicio corre sobre el reloj de fotogramas y no para nunca por si solo
        // hasta el minuto: con el avance automatico, la primera comprobacion se quedaria
        // esperando a que la pantalla estuviera "quieta". Aqui el tiempo lo mueve el test.
        composeRule.mainClock.autoAdvance = false

        composeRule.setContent {
            ZenTheme {
                BreatheScreen(onBack = { backs++ })
            }
        }
    }

    /** Un fotograma. El cambio de estado de uno se pinta en el siguiente. */
    private val frame = 16L

    private fun empezar() {
        composeRule.onNodeWithText("Empezar").performClick()
        // Este fotograma es el que fija el origen del cronometro.
        composeRule.mainClock.advanceTimeBy(frame)
    }

    private fun respirar(millis: Long) {
        composeRule.mainClock.advanceTimeBy(millis)
        composeRule.mainClock.advanceTimeBy(frame)
    }

    @Test
    fun `en reposo no hay cifra de fase ni ejercicio corriendo`() {
        // Un "00" quieto junto a PREPARADO seria un dato con nada detras: la cifra solo
        // existe mientras hay una orden que cumplir.
        composeRule.onNodeWithText("Preparado").assertIsDisplayed()
        composeRule.onNodeWithText("60 S").assertIsDisplayed()
        composeRule.onNodeWithText("Empezar").assertIsDisplayed().assertHasClickAction()
        composeRule.onNodeWithText("6 POR MINUTO").assertIsDisplayed()
    }

    @Test
    fun `guia inspirar, luego espirar, y termina al minuto`() {
        empezar()

        respirar(500)
        composeRule.onNodeWithText("Inspira").assertIsDisplayed()
        composeRule.onNodeWithText("04").assertIsDisplayed()

        respirar(2_000)
        composeRule.onNodeWithText("Inspira").assertIsDisplayed()
        composeRule.onNodeWithText("02").assertIsDisplayed()

        // Pasado el cuarto segundo se suelta el aire, y el tramo es mas largo: seis.
        respirar(3_000)
        composeRule.onNodeWithText("Espira").assertIsDisplayed()
        composeRule.onNodeWithText("05").assertIsDisplayed()

        respirar(60_000)
        composeRule.onNodeWithText("Hecho").assertIsDisplayed()
        composeRule.onNodeWithText("Otra vez").assertIsDisplayed()
        composeRule.onNodeWithText("00 S").assertIsDisplayed()
    }

    @Test
    fun `el ciclo en curso se lee en la cabecera solo mientras corre`() {
        empezar()

        respirar(500)
        composeRule.onNodeWithText("CICLO 1 / 6").assertIsDisplayed()

        respirar(10_000)
        composeRule.onNodeWithText("CICLO 2 / 6").assertIsDisplayed()
    }

    @Test
    fun `detener vuelve al principio en vez de dejar la curva a medias`() {
        // Regresion: parar dejaba el ejercicio congelado a mitad, y al volver a la
        // pantalla la figura a medias se leia como algo pendiente de terminar.
        empezar()
        respirar(7_000)
        composeRule.onNodeWithText("Espira").assertIsDisplayed()

        composeRule.onNodeWithText("Detener").performClick()
        composeRule.mainClock.advanceTimeBy(frame)

        composeRule.onNodeWithText("Preparado").assertIsDisplayed()
        composeRule.onNodeWithText("60 S").assertIsDisplayed()
        composeRule.onNodeWithText("Empezar").assertIsDisplayed()
    }

    @Test
    fun `el tiempo no lo cuenta la escala de animacion del sistema`() {
        // Regresion: con un `Animatable` de 60 000 ms, quien tiene las animaciones
        // apagadas en opciones de desarrollador terminaba el minuto en cero segundos.
        // El cronometro va por fotogramas, asi que un solo fotograma no lo acaba.
        empezar()
        respirar(0)

        composeRule.onNodeWithText("Inspira").assertIsDisplayed()
        composeRule.onNodeWithText("Hecho").assertDoesNotExist()
    }

    @Test
    fun `la pantalla tiene su propia salida`() {
        // Zen es la pantalla de inicio: sin flecha, con la barra de gestos oculta, aqui
        // solo se sale por un gesto que no se ve.
        composeRule.onNodeWithContentDescription("Volver")
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, backs)
    }
}
