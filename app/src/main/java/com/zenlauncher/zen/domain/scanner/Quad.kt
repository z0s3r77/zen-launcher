package com.zenlauncher.zen.domain.scanner

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Un punto en coordenadas **normalizadas**: 0..1 sobre el ancho y el alto de la imagen.
 *
 * No son pixeles a proposito. La deteccion corre sobre un frame reducido (ver
 * [DocumentDetector]), el overlay se pinta sobre una vista previa de otro tamano y el
 * recorte final se aplica sobre la foto a resolucion completa: son tres rejillas de
 * pixeles distintas para la misma hoja. Guardando la fraccion, el mismo cuadrilatero
 * vale para las tres y no hay ni una conversion que se pueda olvidar.
 */
data class ScanPoint(val x: Float, val y: Float) {

    fun distanceTo(other: ScanPoint): Float = hypot(x - other.x, y - other.y)

    companion object {
        fun fromPixels(x: Float, y: Float, width: Int, height: Int): ScanPoint =
            ScanPoint(if (width > 0) x / width else 0f, if (height > 0) y / height else 0f)
    }
}

/**
 * Las cuatro esquinas de la hoja, **siempre en este orden**.
 *
 * El orden no es un detalle de estilo: la transformacion de perspectiva empareja cada
 * esquina de origen con una del rectangulo de destino, y dos esquinas intercambiadas dan
 * un documento del reves o reflejado sin ningun aviso. Por eso el unico camino para
 * construir uno desde una deteccion es [Corners.order], que ordena; el constructor se
 * usa cuando el orden ya se conoce (el usuario arrastrando una esquina concreta).
 */
data class Quad(
    val topLeft: ScanPoint,
    val topRight: ScanPoint,
    val bottomRight: ScanPoint,
    val bottomLeft: ScanPoint,
) {

    /** En el mismo orden en que se declaran: horario empezando arriba a la izquierda. */
    val points: List<ScanPoint> get() = listOf(topLeft, topRight, bottomRight, bottomLeft)

    /**
     * Fraccion de la imagen que ocupa la hoja, entre 0 y 1.
     *
     * Formula del cordon de zapato. Se usa en valor absoluto porque el signo solo dice
     * el sentido de giro, y aqui ya viene fijado por [Corners.order].
     */
    val areaFraction: Float
        get() {
            val p = points
            var sum = 0f
            for (i in p.indices) {
                val a = p[i]
                val b = p[(i + 1) % p.size]
                sum += a.x * b.y - b.x * a.y
            }
            return abs(sum) / 2f
        }

    /** Convexo si los cuatro productos vectoriales consecutivos tienen el mismo signo. */
    val convex: Boolean
        get() {
            val p = points
            var positive = false
            var negative = false
            for (i in p.indices) {
                val a = p[i]
                val b = p[(i + 1) % p.size]
                val c = p[(i + 2) % p.size]
                val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
                if (cross > 0f) positive = true
                if (cross < 0f) negative = true
                // Un cruce a cero es una esquina alineada con sus vecinas: no rompe la
                // convexidad, asi que no se cuenta ni a favor ni en contra.
            }
            return !(positive && negative)
        }

    /**
     * El coseno mas grande en valor absoluto de los cuatro angulos interiores.
     *
     * Se devuelve el coseno y no el angulo para no pagar cuatro arcocosenos por frame:
     * un coseno de 0 es un angulo recto y uno cercano a 1 es una esquina degenerada, que
     * es exactamente lo que hay que descartar. Ver [Corners.RECTANGULARITY_LIMIT].
     */
    val worstCornerCosine: Float
        get() {
            val p = points
            var worst = 0f
            for (i in p.indices) {
                val prev = p[(i + 3) % p.size]
                val here = p[i]
                val next = p[(i + 1) % p.size]
                val ax = prev.x - here.x
                val ay = prev.y - here.y
                val bx = next.x - here.x
                val by = next.y - here.y
                val magnitude = hypot(ax, ay) * hypot(bx, by)
                if (magnitude <= 0f) return 1f
                worst = max(worst, abs((ax * bx + ay * by) / magnitude))
            }
            return worst
        }

    /** El lado mas corto de los cuatro. Un lado minusculo es ruido, no una hoja. */
    val shortestSide: Float
        get() {
            val p = points
            var shortest = Float.MAX_VALUE
            for (i in p.indices) {
                shortest = min(shortest, p[i].distanceTo(p[(i + 1) % p.size]))
            }
            return shortest
        }

    /**
     * Cuanto se ha movido respecto a otro cuadrilatero: el mayor desplazamiento de una
     * esquina, en fraccion de imagen. Es la medida de quietud entre frames.
     *
     * El **mayor** y no la media a proposito: con la media, una esquina que baila sola
     * —justo lo que pasa cuando el borde de la hoja se confunde con el canto de la
     * mesa— queda tapada por las otras tres quietas y la captura automatica dispararia
     * con una esquina mal puesta.
     */
    fun maxCornerShift(other: Quad): Float {
        val a = points
        val b = other.points
        var worst = 0f
        for (i in a.indices) worst = max(worst, a[i].distanceTo(b[i]))
        return worst
    }

    /** Gira las esquinas un cuarto de vuelta en el sentido de las agujas del reloj. */
    fun rotatedClockwise(): Quad = Quad(
        topLeft = bottomLeft,
        topRight = topLeft,
        bottomRight = topRight,
        bottomLeft = bottomRight,
    )

    /** Sustituye una esquina concreta. Es lo que hace arrastrar en la pantalla de edicion. */
    fun withCorner(index: Int, point: ScanPoint): Quad = when (index) {
        0 -> copy(topLeft = point)
        1 -> copy(topRight = point)
        2 -> copy(bottomRight = point)
        3 -> copy(bottomLeft = point)
        else -> this
    }

    // No hay ningun "dame la esquina mas cercana" aqui, y es deliberado: quien lo
    // necesita es la pantalla de ajustar esquinas, y ahi la cercania hay que medirla en
    // **pixeles de pantalla**, no en fraccion de imagen. Con la fraccion, el radio para
    // agarrar una esquina seria mas ancho que alto en una imagen apaisada. Ver
    // `CornerEditor`.

    /** Recorta las cuatro esquinas al interior de la imagen. */
    fun clampedToImage(): Quad = Quad(
        topLeft = topLeft.clamped(),
        topRight = topRight.clamped(),
        bottomRight = bottomRight.clamped(),
        bottomLeft = bottomLeft.clamped(),
    )

    companion object {
        /** El cuadrilatero de partida de la edicion manual: un margen sobre toda la imagen. */
        fun inset(margin: Float): Quad {
            val low = margin.coerceIn(0f, 0.49f)
            val high = 1f - low
            return Quad(
                topLeft = ScanPoint(low, low),
                topRight = ScanPoint(high, low),
                bottomRight = ScanPoint(high, high),
                bottomLeft = ScanPoint(low, high),
            )
        }
    }
}

private fun ScanPoint.clamped(): ScanPoint =
    ScanPoint(x.coerceIn(0f, 1f), y.coerceIn(0f, 1f))
