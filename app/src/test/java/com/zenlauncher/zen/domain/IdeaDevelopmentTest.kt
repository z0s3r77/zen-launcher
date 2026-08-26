package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.notes.HeuristicIdeaDevelopmentModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdeaDevelopmentTest {

    private val model = HeuristicIdeaDevelopmentModel()

    @Test
    fun `una idea con negacion pregunta que se pierde`() {
        val prompts = model.generate("No quiero seguir revisando el móvil sin parar", relatedCount = 0)

        assertTrue(prompts.questions.any { it.contains("dejando fuera") })
    }

    @Test
    fun `una idea sin negacion no lleva esa pregunta`() {
        val prompts = model.generate("Quiero aprender a programar en Kotlin", relatedCount = 0)

        assertTrue(prompts.questions.none { it.contains("dejando fuera") })
    }

    @Test
    fun `una idea ya en forma de pregunta no repite lo que el usuario ya escribio`() {
        val prompts = model.generate("¿Por qué me cuesta tanto concentrarme?", relatedCount = 0)

        assertNull(prompts.centralQuestion)
        assertTrue(prompts.questions.any { it.contains("qué respuesta esperas encontrar") })
    }

    @Test
    fun `una idea sin ninguna raiz reconocida y sin notas relacionadas no dice nada`() {
        // Solo palabras vacias: no hay de donde sacar ni una pregunta central ni un
        // enfoque, y preferimos no decir nada a rellenar.
        val prompts = model.generate("Esto es que pasa", relatedCount = 0)

        assertNull(prompts.centralQuestion)
        assertEquals(emptyList<String>(), prompts.approaches)
        assertEquals(emptyList<String>(), prompts.questions)
    }

    @Test
    fun `tres o mas notas relacionadas anaden una pregunta con el numero real`() {
        val prompts = model.generate("Quiero escribir sobre el aburrimiento", relatedCount = 3)

        assertTrue(prompts.questions.any { it.contains("3 notas relacionadas") })
    }

    @Test
    fun `menos de tres notas relacionadas no anaden esa pregunta`() {
        val prompts = model.generate("Quiero escribir sobre el aburrimiento", relatedCount = 2)

        assertTrue(prompts.questions.none { it.contains("notas relacionadas") })
    }

    @Test
    fun `una idea tecnologica dispara el enfoque tecnologico y no otros`() {
        val prompts = model.generate("Quiero programar una aplicación para organizar tareas", relatedCount = 0)

        assertTrue(prompts.approaches.contains("Tecnológico"))
        assertTrue(prompts.approaches.none { it == "Social" || it == "Psicológico" })
    }

    @Test
    fun `una idea sin vocabulario de ninguna categoria no propone enfoques`() {
        val prompts = model.generate("Comprar pan y pilas", relatedCount = 0)

        assertEquals(emptyList<String>(), prompts.approaches)
    }

    @Test
    fun `la pregunta central usa la raiz dominante de la idea`() {
        val prompts = model.generate("Quiero escribir, escribir y volver a escribir cada día", relatedCount = 0)

        assertEquals("¿Qué papel juega «escrib» en esta idea?", prompts.centralQuestion)
    }
}
