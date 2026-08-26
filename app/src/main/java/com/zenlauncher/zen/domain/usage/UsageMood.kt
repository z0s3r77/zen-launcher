package com.zenlauncher.zen.domain.usage

/**
 * La cara del dia, en dos caracteres de puntuacion.
 *
 * **Es texto, no un icono**, y por eso cabe en Zen: se dibuja con la tipografia
 * monoespaciada como cualquier otro rotulo, no trae un mapa de bits ni un color ni una
 * marca de nadie. Es la unica forma de resumen que se entiende sin leer —y sin saber
 * castellano— en el sitio donde solo caben dos caracteres.
 *
 * Nunca es la unica senal: el glifo viaja siempre con su descripcion en palabras (ver
 * `usage_face_*`), y cuando el dia se va de madre la propia pantalla de inicio saca
 * ademas el pulso con las cifras. Una cara triste que no se pueda explicar seria un
 * reproche sin datos.
 */
enum class UsageFace(val glyph: String) {
    /** El dia va como debe. */
    BIEN(":)"),

    /** Ni bien ni mal: o empieza a subir, o hay una aplicacion acaparando. */
    REGULAR(":|"),

    /** Se ha ido de las manos. */
    MAL(":("),

    /** Exceso, o una conducta compulsiva ahora mismo. */
    ALARMA(":O"),

    /**
     * Sin acceso de uso: no es un dia bueno ni malo, es un dia que no se puede ver.
     *
     * Tiene cara propia a proposito. Poner `:)` sin haber medido nada seria felicitar
     * por un dia que no ha ocurrido, y es justo la mentira que el resto de la pantalla
     * evita con [UsageSnapshot.measured].
     */
    DESCONOCIDO(":?"),
    ;

    /**
     * Si la cara esta diciendo que algo va mal.
     *
     * `DESCONOCIDO` **no** cuenta: no saber no es una mala noticia, y tratarlo como tal
     * llenaria la pantalla de inicio de avisos en un telefono que simplemente no ha
     * concedido el acceso de uso.
     */
    val alarming: Boolean get() = this == MAL || this == ALARMA
}

/**
 * De las cifras del dia a una cara. Funcion pura.
 *
 * Mira las tres cosas que decide el usuario cuando se pregunta si va bien: **cuanto
 * tiempo** lleva, **cuantas veces** ha cogido el telefono y **en que** se le esta yendo.
 * Las dos primeras ya las combina [UsagePressure]; la tercera es la que anade esto.
 *
 * **Por que hace falta la tercera.** Dos horas repartidas entre el correo, el banco y
 * los mensajes son dos horas de usar el telefono; dos horas en las que el 80% se lo
 * lleva una sola aplicacion hecha para que te quedes son otra cosa, y el escalon por
 * tiempo no las distingue. Por eso una aplicacion que acapara empeora la cara aunque el
 * reloj diga que el dia es normal.
 */
object UsageMood {

    /** Acapara: se lleva esta parte del dia... */
    const val CONCENTRATION_PERCENT = 60

    /** ...y ademas tiempo de verdad, para no acusar al 80% de veinte minutos. */
    const val CONCENTRATION_MINUTES = 45

    fun face(reading: UsageReading): UsageFace {
        if (!reading.measured) return UsageFace.DESCONOCIDO

        val concentrated = isConcentrated(reading)
        return when (reading.level) {
            UsageLevel.EXCESO -> UsageFace.ALARMA
            UsageLevel.ALTA -> UsageFace.MAL
            // Una sola aplicacion comiendose el dia baja la cara un escalon: el reloj
            // dice que vas normal, pero no es lo mismo repartir que caer en un sitio.
            UsageLevel.NORMAL -> if (concentrated) UsageFace.MAL else UsageFace.REGULAR
            UsageLevel.CALMA -> if (concentrated) UsageFace.REGULAR else UsageFace.BIEN
        }
    }

    private fun isConcentrated(reading: UsageReading): Boolean {
        val top = reading.topApp ?: return false
        if (top.foregroundMillis < CONCENTRATION_MINUTES * 60_000L) return false
        if (reading.screenMillis <= 0L) return false
        return top.foregroundMillis * 100 / reading.screenMillis >= CONCENTRATION_PERCENT
    }
}
