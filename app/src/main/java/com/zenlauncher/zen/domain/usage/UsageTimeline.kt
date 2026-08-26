package com.zenlauncher.zen.domain.usage

/** Lo unico que Zen necesita de la cronologia de Android, sin sus constantes. */
enum class UsageEventKind {
    APP_ABIERTA,
    APP_CERRADA,

    /** El telefono se desbloqueo. Es lo que cuenta "cuantas veces lo he cogido". */
    DESBLOQUEO,
}

/** Un evento ya traducido. La capa de datos solo mapea; aqui no entra nada de Android. */
data class RawUsageEvent(
    val packageName: String?,
    val kind: UsageEventKind,
    val atMillis: Long,
)

/**
 * Pliega la cronologia del sistema en tramos y totales. Funcion pura.
 *
 * Android no da "tiempo por aplicacion" en la ventana que Zen quiere: da una lista de
 * eventos de entrada y salida, y hay que cerrarlos a mano. Vivir aqui y no en la capa
 * de datos es lo que permite probar los casos raros —el evento de cierre que nunca
 * llega, el reloj que salta, la aplicacion que sigue abierta ahora mismo— sin
 * dispositivo y sin Robolectric.
 */
object UsageTimeline {

    /**
     * Por debajo de esto no es una apertura, es una transicion.
     *
     * Al cambiar de aplicacion el sistema emite pares de entrada y salida de unos pocos
     * milisegundos sobre pantallas intermedias. Sin este suelo, abrir el cajon de
     * aplicaciones y tocar una contaba como tres aperturas y el detector de picoteo
     * saltaba solo.
     */
    const val MIN_SPAN_MILLIS = 1_000L

    /**
     * Hueco maximo entre dos tramos de la misma aplicacion para considerarlos la misma
     * visita.
     *
     * El hueco se mide entre el **fin** de uno y el **principio** del siguiente, no
     * entre sus inicios, asi que no depende de cuanto se quede el usuario en cada
     * pantalla: al pasar de una a otra dentro de la misma aplicacion, la salida y la
     * entrada llegan con milisegundos de diferencia por mucho rato que llevase ahi.
     *
     * Sin este tope, apagar la pantalla con una aplicacion delante y volver a ella horas
     * despues se unia en una sola visita —el apagado cierra el tramo pero no abre
     * ninguno, asi que los dos quedan pegados en la lista— y esa visita se llevaba toda
     * la noche con el movil en el bolsillo.
     */
    const val MERGE_GAP_MILLIS = 2_000L

    /**
     * Los tramos en primer plano, en orden y con una entrada por **visita**, no por
     * pantalla.
     *
     * El orden de los tres pasos no es casual y esta fijado con test:
     *
     * 1. Se levantan los tramos en bruto, **sin filtrar nada**.
     * 2. Se unen los consecutivos de la misma aplicacion (ver [merge]).
     * 3. Solo entonces se descartan los paquetes ignorados y los parpadeos.
     *
     * Filtrar antes de unir rompe las dos cosas: quitando los tramos cortos primero, las
     * tres subpantallas de medio segundo de Ajustes desaparecen en vez de sumar uno y
     * medio; quitando a Zen primero, salir a la pantalla de inicio y volver a la misma
     * aplicacion se uniria en una sola visita cuando son dos de verdad.
     *
     * @param ignore paquetes que no cuentan como uso. Zen va siempre aqui: mirar la
     *   hora en la pantalla de inicio no es consumo de movil, y contarlo haria que el
     *   propio launcher fuese la aplicacion mas usada del dia. Aun asi **separa**
     *   visitas, por lo dicho arriba.
     */
    fun spans(
        events: List<RawUsageEvent>,
        nowMillis: Long,
        ignore: Set<String> = emptySet(),
    ): List<AppOpening> = merge(rawSpans(events, nowMillis))
        .filter { it.packageName !in ignore }
        .filter { it.foregroundMillis >= MIN_SPAN_MILLIS }

    /**
     * Une los tramos consecutivos de la misma aplicacion en una sola visita.
     *
     * **El fallo que arregla se vio en un Nothing Phone (2a) y no en ningun test.**
     * Android emite un `ACTIVITY_RESUMED` por cada pantalla, tambien al navegar *dentro*
     * de una aplicacion: entrar en Ajustes y bajar tres niveles emitia
     * `SettingsHomepage`, `SubSettings`, `SubSettings`, `SubSettings` y Zen lo contaba
     * como cuatro aperturas; el banco sumaba dos solo por su pantalla de arranque. Con
     * la cuenta inflada, el detector de picoteo saltaba sin que el usuario hubiera
     * saltado a ninguna parte: doce "aperturas" que sumaban un minuto entero.
     *
     * La duracion se mide de principio a fin y no sumando trozos, asi que el instante de
     * traspaso entre dos pantallas de la misma aplicacion cuenta como lo que es: tiempo
     * dentro de esa aplicacion. Solo se unen tramos casi pegados; ver [MERGE_GAP_MILLIS].
     */
    private fun merge(spans: List<AppOpening>): List<AppOpening> {
        val merged = mutableListOf<AppOpening>()
        for (span in spans) {
            val last = merged.lastOrNull()
            val gap = last?.let { span.atMillis - (it.atMillis + it.foregroundMillis) }
            if (last != null && last.packageName == span.packageName && gap!! <= MERGE_GAP_MILLIS) {
                merged[merged.lastIndex] = last.copy(
                    foregroundMillis = span.atMillis + span.foregroundMillis - last.atMillis,
                )
            } else {
                merged += span
            }
        }
        return merged
    }

    private fun rawSpans(
        events: List<RawUsageEvent>,
        nowMillis: Long,
    ): List<AppOpening> {
        val ordered = events.sortedBy { it.atMillis }
        val spans = mutableListOf<AppOpening>()
        var openPackage: String? = null
        var openAt = 0L

        fun close(atMillis: Long) {
            val current = openPackage ?: return
            openPackage = null
            spans += AppOpening(current, openAt, (atMillis - openAt).coerceAtLeast(0L))
        }

        for (event in ordered) {
            when (event.kind) {
                UsageEventKind.APP_ABIERTA -> {
                    val packageName = event.packageName ?: continue
                    // Hay ROMs que no emiten el cierre de la anterior al abrir otra. Sin
                    // este cierre implicito, la primera aplicacion del dia se quedaba
                    // abierta hasta la noche y se llevaba el dia entero.
                    close(event.atMillis)
                    openPackage = packageName
                    openAt = event.atMillis
                }

                UsageEventKind.APP_CERRADA -> {
                    if (event.packageName == openPackage) close(event.atMillis)
                }

                // Apagar y encender la pantalla no cierra la aplicacion en primer plano
                // para Android, pero para el usuario si: el rato con el movil en el
                // bolsillo no es tiempo de uso.
                UsageEventKind.DESBLOQUEO -> close(event.atMillis)
            }
        }
        // Lo que sigue delante ahora mismo cuenta hasta ahora, no hasta su ultimo evento:
        // sin esto, una sentada de una hora en curso valia cero.
        close(nowMillis)

        return spans
    }

    /** El dia entero resumido, a partir de sus tramos. */
    fun fold(
        events: List<RawUsageEvent>,
        nowMillis: Long,
        dayStartMillis: Long,
        ignore: Set<String> = emptySet(),
    ): UsageSnapshot {
        val spans = spans(events, nowMillis, ignore)
        val apps = spans
            .groupBy { it.packageName }
            .map { (packageName, list) ->
                AppUsage(
                    packageName = packageName,
                    openings = list.size,
                    foregroundMillis = list.sumOf { it.foregroundMillis },
                )
            }
            .sortedByDescending { it.foregroundMillis }

        return UsageSnapshot(
            dayStartMillis = dayStartMillis,
            nowMillis = nowMillis,
            screenMillis = spans.sumOf { it.foregroundMillis },
            unlocks = events.count { it.kind == UsageEventKind.DESBLOQUEO },
            apps = apps,
        )
    }
}
