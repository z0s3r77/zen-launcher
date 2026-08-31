package com.zenlauncher.zen.presentation.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * El unico sitio desde el que se pide un color en toda la aplicacion.
 *
 * Cada tono es una lectura de [active], que es estado de Compose: cambiar de tema
 * invalida a todo el que lo haya leido —composicion **y** dibujo— y la pantalla entera
 * se repinta sola con la paleta nueva. Por eso `ZenColors.Foreground` sigue
 * escribiendose igual que cuando la paleta era una constante, y por eso funciona
 * tambien dentro de un `Canvas`.
 *
 * Es estado global de proceso, que normalmente seria un error, y aqui es lo correcto por
 * dos razones. Una: Zen es un launcher de una sola Activity, asi que "el tema activo" es
 * literalmente una propiedad del proceso y no del arbol de composicion. Otra: la
 * alternativa idiomatica —un `CompositionLocal`— **no se puede leer dentro de un
 * `DrawScope`**, y media docena de piezas (la curva de Respira, los glifos del
 * reproductor, el editor de esquinas del escaner) pintan dentro de un `Canvas`; habria
 * que sacar cada color a mano por encima del dibujo en cada una de ellas, y el primer
 * `Canvas` que alguien anadiera sin acordarse se quedaria con el tema anterior sin que
 * nada fallara.
 *
 * Quien lo fija es [ZenTheme], y nadie mas.
 */
object ZenColors {

    /**
     * Se escribe solo desde [ZenTheme], y desde su `SideEffect`: escribirlo durante la
     * composicion invalidaria a los hijos que acaban de leerlo en esa misma pasada, que
     * es un bucle de recomposicion.
     */
    internal var active: ZenPalette by mutableStateOf(ZenPalette.Negro)

    val Background: Color get() = active.background
    val Hairline: Color get() = active.hairline
    val Border: Color get() = active.border
    val Faint: Color get() = active.faint

    /** Solo para marcas y trazos; nunca para texto. */
    val Disabled: Color get() = active.disabled

    val Dim: Color get() = active.dim
    val Muted: Color get() = active.muted
    val Secondary: Color get() = active.secondary
    val Tertiary: Color get() = active.tertiary
    val Foreground: Color get() = active.foreground

    /**
     * El texto de un libro, y **solo** el texto de un libro.
     *
     * Mas apagado que [Foreground] a proposito. El resto de Zen son rotulos de dos
     * palabras que se miran de reojo y quieren maximo contraste; aqui hay media hora
     * seguida de prosa sobre negro puro, y en AMOLED un blanco de 12,6:1 sobre negro
     * absoluto deslumbra y deja rastro al desplazar. Sigue muy por encima de AA (fijado
     * en ZenPaletteTest, en las dos paletas): apagado no es ilegible.
     */
    val Reading: Color get() = active.reading

    /**
     * Unico acento. Solo en indicadores de estado restringido.
     *
     * No entra en [ZenPalette] y por tanto **no cambia con el tema**: no es un tono de la
     * escala, es un significado. Los dos temas comparten fondo negro, asi que su
     * contraste es el mismo en ambos y no hay nada que reajustar.
     */
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
     * pantalla que se mira cincuenta veces al dia. Este cumple AA (5,4:1). Como el
     * ambar, es significado y no aspecto: no cambia con el tema.
     */
    val Danger = Color(0xFFE5484D)
}
