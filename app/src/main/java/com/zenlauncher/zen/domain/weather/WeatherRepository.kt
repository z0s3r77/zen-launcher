package com.zenlauncher.zen.domain.weather

/**
 * De donde sale el tiempo. La unica frontera de Zen que sale a la red.
 *
 * **Ninguna de las dos funciones lanza.** Devuelven null o una lista vacia cuando no hay
 * red, cuando el servicio contesta mal o cuando tarda demasiado. No es dejadez: una
 * excepcion sin capturar en la pantalla de inicio deja el telefono sin pantalla de
 * inicio, y el tiempo es lo menos importante que hay aqui. Que no se sepa que tiempo
 * hace no puede tener mas consecuencia que un hueco vacio en la franja.
 */
interface WeatherRepository {

    /** El tiempo de ahora en [place], o null si no se pudo traer. */
    suspend fun current(place: WeatherPlace): WeatherReading?

    /** Sitios que coinciden con lo escrito, o lista vacia. */
    suspend fun search(query: String): List<WeatherPlace>
}
