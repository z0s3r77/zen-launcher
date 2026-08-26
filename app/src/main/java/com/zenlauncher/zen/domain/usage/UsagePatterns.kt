package com.zenlauncher.zen.domain.usage

/** Que forma tiene el habito, mirando varios dias en lugar de un rato. */
enum class PatternKind {
    /** Una sola aplicacion se lleva una parte desproporcionada de la semana. */
    LADRONA,

    /** Una aplicacion que se abre muchisimas veces al dia, dure lo que dure. */
    REPETIDA,

    /** Los ultimos dias van claramente a mas que los anteriores. */
    SUBIENDO,
}

/**
 * Lo que Zen puede hacer al respecto, y solo eso.
 *
 * No hay una accion inventada por patron: son las palancas que la aplicacion **ya**
 * tiene. Una recomendacion que no lleva a ninguna parte es un sermon, y Zen no da
 * sermones; una que lleva a la pantalla de restringidas con la aplicacion senalada es
 * una recomendacion de verdad.
 */
enum class PatternAction {
    /** Llevar a Aplicaciones restringidas. */
    RESTRINGIR,

    /** Nada que ofrecer: es una observacion, y se queda en observacion. */
    NINGUNA,
}

data class UsagePattern(
    val kind: PatternKind,
    /** null en [PatternKind.SUBIENDO]: ahi el problema no es una aplicacion. */
    val packageName: String?,
    /** El numero que lo sostiene: el porcentaje, las veces al dia o la subida. */
    val value: Int,
    /** Media diaria de esa aplicacion. Cero en [PatternKind.SUBIENDO]. */
    val dailyMillis: Long,
    val action: PatternAction,
)

/** Como esta la semana, en una palabra. Es la respuesta a "¿esto lo tengo controlado?". */
enum class WeekVerdict { BAJO_CONTROL, ATENCION, FUERA_DE_MANO }

/**
 * Lee varios dias y saca hasta tres observaciones, cada una anclada a un numero real.
 *
 * Funcion pura sobre [WeeklyUsage]: se prueba entera en la JVM.
 *
 * **Tres reglas de diseno, y las tres son restricciones sobre lo que NO puede hacer:**
 *
 * 1. **Ninguna observacion sin su cifra.** "Usas mucho Instagram" no es informacion;
 *    "Instagram se lleva el 47% de tu semana" si. Lo que hace que alguien cambie algo es
 *    reconocer el dato, no que le digan lo que tiene que hacer.
 * 2. **Como mucho tres, y ordenadas por gravedad.** Una lista de doce hallazgos no se
 *    lee: se cierra. Si hay que elegir, gana la que mas tiempo se lleva.
 * 3. **Nunca dos observaciones sobre la misma aplicacion.** Decir "Instagram se lleva el
 *    47%" y debajo "abres Instagram 34 veces al dia" es el mismo hallazgo escrito dos
 *    veces, y hace parecer que hay dos problemas donde hay uno.
 *
 * Y una regla sobre cuando callarse: **por debajo de [MIN_DAYS] dias medidos no se
 * concluye nada**. Un solo dia no es un habito; llamar "ladrona" a una aplicacion porque
 * ayer viste una serie seria adivinar.
 */
object UsagePatterns {

    /** Menos de dos dias no es un patron, es un dia. */
    const val MIN_DAYS = 2

    const val MAX_PATTERNS = 3

    /** Ladrona: se lleva mas de un tercio de la semana **y** tiempo de verdad al dia. */
    const val THIEF_PERCENT = 35
    const val THIEF_DAILY_MINUTES = 45

    /** Repetida: se abre una vez cada media hora despierto, dia tras dia. */
    const val REPEATED_DAILY_OPENINGS = 20

    /** Subiendo: los dos ultimos dias por encima de los anteriores en este porcentaje. */
    const val RISING_PERCENT = 25

    /**
     * @param exclude paquetes sobre los que no se opina. Van aqui los ya restringidos:
     *   recomendar restringir lo que ya esta restringido es ruido.
     */
    fun of(week: WeeklyUsage, exclude: Set<String> = emptySet()): List<UsagePattern> {
        if (week.measuredDays.size < MIN_DAYS) return emptyList()

        val patterns = mutableListOf<UsagePattern>()
        val candidates = week.apps.filter { it.packageName !in exclude }

        thief(week, candidates)?.let { patterns += it }
        // Nunca dos observaciones sobre la misma aplicacion: si ya salio como ladrona,
        // se busca la siguiente que se repita, no se repite ella.
        val named = patterns.mapNotNull { it.packageName }.toSet()
        repeated(week, candidates.filter { it.packageName !in named })?.let { patterns += it }
        rising(week)?.let { patterns += it }

        return patterns.take(MAX_PATTERNS)
    }

    /** Como esta la semana: los umbrales del dia, aplicados a la media diaria. */
    fun verdict(week: WeeklyUsage): WeekVerdict {
        if (!week.hasData) return WeekVerdict.BAJO_CONTROL
        return when (UsagePressure.level(week.averageMillis, week.averageUnlocks)) {
            UsageLevel.CALMA, UsageLevel.NORMAL -> WeekVerdict.BAJO_CONTROL
            UsageLevel.ALTA -> WeekVerdict.ATENCION
            UsageLevel.EXCESO -> WeekVerdict.FUERA_DE_MANO
        }
    }

    private fun thief(week: WeeklyUsage, candidates: List<AppUsage>): UsagePattern? {
        val top = candidates.firstOrNull() ?: return null
        val share = week.shareOf(top)
        val daily = week.dailyMillis(top)

        // Las dos condiciones, no una: en una semana muy tranquila la aplicacion mas
        // usada se lleva el 60% de casi nada, y eso no es una ladrona de tiempo.
        if (share < THIEF_PERCENT) return null
        if (daily < THIEF_DAILY_MINUTES * 60_000L) return null

        return UsagePattern(
            kind = PatternKind.LADRONA,
            packageName = top.packageName,
            value = share,
            dailyMillis = daily,
            action = PatternAction.RESTRINGIR,
        )
    }

    private fun repeated(week: WeeklyUsage, candidates: List<AppUsage>): UsagePattern? {
        val worst = candidates.maxByOrNull { week.dailyOpenings(it) } ?: return null
        val daily = week.dailyOpenings(worst)
        if (daily < REPEATED_DAILY_OPENINGS) return null

        return UsagePattern(
            kind = PatternKind.REPETIDA,
            packageName = worst.packageName,
            value = daily,
            dailyMillis = week.dailyMillis(worst),
            action = PatternAction.RESTRINGIR,
        )
    }

    /**
     * Los dos ultimos dias medidos contra los anteriores.
     *
     * Se comparan medias y no totales: con tres dias antes y dos despues, los totales
     * dirian que la semana baja siempre.
     */
    private fun rising(week: WeeklyUsage): UsagePattern? {
        val measured = week.measuredDays
        // Hacen falta dos dias recientes y al menos dos con los que comparar.
        if (measured.size < 4) return null

        val recent = measured.takeLast(2)
        val before = measured.dropLast(2)
        val recentAverage = recent.sumOf { it.screenMillis } / recent.size
        val beforeAverage = before.sumOf { it.screenMillis } / before.size
        if (beforeAverage <= 0L) return null

        val rise = (((recentAverage - beforeAverage) * 100) / beforeAverage).toInt()
        if (rise < RISING_PERCENT) return null

        return UsagePattern(
            kind = PatternKind.SUBIENDO,
            packageName = null,
            value = rise,
            dailyMillis = 0L,
            action = PatternAction.NINGUNA,
        )
    }
}
