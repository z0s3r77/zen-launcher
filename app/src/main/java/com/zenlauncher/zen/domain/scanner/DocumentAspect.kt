package com.zenlauncher.zen.domain.scanner

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Que forma tiene de verdad la hoja que se ve inclinada.
 *
 * Es el problema que hace falta resolver para cumplir "la transformacion debe mantener
 * las proporciones". Un A4 fotografiado de lado se proyecta como un trapecio, y estirar
 * ese trapecio hasta el rectangulo que envuelve sus esquinas da un documento **aplastado
 * o estirado**: los lados que estan mas lejos de la camara salen mas cortos de lo que
 * son, y la media de los lados hereda ese error.
 *
 * Lo que se hace aqui es recuperar la proporcion real a partir de la propia proyeccion,
 * con la solucion cerrada de Zhang y He: si se sabe que el original **era** un
 * rectangulo, la forma del trapecio contiene a la vez la distancia focal de la camara y
 * la relacion ancho/alto, y las dos se despejan sin calibrar nada ni pedir ningun dato
 * al sistema.
 *
 * Puro y probado contra proyecciones sintetizadas a mano: se toma un rectangulo de
 * proporcion conocida, se proyecta con una camara inventada y se comprueba que de aqui
 * sale la proporcion de partida. No hace falta ni una foto.
 *
 * ### Por que todo lleva `imageAspect`
 *
 * [ScanPoint] guarda fracciones del ancho y del alto, asi que en una imagen 4:3 un paso
 * horizontal de 0,1 mide mas que uno vertical de 0,1. La geometria de camara exige
 * pixeles cuadrados, asi que aqui dentro se trabaja siempre en unidades de **altura de
 * imagen**: la X se multiplica por `ancho / alto`. Sin ese paso, un folio recto delante
 * de la camara saldria con la proporcion de la pantalla, no con la suya.
 */
object DocumentAspect {

    /**
     * Relacion ancho/alto **real** del documento, deshaciendo la perspectiva.
     *
     * @param imageAspect ancho dividido entre alto de la imagen de la que salen las
     *   esquinas. Ver la nota de la clase: sin el, el resultado sale deformado.
     */
    fun rectified(quad: Quad, imageAspect: Float): Float {
        // El punto principal se supone en el centro de la imagen. Es la aproximacion
        // habitual y en un movil se aleja del centro unos pocos pixeles: para decidir si
        // un folio es 1,41 o 1,33 de proporcion, sobra de largo.
        val cx = imageAspect / 2.0
        val cy = 0.5

        // Homogeneos y centrados en el punto principal, en unidades de altura de imagen.
        val m1 = homogeneous(quad.topLeft, imageAspect, cx, cy)
        val m2 = homogeneous(quad.topRight, imageAspect, cx, cy)
        val m3 = homogeneous(quad.bottomLeft, imageAspect, cx, cy)
        val m4 = homogeneous(quad.bottomRight, imageAspect, cx, cy)

        val denominator2 = determinant(m2, m3, m4)
        val denominator3 = determinant(m3, m2, m4)
        if (abs(denominator2) < EPSILON || abs(denominator3) < EPSILON) {
            return fallback(quad, imageAspect)
        }

        val k2 = determinant(m1, m3, m4) / denominator2
        val k3 = determinant(m1, m2, m4) / denominator3

        val n2 = doubleArrayOf(
            k2 * m2[0] - m1[0],
            k2 * m2[1] - m1[1],
            k2 * m2[2] - m1[2],
        )
        val n3 = doubleArrayOf(
            k3 * m3[0] - m1[0],
            k3 * m3[1] - m1[1],
            k3 * m3[2] - m1[2],
        )

        val focalSquared = solveFocalSquared(n2, n3) ?: assumedFocalSquared(imageAspect)

        val width = (n2[0] * n2[0] + n2[1] * n2[1]) / focalSquared + n2[2] * n2[2]
        val height = (n3[0] * n3[0] + n3[1] * n3[1]) / focalSquared + n3[2] * n3[2]
        if (height <= EPSILON) return fallback(quad, imageAspect)

        val ratio = sqrt(width / height)
        if (!ratio.isFinite() || ratio <= EPSILON) return fallback(quad, imageAspect)

        // Se acota al mismo rango que acepta [Corners.plausible]. Un cuadrilatero torcido
        // puede dar aqui una proporcion de 40:1 sin que nada haya fallado, y con ella
        // [targetSize] pediria un bitmap de una fila de alto. Acotar en el origen evita
        // tener que acordarse de hacerlo en cada sitio que use este numero.
        return ratio.toFloat().coerceIn(Corners.MIN_ASPECT, Corners.MAX_ASPECT)
    }

    /**
     * La distancia focal que se deduce de la propia foto, o null si no se puede.
     *
     * Se puede casi siempre, y **no se puede en un caso muy concreto**: cuando uno de los
     * dos pares de lados opuestos sale paralelo en la imagen. Eso significa que ese par no
     * tiene punto de fuga —se va al infinito—, y con un solo punto de fuga la focal queda
     * sin determinar: no es que el calculo salga mal, es que la informacion no esta ahi.
     *
     * Pasa siempre que el movil este alineado con la hoja en uno de los dos ejes, que es
     * justo lo que hace quien apoya los codos y mira la mesa desde arriba. O sea, no es un
     * caso raro. Ver [assumedFocalSquared].
     */
    private fun solveFocalSquared(n2: DoubleArray, n3: DoubleArray): Double? {
        if (abs(n2[2]) < DEGENERATE_LIMIT || abs(n3[2]) < DEGENERATE_LIMIT) return null
        val candidate = -(n2[0] * n3[0] + n2[1] * n3[1]) / (n2[2] * n3[2])
        // Una focal negativa significa que las cuatro esquinas no pueden ser un rectangulo
        // visto por ninguna camara: el contorno se torcio.
        return candidate.takeIf { it.isFinite() && it > EPSILON }
    }

    /**
     * La focal que se supone cuando la foto no la revela.
     *
     * Es una suposicion declarada, no un numero magico: 0,85 veces el lado largo son unos
     * 60 grados de campo, que es la camara principal de un movil corriente. Un movil
     * concreto se apartara de ahi, y por eso este camino se usa **solo** cuando el otro no
     * existe.
     *
     * Suponerla es mucho mejor que rendirse y medir los lados. Comprobado en
     * `DocumentAspectTest` sobre camaras sintetizadas con focales muy distintas de esta: el
     * error se queda por debajo del 21 % en el peor caso, mientras que la media de los
     * lados llega al 53 %. Y en una foto de frente —sin fuga por ningun lado— el resultado
     * ni siquiera depende de este numero: la focal se simplifica y sale la proporcion
     * exacta.
     */
    private fun assumedFocalSquared(imageAspect: Float): Double {
        val longEdge = max(imageAspect.toDouble(), 1.0)
        val focal = ASSUMED_FOCAL_RATIO * longEdge
        return focal * focal
    }

    /**
     * Relacion ancho/alto **aparente**, midiendo los lados tal como se ven.
     *
     * No deshace la perspectiva: es solo para descartar de un vistazo lo que no puede ser
     * un documento (ver [Corners.plausible]). Barata y siempre definida, que es justo lo
     * que hace falta sesenta veces por segundo.
     */
    fun widthOverHeight(quad: Quad, imageAspect: Float = 1f): Float {
        val top = sideLength(quad.topLeft, quad.topRight, imageAspect)
        val bottom = sideLength(quad.bottomLeft, quad.bottomRight, imageAspect)
        val left = sideLength(quad.topLeft, quad.bottomLeft, imageAspect)
        val right = sideLength(quad.topRight, quad.bottomRight, imageAspect)

        val height = (left + right) / 2f
        if (height <= EPSILON) return 0f
        return ((top + bottom) / 2f) / height
    }

    /**
     * Tamano en pixeles de la hoja ya enderezada.
     *
     * Se toma el lado mas largo que se ve —no la media— para no **perder** resolucion: la
     * media dejaria el borde cercano a la camara, que es el que mas detalle tiene,
     * reducido hasta el tamano del borde lejano. A partir de ahi la otra dimension sale
     * de la proporcion real, no de medir, que es lo que mantiene las proporciones.
     *
     * `maxEdge` acota el resultado porque esto corre dentro del proceso del **launcher**:
     * un warp a 12 megapixeles son 48 MB de bitmap y el sistema mata al launcher antes
     * que a nadie.
     */
    fun targetSize(
        quad: Quad,
        sourceWidth: Int,
        sourceHeight: Int,
        maxEdge: Int,
    ): Pair<Int, Int> {
        if (sourceWidth <= 0 || sourceHeight <= 0) return 1 to 1
        val imageAspect = sourceWidth.toFloat() / sourceHeight

        val topPixels = sideLength(quad.topLeft, quad.topRight, imageAspect) * sourceHeight
        val bottomPixels = sideLength(quad.bottomLeft, quad.bottomRight, imageAspect) * sourceHeight
        val leftPixels = sideLength(quad.topLeft, quad.bottomLeft, imageAspect) * sourceHeight
        val rightPixels = sideLength(quad.topRight, quad.bottomRight, imageAspect) * sourceHeight

        val ratio = rectified(quad, imageAspect).coerceIn(Corners.MIN_ASPECT, Corners.MAX_ASPECT)

        val longestWidth = maxOf(topPixels, bottomPixels)
        val longestHeight = maxOf(leftPixels, rightPixels)

        // Se parte de la dimension mejor resuelta y la otra se deriva de la proporcion.
        var width: Float
        var height: Float
        if (longestWidth >= longestHeight * ratio) {
            width = longestWidth
            height = longestWidth / ratio
        } else {
            height = longestHeight
            width = longestHeight * ratio
        }

        val longest = maxOf(width, height)
        if (longest > maxEdge) {
            val scale = maxEdge / longest
            width *= scale
            height *= scale
        }

        return width.roundToInt().coerceAtLeast(1) to height.roundToInt().coerceAtLeast(1)
    }

    /** Cuando la solucion cerrada no se puede aplicar, se mide y ya. */
    private fun fallback(quad: Quad, imageAspect: Float): Float =
        widthOverHeight(quad, imageAspect).coerceIn(Corners.MIN_ASPECT, Corners.MAX_ASPECT)

    private fun homogeneous(
        point: ScanPoint,
        imageAspect: Float,
        centerX: Double,
        centerY: Double,
    ): DoubleArray = doubleArrayOf(
        point.x.toDouble() * imageAspect - centerX,
        point.y.toDouble() - centerY,
        1.0,
    )

    private fun determinant(a: DoubleArray, b: DoubleArray, c: DoubleArray): Double =
        a[0] * (b[1] * c[2] - b[2] * c[1]) -
            a[1] * (b[0] * c[2] - b[2] * c[0]) +
            a[2] * (b[0] * c[1] - b[1] * c[0])

    private fun sideLength(a: ScanPoint, b: ScanPoint, imageAspect: Float): Float =
        hypot((a.x - b.x) * imageAspect, a.y - b.y)

    private const val EPSILON = 1e-9

    /**
     * Por debajo de esto, la tercera componente se considera cero y el par de lados,
     * paralelo. No es una tolerancia numerica al azar: marca la frontera del caso en el
     * que la focal deja de poder deducirse. Ver [solveFocalSquared].
     */
    private const val DEGENERATE_LIMIT = 1e-4

    /** Unos 60 grados de campo. Ver [assumedFocalSquared]. */
    private const val ASSUMED_FOCAL_RATIO = 0.85
}
