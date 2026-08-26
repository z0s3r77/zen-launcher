package com.zenlauncher.zen.domain.reading

/**
 * Como se ve el texto. Puro y sin Android: los escalones y sus limites son una decision
 * de producto, no de la pantalla que los pinta.
 *
 * Se guardan **escalones** y no medidas. Un "16,5 sp" en las preferencias es un numero
 * que nadie eligio y que no se puede subir ni bajar sin decidir cuanto es un paso; con
 * escalones, cada toque es un paso y los extremos estan acotados de fabrica. Ademas
 * sobrevive a cambiar la tipografia: si manana el cuerpo base cambia, los ajustes de
 * quien ya leia siguen significando lo mismo.
 *
 * Los ajustes son **del lector, no del libro**: quien encuentra su tamano de letra lo
 * quiere en todos, y guardarlo por libro obligaria a recolocarlo en cada importacion.
 */
data class ReadingSettings(
    val textStep: Int = DEFAULT_TEXT,
    val leadingStep: Int = DEFAULT_LEADING,
    val marginStep: Int = DEFAULT_MARGIN,
    /**
     * Serif para leer, la de siempre para el resto.
     *
     * Es el unico sitio de Zen donde entra una tipografia que no es Archivo ni DM Mono,
     * y entra porque el trabajo es otro: la lengua visual del launcher esta hecha para
     * rotulos de dos palabras que se miran de reojo, y aqui hay paginas seguidas de
     * prosa. Se puede apagar.
     */
    val serif: Boolean = true,
) {
    /** Cuerpo del texto en sp. Escala ademas con el `fontScale` del sistema. */
    val fontSizeSp: Float get() = TEXT_MIN_SP + textStep.coerceIn(0, TEXT_STEPS) * TEXT_STEP_SP

    /**
     * Interlineado como multiplo del cuerpo, no como valor absoluto: subir la letra sin
     * subir el interlineado junta las lineas y es exactamente lo contrario de lo que
     * busca quien acaba de agrandar el texto.
     */
    val lineHeightRatio: Float
        get() = LEADING_MIN + leadingStep.coerceIn(0, LEADING_STEPS) * LEADING_STEP

    /** Margen lateral en dp. */
    val marginDp: Int get() = MARGIN_MIN_DP + marginStep.coerceIn(0, MARGIN_STEPS) * MARGIN_STEP_DP

    fun withText(step: Int) = copy(textStep = step.coerceIn(0, TEXT_STEPS))

    fun withLeading(step: Int) = copy(leadingStep = step.coerceIn(0, LEADING_STEPS))

    fun withMargin(step: Int) = copy(marginStep = step.coerceIn(0, MARGIN_STEPS))

    companion object {
        const val TEXT_STEPS = 6
        const val LEADING_STEPS = 4
        const val MARGIN_STEPS = 4

        const val DEFAULT_TEXT = 2
        const val DEFAULT_LEADING = 2
        const val DEFAULT_MARGIN = 2

        private const val TEXT_MIN_SP = 15f
        private const val TEXT_STEP_SP = 1.5f
        private const val LEADING_MIN = 1.4f
        private const val LEADING_STEP = 0.15f
        private const val MARGIN_MIN_DP = 16
        private const val MARGIN_STEP_DP = 8
    }
}
