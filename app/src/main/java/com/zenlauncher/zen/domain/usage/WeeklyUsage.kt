package com.zenlauncher.zen.domain.usage

/**
 * Los ultimos dias, del mas antiguo al mas reciente.
 *
 * **No se guarda nada en disco para tener esto.** Android conserva sus propios eventos
 * de uso durante unos dias y Zen los vuelve a pedir cada vez que se abre la pantalla de
 * la semana; lo que se lee vive en memoria mientras dura la consulta y se va con ella.
 * Guardar un historico propio significaria escribir en disco que hace el usuario con
 * cada aplicacion, dia a dia, y eso es justo lo que Zen no quiere tener.
 *
 * El precio es que **la ventana no la decide Zen**: si el sistema ya no guarda un dia,
 * ese dia llega sin medir y se dice, en lugar de dibujar un cero que seria mentira.
 */
data class WeeklyUsage(val days: List<UsageSnapshot>) {

    /** Solo los dias de los que el sistema conservaba eventos. */
    val measuredDays: List<UsageSnapshot> get() = days.filter { it.measured }

    val hasData: Boolean get() = measuredDays.isNotEmpty()

    val totalMillis: Long get() = measuredDays.sumOf { it.screenMillis }

    /**
     * Media **por dia medido**, no por dia de la semana.
     *
     * Dividir siempre entre siete diluiria la media en un telefono que solo conserva
     * tres dias, y ensenaria una semana tranquila que nunca ocurrio.
     */
    val averageMillis: Long
        get() = if (measuredDays.isEmpty()) 0L else totalMillis / measuredDays.size

    val averageUnlocks: Int
        get() = if (measuredDays.isEmpty()) 0 else measuredDays.sumOf { it.unlocks } / measuredDays.size

    /** El dia con mas pantalla, para poder escalar la grafica y senalarlo. */
    val busiestMillis: Long get() = measuredDays.maxOfOrNull { it.screenMillis } ?: 0L

    /** El de hoy: el ultimo de la lista. */
    val today: UsageSnapshot? get() = days.lastOrNull()

    /** Sumado por aplicacion en todos los dias medidos, de mas a menos tiempo. */
    val apps: List<AppUsage> by lazy {
        measuredDays
            .flatMap { it.apps }
            .groupBy { it.packageName }
            .map { (packageName, list) ->
                AppUsage(
                    packageName = packageName,
                    openings = list.sumOf { it.openings },
                    foregroundMillis = list.sumOf { it.foregroundMillis },
                )
            }
            .sortedByDescending { it.foregroundMillis }
    }

    /** Que parte del tiempo total se lleva una aplicacion, en tanto por ciento. */
    fun shareOf(app: AppUsage): Int =
        if (totalMillis <= 0L) 0 else ((app.foregroundMillis * 100) / totalMillis).toInt()

    /** Media diaria de aperturas de una aplicacion. */
    fun dailyOpenings(app: AppUsage): Int =
        if (measuredDays.isEmpty()) 0 else app.openings / measuredDays.size

    /** Media diaria de tiempo de una aplicacion. */
    fun dailyMillis(app: AppUsage): Long =
        if (measuredDays.isEmpty()) 0L else app.foregroundMillis / measuredDays.size
}
