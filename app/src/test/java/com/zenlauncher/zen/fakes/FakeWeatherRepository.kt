package com.zenlauncher.zen.fakes

import com.zenlauncher.zen.domain.weather.WeatherPlace
import com.zenlauncher.zen.domain.weather.WeatherReading
import com.zenlauncher.zen.domain.weather.WeatherRepository

/**
 * Falso servicio del tiempo.
 *
 * Cuenta las peticiones porque la mitad de lo que hay que probar aqui es **cuantas
 * veces se sale a la red**: es la unica funcion de Zen que lo hace, y la regla es que no
 * se pide mas de una vez cada media hora aunque se vuelva a la home cincuenta veces.
 *
 * `reading = null` es el telefono sin cobertura, que tambien es un camino con reglas
 * propias: el intento cuenta aunque falle.
 */
class FakeWeatherRepository(
    var reading: WeatherReading? = null,
    var results: List<WeatherPlace> = emptyList(),
) : WeatherRepository {

    var currentCalls = 0
        private set
    var searchCalls = 0
        private set
    val askedFor = mutableListOf<WeatherPlace>()

    override suspend fun current(place: WeatherPlace): WeatherReading? {
        currentCalls++
        askedFor += place
        return reading
    }

    override suspend fun search(query: String): List<WeatherPlace> {
        searchCalls++
        return results
    }
}
