package com.zenlauncher.zen.data.news

import com.zenlauncher.zen.domain.news.NewsEdition
import com.zenlauncher.zen.domain.news.NewsHeadline
import com.zenlauncher.zen.domain.news.NewsPoint

/**
 * Traduce el HTML de la portada de La Doxa a [NewsEdition]. Puro: ni red ni Android.
 *
 * **Expresiones regulares y no un analizador de HTML.** Meter jsoup por una pagina son
 * cientos de kilobytes en el proceso que menos puede permitirse morir en este telefono,
 * y esta aplicacion ya escribe su SQLite y su JSON a mano por la misma razon. La pagina
 * la genera un sitio estatico, asi que su marcado no cambia por si solo entre visitas;
 * cuando cambie, esto devolvera null y la pantalla dira que no se pudo leer, que es
 * exactamente lo que tiene que pasar.
 *
 * **Todo lo que no se entiende se descarta, no se rellena.** Un punto sin enlace, sin
 * titulo o sin resumen no se pinta a medias: media noticia con un hueco donde deberia
 * estar el enlace es peor que un punto menos.
 *
 * Va aparte del cliente HTTP para poder probarlo con una portada guardada: sin esto, la
 * unica forma de comprobar que se lee bien seria salir a la red desde un test.
 */
internal object DoxaPortada {

    /** El unico sitio al que se pide y el unico al que se deja enlazar. */
    const val BASE_URL = "https://noticiasdoxa.es"

    /** Lo que se ensena de la portada: el titular y los siete primeros puntos. */
    const val MAX_POINTS = 7

    /**
     * @return null si falta el titular o no hay ni un punto legible. Media portada no
     *   es una portada: sin titular no se sabe de que va el dia, y sin puntos no hay
     *   nada que abrir.
     */
    fun parse(html: String, fetchedAtMillis: Long): NewsEdition? {
        val headline = headline(html) ?: return null
        val points = points(html)
        if (points.isEmpty()) return null
        return NewsEdition(
            headline = headline,
            points = points,
            fetchedAtMillis = fetchedAtMillis,
            editionLabel = editionLabel(html),
        )
    }

    private fun headline(html: String): NewsHeadline? {
        val match = HEADLINE.find(html) ?: return null
        val title = match.groupValues[1].toText()
        val subtitle = match.groupValues[2].toText()
        if (title.isEmpty()) return null
        return NewsHeadline(title = title, subtitle = subtitle)
    }

    private fun points(html: String): List<NewsPoint> =
        POINT_BLOCK.findAll(html)
            .mapNotNull { block -> point(block.groupValues[1]) }
            .take(MAX_POINTS)
            .toList()

    private fun point(block: String): NewsPoint? {
        val link = POINT_LINK.find(block) ?: return null
        val url = absolute(link.groupValues[1]) ?: return null
        val title = link.groupValues[2].toText()
        if (title.isEmpty()) return null
        val summary = POINT_SUMMARY.find(block)?.groupValues?.get(1)?.toText().orEmpty()
        if (summary.isEmpty()) return null
        return NewsPoint(
            // El numero es el que la portada le da. Contando por nuestra cuenta, un
            // punto descartado por ilegible correria la numeracion y el "04" de la
            // pantalla no seria el "04" del sitio.
            index = POINT_INDEX.find(block)?.groupValues?.get(1)?.toText().orEmpty(),
            title = title,
            summary = summary,
            url = url,
            section = POINT_SECTION.find(block)?.groupValues?.get(1)?.toText()
                ?.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * La fecha que la cabecera del sitio declara, en ISO.
     *
     * Se busca el patron de fecha dentro de la linea, no la frase que lo rodea: el
     * rotulo es "Edición del 2026-08-25 · 31 historias" hoy y podria ser otro manana,
     * pero una fecha ISO se reconoce sola.
     */
    private fun editionLabel(html: String): String? {
        val line = MASTHEAD.find(html)?.groupValues?.get(1)?.toText() ?: return null
        return ISO_DATE.find(line)?.value
    }

    /**
     * Solo enlaces del propio sitio, y solo por https.
     *
     * Lo que sale de aqui acaba en un `ACTION_VIEW`, asi que es una direccion sacada de
     * una pagina y entregada al sistema. Un `href` con otro esquema —`javascript:`,
     * `intent:`— o de otro dominio se descarta: Zen enlaza a la noticia que resumio, no
     * a lo que aparezca en el atributo.
     */
    private fun absolute(href: String): String? {
        val clean = href.trim()
        return when {
            clean.startsWith("$BASE_URL/") -> clean
            // Doble barra es "//otrodominio.com", no una ruta del sitio.
            clean.startsWith("/") && !clean.startsWith("//") -> BASE_URL + clean
            else -> null
        }
    }

    /** Quita el marcado, traduce las entidades y deja una sola linea sin dobles espacios. */
    private fun String.toText(): String =
        replace(TAG, " ").decodeEntities().replace(WHITESPACE, " ").trim()

    private fun String.decodeEntities(): String = ENTITY.replace(this) { match ->
        val body = match.groupValues[1]
        when {
            body.startsWith("#x", ignoreCase = true) ->
                body.drop(2).toIntOrNull(16)?.toChars() ?: match.value

            body.startsWith("#") -> body.drop(1).toIntOrNull()?.toChars() ?: match.value
            // Una entidad con nombre que no esta en la tabla se deja tal cual: borrarla
            // se comeria un caracter del texto sin que nadie pueda notarlo.
            else -> NAMED_ENTITIES[body] ?: match.value
        }
    }

    private fun Int.toChars(): String? =
        if (this in 1..0x10FFFF) String(Character.toChars(this)) else null

    private val TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")
    private val ENTITY = Regex("&(#[xX]?[0-9A-Fa-f]+|[A-Za-z][A-Za-z0-9]*);")
    private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")

    private val MASTHEAD = Regex(
        """<p[^>]*class="mast-line"[^>]*>(.*?)</p>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private val HEADLINE = Regex(
        """class="estado-lede".*?<h2[^>]*>(.*?)</h2>.*?<p[^>]*class="nota-deck"[^>]*>(.*?)</p>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private val POINT_BLOCK = Regex(
        """<li[^>]*class="toca-punto"[^>]*>(.*?)</li>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private val POINT_LINK = Regex(
        """<h3[^>]*class="toca-titulo"[^>]*>\s*<a[^>]*href="([^"]*)"[^>]*>(.*?)</a>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private val POINT_SUMMARY = Regex(
        """<p[^>]*class="toca-porque"[^>]*>(.*?)</p>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private val POINT_SECTION = Regex(
        """<span[^>]*class="resumen-seccion"[^>]*>(.*?)</span>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    private val POINT_INDEX = Regex(
        """<span[^>]*class="banda-num"[^>]*>(.*?)</span>""",
        RegexOption.DOT_MATCHES_ALL,
    )

    /**
     * Solo las entidades estructurales y las de puntuacion que este sitio usa. Las
     * letras acentuadas llegan en UTF-8 literal, asi que una tabla con medio HTML4
     * seria codigo muerto.
     */
    private val NAMED_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to " ",
        "hellip" to "…",
        "mdash" to "—",
        "ndash" to "–",
        "laquo" to "«",
        "raquo" to "»",
        "lsquo" to "‘",
        "rsquo" to "’",
        "ldquo" to "“",
        "rdquo" to "”",
        "middot" to "·",
        "bull" to "·",
        "deg" to "°",
        "euro" to "€",
        "rarr" to "→",
    )
}
