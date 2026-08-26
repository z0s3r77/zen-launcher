package com.zenlauncher.zen.domain.news

/**
 * La portada de un dia: el titular de arriba y los puntos numerados de debajo.
 *
 * Es una **foto**, no un flujo. Se baja una vez al dia y se queda escrita tal cual; no
 * hay paginacion, no hay "cargar mas" y no hay nada que refrescarse solo mientras se
 * lee. Un launcher que se mira cincuenta veces al dia no puede tener dentro algo que
 * cambie mientras lo miras: la portada de hoy es la de hoy hasta manana.
 *
 * [editionLabel] es la fecha que **la propia portada dice** ("2026-08-25"), no la del
 * telefono. Se ensena esa y no la del reloj por la misma razon que una lectura del
 * tiempo lleva su hora: si la de hoy no se pudo bajar y lo que hay es la de ayer, el
 * usuario tiene que poder verlo sin adivinarlo. Puede faltar —la cabecera del sitio
 * puede cambiar— y entonces se cae a la hora de descarga, que siempre existe.
 */
data class NewsEdition(
    val headline: NewsHeadline,
    val points: List<NewsPoint>,
    /** Cuando se bajo, en hora de pared. Es lo que decide si hay que volver a bajarla. */
    val fetchedAtMillis: Long,
    val editionLabel: String? = null,
)

/**
 * Lo de arriba de la portada: una frase que resume el dia y un parrafo que lo explica.
 *
 * El parrafo no es adorno: un titular solo dice de que va, y la gracia de entrar aqui
 * en vez de abrir un navegador es enterarse sin abrir siete pestanas.
 */
data class NewsHeadline(
    val title: String,
    val subtitle: String,
)

/**
 * Uno de los puntos numerados de la portada.
 *
 * [url] es obligatoria y absoluta: **ninguna cifra sin salida**, y aqui la salida es la
 * noticia entera. Un punto sin enlace es un resumen que no lleva a ninguna parte, asi
 * que el analizador lo descarta en lugar de pintarlo muerto.
 *
 * [section] puede faltar. Se ensena si esta porque separa de un vistazo lo que es
 * economia de lo que es ciencia, pero inventarle una a un punto que no la trae seria
 * clasificar por nuestra cuenta.
 */
data class NewsPoint(
    /** El numero que la portada le da: "01", "02"... Se respeta el suyo, no se cuenta. */
    val index: String,
    val title: String,
    val summary: String,
    val url: String,
    val section: String? = null,
)
