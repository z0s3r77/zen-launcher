package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.scanner.Corners
import com.zenlauncher.zen.domain.scanner.DocumentAspect
import com.zenlauncher.zen.domain.scanner.Quad
import com.zenlauncher.zen.domain.scanner.ScanPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Recuperar la proporcion real de una hoja vista en escorzo.
 *
 * Es la comprobacion de que "la transformacion mantiene las proporciones". Se hace
 * **sintetizando la proyeccion**: se toma un rectangulo de proporcion conocida, se inclina
 * sobre los dos ejes y se proyecta con una camara estenopeica inventada, y se comprueba
 * que de las cuatro esquinas sale la proporcion de partida. No hace falta ni una foto ni
 * un dispositivo, y a cambio se puede recorrer un abanico de posturas que a mano no se
 * conseguiria repetir.
 *
 * Si esto se rompiera, un A4 fotografiado de lado saldria aplastado o estirado y **nada en
 * la pantalla lo delataria**: el documento se ve igual de recto.
 */
class DocumentAspectTest {

    /** A4 vertical: 210 x 297 mm. */
    private val a4 = 210f / 297f

    /** El mismo A4 tumbado. */
    private val a4Apaisado = 297f / 210f

    @Test
    fun `un folio de frente da su propia proporcion`() {
        val quad = proyectar(ratio = a4, inclinacionX = 0f, inclinacionY = 0f)
        assertEquals(a4, DocumentAspect.rectified(quad, IMAGEN_ASPECTO), 0.01f)
    }

    @Test
    fun `un folio inclinado sobre los dos ejes da su proporcion`() {
        // La postura normal de quien fotografia un papel sobre la mesa: ni de frente ni
        // alineado con ningun eje. Aqui hay dos puntos de fuga y la solucion cerrada se
        // puede aplicar entera.
        val quad = proyectar(ratio = a4, inclinacionX = 30f, inclinacionY = 20f)
        assertEquals(a4, DocumentAspect.rectified(quad, IMAGEN_ASPECTO), 0.02f)
    }

    @Test
    fun `y con una inclinacion fuerte, que es donde mas se nota`() {
        val quad = proyectar(ratio = a4, inclinacionX = 45f, inclinacionY = 30f)
        assertEquals(a4, DocumentAspect.rectified(quad, IMAGEN_ASPECTO), 0.03f)
    }

    @Test
    fun `un folio apaisado no se confunde con uno vertical`() {
        val quad = proyectar(ratio = a4Apaisado, inclinacionX = 25f, inclinacionY = 18f)
        val recuperada = DocumentAspect.rectified(quad, IMAGEN_ASPECTO)

        assertEquals(a4Apaisado, recuperada, 0.03f)
        assertTrue("Deberia salir apaisado, salio $recuperada", recuperada > 1f)
    }

    @Test
    fun `alineado con un eje, la focal deja de deducirse y se supone`() {
        // Caso degenerado y **nada raro**: quien apoya los codos y mira la mesa desde
        // arriba inclina sobre un solo eje. Entonces uno de los dos pares de lados sale
        // paralelo en la imagen, no tiene punto de fuga y la focal no esta en la foto.
        // Se supone la de una camara de movil corriente, que es lo que hace esta camara
        // sintetizada, y el resultado vuelve a ser exacto.
        for (inclinacion in listOf(20f, 35f, 50f)) {
            val quad = proyectar(ratio = a4, inclinacionX = inclinacion, inclinacionY = 0f)
            assertEquals(
                "Inclinada $inclinacion grados sobre un solo eje",
                a4,
                DocumentAspect.rectified(quad, IMAGEN_ASPECTO),
                0.02f,
            )
        }
    }

    @Test
    fun `con una camara distinta de la supuesta se sigue acertando mas que midiendo`() {
        // El caso peor de todos: postura degenerada Y una camara que no se parece a la
        // supuesta. Ni asi conviene rendirse y medir los lados, que es la alternativa
        // ingenua. Este test es la justificacion de que exista una focal supuesta.
        for (focalReal in listOf(0.60, 1.30)) {
            for (inclinacion in listOf(35f, 50f)) {
                val quad = proyectar(a4, inclinacion, 0f, focalRatio = focalReal)

                val errorRecuperado = abs(DocumentAspect.rectified(quad, IMAGEN_ASPECTO) - a4)
                val errorMedido = abs(DocumentAspect.widthOverHeight(quad, IMAGEN_ASPECTO) - a4)

                assertTrue(
                    "focal $focalReal, inclinacion $inclinacion: " +
                        "recuperado $errorRecuperado deberia fallar menos que medido $errorMedido",
                    errorRecuperado < errorMedido,
                )
            }
        }
    }

    @Test
    fun `medir los lados a ojo se equivoca donde la solucion cerrada acierta`() {
        // La comparacion de las dos sobre la misma hoja: es lo que justifica que haya una
        // solucion cerrada en lugar de la media de los lados.
        val quad = proyectar(ratio = a4, inclinacionX = 40f, inclinacionY = 25f)

        val errorAparente = abs(DocumentAspect.widthOverHeight(quad, IMAGEN_ASPECTO) - a4)
        val errorRecuperado = abs(DocumentAspect.rectified(quad, IMAGEN_ASPECTO) - a4)

        assertTrue(
            "Aparente fallo $errorAparente y recuperada $errorRecuperado",
            errorAparente > errorRecuperado * 3f,
        )
    }

    @Test
    fun `un cuadrilatero imposible degrada en lugar de reventar`() {
        // Cuatro esquinas que no pueden ser un rectangulo visto por ninguna camara. Sale
        // un numero acotado en vez de un NaN paseandose por el proceso del launcher.
        val imposible = Quad(
            topLeft = ScanPoint(0.10f, 0.10f),
            topRight = ScanPoint(0.90f, 0.11f),
            bottomRight = ScanPoint(0.95f, 0.20f),
            bottomLeft = ScanPoint(0.05f, 0.88f),
        )
        val resultado = DocumentAspect.rectified(imposible, IMAGEN_ASPECTO)

        assertTrue("Salio $resultado", resultado.isFinite())
        assertTrue(resultado in Corners.MIN_ASPECT..Corners.MAX_ASPECT)
    }

    @Test
    fun `el tamano de salida respeta la proporcion recuperada`() {
        val quad = proyectar(ratio = a4, inclinacionX = 35f, inclinacionY = 22f)
        val (ancho, alto) = DocumentAspect.targetSize(
            quad = quad,
            sourceWidth = ANCHO_PX,
            sourceHeight = ALTO_PX,
            maxEdge = 2400,
        )
        assertEquals(a4, ancho.toFloat() / alto, 0.03f)
    }

    @Test
    fun `el tamano de salida nunca pasa del limite`() {
        // El limite existe porque esto corre en el proceso del launcher: un warp sin acotar
        // reserva decenas de megabytes y el sistema mata a Zen antes que a nadie.
        val (ancho, alto) = DocumentAspect.targetSize(
            quad = Quad.inset(0.01f),
            sourceWidth = 4000,
            sourceHeight = 3000,
            maxEdge = 1200,
        )
        assertTrue("$ancho x $alto", maxOf(ancho, alto) <= 1200)
        assertTrue(ancho > 0 && alto > 0)
    }

    @Test
    fun `una imagen sin tamano no revienta`() {
        val (ancho, alto) = DocumentAspect.targetSize(Quad.inset(0.1f), 0, 0, 2400)
        assertEquals(1, ancho)
        assertEquals(1, alto)
    }

    /**
     * Proyecta un rectangulo de proporcion `ratio`, girado sobre los dos ejes.
     *
     * Camara estenopeica: se gira el rectangulo alrededor del eje X y luego del Y, se aleja
     * y se divide por la profundidad. Girar sobre **los dos** es lo que crea los dos puntos
     * de fuga que la solucion cerrada necesita; con uno solo se cae al camino de la focal
     * supuesta, que es justo lo que comprueba otro test de aqui.
     *
     * @param focalRatio focal como fraccion del lado largo de la imagen. Por defecto, la
     *   misma que supone [DocumentAspect] cuando no puede deducirla.
     */
    private fun proyectar(
        ratio: Float,
        inclinacionX: Float,
        inclinacionY: Float,
        focalRatio: Double = FOCAL_SUPUESTA,
    ): Quad {
        val alto = 1.0
        val ancho = alto * ratio
        val ax = Math.toRadians(inclinacionX.toDouble())
        val ay = Math.toRadians(inclinacionY.toDouble())
        val focal = focalRatio * maxOf(ANCHO_PX, ALTO_PX)

        val proyectadas = listOf(
            -ancho / 2 to -alto / 2,
            ancho / 2 to -alto / 2,
            ancho / 2 to alto / 2,
            -ancho / 2 to alto / 2,
        ).map { (x, y) ->
            val yGirado = y * cos(ax)
            var z = y * sin(ax)
            val xGirado = x * cos(ay) + z * sin(ay)
            z = -x * sin(ay) + z * cos(ay) + DISTANCIA

            ScanPoint(
                x = (0.5 + (focal * xGirado / z) / ANCHO_PX).toFloat(),
                y = (0.5 + (focal * yGirado / z) / ALTO_PX).toFloat(),
            )
        }

        return Quad(
            topLeft = proyectadas[0],
            topRight = proyectadas[1],
            bottomRight = proyectadas[2],
            bottomLeft = proyectadas[3],
        )
    }

    private companion object {
        const val ANCHO_PX = 1200
        const val ALTO_PX = 1600
        val IMAGEN_ASPECTO = ANCHO_PX.toFloat() / ALTO_PX

        /** La misma que supone el codigo cuando la foto no la revela: unos 60 grados. */
        const val FOCAL_SUPUESTA = 0.85

        /** Distancia de la camara al papel, en alturas de papel. */
        const val DISTANCIA = 2.2
    }
}
