package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.presentation.notes.NotesScreen
import com.zenlauncher.zen.presentation.notes.NotesUiState
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
class NotesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var quickNotes = 0
    private val opened = mutableListOf<Note>()
    private var backs = 0

    private val ahora = 1_700_000_000_000

    private fun nota(
        id: String,
        body: String = "Una idea suelta",
        title: String? = null,
        createdAt: Long = ahora,
    ) = Note(
        id = id,
        createdAtMillis = createdAt,
        updatedAtMillis = createdAt,
        body = body,
        title = title,
    )

    private fun render(state: NotesUiState) {
        composeRule.setContent {
            ZenTheme {
                NotesScreen(
                    state = state,
                    query = state.query,
                    nowMillis = ahora,
                    onQueryChange = {},
                    onQuickNote = { quickNotes++ },
                    onOpenNote = { opened += it },
                    onBack = { backs++ },
                    locale = Locale("es", "ES"),
                )
            }
        }
    }

    @Test
    fun `capturar esta arriba del todo y a un toque`() {
        // Es lo unico de esta pantalla que tiene prisa: una idea se apunta en el
        // momento o se pierde. Buscar y releer se hacen con calma.
        render(NotesUiState(notes = listOf(nota("a")), total = 1, loading = false))

        composeRule.onNodeWithText("Nota rápida").assertIsDisplayed().assertHasClickAction().performClick()

        assertEquals(1, quickNotes)
    }

    @Test
    fun `no ensena desarrollar una idea mientras no exista`() {
        // Una fila que no lleva a ninguna parte ensena a desconfiar de las que si
        // funcionan: es la razon por la que se quito el PRONTO de la pantalla de inicio.
        render(NotesUiState(notes = listOf(nota("a")), total = 1, loading = false))

        composeRule.onNodeWithText("Desarrollar una idea").assertDoesNotExist()
    }

    @Test
    fun `una nota sin titulo generado se lee por su primera linea`() {
        // Sin titulo no se pinta un hueco ni un "Sin titulo": un marcador de ausencia
        // solo informa de que a la aplicacion le falta algo.
        render(
            NotesUiState(
                notes = listOf(nota("a", body = "La gente ya no sabe aburrirse\nY eso importa")),
                total = 1,
                loading = false,
            ),
        )

        composeRule.onNodeWithText("La gente ya no sabe aburrirse").assertIsDisplayed()
    }

    @Test
    fun `el titulo generado sustituye a la primera linea cuando existe`() {
        render(
            NotesUiState(
                notes = listOf(nota("a", body = "La gente ya no sabe aburrirse", title = "El aburrimiento")),
                total = 1,
                loading = false,
            ),
        )

        composeRule.onNodeWithText("El aburrimiento").assertIsDisplayed()
        composeRule.onNodeWithText("La gente ya no sabe aburrirse").assertDoesNotExist()
    }

    @Test
    fun `tocar una nota la abre`() {
        val nota = nota("a", body = "Una idea concreta")
        render(NotesUiState(notes = listOf(nota), total = 1, loading = false))

        composeRule.onNodeWithText("Una idea concreta").performClick()

        assertEquals(listOf(nota), opened)
    }

    @Test
    fun `sin notas lo dice, y no dice que la busqueda fallara`() {
        render(NotesUiState(total = 0, loading = false))

        composeRule.onNodeWithText("TODAVÍA NO HAY NOTAS").assertIsDisplayed()
        composeRule.onNodeWithText("NADA CON ESAS PALABRAS").assertDoesNotExist()
    }

    @Test
    fun `una busqueda sin resultados se dice distinto de una biblioteca vacia`() {
        render(NotesUiState(query = "aburrimiento", notes = emptyList(), total = 4, loading = false))

        composeRule.onNodeWithText("NADA CON ESAS PALABRAS").assertIsDisplayed()
        composeRule.onNodeWithText("TODAVÍA NO HAY NOTAS").assertDoesNotExist()
    }

    @Test
    fun `el rotulo de la lista cambia segun se este buscando o no`() {
        render(NotesUiState(notes = listOf(nota("a")), total = 1, loading = false))
        composeRule.onNodeWithText("IDEAS RECIENTES").assertIsDisplayed()
    }

    @Test
    fun `buscando, la lista se rotula como resultados`() {
        render(
            NotesUiState(query = "idea", notes = listOf(nota("a")), total = 3, loading = false),
        )

        composeRule.onNodeWithText("RESULTADOS").assertIsDisplayed()
        composeRule.onNodeWithText("IDEAS RECIENTES").assertDoesNotExist()
    }

    @Test
    fun `varias notas relacionadas a la vez no revientan la lista`() {
        // Regresion encontrada en el dispositivo: la clave de estos elementos quedo como
        // texto literal en vez de interpolada, asi que las dos filas compartian clave y
        // LazyColumn lanzaba IllegalArgumentException. Al ser Zen el launcher, eso deja
        // el telefono sin pantalla de inicio. Este test renderiza DOS, que es el minimo
        // para que dos claves puedan chocar.
        render(
            NotesUiState(
                query = "idea",
                notes = listOf(nota("a", body = "Una idea")),
                related = listOf(
                    nota("b", body = "Otra parecida"),
                    nota("c", body = "Y una tercera parecida"),
                ),
                total = 3,
                loading = false,
            ),
        )

        composeRule.onNodeWithText("TAMBIÉN RELACIONADAS").assertIsDisplayed()
        composeRule.onNodeWithText("Otra parecida").assertIsDisplayed()
        composeRule.onNodeWithText("Y una tercera parecida").assertIsDisplayed()
    }

    @Test
    fun `varias notas con propuestas a la vez tampoco revientan`() {
        render(
            NotesUiState(
                notes = listOf(nota("a", body = "Una idea")),
                withSuggestions = listOf(
                    nota("b", body = "Con propuesta"),
                    nota("c", body = "Con otra propuesta"),
                ),
                total = 3,
                loading = false,
            ),
        )

        composeRule.onNodeWithText("POSIBLES CONEXIONES").assertIsDisplayed()
        composeRule.onNodeWithText("Con propuesta").assertIsDisplayed()
        composeRule.onNodeWithText("Con otra propuesta").assertIsDisplayed()
    }

    @Test
    fun `una nota que sale por palabra y por parecido no choca consigo misma`() {
        // Las dos listas pueden contener la misma nota: sin prefijo en la clave, esa
        // coincidencia es la que rompe la pantalla.
        render(
            NotesUiState(
                query = "idea",
                notes = listOf(nota("a", body = "Una idea")),
                related = listOf(nota("a", body = "Una idea")),
                total = 1,
                loading = false,
            ),
        )

        composeRule.onNodeWithText("TAMBIÉN RELACIONADAS").assertIsDisplayed()
    }

    @Test
    fun `la cabecera cuenta todas las notas, no las que se estan viendo`() {
        // Si el numero bajara al buscar, buscar pareceria borrar notas.
        render(
            NotesUiState(query = "idea", notes = listOf(nota("a")), total = 12, loading = false),
        )

        composeRule.onNodeWithText("12").assertIsDisplayed()
    }
}
