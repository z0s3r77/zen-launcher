package com.zenlauncher.zen.fakes

import com.zenlauncher.zen.domain.news.NewsEdition
import com.zenlauncher.zen.domain.news.NewsRepository

/**
 * Falsa portada.
 *
 * Cuenta las descargas porque la mitad de lo que hay que probar aqui es **cuantas veces
 * se sale a la red**: la regla es una al dia, y entrar diez veces en la pantalla el
 * mismo dia no puede abrir ni una conexion.
 *
 * `edition = null` es el telefono sin cobertura, que tambien es un camino con reglas
 * propias: un fallo no borra la portada que ya hubiera guardada.
 */
class FakeNewsRepository(
    var edition: NewsEdition? = null,
) : NewsRepository {

    var frontPageCalls = 0
        private set

    override suspend fun frontPage(): NewsEdition? {
        frontPageCalls++
        return edition
    }
}
