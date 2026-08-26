package com.zenlauncher.zen.domain.weather

/**
 * De codigo meteorologico de la OMM a glifo. Funcion pura.
 *
 * Es la tabla WW estandar (la que publica Open-Meteo en `weather_code`), agrupada en las
 * siete formas de cielo que Zen sabe dibujar. Agrupar es el trabajo: la tabla distingue
 * llovizna de lluvia y lluvia de chubasco, tres cosas que en una franja de tres
 * caracteres se ven exactamente igual y ante las que uno hace lo mismo, coger el
 * paraguas.
 *
 * Lo que no esta en la tabla devuelve null y se queda sin glifo, con los grados solos.
 * Traducir un codigo desconocido al glifo mas parecido seria adivinar el tiempo.
 */
object WeatherCodes {

    fun condition(code: Int): WeatherCondition? = when (code) {
        // 0 despejado, 1 mayormente despejado.
        0, 1 -> WeatherCondition.DESPEJADO
        2 -> WeatherCondition.NUBES_CLAROS
        3 -> WeatherCondition.NUBLADO
        // 45 niebla, 48 niebla helada.
        45, 48 -> WeatherCondition.NIEBLA
        // 51-57 llovizna (incluida la helada), 61-67 lluvia, 80-82 chubascos: para
        // quien mira la pantalla antes de salir, las tres son "llevate algo".
        51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82 -> WeatherCondition.LLUVIA
        // 71-77 nieve y granos de nieve, 85-86 chubascos de nieve.
        71, 73, 75, 77, 85, 86 -> WeatherCondition.NIEVE
        // 95-99 tormenta, con granizo o sin el. La tormenta manda sobre la lluvia: en
        // una tormenta tambien llueve, y lo que define el dia es la tormenta.
        95, 96, 99 -> WeatherCondition.TORMENTA
        else -> null
    }
}
