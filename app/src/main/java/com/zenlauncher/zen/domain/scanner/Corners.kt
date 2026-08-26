package com.zenlauncher.zen.domain.scanner

import kotlin.math.atan2

/**
 * Poner en orden las cuatro esquinas que devuelve la deteccion, y decidir si lo que se
 * ha encontrado se parece a una hoja.
 *
 * Puro y sin Android **aunque la deteccion la haga OpenCV**: lo que OpenCV entrega es un
 * poligono de cuatro vertices en el orden en que fue trazando el contorno, que depende
 * de por donde empezo. Convertir eso en "arriba izquierda, arriba derecha, abajo
 * derecha, abajo izquierda" es geometria, no vision por computador, y aqui se puede
 * probar contra numeros escritos a mano en lugar de contra una foto.
 */
object Corners {

    /**
     * Area minima para dibujar el cuadrilatero sobre la camara.
     *
     * Mas permisiva que la de capturar: mientras el usuario acerca el movil se le ensena
     * lo que hay detectado aunque todavia no sirva, porque ver el marco es lo que le dice
     * hacia donde moverse. Ver [CaptureDecision.MIN_COVERAGE].
     */
    const val MIN_AREA_FRACTION = 0.06f

    /**
     * Coseno maximo admitido en una esquina: 0,80 son unos 37 grados.
     *
     * No se exige que se parezca a un angulo recto. Una hoja fotografiada de lado tiene
     * angulos muy abiertos y muy cerrados a la vez, y esa es justo la foto que hay que
     * poder rectificar. Lo que este limite descarta son los triangulos aplastados que
     * salen cuando el trazado de contornos une el borde de la hoja con la sombra de al
     * lado, no las hojas inclinadas.
     */
    const val RECTANGULARITY_LIMIT = 0.80f

    /** Un lado por debajo de esto es ruido: la decima parte de la imagen. */
    const val MIN_SIDE_FRACTION = 0.10f

    /** Relacion de aspecto admitida del rectangulo ya enderezado, en las dos direcciones. */
    const val MIN_ASPECT = 0.20f
    const val MAX_ASPECT = 5.0f

    /**
     * Ordena cuatro puntos sueltos como [Quad].
     *
     * Los ordena por su angulo alrededor del centro —lo que los deja en sentido horario,
     * porque en coordenadas de imagen la Y crece hacia abajo— y luego rota la lista para
     * que empiece por la esquina de menor `x + y`, que es la de arriba a la izquierda.
     *
     * Con la hoja girada casi 45 grados esa eleccion es una moneda al aire y el documento
     * puede salir tumbado. Es sabido y no se corrige aqui: adivinar la orientacion de una
     * hoja en blanco es imposible sin leer lo que pone, y por eso la pantalla de revision
     * tiene un boton de rotar.
     *
     * Devuelve null si no son exactamente cuatro puntos o si estan tan juntos que el
     * angulo alrededor del centro no significa nada.
     */
    fun order(points: List<ScanPoint>): Quad? {
        if (points.size != 4) return null

        val centerX = points.sumOf { it.x.toDouble() }.toFloat() / points.size
        val centerY = points.sumOf { it.y.toDouble() }.toFloat() / points.size

        // Dos puntos en el mismo sitio no son un cuadrilatero: son un contorno que se
        // cerro sobre si mismo, y ordenarlos por angulo daria un orden arbitrario.
        for (i in points.indices) {
            for (j in i + 1 until points.size) {
                if (points[i].distanceTo(points[j]) < COINCIDENT_LIMIT) return null
            }
        }

        val clockwise = points.sortedBy { atan2(it.y - centerY, it.x - centerX) }
        val start = clockwise.indices.minByOrNull { clockwise[it].x + clockwise[it].y } ?: 0
        val rotated = List(points.size) { clockwise[(start + it) % points.size] }

        return Quad(
            topLeft = rotated[0],
            topRight = rotated[1],
            bottomRight = rotated[2],
            bottomLeft = rotated[3],
        )
    }

    /**
     * Si el cuadrilatero se parece lo bastante a una hoja como para ensenarlo.
     *
     * Enseñar no es capturar: aqui se comprueba la **forma** (convexo, sin esquinas
     * degeneradas, lados de un tamano razonable y una proporcion que pueda ser un
     * documento). Cuanto ocupa y si el movil esta quieto lo decide [CaptureDecision],
     * porque eso cambia frame a frame y esto no.
     */
    fun plausible(quad: Quad): Boolean {
        if (!quad.convex) return false
        if (quad.areaFraction < MIN_AREA_FRACTION) return false
        if (quad.shortestSide < MIN_SIDE_FRACTION) return false
        if (quad.worstCornerCosine > RECTANGULARITY_LIMIT) return false

        val aspect = DocumentAspect.widthOverHeight(quad)
        return aspect in MIN_ASPECT..MAX_ASPECT
    }

    /** Dos esquinas mas juntas que esto son la misma esquina contada dos veces. */
    private const val COINCIDENT_LIMIT = 0.01f
}
