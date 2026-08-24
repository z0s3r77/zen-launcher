package com.zenlauncher.zen.domain.notes

/**
 * Enlaces escritos dentro del texto de una nota.
 *
 * **No hay boton de "adjuntar enlace".** Un enlace llega siempre de la misma forma —se
 * pega— y pedir un toque mas para clasificar lo que se acaba de pegar es exactamente la
 * friccion que la captura rapida existe para quitar. Se reconoce al guardar y se
 * convierte en adjunto solo.
 *
 * Se escribe a mano en lugar de usar `android.util.Patterns.WEB_URL` por dos razones: es
 * dominio puro y se prueba sin Android, y `WEB_URL` reconoce cosas como `etc.es` en
 * mitad de una frase, que en un cuaderno personal son falsos positivos constantes.
 */
object LinkExtractor {

    /**
     * Los enlaces del texto, en orden de aparicion y sin repetir.
     *
     * Repetido no se anade dos veces: pegar el mismo enlace arriba y abajo de una nota
     * es normal al pensar en voz alta, y la nota acabaria con dos adjuntos identicos.
     */
    fun extract(text: String): List<String> =
        PATTERN.findAll(text)
            .map { trimTrailingPunctuation(it.value) }
            .map(::normalize)
            .distinct()
            .toList()

    /**
     * Le pone esquema al que no lo trae, para que abrirlo no dependa de quien lo lea.
     *
     * `https` y no `http`: un enlace sin esquema escrito hoy casi nunca es de un sitio
     * que solo hable por el canal sin cifrar, y si lo fuera, el servidor redirige.
     */
    fun normalize(raw: String): String =
        if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"

    /**
     * Quita la puntuacion final que pertenece a la frase y no al enlace.
     *
     * "Mira esto: https://ejemplo.es." termina en un punto que es de la frase. El
     * parentesis de cierre solo se quita si no hay uno de apertura dentro del propio
     * enlace, porque hay direcciones que lo llevan de verdad —las de Wikipedia, sin ir
     * mas lejos— y recortarlo las rompe.
     */
    private fun trimTrailingPunctuation(url: String): String {
        var end = url.length
        while (end > 0) {
            val char = url[end - 1]
            val isClosingParen = char == ')'
            val balanced = isClosingParen && url.take(end).count { it == '(' } >= url.take(end).count { it == ')' }
            if (char in TRAILING || (isClosingParen && !balanced)) {
                end--
            } else {
                break
            }
        }
        return url.take(end)
    }

    private const val TRAILING = ".,;:!?\"'»…"

    /**
     * Tres formas de escribir un enlace: con esquema, empezando por `www.`, o un dominio
     * seguido de barra. La tercera **exige la barra** a proposito: sin ella, cualquier
     * frase con un punto pegado a una palabra de dos letras ("no vale.Es lo que hay")
     * se convertiria en un enlace.
     */
    private val PATTERN = Regex(
        """(?:https?://[^\s<>"]+)""" +
            """|(?:www\.[^\s<>"]+)""" +
            """|(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]*[a-zA-Z0-9])?\.[a-zA-Z]{2,24}/[^\s<>"]*)""",
    )
}
