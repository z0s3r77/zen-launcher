package com.zenlauncher.zen.data.news

import android.util.Log
import com.zenlauncher.zen.domain.news.NewsEdition
import com.zenlauncher.zen.domain.news.NewsHeadline
import com.zenlauncher.zen.domain.news.NewsPoint
import org.json.JSONArray
import org.json.JSONObject

/**
 * La portada, escrita y leida como una sola cadena JSON.
 *
 * Va entera en **una** clave de DataStore, y no en veinte claves sueltas como el tiempo,
 * porque el tiempo son tres campos y esto son ocho textos con siete enlaces: una clave
 * por campo serian mas de treinta, con su indice en el nombre, y quitar un punto habria
 * que hacerlo borrando claves una a una. La portada ademas se guarda o no se guarda
 * entera: nunca hay media portada valida.
 *
 * `org.json` viene en Android y ya se usa para leer el tiempo, asi que no entra ninguna
 * libreria nueva.
 *
 * [decode] **no lanza**: unos bytes corruptos en el fichero de preferencias no pueden
 * dejar el telefono sin pantalla de inicio. Devuelve null y se vuelve a bajar.
 */
internal object NewsJson {

    /**
     * Version del formato. Lo escrito por una version que no se reconoce se tira: no
     * hay migracion que escribir porque lo unico que se pierde es una descarga, y se
     * recupera sola la siguiente vez que se abre la pantalla.
     */
    private const val VERSION = 1

    fun encode(edition: NewsEdition): String = JSONObject().apply {
        put(VERSION_KEY, VERSION)
        put(FETCHED_KEY, edition.fetchedAtMillis)
        edition.editionLabel?.let { put(EDITION_KEY, it) }
        put(TITLE_KEY, edition.headline.title)
        put(SUBTITLE_KEY, edition.headline.subtitle)
        put(
            POINTS_KEY,
            JSONArray().apply {
                edition.points.forEach { point ->
                    put(
                        JSONObject().apply {
                            put(INDEX_KEY, point.index)
                            put(TITLE_KEY, point.title)
                            put(SUMMARY_KEY, point.summary)
                            put(URL_KEY, point.url)
                            point.section?.let { put(SECTION_KEY, it) }
                        },
                    )
                }
            },
        )
    }.toString()

    fun decode(json: String): NewsEdition? = try {
        val root = JSONObject(json)
        if (root.optInt(VERSION_KEY) != VERSION) {
            null
        } else {
            val points = root.optJSONArray(POINTS_KEY)?.points().orEmpty()
            // Sin puntos no es una portada, igual que al analizar el HTML. Devolver una
            // vacia dejaria la pantalla creyendo que ya tiene la de hoy.
            if (points.isEmpty()) {
                null
            } else {
                NewsEdition(
                    headline = NewsHeadline(
                        title = root.optString(TITLE_KEY),
                        subtitle = root.optString(SUBTITLE_KEY),
                    ),
                    points = points,
                    fetchedAtMillis = root.optLong(FETCHED_KEY),
                    editionLabel = root.optString(EDITION_KEY).takeIf { it.isNotEmpty() },
                )
            }
        }
    } catch (error: Throwable) {
        Log.w(TAG, "Portada guardada ilegible", error)
        null
    }

    private fun JSONArray.points(): List<NewsPoint> = (0 until length()).mapNotNull { index ->
        val item = optJSONObject(index) ?: return@mapNotNull null
        val url = item.optString(URL_KEY).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val title = item.optString(TITLE_KEY).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        NewsPoint(
            index = item.optString(INDEX_KEY),
            title = title,
            summary = item.optString(SUMMARY_KEY),
            url = url,
            section = item.optString(SECTION_KEY).takeIf { it.isNotEmpty() },
        )
    }

    private const val TAG = "ZenNews"

    private const val VERSION_KEY = "v"
    private const val FETCHED_KEY = "bajada"
    private const val EDITION_KEY = "edicion"
    private const val TITLE_KEY = "titulo"
    private const val SUBTITLE_KEY = "bajadilla"
    private const val POINTS_KEY = "puntos"
    private const val INDEX_KEY = "n"
    private const val SUMMARY_KEY = "resumen"
    private const val URL_KEY = "url"
    private const val SECTION_KEY = "seccion"
}
