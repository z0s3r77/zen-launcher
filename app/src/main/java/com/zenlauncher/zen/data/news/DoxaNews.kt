package com.zenlauncher.zen.data.news

import android.util.Log
import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.domain.news.NewsEdition
import com.zenlauncher.zen.domain.news.NewsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * La portada de La Doxa, bajada de noticiasdoxa.es.
 *
 * **La segunda salida a la red de la aplicacion**, despues del tiempo, y va con las
 * mismas reglas: sin librerias nuevas —`HttpURLConnection` y nada mas—, con tope de
 * tamano, con tiempo de espera y sin lanzar nunca. Lo que sale del telefono es una
 * peticion GET sin parametros: no hay clave, no hay cuenta, no hay identificador y no
 * viaja ni un dato del usuario.
 *
 * **Se pide una vez al dia y solo si alguien entra en la pantalla de Noticias.** No hay
 * sondeo, ni servicio, ni descarga en segundo plano: el momento en que el dato se pide
 * y el momento en que alguien puede leerlo son el mismo. Ver `NewsRefresh`.
 *
 * Se descarga la pagina entera —unos 30 kB— porque el sitio no publica ningun canal:
 * no hay RSS, ni JSON, ni sitemap con contenido. Comprobado; si algun dia lo hubiera,
 * es esta clase la que cambia y nadie mas se entera.
 */
class DoxaNews(
    private val clock: ZenClock,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : NewsRepository {

    override suspend fun frontPage(): NewsEdition? = withContext(io) {
        val body = get(DoxaPortada.BASE_URL + "/") ?: return@withContext null
        try {
            DoxaPortada.parse(body, clock.wallTimeMillis())
        } catch (error: Throwable) {
            // El analizador no deberia lanzar, pero esto se ejecuta en el proceso del
            // launcher: si un dia lo hace por un caso raro del marcado, la pantalla
            // dice que no pudo leerse en vez de tirar el telefono.
            Log.w(TAG, "Portada ilegible", error)
            null
        }
    }

    private fun get(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                // Sin redirecciones, igual que el tiempo: la peticion va a un sitio
                // conocido y una cadena de saltos solo puede llevarla a otro.
                instanceFollowRedirects = false
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "El sitio de noticias respondio ${connection.responseCode}")
                return null
            }
            connection.inputStream.reader(Charsets.UTF_8).use { it.readCapped() }
        } catch (error: Throwable) {
            // Incluye SecurityException: si algun dia se quita el permiso INTERNET del
            // manifiesto, esto degrada como cualquier otro fallo de red.
            Log.w(TAG, "No se pudo bajar la portada", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Lee hasta el tope y corta.
     *
     * En bucle y no de una lectura: `Reader.read` devuelve lo que tenga a mano, que en
     * una respuesta de 30 kB es un trozo, no el todo. El tiempo se lee de una porque su
     * respuesta cabe en un paquete; aqui eso dejaria la portada partida por la mitad y
     * el analizador devolveria null sin que nada estuviera roto.
     */
    private fun java.io.Reader.readCapped(): String? {
        val buffer = CharArray(CHUNK_CHARS)
        val text = StringBuilder()
        while (text.length < MAX_BODY_CHARS) {
            val read = read(buffer)
            if (read < 0) break
            text.appendRange(buffer, 0, read)
        }
        return text.takeIf { it.isNotEmpty() }?.toString()
    }

    private companion object {
        const val TAG = "ZenNews"

        const val TIMEOUT_MILLIS = 8_000

        const val CHUNK_CHARS = 8 * 1024

        /**
         * Tope de la respuesta. La portada ronda los 30 kB; medio mega deja sitio para
         * que crezca y corta en seco cualquier respuesta absurda, que en el launcher es
         * memoria que el sistema puede acabar cobrandose.
         */
        const val MAX_BODY_CHARS = 512 * 1024
    }
}
