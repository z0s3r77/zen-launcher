package com.zenlauncher.zen.domain.scanner

/**
 * Cuando disparar solo.
 *
 * Puro y sin Android, que es lo que permite probar la regla entera —"cuatro esquinas
 * fiables, tamano suficiente, movil quieto y sin saltos entre frames"— sin una camara
 * delante. Recibe una deteccion por frame y devuelve el estado siguiente; no guarda nada
 * por su cuenta, asi que el ViewModel puede reiniciarlo con solo tirar el estado.
 *
 * La condicion de "sin cambios bruscos" se mide como el mayor desplazamiento de una
 * esquina respecto al frame anterior, y se exige mantenida durante varios frames
 * seguidos. Un solo frame quieto no significa nada: la mano pasa por el punto de
 * inversion de su propio temblor sesenta veces por segundo.
 */
object CaptureDecision {

    /**
     * Fraccion minima de la imagen que tiene que ocupar la hoja para disparar.
     *
     * Muy por encima de [Corners.MIN_AREA_FRACTION], que es la de **dibujar** el marco.
     * Son dos numeros distintos a proposito: enseñar el marco de una hoja lejana es lo
     * que le dice al usuario que se acerque, y dispararle a esa misma hoja daria un
     * documento de cien pixeles de ancho.
     */
    const val MIN_COVERAGE = 0.25f

    /**
     * Cuanto puede haberse movido una esquina entre dos frames y seguir contando como
     * quieto: el 1,5 % del alto de la imagen.
     *
     * Cuenta el desplazamiento de la esquina que mas se mueve, no la media. Ver
     * [Quad.maxCornerShift].
     */
    const val MAX_CORNER_SHIFT = 0.015f

    /**
     * Frames seguidos cumpliendolo todo antes de disparar.
     *
     * Diez, sobre un analisis limitado a unos 15 frames por segundo, son dos tercios de
     * segundo largos de hoja quieta. Menos y dispara mientras el usuario todavia esta
     * encuadrando; mas y parece que no funciona.
     */
    const val REQUIRED_STEADY_FRAMES = 10

    /**
     * El estado que se arrastra de un frame al siguiente.
     *
     * @param quad la ultima deteccion que valia, o null si no habia hoja.
     * @param steadyFrames cuantos frames seguidos lleva quieta y encuadrada.
     * @param hint por que no se dispara todavia, en algo que se pueda escribir.
     */
    data class State(
        val quad: Quad? = null,
        val steadyFrames: Int = 0,
        val hint: CaptureHint = CaptureHint.SEARCHING,
    ) {
        /** Se dispara justo en el frame en que se cumple la cuenta, ni antes ni despues. */
        val readyToCapture: Boolean
            get() = hint == CaptureHint.READY && steadyFrames >= REQUIRED_STEADY_FRAMES

        val phase: ScanPhase
            get() = when {
                readyToCapture -> ScanPhase.READY_TO_CAPTURE
                quad != null -> ScanPhase.DOCUMENT_DETECTED
                else -> ScanPhase.DETECTING
            }
    }

    /**
     * Un frame.
     *
     * @param detected el cuadrilatero ya ordenado que devolvio la deteccion, o null.
     * @param imageAspect ancho entre alto del frame analizado. Ver [DocumentAspect].
     * @param deviceStill si el acelerometro dice que el movil esta quieto. Es una
     *   condicion **aparte** de que las esquinas no bailen: apuntando a una hoja con
     *   poco contraste las esquinas se quedan clavadas en un borde equivocado aunque la
     *   mano se mueva, y al reves, un movil apoyado en la mesa con una sombra pasando por
     *   encima da esquinas que saltan. Hacen falta las dos.
     */
    fun next(
        previous: State,
        detected: Quad?,
        imageAspect: Float,
        deviceStill: Boolean,
    ): State {
        if (detected == null || !Corners.plausible(detected)) {
            // Se suelta el cuadrilatero anterior en lugar de dejarlo pintado: un marco
            // que se queda sobre una hoja que ya no esta es peor que ningun marco.
            return State(quad = null, steadyFrames = 0, hint = CaptureHint.SEARCHING)
        }

        if (detected.areaFraction < MIN_COVERAGE) {
            return State(quad = detected, steadyFrames = 0, hint = CaptureHint.TOO_FAR)
        }

        val shift = previous.quad?.maxCornerShift(detected)
        val steady = shift != null && shift <= MAX_CORNER_SHIFT && deviceStill

        return State(
            quad = detected,
            // Al perder la quietud la cuenta vuelve a cero, no baja un escalon: media
            // cuenta guardada de un encuadre que ya se abandono dispararia antes de
            // tiempo en el siguiente.
            steadyFrames = if (steady) previous.steadyFrames + 1 else 0,
            hint = if (steady) CaptureHint.READY else CaptureHint.HOLD_STILL,
        )
    }
}
