package com.zenlauncher.zen.domain.scanner

import kotlin.math.abs

/**
 * La transformacion de perspectiva, en Kotlin puro.
 *
 * OpenCV ya sabe hacer esto —y es quien mueve los pixeles de verdad, ver
 * `OpenCvImageProcessor`—, pero la matriz hace falta ademas en dos sitios donde llamar a
 * codigo nativo seria absurdo o imposible:
 *
 * 1. **La vista previa de la edicion manual.** Mientras el dedo arrastra una esquina hay
 *    que redibujar; volver a deformar un bitmap de dos megapixeles en cada movimiento del
 *    dedo daria tirones. Con la matriz aqui se dibuja el bitmap original ya deformado por
 *    Canvas, que lo hace la GPU, y solo al soltar se rehace de verdad.
 * 2. **Los tests.** Nada de lo nativo se puede ejecutar en la JVM, asi que sin esto no
 *    habria forma de comprobar que enderezar un trapecio da un rectangulo sin un
 *    dispositivo delante.
 *
 * Se resuelve el sistema de ocho ecuaciones por eliminacion de Gauss con pivoteo
 * parcial. Ocho incognitas —la novena se fija a 1, porque la matriz es homogenea y solo
 * importa salvo escala— y cuatro puntos, que dan exactamente ocho ecuaciones.
 */
class Homography private constructor(private val h: DoubleArray) {

    /** Lleva un punto del espacio de origen al de destino. */
    fun map(point: ScanPoint): ScanPoint {
        val x = point.x.toDouble()
        val y = point.y.toDouble()
        val w = h[6] * x + h[7] * y + 1.0
        if (abs(w) < EPSILON) return point
        return ScanPoint(
            x = ((h[0] * x + h[1] * y + h[2]) / w).toFloat(),
            y = ((h[3] * x + h[4] * y + h[5]) / w).toFloat(),
        )
    }

    /**
     * Los nueve coeficientes en fila, como los espera `android.graphics.Matrix`.
     *
     * Ese es el formato de Canvas, y es lo que permite que la vista previa de la edicion
     * manual la dibuje la GPU en lugar de rehacer el bitmap en cada movimiento del dedo.
     */
    fun toRowMajor(): FloatArray = floatArrayOf(
        h[0].toFloat(), h[1].toFloat(), h[2].toFloat(),
        h[3].toFloat(), h[4].toFloat(), h[5].toFloat(),
        h[6].toFloat(), h[7].toFloat(), 1f,
    )

    companion object {
        /**
         * La matriz que lleva las cuatro esquinas de `source` a las de `destination`,
         * cada una a la que le toca por orden.
         *
         * Devuelve null si el sistema es degenerado —tres esquinas en linea recta, dos
         * encimadas—, que es exactamente el cuadrilatero que no se puede enderezar. Se
         * devuelve null en lugar de lanzar porque esto corre con lo que venga de la
         * camara: una excepcion aqui deja el telefono sin pantalla de inicio.
         */
        fun between(source: Quad, destination: Quad): Homography? {
            val from = source.points
            val to = destination.points

            // Ocho filas de nueve columnas: ocho coeficientes mas el termino independiente.
            val matrix = Array(8) { DoubleArray(9) }
            for (i in 0 until 4) {
                val x = from[i].x.toDouble()
                val y = from[i].y.toDouble()
                val u = to[i].x.toDouble()
                val v = to[i].y.toDouble()

                val even = matrix[i * 2]
                even[0] = x; even[1] = y; even[2] = 1.0
                even[3] = 0.0; even[4] = 0.0; even[5] = 0.0
                even[6] = -x * u; even[7] = -y * u; even[8] = u

                val odd = matrix[i * 2 + 1]
                odd[0] = 0.0; odd[1] = 0.0; odd[2] = 0.0
                odd[3] = x; odd[4] = y; odd[5] = 1.0
                odd[6] = -x * v; odd[7] = -y * v; odd[8] = v
            }

            val solution = solve(matrix) ?: return null
            return Homography(solution)
        }

        /** Gauss con pivoteo parcial. Null si la matriz es singular. */
        private fun solve(matrix: Array<DoubleArray>): DoubleArray? {
            val size = matrix.size
            for (column in 0 until size) {
                // Pivoteo parcial: sin el, una fila con un cero justo en la diagonal
                // —el caso de una esquina en el origen— dividiria por cero.
                var pivot = column
                for (row in column + 1 until size) {
                    if (abs(matrix[row][column]) > abs(matrix[pivot][column])) pivot = row
                }
                if (abs(matrix[pivot][column]) < EPSILON) return null
                if (pivot != column) {
                    val swap = matrix[pivot]
                    matrix[pivot] = matrix[column]
                    matrix[column] = swap
                }

                val diagonal = matrix[column][column]
                for (row in 0 until size) {
                    if (row == column) continue
                    val factor = matrix[row][column] / diagonal
                    if (factor == 0.0) continue
                    for (k in column..size) {
                        matrix[row][k] -= factor * matrix[column][k]
                    }
                }
            }

            val result = DoubleArray(size)
            for (i in 0 until size) {
                val diagonal = matrix[i][i]
                if (abs(diagonal) < EPSILON) return null
                result[i] = matrix[i][size] / diagonal
                if (!result[i].isFinite()) return null
            }
            return result
        }

        private const val EPSILON = 1e-12
    }
}
