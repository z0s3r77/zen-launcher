package com.zenlauncher.zen.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Paleta Industrial. Monocroma salvo un unico ambar apagado reservado a marcas de
 * estado de 6dp: nunca se usa como relleno, como fondo ni como senal de recompensa.
 *
 * Los tonos estan ordenados por luminosidad para que la jerarquia visual sea
 * exclusivamente de contraste. No hay dynamic color a proposito.
 */
object ZenColors {
    /**
     * Negro puro, no un gris muy oscuro.
     *
     * El Phone (2a) es AMOLED: con 0x000000 el pixel se apaga del todo, asi que el
     * fondo —que ocupa casi toda la pantalla en Zen— no consume nada. Un #0A0A0B
     * enciende cada subpixel un poco para nada.
     */
    val Background = Color(0xFF000000)
    val Hairline = Color(0xFF1B1B1E)
    val Border = Color(0xFF232326)
    val Faint = Color(0xFF303034)
    /** Solo para marcas y trazos; nunca para texto. */
    val Disabled = Color(0xFF46464B)

    // A partir de aqui todos los tonos llevan texto, asi que todos cumplen AA (4.5:1)
    // sobre el fondo negro. Dim estaba en #56565B (2.88:1) y Muted en #6E6E73 (4.14:1):
    // ambos quedaban por debajo del minimo legible.
    val Dim = Color(0xFF747479)
    val Muted = Color(0xFF808086)
    val Secondary = Color(0xFF8A8A8F)
    val Tertiary = Color(0xFFA9A9AD)
    val Foreground = Color(0xFFEAEAE7)

    /** Unico acento. Solo en indicadores de estado restringido. */
    val Accent = Color(0xFFB8894A)

    /**
     * Unico rojo, y **una sola fila lo lleva**: "Salir de Zen".
     *
     * No marca peligro ni error —nada se rompe al salir— sino la unica accion que te
     * saca de la aplicacion, entre seis que te mueven dentro de ella. En una lista
     * monocroma donde todas las filas se leen igual, el color es lo que evita pulsarla
     * por inercia al bajar el dedo.
     *
     * Rojo apagado y no puro: #FF0000 sobre negro vibra y tira de la vista en una
     * pantalla que se mira cincuenta veces al dia. Este cumple AA (5.4:1), fijado en
     * ZenColorsTest junto al resto de tonos que llevan texto.
     */
    val Danger = Color(0xFFE5484D)
}
