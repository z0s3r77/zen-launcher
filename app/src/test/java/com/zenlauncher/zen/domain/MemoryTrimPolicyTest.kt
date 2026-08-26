package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.system.MemoryRelease
import com.zenlauncher.zen.domain.system.MemoryTrim
import com.zenlauncher.zen.domain.system.MemoryTrimPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryTrimPolicyTest {

    /**
     * La decision que va al reves de lo que parece, y por eso tiene test propio.
     * `TRIM_MEMORY_UI_HIDDEN` llega cada vez que se abre una aplicacion —decenas de
     * veces al dia— y no significa que falte memoria. Soltando ahi, cada vuelta a la
     * pantalla de inicio releia por IPC la lista entera de aplicaciones y la reticula se
     * pintaba vacia primero.
     */
    @Test
    fun `salir del launcher no suelta nada`() {
        assertEquals(MemoryRelease.NADA, MemoryTrimPolicy.decide(MemoryTrim.UI_OCULTA))
    }

    @Test
    fun `en la cola de candidatos a morir se suelta todo lo cacheable`() {
        // Mas vale una pantalla de inicio lenta que un telefono sin pantalla de inicio.
        assertEquals(MemoryRelease.SOLTAR, MemoryTrimPolicy.decide(MemoryTrim.EN_SEGUNDO_PLANO))
    }

    @Test
    fun `un aviso que no se reconoce no toca nada`() {
        assertEquals(MemoryRelease.NADA, MemoryTrimPolicy.decide(MemoryTrim.OTRA))
    }
}
