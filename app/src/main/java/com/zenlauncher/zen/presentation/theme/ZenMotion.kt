package com.zenlauncher.zen.presentation.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically

/**
 * Las transiciones del sistema Industrial, en un solo sitio como los colores y el
 * espaciado.
 *
 * La regla de Zen sigue siendo la misma —**nada se anima por decoración**— y esto no la
 * rompe: aquí no hay ni una animación que corra sola, en bucle o para llamar la
 * atención. Todas duran lo que dura un cambio que el usuario acaba de provocar, y solo
 * existen para responder a una pregunta: *de dónde ha salido esto*. Una pantalla que
 * aparece de golpe obliga a releerla entera; una que entra desde su lado ya se ha
 * explicado antes de terminar de llegar.
 *
 * Por eso los tiempos son cortos: [EnterMillis] entrando y [ExitMillis] saliendo, por
 * debajo del umbral en el que un movimiento empieza a *sentirse* como una espera. Y por
 * eso los desplazamientos son fracciones pequeñas ([SlideDivisor]), no pantallas
 * enteras cruzando de lado a lado: lo que se mueve indica la dirección, no la recorre.
 *
 * Se acortan solas: Compose lee la escala de animación del sistema
 * (`animator_duration_scale`), así que quien la baja o la apaga en las opciones de
 * desarrollador —o desde accesibilidad— recibe la interfaz sin movimiento y sin que Zen
 * tenga que preguntar nada.
 */
object ZenMotion {

    /** Entrar cuesta un poco más que salir: lo que llega hay que poder seguirlo. */
    const val EnterMillis = 180

    /** Lo que se va no merece atención: se quita antes de que estorbe. */
    const val ExitMillis = 120

    /**
     * Arranque rápido y frenada larga. Es la curva de un objeto real al detenerse, y la
     * razón de que un desplazamiento corto se lea como movimiento y no como un salto.
     */
    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    /**
     * El desplazamiento es 1/10 de la dimensión, no la dimensión entera: basta para
     * decir de qué lado viene algo, y no obliga a esperar a que cruce la pantalla.
     */
    private const val SlideDivisor = 10

    private fun <T> entering(): FiniteAnimationSpec<T> = tween(EnterMillis, easing = Standard)

    private fun <T> leaving(): FiniteAnimationSpec<T> = tween(ExitMillis, easing = Standard)

    /** Abrir una pantalla: entra desde la derecha, como el sentido de la navegación. */
    val ScreenEnter: EnterTransition = fadeIn(entering()) +
        slideInHorizontally(entering()) { width -> width / SlideDivisor }

    /** La que se queda atrás cede el sitio hacia el lado contrario. */
    val ScreenExit: ExitTransition = fadeOut(leaving()) +
        slideOutHorizontally(leaving()) { width -> -width / SlideDivisor }

    /** Volver invierte el sentido: lo de antes vuelve por donde se fue. */
    val ScreenPopEnter: EnterTransition = fadeIn(entering()) +
        slideInHorizontally(entering()) { width -> -width / SlideDivisor }

    val ScreenPopExit: ExitTransition = fadeOut(leaving()) +
        slideOutHorizontally(leaving()) { width -> width / SlideDivisor }

    /**
     * Algo que aparece **en su sitio** y empuja lo de abajo, como el mando del
     * reproductor al empezar a sonar algo. Crece en alto en vez de aparecer entero, que
     * es lo que evita el salto brusco del resto de la pantalla.
     */
    val RevealEnter: EnterTransition = fadeIn(entering()) + expandVertically(entering())

    val RevealExit: ExitTransition = fadeOut(leaving()) + shrinkVertically(leaving())

    /**
     * Una cara de la pantalla sustituida por otra, como la home y su menú.
     *
     * Es direccional a propósito: el menú entra desde abajo —de donde sale la fila que
     * lo abre— y al cerrarse la home vuelve desde arriba. Con la misma transición en
     * los dos sentidos no se sabría si se está entrando o saliendo.
     */
    fun swap(openingMenu: Boolean): ContentTransform {
        val direction = if (openingMenu) 1 else -1
        val entrada = fadeIn(entering()) +
            slideInVertically(entering()) { height -> direction * height / SwapDivisor }
        val salida = fadeOut(leaving()) +
            slideOutVertically(leaving()) { height -> -direction * height / SwapDivisor }
        return ContentTransform(
            targetContentEnter = entrada,
            initialContentExit = salida,
            // Sin recortar: durante el cruce las dos caras se salen un poco de su caja,
            // y recortarlas dejaria un borde duro justo donde se esta mirando.
            sizeTransform = SizeTransform(clip = false),
        )
    }

    /**
     * El intercambio se desplaza menos todavía que una pantalla nueva: aquí no cambia
     * el sitio donde estás, solo lo que se ve dentro.
     */
    private const val SwapDivisor = 16
}
