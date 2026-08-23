package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.zenlauncher.zen.domain.model.InstalledApp
import com.zenlauncher.zen.presentation.apps.CandidateAppRow
import com.zenlauncher.zen.presentation.apps.ChosenAppRow
import com.zenlauncher.zen.presentation.apps.HomeAppsScreen
import com.zenlauncher.zen.presentation.apps.HomeAppsUiState
import com.zenlauncher.zen.presentation.theme.ZenTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class HomeAppsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val added = mutableListOf<InstalledApp>()
    private val removed = mutableListOf<InstalledApp>()
    private val queries = mutableListOf<String>()
    private var backs = 0

    private fun app(pkg: String, label: String) =
        InstalledApp(packageName = pkg, label = label, componentName = "$pkg/.Main")

    private fun render(
        chosen: List<ChosenAppRow> = emptyList(),
        candidates: List<CandidateAppRow> = emptyList(),
        query: String = "",
    ) {
        composeRule.setContent {
            ZenTheme {
                HomeAppsScreen(
                    state = HomeAppsUiState(
                        query = query,
                        chosen = chosen,
                        candidates = candidates,
                        loading = false,
                    ),
                    onQueryChange = { queries += it },
                    onAdd = { added += it },
                    onRemove = { removed += it },
                    onBack = { backs++ },
                )
            }
        }
    }

    @Test
    fun `sin escribir no ensena ninguna aplicacion, solo dice como buscarla`() {
        // El motivo de que esta pantalla exista: colgada de Ajustes habia una lista con
        // TODAS las aplicaciones del telefono, y elegir entre doscientas no es elegir.
        render(chosen = listOf(ChosenAppRow(app("com.example.notes", "Notas"), position = 0)))

        composeRule.onNodeWithText("Instagram").assertDoesNotExist()
        composeRule
            .onNodeWithText(
                "ESCRIBE PARA BUSCARLA. NO SE LISTAN TODAS: ELEGIR ENTRE DOSCIENTAS NO ES ELEGIR",
            )
            .assertIsDisplayed()
    }

    @Test
    fun `lo que ya esta puesto sale arriba con el numero de su hueco`() {
        render(
            chosen = listOf(
                ChosenAppRow(app("com.example.notes", "Notas"), position = 0),
                ChosenAppRow(app("com.google.android.dialer", "Teléfono"), position = 1),
            ),
        )

        // El mismo numero que lleva la celda en la reticula de la home.
        composeRule.onNodeWithText("01").assertIsDisplayed()
        composeRule.onNodeWithText("02").assertIsDisplayed()
        composeRule.onNodeWithText("Teléfono").assertIsDisplayed()
    }

    @Test
    fun `tocar una elegida la quita`() {
        val notas = app("com.example.notes", "Notas")
        render(chosen = listOf(ChosenAppRow(notas, position = 0)))

        composeRule.onNodeWithText("Notas").assertHasClickAction().performClick()

        assertEquals(listOf(notas), removed)
    }

    @Test
    fun `escribir en el buscador llega al modelo`() {
        render()

        composeRule.onNodeWithText("Escribe el nombre de una aplicación").performTextInput("tel")

        assertEquals(listOf("tel"), queries)
    }

    @Test
    fun `tocar un resultado lo anade`() {
        val telefono = app("com.google.android.dialer", "Teléfono")
        render(query = "tel", candidates = listOf(CandidateAppRow(telefono, chosen = false)))

        composeRule.onNodeWithText("Teléfono").assertHasClickAction().performClick()

        assertEquals(listOf(telefono), added)
    }

    @Test
    fun `una que ya esta puesta se ve en la busqueda pero no se puede repetir`() {
        // Repetirla en la reticula seria un hueco perdido.
        val notas = app("com.example.notes", "Notas")
        render(
            query = "not",
            chosen = listOf(ChosenAppRow(notas, position = 0)),
            candidates = listOf(CandidateAppRow(notas, chosen = true)),
        )

        // Sale dos veces: arriba como elegida —esa si quita— y abajo como resultado,
        // que se ve pero no se puede volver a anadir.
        assertEquals(2, composeRule.onAllNodesWithText("Notas").fetchSemanticsNodes().size)
        composeRule.onNodeWithText("QUITAR").assertIsDisplayed()
    }

    @Test
    fun `con el inicio lleno los resultados se apagan en vez de desaparecer`() {
        // Que se entienda que la aplicacion existe y lo que falta es sitio.
        val lleno = (1..HomeAppsUiState.MAX_HOME_APPS).map {
            ChosenAppRow(app("com.app$it", "App $it"), position = it - 1)
        }
        val telefono = app("com.google.android.dialer", "Teléfono")
        render(
            query = "tel",
            chosen = lleno,
            candidates = listOf(CandidateAppRow(telefono, chosen = false)),
        )

        // Con el inicio lleno hay ocho filas por encima: el resultado y el aviso viven
        // debajo del pliegue de la lista.
        val lista = composeRule.onNode(hasScrollAction())
        lista.performScrollToNode(hasText("Teléfono"))
        composeRule.onNodeWithText("Teléfono").assertIsDisplayed().assertHasNoClickAction()

        lista.performScrollToNode(hasText("YA HAY 8. QUITA UNA PARA PODER AÑADIR OTRA"))
        composeRule.onNodeWithText("YA HAY 8. QUITA UNA PARA PODER AÑADIR OTRA")
            .assertIsDisplayed()
        assertEquals(emptyList<InstalledApp>(), added)
    }

    @Test
    fun `una busqueda sin resultados lo dice`() {
        render(query = "zzz")

        composeRule.onNodeWithText("NINGUNA APLICACIÓN SE LLAMA ASÍ").assertIsDisplayed()
    }

    @Test
    fun `hay una salida propia sin depender del gesto del sistema`() {
        // Con la barra de navegacion oculta, el primer deslizamiento desde el borde saca
        // las barras del sistema en vez de volver atras. Ver `EdgeBackPolicy`.
        render()

        composeRule.onNodeWithContentDescription("Volver")
            .assertHasClickAction()
            .performClick()

        assertEquals(1, backs)
    }
}
