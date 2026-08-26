package com.zenlauncher.zen.domain.weather

/**
 * El tiempo que hace, en un glifo de tres caracteres.
 *
 * **Son texto, no iconos**, por la misma razon que la cara del dia (ver `UsageFace`):
 * se dibujan con DM Mono como cualquier otro rotulo. Un simbolo de Unicode —el sol o la
 * nube de la tabla de simbolos— no existe en DM Mono ni en Archivo, asi que Android lo
 * sacaria de la fuente de reserva del sistema, que en un movil actual es la de emoji: un
 * mapa de bits a todo color en la unica pantalla monocroma de la aplicacion.
 *
 * Tres caracteres y no dos —la cara son dos— porque una nube o la lluvia necesitan una
 * repeticion para leerse como tal; y todos miden lo mismo para que la franja no baile de
 * ancho cada vez que cambia el tiempo.
 *
 * **No hay viento aqui, y es a proposito.** El codigo meteorologico de la OMM describe
 * el cielo y no la fuerza del aire (ver [WeatherCodes]); anadir un escalon de viento
 * significaria una segunda tabla, con su propio umbral, decidiendo sobre el mismo glifo
 * que la primera. Dos tablas se desincronizan y acaban contradiciendose en la misma
 * pantalla, que es justo lo que este proyecto evita con el veredicto de la semana.
 */
enum class WeatherCondition(val glyph: String) {
    /** Sol: el disco y sus rayos. */
    DESPEJADO("-O-"),

    /** Sol entre nubes. */
    NUBES_CLAROS("-~-"),

    NUBLADO("~~~"),

    /** Lineas inclinadas, como cae. */
    LLUVIA("///"),

    /** Lluvia con un rayo dentro. */
    TORMENTA("/!/"),

    NIEVE("***"),

    /** Capas horizontales: lo unico que se ve dentro de la niebla. */
    NIEBLA("==="),
}

/**
 * Un sitio del que se puede saber el tiempo.
 *
 * Lo elige el usuario a mano, escribiendo el nombre. **Zen no pide la ubicacion**: el
 * permiso de localizacion es el mas caro de todos los que existen y aqui compraria muy
 * poco, porque la ciudad en la que uno vive no cambia todos los dias. Se escribe una vez
 * y se queda escrita.
 */
data class WeatherPlace(
    val name: String,
    val latitude: Double,
    val longitude: Double,
) {
    /**
     * Las coordenadas son lo unico que viaja a la red, asi que se recortan a dos
     * decimales: son unos 300 m de resolucion, de sobra para el tiempo de una ciudad y
     * no lo bastante para senalar un portal. Ver `OpenMeteoWeather`.
     */
    val coarseLatitude: Double get() = Math.round(latitude * 100) / 100.0
    val coarseLongitude: Double get() = Math.round(longitude * 100) / 100.0
}

/**
 * El tiempo en un momento dado.
 *
 * [observedAtMillis] no es adorno: es lo que permite distinguir "hace 18 grados" de
 * "hacian 18 grados hace ocho horas y desde entonces no hay red". Sin esa marca, un dato
 * viejo se ensena con la misma cara que uno recien traido, que es la clase de mentira
 * que Zen evita en el resto de la aplicacion (ver `UsageSnapshot.measured`).
 *
 * [condition] puede faltar: la tabla de la OMM tiene codigos que [WeatherCodes] no
 * traduce. Media informacion util se ensena; inventar un glifo "desconocido" seria
 * pintar algo que no dice nada.
 */
data class WeatherReading(
    val degrees: Int,
    val condition: WeatherCondition?,
    val observedAtMillis: Long,
)
