package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.scanner.Homography
import com.zenlauncher.zen.domain.scanner.Quad
import com.zenlauncher.zen.domain.scanner.ScanPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * La transformacion de perspectiva.
 *
 * Nada de lo nativo se puede ejecutar en la JVM, asi que esta es la unica forma de
 * comprobar que enderezar un trapecio da un rectangulo sin un dispositivo delante.
 */
class HomographyTest {

    private val unidad = Quad(
        topLeft = ScanPoint(0f, 0f),
        topRight = ScanPoint(1f, 0f),
        bottomRight = ScanPoint(1f, 1f),
        bottomLeft = ScanPoint(0f, 1f),
    )

    /** Un folio visto de lado: el borde de arriba esta mas lejos y sale mas corto. */
    private val trapecio = Quad(
        topLeft = ScanPoint(0.30f, 0.20f),
        topRight = ScanPoint(0.70f, 0.20f),
        bottomRight = ScanPoint(0.90f, 0.80f),
        bottomLeft = ScanPoint(0.10f, 0.80f),
    )

    @Test
    fun `las cuatro esquinas caen exactamente donde se les dijo`() {
        val h = Homography.between(trapecio, unidad)
        assertNotNull(h)

        trapecio.points.forEachIndexed { indice, origen ->
            val destino = unidad.points[indice]
            val mapeado = h!!.map(origen)
            assertEquals("Esquina $indice en x", destino.x, mapeado.x, 1e-4f)
            assertEquals("Esquina $indice en y", destino.y, mapeado.y, 1e-4f)
        }
    }

    @Test
    fun `el centro del trapecio no cae en el centro del rectangulo`() {
        // Es la comprobacion de que esto es de verdad una homografia y no un simple
        // estirado: con una transformacion afin el centro iria al centro, y entonces la
        // perspectiva no se estaria deshaciendo.
        val h = Homography.between(trapecio, unidad)!!

        val centroDelTrapecio = ScanPoint(
            x = trapecio.points.map { it.x }.average().toFloat(),
            y = trapecio.points.map { it.y }.average().toFloat(),
        )
        val mapeado = h.map(centroDelTrapecio)

        assertEquals(0.5f, mapeado.x, 1e-4f)
        assertTrue(
            "El centro del trapecio deberia caer por debajo de la mitad, cayo en ${mapeado.y}",
            mapeado.y > 0.5f,
        )
    }

    @Test
    fun `ir y volver devuelve el punto de partida`() {
        val ida = Homography.between(trapecio, unidad)!!
        val vuelta = Homography.between(unidad, trapecio)!!

        val punto = ScanPoint(0.42f, 0.63f)
        val redondo = vuelta.map(ida.map(punto))

        assertEquals(punto.x, redondo.x, 1e-3f)
        assertEquals(punto.y, redondo.y, 1e-3f)
    }

    @Test
    fun `los puntos de dentro se quedan dentro`() {
        // Si el enderezado sacara pixeles fuera de la hoja, el documento saldria con
        // trozos de mesa por los bordes.
        val h = Homography.between(trapecio, unidad)!!

        for (paso in 0..10) {
            val t = paso / 10f
            // Un punto sobre el segmento que une el centro del borde superior con el del
            // inferior: siempre esta dentro del trapecio.
            val punto = ScanPoint(0.5f, 0.20f + 0.60f * t)
            val mapeado = h.map(punto)
            assertTrue("x fuera: ${mapeado.x}", mapeado.x in -1e-3f..1.001f)
            assertTrue("y fuera: ${mapeado.y}", mapeado.y in -1e-3f..1.001f)
        }
    }

    @Test
    fun `tres esquinas en linea recta no dan matriz`() {
        // Es el cuadrilatero que no se puede enderezar. Devuelve null en lugar de lanzar
        // porque esto corre con lo que venga de la camara, y una excepcion en el proceso
        // del launcher deja el telefono sin pantalla de inicio.
        val degenerado = Quad(
            topLeft = ScanPoint(0f, 0f),
            topRight = ScanPoint(0.5f, 0f),
            bottomRight = ScanPoint(1f, 0f),
            bottomLeft = ScanPoint(0f, 1f),
        )
        assertNull(Homography.between(degenerado, unidad))
    }

    @Test
    fun `dos esquinas encimadas tampoco`() {
        val degenerado = Quad(
            topLeft = ScanPoint(0.3f, 0.3f),
            topRight = ScanPoint(0.3f, 0.3f),
            bottomRight = ScanPoint(0.7f, 0.7f),
            bottomLeft = ScanPoint(0.3f, 0.7f),
        )
        assertNull(Homography.between(degenerado, unidad))
    }

    @Test
    fun `una esquina en el origen no rompe la eliminacion`() {
        // Regresion del pivoteo parcial: sin el, la fila con un cero justo en la diagonal
        // —que es lo que da una esquina en (0,0)— dividia por cero.
        val pegadoAlOrigen = Quad(
            topLeft = ScanPoint(0f, 0f),
            topRight = ScanPoint(0.8f, 0.1f),
            bottomRight = ScanPoint(0.9f, 0.9f),
            bottomLeft = ScanPoint(0.1f, 0.8f),
        )
        val h = Homography.between(pegadoAlOrigen, unidad)
        assertNotNull(h)
        assertTrue(h!!.toRowMajor().all { abs(it) < Float.MAX_VALUE && !it.isNaN() })
    }

    @Test
    fun `la matriz sale en el formato que espera Canvas`() {
        // Nueve coeficientes por filas, con el ultimo fijado a 1: es lo que permite que la
        // vista previa la dibuje la GPU en vez de rehacer el bitmap en cada movimiento.
        val valores = Homography.between(trapecio, unidad)!!.toRowMajor()
        assertEquals(9, valores.size)
        assertEquals(1f, valores[8], 0f)
    }
}
