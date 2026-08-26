package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.notes.AttachmentKind
import com.zenlauncher.zen.domain.notes.Note
import com.zenlauncher.zen.domain.notes.NoteAttachment
import com.zenlauncher.zen.domain.notes.RecurringCluster
import com.zenlauncher.zen.domain.notes.RecurringWord
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
    private var developIdeas = 0
    private val opened = mutableListOf<Note>()
    private var backs = 0
    private val accepted = mutableListOf<Pair<RecurringCluster, String>>()
    private val ignored = mutableListOf<RecurringCluster>()
    private var openedProjects = 0
    private var query = ""

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
                    onQueryChange = { query = it },
                    onQuickNote = { quickNotes++ },
                    onDevelopIdea = { developIdeas++ },
                    onOpenNote = { opened += it },
                    onAcceptClusterSuggestion = { cluster, title -> accepted += cluster to title },
                    onIgnoreClusterSuggestion = { ignored += it },
                    onOpenProjects = { openedProjects++ },
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

        composeRule.onNodeWithText("NOTA RÁPIDA").assertIsDisplayed().assertHasClickAction().performClick()

        assertEquals(1, quickNotes)
    }

    @Test
    fun `desarrollar una idea esta debajo de la captura y a un toque`() {
        render(NotesUiState(notes = listOf(nota("a")), total = 1, loading = false))

        composeRule.onNodeWithText("DESARROLLAR").assertIsDisplayed().performClick()

        assertEquals(1, developIdeas)
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
    fun `el titulo generado encabeza la tarjeta y el cuerpo se lee debajo`() {
        // Con titulo generado el cuerpo no repite nada, asi que se ensena entero: una
        // tarjeta que solo dijera el titulo obligaria a abrir la nota para recordarla,
        // que es justo lo que se venia a evitar.
        render(
            NotesUiState(
                notes = listOf(nota("a", body = "La gente ya no sabe aburrirse", title = "El aburrimiento")),
                total = 1,
                loading = false,
            ),
        )

        composeRule.onNodeWithText("El aburrimiento").assertIsDisplayed()
        composeRule.onNodeWithText("La gente ya no sabe aburrirse").assertIsDisplayed()
    }

    @Test
    fun `sin titulo generado, la primera linea encabeza y no se repite debajo`() {
        // Regresion de la retícula: el titulo de una nota sin titulo generado ES la
        // primera linea del cuerpo. Pintando el cuerpo entero debajo, esa linea salia
        // dos veces en el mismo recuadro y parecia un fallo de la aplicacion.
        render(
            NotesUiState(
                notes = listOf(nota("a", body = "La gente ya no sabe aburrirse\nY eso importa")),
                total = 1,
                loading = false,
            ),
        )

        composeRule.onNodeWithText("La gente ya no sabe aburrirse").assertIsDisplayed()
        composeRule.onNodeWithText("Y eso importa").assertIsDisplayed()
        composeRule.onNodeWithText("La gente ya no sabe aburrirse\nY eso importa").assertDoesNotExist()
    }

    @Test
    fun `una nota que solo lleva una foto lo dice en su recuadro`() {
        // Sin texto, el recuadro se quedaria en blanco con una fecha suelta. Va escrito
        // y no como icono: se lee igual sin distinguir formas de seis pixeles.
        render(
            NotesUiState(
                notes = listOf(
                    nota("a", body = "").copy(
                        attachments = listOf(
                            NoteAttachment(
                                id = "img",
                                noteId = "a",
                                kind = AttachmentKind.IMAGE,
                                value = "fotos/a.jpg",
                                createdAtMillis = ahora,
                            ),
                        ),
                    ),
                ),
                total = 1,
                loading = false,
            ),
        )

        composeRule.onNodeWithText("1 FOTO").assertIsDisplayed()
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
    fun `las notas con propuesta se anuncian sin repetir el recuadro entero`() {
        // Casi siempre son notas que ya estan arriba en la lista: pintarlas otra vez
        // como recuadro con su mensaje era ver la misma nota dos veces en una pantalla,
        // que es justo lo que hacia que Notas pareciera un caos.
        render(
            NotesUiState(
                notes = listOf(nota("a", body = "Una idea")),
                withSuggestions = listOf(nota("b", body = "Con propuesta\nY un mensaje largo debajo")),
                total = 2,
                loading = false,
            ),
        )

        composeRule.onNodeWithText("Con propuesta").assertIsDisplayed()
        composeRule.onNodeWithText("Y un mensaje largo debajo").assertDoesNotExist()
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
    fun `la lista se abre por arriba aunque las notas lleguen despues del primer fotograma`() {
        // Regresion: el aire del final era un elemento mas de la retícula. En el primer
        // fotograma —todavia cargando— era el UNICO, asi que quedaba de ancla; al llegar
        // las notas y pasar al final, la retícula se desplazaba para mantenerlo a la
        // vista y Notas se abria por la mitad de la lista. Ahora es margen, no elemento.
        var estado by mutableStateOf(NotesUiState(loading = true))
        composeRule.setContent {
            ZenTheme {
                NotesScreen(
                    state = estado,
                    query = "",
                    nowMillis = ahora,
                    onQueryChange = {},
                    onQuickNote = {},
                    onDevelopIdea = {},
                    onOpenNote = {},
                    onAcceptClusterSuggestion = { _, _ -> },
                    onIgnoreClusterSuggestion = {},
                    onOpenProjects = {},
                    onBack = {},
                    locale = Locale("es", "ES"),
                )
            }
        }

        val muchas = (1..12).map { nota("n$it", body = "Idea numero $it") }
        composeRule.runOnIdle { estado = NotesUiState(notes = muchas, total = 12, loading = false) }

        composeRule.onNodeWithText("Idea numero 1").assertIsDisplayed()
    }

    @Test
    fun `la cabecera cuenta todas las notas, no las que se estan viendo`() {
        // Si el numero bajara al buscar, buscar pareceria borrar notas.
        render(
            NotesUiState(query = "idea", notes = listOf(nota("a")), total = 12, loading = false),
        )

        composeRule.onNodeWithText("12").assertIsDisplayed()
    }

    @Test
    fun `sin proyectos no aparece la fila`() {
        render(NotesUiState(notes = listOf(nota("a")), total = 1, loading = false))

        composeRule.onNodeWithText("PROYECTOS").assertDoesNotExist()
    }

    @Test
    fun `con al menos un proyecto la fila abre la lista`() {
        render(NotesUiState(notes = listOf(nota("a")), hasProjects = true, total = 1, loading = false))

        composeRule.onNodeWithText("PROYECTOS").performClick()

        assertEquals(1, openedProjects)
    }

    @Test
    fun `sin patrones ni sugerencias no aparece la seccion`() {
        render(NotesUiState(notes = listOf(nota("a")), total = 1, loading = false))

        composeRule.onNodeWithText("PATRONES").assertDoesNotExist()
    }

    @Test
    fun `una palabra recurrente se toca y busca por su raiz`() {
        render(
            NotesUiState(
                notes = listOf(nota("a")),
                patterns = listOf(RecurringWord(stem = "aburr", noteCount = 5)),
                total = 1,
                loading = false,
            ),
        )

        composeRule.onNodeWithText("PATRONES").assertIsDisplayed()
        composeRule.onNodeWithText("5 notas mencionan «aburr»").performClick()

        assertEquals("aburr", query)
    }

    @Test
    fun `una sugerencia de proyecto se acepta con un titulo`() {
        render(
            NotesUiState(
                notes = listOf(nota("a"), nota("b"), nota("c")),
                projectSuggestions = listOf(RecurringCluster(setOf("a", "b", "c"))),
                total = 3,
                loading = false,
            ),
        )

        composeRule.onNodeWithText("3 ideas podrían formar un proyecto").assertIsDisplayed()
        composeRule.onNodeWithText("Aceptar").performClick()
        composeRule.onNodeWithText("Crear proyecto").performClick()

        assertEquals(1, accepted.size)
        assertEquals(setOf("a", "b", "c"), accepted.first().first.noteIds)
    }

    @Test
    fun `una sugerencia de proyecto se puede ignorar`() {
        val cluster = RecurringCluster(setOf("a", "b", "c"))
        render(
            NotesUiState(
                notes = listOf(nota("a"), nota("b"), nota("c")),
                projectSuggestions = listOf(cluster),
                total = 3,
                loading = false,
            ),
        )

        composeRule.onNodeWithText("Ignorar").performClick()

        assertEquals(listOf(cluster), ignored)
    }
}
