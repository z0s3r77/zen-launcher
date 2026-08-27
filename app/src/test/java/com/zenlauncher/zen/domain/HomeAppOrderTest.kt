package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.apps.HomeAppOrder
import com.zenlauncher.zen.domain.apps.HomeAppOrder.Slot
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeAppOrderTest {

    // Dos columnas de 200 de ancho y filas de 60, como la reticula de verdad: los
    // centros caen en (100, 30), (300, 30), (100, 90)...
    private fun reticula(huecos: Int): List<Slot> = (0 until huecos).map { index ->
        Slot(
            x = if (index % 2 == 0) 100f else 300f,
            y = 30f + (index / 2) * 60f,
        )
    }

    @Test
    fun `mover hacia adelante desplaza a los de en medio`() {
        assertEquals(
            listOf("b", "c", "a", "d"),
            HomeAppOrder.move(listOf("a", "b", "c", "d"), from = 0, to = 2),
        )
    }

    @Test
    fun `mover hacia atras desplaza a los de en medio`() {
        assertEquals(
            listOf("a", "d", "b", "c"),
            HomeAppOrder.move(listOf("a", "b", "c", "d"), from = 3, to = 1),
        )
    }

    @Test
    fun `mover al mismo hueco no cambia nada`() {
        val apps = listOf("a", "b", "c")
        assertEquals(apps, HomeAppOrder.move(apps, from = 1, to = 1))
    }

    @Test
    fun `un indice fuera de rango deja la lista como estaba`() {
        val apps = listOf("a", "b", "c")
        assertEquals(apps, HomeAppOrder.move(apps, from = 0, to = 9))
        assertEquals(apps, HomeAppOrder.move(apps, from = -1, to = 0))
    }

    @Test
    fun `sin arrastre el destino es el hueco de partida`() {
        assertEquals(2, HomeAppOrder.slotAt(from = 2, dragX = 0f, dragY = 0f, slots = reticula(8)))
    }

    @Test
    fun `un roce no reordena la pantalla de inicio`() {
        // Media celda escasa hacia el lado: el centro sigue mas cerca de donde estaba.
        assertEquals(0, HomeAppOrder.slotAt(from = 0, dragX = 90f, dragY = 0f, slots = reticula(8)))
    }

    @Test
    fun `arrastrar a la celda de al lado da el hueco de al lado`() {
        assertEquals(1, HomeAppOrder.slotAt(from = 0, dragX = 200f, dragY = 0f, slots = reticula(8)))
    }

    @Test
    fun `arrastrar a la fila de abajo da el hueco de abajo`() {
        assertEquals(2, HomeAppOrder.slotAt(from = 0, dragX = 0f, dragY = 60f, slots = reticula(8)))
    }

    @Test
    fun `arrastrar mas alla de la reticula se queda en el ultimo hueco de esa columna`() {
        // Notas y Lectura no entran en `slots`: fuera de la reticula de aplicaciones no
        // hay a donde ir, y el destino se queda pegado al hueco que si existe. Bajando
        // en linea recta por la columna izquierda ese hueco es el 04 (indice 4), no el
        // ultimo de todos: la columna tambien cuenta para saber que hueco esta mas cerca.
        assertEquals(4, HomeAppOrder.slotAt(from = 0, dragX = 0f, dragY = 900f, slots = reticula(6)))
    }

    @Test
    fun `reordenar respeta lo guardado que no se ve`() {
        // Regresion: se reescribian los favoritos con lo que la home estaba pintando, y
        // una favorita restringida —que no se pinta pero sigue guardada— desaparecia
        // para siempre en cuanto se movia cualquier otra.
        val guardado = listOf("a", "restringida", "b", "c")
        val visible = listOf("a", "b", "c")

        assertEquals(
            listOf("b", "restringida", "a", "c"),
            HomeAppOrder.reorder(guardado, visible, from = 0, to = 1),
        )
    }

    @Test
    fun `reordenar sin nada guardado escribe lo que se ve`() {
        // La home va con las esenciales: lo que el usuario acaba de ordenar con el dedo
        // pasa a ser su eleccion.
        assertEquals(
            listOf("b", "a", "c"),
            HomeAppOrder.reorder(
                stored = emptyList(),
                visible = listOf("a", "b", "c"),
                from = 0,
                to = 1,
            ),
        )
    }

    @Test
    fun `un movimiento que no cambia nada devuelve lo guardado tal cual`() {
        val guardado = listOf("a", "oculta", "b")
        assertEquals(guardado, HomeAppOrder.reorder(guardado, listOf("a", "b"), from = 1, to = 1))
    }
}
