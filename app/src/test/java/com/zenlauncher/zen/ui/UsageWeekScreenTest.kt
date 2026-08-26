package com.zenlauncher.zen.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.zenlauncher.zen.domain.usage.AppUsage
import com.zenlauncher.zen.domain.usage.PatternAction
import com.zenlauncher.zen.domain.usage.PatternKind
import com.zenlauncher.zen.domain.usage.UsagePattern
import com.zenlauncher.zen.domain.usage.UsagePatterns
import com.zenlauncher.zen.domain.usage.UsageSnapshot
import com.zenlauncher.zen.domain.usage.WeekVerdict
import com.zenlauncher.zen.domain.usage.WeeklyUsage
import com.zenlauncher.zen.presentation.theme.ZenTheme
import com.zenlauncher.zen.presentation.usage.PatternRow
import com.zenlauncher.zen.presentation.usage.UsageWeekScreen
import com.zenlauncher.zen.presentation.usage.WeekUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class UsageWeekScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var restricted = 0
    private var grants = 0

    private fun minutos(value: Long) = value * 60_000L

    private fun dia(index: Int, minutes: Long, unlocks: Int = 20) = UsageSnapshot(
        dayStartMillis = index * 86_400_000L,
        nowMillis = index * 86_400_000L + minutos(minutes),
        screenMillis = minutos(minutes),
        unlocks = unlocks,
        apps = listOf(AppUsage("com.instagram.android", 12, minutos(minutes / 2))),
    )

    private fun render(state: WeekUiState) {
        composeRule.setContent {
            ZenTheme {
                UsageWeekScreen(
                    state = state,
                    onBack = {},
                    onOpenRestricted = { restricted++ },
                    onGrantAccess = { grants++ },
                    locale = Locale.forLanguageTag("es-ES"),
                )
            }
        }
    }

    private fun semana(days: Int = 4) = WeekUiState(
        week = WeeklyUsage((0 until days).map { dia(it, 120) }),
        verdict = WeekVerdict.BAJO_CONTROL,
        hasAccess = true,
        loading = false,
    )

    @Test
    fun `la semana ensena media, total y desbloqueos`() {
        render(semana())

        composeRule.onNodeWithText("Media diaria").assertIsDisplayed()
        composeRule.onNodeWithText("2h 0m").assertIsDisplayed()
        composeRule.onNodeWithText("Total de la semana").assertIsDisplayed()
    }

    /**
     * La pregunta con la que alguien entra aqui es "¿lo tengo controlado?", y esa se
     * contesta con una palabra antes que con tres observaciones.
     */
    @Test
    fun `el veredicto se lee como una palabra`() {
        render(semana().copy(verdict = WeekVerdict.FUERA_DE_MANO))

        composeRule.onNodeWithText("¿LO TIENES CONTROLADO?").assertIsDisplayed()
        composeRule.onNodeWithText("NO").assertIsDisplayed()
    }

    /**
     * Una grafica no se lee sola. Cada barra dice su dia y su tiempo, que es la regla de
     * que todo estado se pueda leer como texto y no solo por la forma.
     */
    @Test
    fun `cada barra de la grafica se puede leer como texto`() {
        render(semana(days = 2))

        // El rotulo del dia mas su duracion: "L 2h 0m".
        composeRule.onAllNodes(hasContentDescriptionSubstring("2h 0m"))
            .onFirst()
            .assertExists()
    }

    @Test
    fun `un dia que Android ya no conserva sale sin medir, no a cero`() {
        val week = WeeklyUsage(listOf(UsageSnapshot.unmeasured(0L, 0L), dia(1, 120)))
        render(semana().copy(week = week))

        composeRule.onNodeWithContentDescription("SIN DATOS").assertExists()
    }

    @Test
    fun `una observacion lleva su cifra y su salida`() {
        render(
            semana().copy(
                patterns = listOf(
                    PatternRow(
                        pattern = UsagePattern(
                            kind = PatternKind.LADRONA,
                            packageName = "com.instagram.android",
                            value = 47,
                            dailyMillis = minutos(95),
                            action = PatternAction.RESTRINGIR,
                        ),
                        label = "Instagram",
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("Instagram se lleva el 47% de tu semana").assertIsDisplayed()
        composeRule.onNodeWithText("1h 35m AL DÍA").assertIsDisplayed()

        composeRule.onNodeWithText("RESTRINGIR").performClick()
        assertEquals(1, restricted)
    }

    @Test
    fun `una observacion sin arreglo dentro de Zen no ofrece boton`() {
        render(
            semana().copy(
                patterns = listOf(
                    PatternRow(
                        pattern = UsagePattern(
                            kind = PatternKind.SUBIENDO,
                            packageName = null,
                            value = 40,
                            dailyMillis = 0L,
                            action = PatternAction.NINGUNA,
                        ),
                        label = null,
                    ),
                ),
            ),
        )

        composeRule.onNodeWithText("RESTRINGIR").assertDoesNotExist()
    }

    /**
     * "No hay patron" y "todavia no se puede saber" no son lo mismo: decir que todo va
     * bien con un solo dia medido seria adivinar.
     */
    @Test
    fun `con menos de dos dias medidos se dice que es pronto`() {
        render(semana().copy(week = WeeklyUsage(listOf(dia(0, 120)))))

        composeRule.onNodeWithText(
            "HACEN FALTA DOS DÍAS MEDIDOS PARA HABLAR DE UN PATRÓN",
        ).assertIsDisplayed()
    }

    @Test
    fun `con dias suficientes y nada que destacar lo dice`() {
        render(semana(days = UsagePatterns.MIN_DAYS + 1))

        composeRule.onNodeWithText("NINGÚN PATRÓN DESTACA ESTA SEMANA").assertIsDisplayed()
    }

    /**
     * Regresion: "todavia no lo se" y "no hay nada" son iguales en los datos y no lo son
     * para el usuario. Sin estado de carga, la pantalla decia "sin datos" durante el
     * medio segundo que tardan las siete consultas.
     */
    @Test
    fun `mientras se leen los siete dias no se dice que no hay datos`() {
        render(WeekUiState(hasAccess = true, loading = true))

        composeRule.onNodeWithText("LEYENDO LA SEMANA…").assertIsDisplayed()
        composeRule.onNodeWithText(
            "ANDROID NO CONSERVA NINGÚN DÍA TODAVÍA. VUELVE MAÑANA.",
        ).assertDoesNotExist()
    }

    @Test
    fun `sin acceso ofrece concederlo`() {
        render(WeekUiState(hasAccess = false, loading = false))

        composeRule.onNodeWithText("Conceder en Ajustes de Android").performClick()
        assertEquals(1, grants)
    }

    private fun hasContentDescriptionSubstring(text: String): SemanticsMatcher =
        SemanticsMatcher("contentDescription contiene '$text'") { node ->
            node.config.getOrNull(SemanticsProperties.ContentDescription)
                ?.any { it.contains(text) } == true
        }
}
