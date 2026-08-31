package com.zenlauncher.zen.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.zenlauncher.zen.domain.model.ZenThemeChoice

/**
 * Los tonos de un tema, ordenados **por luminosidad**.
 *
 * El orden no es decorativo: la paleta es monocroma, asi que toda la jerarquia visual de
 * Zen es contraste y nada mas. De [background] a [foreground] la escala sube de forma
 * monotona, y `ZenPaletteTest` lo comprueba en las dos paletas —dos tonos intercambiados
 * romperian la jerarquia entera sin que ningun test de pantalla se enterase—.
 *
 * El corte esta entre [disabled] y [dim]: por debajo son filetes, bordes y marcas huecas
 * que **no llevan texto** y se quedan a proposito por debajo de 3:1; de [dim] en adelante
 * todos llevan texto y cumplen AA (4,5:1) sobre el fondo.
 */
@Immutable
data class ZenPalette(
    val background: Color,
    val hairline: Color,
    val border: Color,
    val faint: Color,
    /** Solo para marcas y trazos; nunca para texto. */
    val disabled: Color,
    val dim: Color,
    val muted: Color,
    val secondary: Color,
    val tertiary: Color,
    /** El texto de un libro, y **solo** el texto de un libro. Ver [ZenColors.Reading]. */
    val reading: Color,
    val foreground: Color,
) {
    companion object {
        /**
         * El tema de siempre: negro puro y grises apretados contra el fondo.
         *
         * El Phone (2a) es AMOLED: con 0x000000 el pixel se apaga del todo, asi que el
         * fondo —que ocupa casi toda la pantalla en Zen— no consume nada. Un #0A0A0B
         * enciende cada subpixel un poco para nada.
         */
        val Negro = ZenPalette(
            background = Color(0xFF000000),
            hairline = Color(0xFF1B1B1E),
            border = Color(0xFF232326),
            faint = Color(0xFF303034),
            disabled = Color(0xFF46464B),
            // A partir de aqui todos los tonos llevan texto. Dim estaba en #56565B
            // (2,88:1) y Muted en #6E6E73 (4,14:1): ambos quedaban por debajo del
            // minimo legible.
            dim = Color(0xFF747479),
            muted = Color(0xFF808086),
            secondary = Color(0xFF8A8A8F),
            tertiary = Color(0xFFA9A9AD),
            reading = Color(0xFFC9C9C4),
            foreground = Color(0xFFEAEAE7),
        )

        /**
         * Los grises con los que Android pinta sus propios paneles.
         *
         * **El fondo sigue siendo negro puro**, y eso es lo que hace que este tema sea
         * viable: la pantalla de inicio se mira cincuenta veces al dia y se queda
         * encendida mientras se elige aplicacion, asi que subirle el fondo a un gris
         * costaria bateria en cada una de esas veces. Lo que sube es todo lo demas.
         *
         * A cambio la escala se separa mucho mas del fondo que en [Negro]: superficies
         * que se ven como superficies (#1C1C1E a #48484A, los tonos de los mosaicos de
         * ajustes rapidos) y texto en blanco puro en vez de un hueso apagado. Es un tema
         * mas contrastado, no mas claro.
         */
        val Sistema = ZenPalette(
            background = Color(0xFF000000),
            hairline = Color(0xFF1C1C1E),
            border = Color(0xFF2C2C2E),
            faint = Color(0xFF3A3A3C),
            disabled = Color(0xFF48484A),
            dim = Color(0xFF8E8E93),
            muted = Color(0xFF98989D),
            secondary = Color(0xFFAEAEB2),
            tertiary = Color(0xFFC7C7CC),
            // Entre tertiary y foreground, igual que en Negro: media hora de prosa
            // seguida no se lee con el blanco de un rotulo de dos palabras.
            reading = Color(0xFFD8D8DD),
            foreground = Color(0xFFFFFFFF),
        )

        fun of(choice: ZenThemeChoice): ZenPalette = when (choice) {
            ZenThemeChoice.NEGRO -> Negro
            ZenThemeChoice.SISTEMA -> Sistema
        }
    }
}
