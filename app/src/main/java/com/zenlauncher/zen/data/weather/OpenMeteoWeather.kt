package com.zenlauncher.zen.data.weather

import android.util.Log
import com.zenlauncher.zen.core.ZenClock
import com.zenlauncher.zen.domain.weather.WeatherCodes
import com.zenlauncher.zen.domain.weather.WeatherPlace
import com.zenlauncher.zen.domain.weather.WeatherReading
import com.zenlauncher.zen.domain.weather.WeatherRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * El tiempo, traido de Open-Meteo. **La unica salida a la red de toda la aplicacion.**
 *
 * Por que este servicio y no otro: no pide clave, no pide registro, no devuelve
 * publicidad y no hay una cuenta a la que atar lo consultado. Lo unico que sale del
 * telefono es un par de coordenadas **recortadas a dos decimales** (unos 300 m, ver
 * [WeatherPlace.coarseLatitude]) y son las de la ciudad que el usuario escribio a mano,
 * no las del GPS: Zen no pide el permiso de ubicacion.
 *
 * Sin dependencias nuevas: `HttpURLConnection` y `org.json` vienen en Android. Traer
 * Retrofit y un serializador para dos peticiones GET seria mas codigo del que se ahorra,
 * y esta aplicacion ya escribe su SQLite a mano por la misma razon.
 *
 * **Nada de aqui lanza.** Cualquier fallo —sin red, servicio caido, respuesta rara,
 * permiso INTERNET revocado— se traduce en null o lista vacia. Una excepcion que suba
 * hasta la pantalla de inicio deja el telefono sin pantalla de inicio, y el tiempo es lo
 * menos importante que hay aqui.
 */
class OpenMeteoWeather(
    private val clock: ZenClock,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : WeatherRepository {

    override suspend fun current(place: WeatherPlace): WeatherReading? = withContext(io) {
        val url = "$FORECAST_URL?latitude=${place.coarseLatitude.format()}" +
            "&longitude=${place.coarseLongitude.format()}" +
            "&current=temperature_2m,weather_code"
        val body = get(url) ?: return@withContext null
        try {
            val current = JSONObject(body).optJSONObject("current") ?: return@withContext null
            // `has` y no `opt` con valor por defecto: sin temperatura no hay lectura, y
            // un cero por defecto seria un dia helado inventado.
            if (!current.has("temperature_2m")) return@withContext null
            WeatherReading(
                degrees = Math.round(current.getDouble("temperature_2m")).toInt(),
                condition = WeatherCodes.condition(current.optInt("weather_code", -1)),
                observedAtMillis = clock.wallTimeMillis(),
            )
        } catch (error: Throwable) {
            Log.w(TAG, "Respuesta del tiempo ilegible", error)
            null
        }
    }

    override suspend fun search(query: String): List<WeatherPlace> = withContext(io) {
        val trimmed = query.trim()
        if (trimmed.length < MIN_QUERY_LENGTH) return@withContext emptyList()
        val url = "$GEOCODING_URL?name=${trimmed.encode()}&count=$MAX_RESULTS" +
            "&language=es&format=json"
        val body = get(url) ?: return@withContext emptyList()
        try {
            val results = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
            (0 until results.length()).mapNotNull { index ->
                results.optJSONObject(index)?.toPlace()
            }
        } catch (error: Throwable) {
            Log.w(TAG, "Respuesta del buscador de ciudades ilegible", error)
            emptyList()
        }
    }

    /**
     * El nombre lleva el pais pegado porque hay una Santiago en media docena de paises y
     * dos Guadalajara en dos continentes: una lista de seis "Santiago" identicos no es
     * una eleccion, es una loteria.
     */
    private fun JSONObject.toPlace(): WeatherPlace? {
        val name = optString("name").takeIf { it.isNotBlank() } ?: return null
        if (!has("latitude") || !has("longitude")) return null
        val region = listOfNotNull(
            optString("admin1").takeIf { it.isNotBlank() },
            optString("country").takeIf { it.isNotBlank() },
        ).joinToString(", ")
        return WeatherPlace(
            name = if (region.isEmpty()) name else "$name, $region",
            latitude = getDouble("latitude"),
            longitude = getDouble("longitude"),
        )
    }

    private fun get(url: String): String? {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                // Sin redirecciones: la peticion va a un sitio conocido y una cadena de
                // saltos solo puede llevarla a otro.
                instanceFollowRedirects = false
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                Log.w(TAG, "El servicio del tiempo respondio ${connection.responseCode}")
                return null
            }
            // Con tope: una respuesta enorme no puede comerse la memoria del launcher,
            // que es el proceso que menos puede permitirse morir en este telefono.
            connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(MAX_BODY_CHARS)
                val read = reader.read(buffer)
                if (read <= 0) null else String(buffer, 0, read)
            }
        } catch (error: Throwable) {
            // Incluye SecurityException: si algun dia se quita el permiso INTERNET del
            // manifiesto, esto tiene que degradar como cualquier otro fallo de red.
            Log.w(TAG, "No se pudo consultar el tiempo", error)
            null
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * `Locale.US` a la fuerza. Con el telefono en castellano, `%.2f` escribe "40,42" y
     * la coma parte el parametro de la URL en dos: la peticion sale con una latitud
     * imposible y el servicio responde un error que parece falta de red.
     */
    private fun Double.format(): String = String.format(Locale.US, "%.2f", this)

    private fun String.encode(): String = URLEncoder.encode(this, "UTF-8")

    private companion object {
        const val TAG = "ZenWeather"

        const val FORECAST_URL = "https://api.open-meteo.com/v1/forecast"
        const val GEOCODING_URL = "https://geocoding-api.open-meteo.com/v1/search"

        const val TIMEOUT_MILLIS = 5_000

        /** Escribir una o dos letras devuelve el mundo entero; no es una busqueda. */
        const val MIN_QUERY_LENGTH = 3
        const val MAX_RESULTS = 8

        /** De sobra para ocho resultados; corta en seco cualquier respuesta absurda. */
        const val MAX_BODY_CHARS = 32 * 1024
    }
}
