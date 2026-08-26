package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.scanner.Corners
import com.zenlauncher.zen.domain.scanner.Quad
import com.zenlauncher.zen.domain.scanner.ScanPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ordenar y validar las cuatro esquinas.
 *
 * Se prueba con numeros escritos a mano y no con una foto: lo que OpenCV entrega es un
 * poligono en el orden en que fue trazando el contorno, y convertirlo en "arriba
 * izquierda, arriba derecha, abajo derecha, abajo izquierda" es geometria pura.
 */
class CornersTest {

    private val topLeft = ScanPoint(0.15f, 0.10f)
    private val topRight = ScanPoint(0.85f, 0.12f)
    private val bottomRight = ScanPoint(0.88f, 0.90f)
    private val bottomLeft = ScanPoint(0.12f, 0.88f)

    @Test
    fun `cuatro puntos desordenados salen en el orden de siempre`() {
        // Es la razon de ser de esta funcion: dos esquinas intercambiadas dan un documento
        // del reves o reflejado, y no hay ningun error visible que lo delate.
        val ordered = Corners.order(listOf(bottomRight, topLeft, bottomLeft, topRight))

        assertNotNull(ordered)
        assertEquals(topLeft, ordered!!.topLeft)
        assertEquals(topRight, ordered.topRight)
        assertEquals(bottomRight, ordered.bottomRight)
        assertEquals(bottomLeft, ordered.bottomLeft)
    }

    @Test
    fun `empezar el contorno por cualquier esquina da el mismo resultado`() {
        // El trazado de contornos empieza por donde encuentra el primer pixel de borde,
        // que depende de la iluminacion: el orden de salida no puede depender de eso.
        val entrada = listOf(topLeft, topRight, bottomRight, bottomLeft)
        val esperado = Corners.order(entrada)

        for (giro in 1 until entrada.size) {
            val rotada = entrada.drop(giro) + entrada.take(giro)
            assertEquals("Girando $giro posiciones", esperado, Corners.order(rotada))
        }
    }

    @Test
    fun `un contorno recorrido al reves tambien se ordena bien`() {
        // `findContours` devuelve unos contornos en sentido horario y otros al reves,
        // segun sean el borde exterior de una mancha clara o de una oscura.
        assertEquals(
            Corners.order(listOf(topLeft, topRight, bottomRight, bottomLeft)),
            Corners.order(listOf(topLeft, bottomLeft, bottomRight, topRight)),
        )
    }

    @Test
    fun `sin cuatro puntos no hay cuadrilatero`() {
        assertNull(Corners.order(listOf(topLeft, topRight, bottomRight)))
        assertNull(Corners.order(listOf(topLeft, topRight, bottomRight, bottomLeft, topLeft)))
        assertNull(Corners.order(emptyList()))
    }

    @Test
    fun `dos esquinas encimadas no son un cuadrilatero`() {
        // Es un contorno que se cerro sobre si mismo. Ordenarlo por angulo alrededor del
        // centro daria un orden arbitrario, y el enderezado saldria retorcido.
        assertNull(
            Corners.order(listOf(topLeft, topLeft.copy(x = topLeft.x + 0.001f), bottomRight, bottomLeft)),
        )
    }

    @Test
    fun `una hoja inclinada sigue siendo plausible`() {
        // Lo que se descarta no son las hojas torcidas —esas son justo las que hay que
        // poder enderezar— sino las formas que no pueden ser una hoja.
        val inclinada = Quad(
            topLeft = ScanPoint(0.30f, 0.10f),
            topRight = ScanPoint(0.92f, 0.26f),
            bottomRight = ScanPoint(0.72f, 0.90f),
            bottomLeft = ScanPoint(0.10f, 0.70f),
        )
        assertTrue(Corners.plausible(inclinada))
    }

    @Test
    fun `un cuadrilatero cruzado no vale`() {
        // Sale cuando el trazado une el borde de la hoja con la sombra de al lado. Un
        // reloj de arena no es convexo y enderezarlo da una imagen doblada por la mitad.
        val cruzado = Quad(
            topLeft = ScanPoint(0.10f, 0.10f),
            topRight = ScanPoint(0.90f, 0.10f),
            bottomRight = ScanPoint(0.10f, 0.90f),
            bottomLeft = ScanPoint(0.90f, 0.90f),
        )
        assertFalse(cruzado.convex)
        assertFalse(Corners.plausible(cruzado))
    }

    @Test
    fun `una mancha diminuta no vale aunque tenga cuatro esquinas`() {
        assertFalse(Corners.plausible(Quad.inset(0.48f)))
    }

    @Test
    fun `una tira alargadisima no es un documento`() {
        // El canto de una mesa o el marco de una puerta dan cuadrilateros perfectos.
        val tira = Quad(
            topLeft = ScanPoint(0.02f, 0.45f),
            topRight = ScanPoint(0.98f, 0.45f),
            bottomRight = ScanPoint(0.98f, 0.52f),
            bottomLeft = ScanPoint(0.02f, 0.52f),
        )
        assertFalse(Corners.plausible(tira))
    }

    @Test
    fun `el area es la fraccion de imagen que ocupa`() {
        // Media imagen de ancho por media de alto es la cuarta parte. Es lo que mira la
        // captura automatica para saber si hay que acercarse.
        val cuarto = Quad(
            topLeft = ScanPoint(0.25f, 0.25f),
            topRight = ScanPoint(0.75f, 0.25f),
            bottomRight = ScanPoint(0.75f, 0.75f),
            bottomLeft = ScanPoint(0.25f, 0.75f),
        )
        assertEquals(0.25f, cuarto.areaFraction, 0.0001f)
    }

    @Test
    fun `el desplazamiento entre frames es el de la esquina que mas se mueve`() {
        // La media taparia una esquina que baila sola, que es justo lo que pasa cuando el
        // borde de la hoja se confunde con el canto de la mesa.
        val quieta = Quad.inset(0.1f)
        val unaEsquinaSuelta = quieta.withCorner(2, ScanPoint(0.9f, 0.5f))

        assertEquals(0.4f, quieta.maxCornerShift(unaEsquinaSuelta), 0.0001f)
    }

    @Test
    fun `girar cuatro veces deja el cuadrilatero como estaba`() {
        val original = Quad(topLeft, topRight, bottomRight, bottomLeft)
        var girado = original
        repeat(4) { girado = girado.rotatedClockwise() }
        assertEquals(original, girado)
    }
}
