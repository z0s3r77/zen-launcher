package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.model.ZenThemeChoice
import org.junit.Assert.assertEquals
import org.junit.Test

class ZenThemeChoiceTest {

    @Test
    fun `el siguiente da la vuelta`() {
        // Regresion: la fila de ajustes es un solo toque, asi que sin dar la vuelta
        // quien eligiera el ultimo tema se quedaria sin forma de volver desde ahi.
        assertEquals(ZenThemeChoice.SISTEMA, ZenThemeChoice.NEGRO.next())
        assertEquals(ZenThemeChoice.NEGRO, ZenThemeChoice.SISTEMA.next())
    }

    @Test
    fun `recorriendo la vuelta entera se pasa por todos los temas`() {
        val recorrido = generateSequence(ZenThemeChoice.Default) { it.next() }
            .take(ZenThemeChoice.entries.size)
            .toSet()

        assertEquals(ZenThemeChoice.entries.toSet(), recorrido)
    }

    @Test
    fun `un id desconocido cae en el de fabrica en lugar de reventar`() {
        // Puede llegar de un fichero de preferencias tocado a mano o de una version que
        // anadio un tema y se desinstalo. Una excepcion aqui deja el telefono sin
        // pantalla de inicio.
        assertEquals(ZenThemeChoice.Default, ZenThemeChoice.ofIdOrDefault(null))
        assertEquals(ZenThemeChoice.Default, ZenThemeChoice.ofIdOrDefault(""))
        assertEquals(ZenThemeChoice.Default, ZenThemeChoice.ofIdOrDefault("azul"))
        assertEquals(ZenThemeChoice.Default, ZenThemeChoice.ofIdOrDefault("NEGRO"))
    }

    @Test
    fun `el id de cada tema es estable y unico`() {
        // Lo que se guarda en DataStore es el id, no el ordinal: cambiar uno le cambia
        // el tema a quien ya lo habia elegido.
        assertEquals("negro", ZenThemeChoice.NEGRO.id)
        assertEquals("sistema", ZenThemeChoice.SISTEMA.id)
        assertEquals(
            ZenThemeChoice.entries.size,
            ZenThemeChoice.entries.map { it.id }.toSet().size,
        )
        ZenThemeChoice.entries.forEach {
            assertEquals(it, ZenThemeChoice.ofIdOrDefault(it.id))
        }
    }
}
