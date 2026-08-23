package com.zenlauncher.zen.domain

import com.zenlauncher.zen.system.HomeRoleTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRoleTargetTest {

    @Test
    fun `si Zen no es el launcher se pide el rol con el dialogo del sistema`() {
        assertEquals(
            HomeRoleTarget.REQUEST_ROLE,
            HomeRoleTarget.of(alreadyHome = false, roleAvailable = true),
        )
    }

    @Test
    fun `si Zen ya es el launcher se abre el selector para poder salir`() {
        // Regresion: con createRequestRoleIntent aqui, Zen no ofrecia ninguna forma de
        // devolver la pantalla de inicio al launcher anterior desde dentro de la app.
        assertEquals(
            HomeRoleTarget.HOME_SETTINGS,
            HomeRoleTarget.of(alreadyHome = true, roleAvailable = true),
        )
    }

    @Test
    fun `salir sigue siendo posible aunque la ROM no exponga el rol`() {
        assertEquals(
            HomeRoleTarget.HOME_SETTINGS,
            HomeRoleTarget.of(alreadyHome = true, roleAvailable = false),
        )
    }

    @Test
    fun `sin rol disponible y sin ser launcher se cae al selector`() {
        assertEquals(
            HomeRoleTarget.HOME_SETTINGS,
            HomeRoleTarget.of(alreadyHome = false, roleAvailable = false),
        )
    }
}
