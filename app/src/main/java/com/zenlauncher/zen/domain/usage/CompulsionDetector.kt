package com.zenlauncher.zen.domain.usage

/**
 * La forma que tiene la conducta, no su gravedad.
 *
 * Las tres se parecen en el total del dia y no se parecen en nada mas, y por eso el
 * aviso las nombra: decirle a alguien "llevas mucho movil" no le dice nada; decirle
 * "has abierto esto siete veces en media hora" le dice exactamente lo que acaba de
 * hacer.
 */
enum class CompulsionKind {
    /** Una sola sentada muy larga en la misma aplicacion. */
    ARRASTRE,

    /** La misma aplicacion abierta una y otra vez en poco rato. */
    REPETICION,

    /** Saltar de aplicacion en aplicacion sin quedarse en ninguna. */
    PICOTEO,
}

/**
 * Un patron detectado, con los numeros que lo sostienen.
 *
 * Lleva sus propias cifras porque el aviso las ensena: sin ellas seria una acusacion
 * sin pruebas, y lo que hace que alguien se pare es reconocerse en el dato.
 */
data class Compulsion(
    val kind: CompulsionKind,
    /** null solo en [CompulsionKind.PICOTEO]: ahi no sobra una aplicacion, sobra el salto. */
    val packageName: String?,
    val openings: Int,
    val foregroundMillis: Long,
    val windowMinutes: Int,
)

/**
 * Mira el ultimo rato y dice si hay una conducta compulsiva, o null.
 *
 * Funcion pura sobre una lista de aperturas: ni consulta al sistema ni sabe que hora es
 * mas alla del `nowMillis` que se le pasa, asi que se prueba entera en la JVM.
 *
 * **Solo mira ventanas cortas, nunca el total del dia.** El total ya lo cuenta
 * [UsagePressure] y responde a otra pregunta. Una conducta compulsiva es un
 * comportamiento *concentrado*: cuatro horas repartidas por el dia no son lo mismo que
 * cuarenta minutos seguidos, aunque el reloj diga cuatro horas en los dos casos.
 *
 * **Precedencia: arrastre, repeticion, picoteo.** Es el orden de lo mas claro a lo mas
 * discutible. Un arrastre de cuarenta minutos no admite otra lectura; el picoteo puede
 * ser perfectamente alguien trabajando entre dos aplicaciones, y por eso es el ultimo y
 * el mas dificil de disparar.
 */
object CompulsionDetector {

    /** Ventana y minimo del arrastre: una sentada de 40 minutos dentro de la ultima hora. */
    const val DRAG_WINDOW_MINUTES = 60
    const val DRAG_MINUTES = 40

    /** Repeticion: cinco aperturas de la misma aplicacion en media hora. */
    const val REPEAT_WINDOW_MINUTES = 30
    const val REPEAT_OPENINGS = 5

    /** Picoteo: doce cambios de aplicacion en un cuarto de hora. */
    const val SCATTER_WINDOW_MINUTES = 15
    const val SCATTER_OPENINGS = 12

    /**
     * ...y ademas, tiempo de verdad delante de la pantalla.
     *
     * Doce visitas que suman un minuto no son picoteo compulsivo: son avisos, consultas
     * de un segundo y transiciones del sistema. Visto en un Nothing Phone (2a) recien
     * instalado: el aviso salto con "12 APERTURAS · 1m", que se lee solo como lo que
     * era, un falso positivo. Picotear es saltar **y quedarse un rato en total**.
     */
    const val SCATTER_MIN_MINUTES = 5

    /**
     * @param exempt paquetes que no cuentan para el arrastre ni para la repeticion. Son
     *   las aplicaciones "que no quitan tiempo" de [com.zenlauncher.zen.domain.apps.EssentialApps]:
     *   una hora de navegador GPS conduciendo, o una llamada larga, son tiempo de
     *   pantalla y no son una recaida. Para el picoteo si cuentan, porque ahi lo que se
     *   mide es el salto, no donde se aterriza.
     */
    fun detect(
        openings: List<AppOpening>,
        nowMillis: Long,
        exempt: Set<String> = emptySet(),
    ): Compulsion? {
        val candidates = openings.filter { it.packageName !in exempt }

        drag(candidates, nowMillis)?.let { return it }
        repeat(candidates, nowMillis)?.let { return it }
        return scatter(openings, nowMillis)
    }

    private fun drag(openings: List<AppOpening>, nowMillis: Long): Compulsion? {
        // Se filtra por cuando **acabo**, no por cuando empezo: una sentada que arranco
        // hace hora y media y sigue viva es justo el caso que hay que cazar, y filtrando
        // por el inicio se caia fuera de la ventana.
        val longest = openings
            .filter { it.atMillis + it.foregroundMillis >= nowMillis - minutes(DRAG_WINDOW_MINUTES) }
            .maxByOrNull { it.foregroundMillis }
            ?: return null

        if (longest.foregroundMillis < minutes(DRAG_MINUTES)) return null

        return Compulsion(
            kind = CompulsionKind.ARRASTRE,
            packageName = longest.packageName,
            openings = 1,
            foregroundMillis = longest.foregroundMillis,
            windowMinutes = DRAG_WINDOW_MINUTES,
        )
    }

    private fun repeat(openings: List<AppOpening>, nowMillis: Long): Compulsion? {
        val window = openings.filter { it.atMillis >= nowMillis - minutes(REPEAT_WINDOW_MINUTES) }
        val worst = window
            .groupBy { it.packageName }
            // Con dos aplicaciones empatadas gana la que mas tiempo se llevo: es la que
            // el usuario va a reconocer.
            .maxByOrNull { (_, list) -> list.size * 1_000_000L + list.sumOf { it.foregroundMillis } }
            ?: return null

        val (packageName, list) = worst
        if (list.size < REPEAT_OPENINGS) return null

        return Compulsion(
            kind = CompulsionKind.REPETICION,
            packageName = packageName,
            openings = list.size,
            foregroundMillis = list.sumOf { it.foregroundMillis },
            windowMinutes = REPEAT_WINDOW_MINUTES,
        )
    }

    private fun scatter(openings: List<AppOpening>, nowMillis: Long): Compulsion? {
        val window = openings.filter { it.atMillis >= nowMillis - minutes(SCATTER_WINDOW_MINUTES) }
        if (window.size < SCATTER_OPENINGS) return null
        if (window.sumOf { it.foregroundMillis } < minutes(SCATTER_MIN_MINUTES)) return null
        // Abrir doce veces la misma aplicacion no es picoteo, es repeticion, y esa ya se
        // descarto antes (o estaba exenta). Sin esto, una aplicacion exenta abierta doce
        // veces se colaba por la puerta de atras como si fueran doce aplicaciones.
        if (window.distinctBy { it.packageName }.size < 2) return null

        return Compulsion(
            kind = CompulsionKind.PICOTEO,
            packageName = null,
            openings = window.size,
            foregroundMillis = window.sumOf { it.foregroundMillis },
            windowMinutes = SCATTER_WINDOW_MINUTES,
        )
    }

    private fun minutes(value: Int): Long = value * 60_000L
}
