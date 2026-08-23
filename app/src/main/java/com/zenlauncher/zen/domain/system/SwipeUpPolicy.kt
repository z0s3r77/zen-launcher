package com.zenlauncher.zen.domain.system

/**
 * Cuando un deslizamiento hacia arriba abre la lista de aplicaciones, y cuando no.
 *
 * El problema que resuelve: Zen oculta la barra de gestos, pero **Android sigue
 * quedandose el borde inferior**. Deslizar ahi saca la barra un momento (ver
 * [SystemBarsPolicy]) y ademas entrega el mismo gesto a la aplicacion, asi que la lista
 * de aplicaciones se abria sola cada vez que el usuario solo queria recuperar la barra.
 * Un gesto del sistema no puede tener efectos secundarios en Zen: si el usuario va al
 * borde, va al sistema.
 *
 * La franja de abajo se reserva entera, aunque el deslizamiento sea largo y acabe muy
 * arriba: lo que decide es **donde empieza**, que es lo unico que distingue "quiero la
 * barra" de "quiero la lista".
 */
object SwipeUpPolicy {

    /**
     * Alto minimo de la franja del sistema cuando el dispositivo no lo dice.
     *
     * Con las barras ocultas hay ROM que devuelve cero en los insets de gestos, y creer
     * ese cero dejaria el borde otra vez sin proteger. 48dp es el alto habitual de la
     * zona de gestos de Android y coincide con el minimo tactil.
     */
    const val MIN_SYSTEM_EDGE_DP = 48

    /**
     * @param startY donde empezo el arrastre, en pixeles desde arriba.
     * @param height alto de la zona que escucha el gesto, en pixeles.
     * @param systemEdge alto de la franja inferior reservada al sistema, en pixeles.
     * @param dragged recorrido vertical acumulado; negativo hacia arriba.
     * @param threshold recorrido minimo para que cuente como gesto y no como roce.
     */
    fun opensDrawer(
        startY: Float,
        height: Float,
        systemEdge: Float,
        dragged: Float,
        threshold: Float,
    ): Boolean = dragged <= -threshold && !startsInSystemEdge(startY, height, systemEdge)

    private fun startsInSystemEdge(startY: Float, height: Float, systemEdge: Float): Boolean =
        startY >= height - systemEdge
}
