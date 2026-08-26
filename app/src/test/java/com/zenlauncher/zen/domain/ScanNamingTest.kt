package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.scanner.ScanNaming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Como se llama lo que se guarda. El usuario lo ve en su galeria y en sus documentos. */
class ScanNamingTest {

    private val momento = ScanNaming.Stamp(
        year = 2026,
        month = 3,
        day = 7,
        hour = 9,
        minute = 4,
        second = 2,
    )

    @Test
    fun `una sola pagina no lleva sufijo`() {
        // Con una pagina el sufijo sobra y ensucia el nombre.
        assertEquals(
            "Escaneo_2026-03-07_090402.jpg",
            ScanNaming.imageName(momento, pageNumber = 1, totalPages = 1),
        )
    }

    @Test
    fun `con varias paginas el numero es lo unico que las distingue`() {
        assertEquals(
            "Escaneo_2026-03-07_090402_03.jpg",
            ScanNaming.imageName(momento, pageNumber = 3, totalPages = 5),
        )
    }

    @Test
    fun `ordenar por nombre y por fecha dan lo mismo`() {
        // Es la razon del formato con ceros: en una carpeta llamada Escaneo, ordenar
        // alfabeticamente tiene que dar el orden cronologico.
        val enero = ScanNaming.pdfName(momento.copy(month = 1, day = 31, hour = 23))
        val marzo = ScanNaming.pdfName(momento)
        val marzoMasTarde = ScanNaming.pdfName(momento.copy(hour = 18))

        assertTrue(enero < marzo)
        assertTrue(marzo < marzoMasTarde)
    }

    @Test
    fun `las tres extensiones salen de la misma marca de tiempo`() {
        assertEquals("Escaneo_2026-03-07_090402.pdf", ScanNaming.pdfName(momento))
        assertEquals("Escaneo_2026-03-07_090402.txt", ScanNaming.textName(momento))
    }

    @Test
    fun `la medianoche no se confunde con el mediodia`() {
        // Reloj de 24 horas: con uno de 12, dos escaneos del mismo dia se llamarian igual.
        val medianoche = ScanNaming.pdfName(momento.copy(hour = 0, minute = 0, second = 0))
        val mediodia = ScanNaming.pdfName(momento.copy(hour = 12, minute = 0, second = 0))

        assertEquals("Escaneo_2026-03-07_000000.pdf", medianoche)
        assertEquals("Escaneo_2026-03-07_120000.pdf", mediodia)
    }
}
