package com.zenlauncher.zen.domain.system

/**
 * Cuando un arrastre desde un borde lateral cuenta como "volver".
 *
 * El problema que resuelve: con las barras ocultas ([SystemBarsPolicy]), Android se
 * queda **el primer** deslizamiento desde el borde para sacarlas, y solo el segundo
 * llega como gesto de atras. El usuario tiene que deslizar dos veces siempre.
 *
 * Como el sistema, ademas de sacar las barras, **entrega el mismo gesto a la
 * aplicacion** —lo mismo que pasaba en el borde inferior—, Zen puede reconocerlo por su
 * cuenta y volver al primer intento. No es un apano: es la
 * unica forma de que el gesto responda cuando toca sin renunciar a ocultar las barras.
 *
 * Los dos bordes valen, cada uno hacia dentro: desde la izquierda se arrastra a la
 * derecha y desde la derecha a la izquierda. Zurdos y diestros sujetan el telefono de
 * forma distinta, y Android acepta los dos.
 */
object EdgeBackPolicy {

    /**
     * Ancho de la franja lateral que escucha el gesto.
     *
     * Mas ancha que la zona de gestos de Android (~20dp) a proposito: el dedo que viene
     * del marco entra ya con velocidad, y el primer punto que la pantalla registra
     * suele estar unos pixeles dentro. Con 20dp se perdian arrastres que el usuario
     * habia empezado en el borde.
     */
    const val EDGE_DP = 32

    /** Recorrido minimo hacia dentro. Por debajo es un roce al agarrar el telefono. */
    const val THRESHOLD_DP = 56

    /**
     * @param startX donde toco el dedo, en pixeles desde la izquierda.
     * @param width ancho de la zona que escucha el gesto, en pixeles.
     * @param edge ancho de la franja lateral, en pixeles.
     * @param dragged recorrido horizontal acumulado; positivo hacia la derecha.
     * @param threshold recorrido minimo para que cuente.
     */
    fun goesBack(
        startX: Float,
        width: Float,
        edge: Float,
        dragged: Float,
        threshold: Float,
    ): Boolean = when {
        startX <= edge -> dragged >= threshold
        startX >= width - edge -> dragged <= -threshold
        // Desde el centro no: ahi el arrastre horizontal no significa nada, y hacerlo
        // valer convertiria cualquier roce sobre la retícula en una vuelta atras.
        else -> false
    }
}
