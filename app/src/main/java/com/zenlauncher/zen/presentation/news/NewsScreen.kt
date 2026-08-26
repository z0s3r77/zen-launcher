package com.zenlauncher.zen.presentation.news

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.news.NewsEdition
import com.zenlauncher.zen.domain.news.NewsPoint
import com.zenlauncher.zen.presentation.components.MonoData
import com.zenlauncher.zen.presentation.components.MonoLabel
import com.zenlauncher.zen.presentation.components.ZenHairline
import com.zenlauncher.zen.presentation.components.ZenHeaderStrip
import com.zenlauncher.zen.presentation.components.ZenScreen
import com.zenlauncher.zen.presentation.components.ZenTagButton
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles
import com.zenlauncher.zen.presentation.util.ZenDateFormats
import java.util.Locale

/**
 * La portada del dia: el titular de arriba y los siete puntos, cada uno con su salida a
 * la noticia entera.
 *
 * **Se desplaza, y es de las pocas de Zen que puede.** La home no se desplaza porque el
 * reloj tiene que estar siempre en el mismo pixel; esto es lo contrario, un texto que se
 * lee de arriba abajo una vez al dia. Por eso mismo **nada se recorta aqui**: un titular
 * cortado a mitad de frase no es un titular, y esta pantalla tiene alto de sobra para
 * darlo entero. Los `maxLines` de esta aplicacion existen para que una fila no rompa el
 * ritmo de una lista; aqui la fila **es** el contenido.
 *
 * Lo que no se hace, a proposito:
 *
 * - **No hay titulares en la pantalla de inicio.** Un titular en la home seria algo que
 *   invita a leer cincuenta veces al dia, que es exactamente lo que este launcher
 *   evita. Aqui se entra a proposito, como a Respira.
 * - **No hay nada que se actualice solo.** La portada se baja una vez al dia y se queda
 *   quieta: no hay tirar para refrescar, ni "cargar mas", ni una cifra de no leidos.
 * - **No se lee la noticia dentro de Zen.** El punto lleva su resumen —que es lo que se
 *   viene a leer— y quien quiera la noticia entera sale al navegador. Zen no es un
 *   lector de noticias, y meter aqui un navegador seria abrir la puerta que cierra.
 */
@Composable
fun NewsScreen(
    state: NewsUiState,
    onOpenLink: (String) -> Unit,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    nowMillis: Long = System.currentTimeMillis(),
    locale: Locale = Locale.getDefault(),
) {
    val edition = state.edition

    ZenScreen(modifier = modifier, onSwipeBack = onBack) {
        ZenHeaderStrip(
            left = stringResource(R.string.news_title),
            // La fecha que la propia portada declara, no la del telefono: es lo que
            // distingue la de hoy de la de ayer de un vistazo.
            right = edition?.let { editionStamp(it, nowMillis, locale) }
                ?: stringResource(R.string.news_no_date),
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            // El aire final va en el relleno del contenido, no como un elemento mas:
            // un espaciador suelto en una lista perezosa puede quedarse de ancla y
            // arrastrar la pantalla cuando llega lo demas (ver `NotesScreen`).
            contentPadding = PaddingValues(bottom = ZenSpacing.XLarge),
        ) {
            item(key = "portada-cabeza") {
                Spacer(Modifier.height(ZenSpacing.Large))
                when {
                    // Bajando y sin nada guardado: se dice, en vez de dejar la pantalla
                    // en blanco mientras la red responde.
                    edition == null && state.downloading -> MonoLabel(
                        text = stringResource(R.string.news_downloading),
                        color = ZenColors.Dim,
                    )

                    edition == null -> MonoLabel(
                        text = stringResource(R.string.news_empty),
                        color = ZenColors.Dim,
                        maxLines = 6,
                    )

                    else -> Headline(edition = edition, fromToday = state.fromToday)
                }
            }

            if (edition != null) {
                item(key = "portada-seccion") {
                    Spacer(Modifier.height(ZenSpacing.Large))
                    ZenHairline()
                    Spacer(Modifier.height(ZenSpacing.Medium))
                    MonoLabel(text = stringResource(R.string.news_points))
                }

                // La clave lleva el numero **y** la direccion: dos claves iguales en una
                // lista perezosa lanzan excepcion, y una excepcion dentro de Zen deja el
                // telefono sin pantalla de inicio. Con solo la direccion, una portada
                // que repitiera enlace bastaria para tirarlo.
                itemsIndexed(
                    items = edition.points,
                    key = { index, point -> "punto-$index-${point.url}" },
                ) { _, point ->
                    ZenHairline()
                    NewsPointRow(point = point, locale = locale, onOpen = onOpenLink)
                }
            }

            item(key = "portada-pie") {
                ZenHairline()
                Spacer(Modifier.height(ZenSpacing.Medium))
                Footer(state = state, nowMillis = nowMillis, locale = locale, onRefresh = onRefresh)
            }
        }
    }
}

/** El titular y su parrafo: de que va el dia antes de bajar a los puntos. */
@Composable
private fun Headline(edition: NewsEdition, fromToday: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        // Sin `maxLines`: es la frase que resume el dia entero y cortarla la deja sin
        // decir nada. La pantalla se desplaza justo para esto.
        Text(
            text = edition.headline.title,
            style = ZenTextStyles.Title,
            color = ZenColors.Foreground,
        )
        if (edition.headline.subtitle.isNotEmpty()) {
            Spacer(Modifier.height(ZenSpacing.Medium))
            Text(
                text = edition.headline.subtitle,
                style = ZenTextStyles.Body,
                color = ZenColors.Tertiary,
            )
        }
        // Solo cuando lo que se lee no es de hoy. Lo que no tiene nada detras no se
        // pinta: en un dia normal esta linea no existe.
        if (!fromToday) {
            Spacer(Modifier.height(ZenSpacing.Medium))
            MonoLabel(
                text = stringResource(R.string.news_old_edition),
                color = ZenColors.Tertiary,
            )
        }
    }
}

/**
 * Un punto de la portada. La fila entera abre la noticia, no un enlace dentro del texto.
 *
 * Un enlace de tres palabras dentro de un parrafo es un blanco de 8dp; aqui el area
 * tactil es todo el bloque. Debajo va escrito lo que hace —LEER LA NOTICIA— porque sin
 * iconos y sin subrayados nada mas distingue un bloque que se toca de uno que se lee.
 */
@Composable
private fun NewsPointRow(
    point: NewsPoint,
    locale: Locale,
    onOpen: (String) -> Unit,
) {
    val openLabel = stringResource(R.string.news_open)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = openLabel) { onOpen(point.url) }
            .padding(vertical = ZenSpacing.Medium),
    ) {
        // El mismo ancho de indice que `ZenListRow`: los numeros de esta pantalla caen
        // en la misma columna que los del resto de la aplicacion.
        Text(
            text = point.index,
            style = ZenTextStyles.MonoIndex,
            color = ZenColors.Dim,
            modifier = Modifier.width(INDEX_WIDTH),
            maxLines = 1,
        )
        Spacer(Modifier.width(ZenSpacing.Medium))
        Column(Modifier.fillMaxWidth()) {
            point.section?.let { section ->
                MonoLabel(text = section.uppercase(locale))
                Spacer(Modifier.height(ZenSpacing.Small))
            }
            Text(
                text = point.title,
                style = ZenTextStyles.Headline,
                color = ZenColors.Foreground,
            )
            if (point.summary.isNotEmpty()) {
                Spacer(Modifier.height(ZenSpacing.Small))
                Text(
                    text = point.summary,
                    style = ZenTextStyles.Body,
                    color = ZenColors.Secondary,
                )
            }
            Spacer(Modifier.height(ZenSpacing.Medium))
            MonoLabel(text = stringResource(R.string.news_read), color = ZenColors.Muted)
        }
    }
}

/** De donde salio esto, cuando se bajo y como volver a bajarlo. */
@Composable
private fun Footer(
    state: NewsUiState,
    nowMillis: Long,
    locale: Locale,
    onRefresh: () -> Unit,
) {
    val edition = state.edition
    Column(Modifier.fillMaxWidth()) {
        // Un fallo con portada guardada se dice aqui: arriba ya se ve que es de otro
        // dia, y lo que falta es por que.
        if (state.failed && edition != null) {
            MonoLabel(
                text = stringResource(R.string.news_failed),
                color = ZenColors.Dim,
                maxLines = 3,
            )
            Spacer(Modifier.height(ZenSpacing.Small))
        }
        if (edition != null) {
            MonoData(
                text = downloadedText(edition, state.fromToday, nowMillis, locale),
                color = ZenColors.Dim,
            )
            Spacer(Modifier.height(ZenSpacing.Small))
        }
        // De donde sale lo que se acaba de leer. Zen no firma como suyo el trabajo de
        // otro, y ademas es el unico sitio de la aplicacion donde se dice a que servidor
        // se conecta esta pantalla.
        MonoLabel(text = stringResource(R.string.news_source), color = ZenColors.Dim)
        Spacer(Modifier.height(ZenSpacing.Medium))
        // "Descargando" se escribe en **un solo sitio** de la pantalla: arriba cuando no
        // hay portada que ensenar, y aqui cuando se pidio ACTUALIZAR sobre una que ya
        // estaba. Estuvo en los dos a la vez —tambien como rotulo del boton— y el mismo
        // aviso repetido dos veces se lee como dos cosas distintas pasando a la vez.
        if (state.downloading && edition != null) {
            MonoLabel(text = stringResource(R.string.news_downloading), color = ZenColors.Dim)
            Spacer(Modifier.height(ZenSpacing.Small))
        }
        // El rotulo del boton no cambia mientras baja: un control que se renombra solo
        // deja de ser el mismo control, y este sigue haciendo lo mismo al tocarlo.
        ZenTagButton(text = stringResource(R.string.news_refresh), onClick = onRefresh)
    }
}

/**
 * La fecha de la edicion para la franja: la que dice la portada, y si no se pudo leer,
 * la del dia en que se bajo. Nunca la de hoy porque si: eso convertiria una portada de
 * ayer en una de hoy solo por mirarla.
 */
private fun editionStamp(edition: NewsEdition, nowMillis: Long, locale: Locale): String =
    edition.editionLabel?.let { ZenDateFormats.isoShortDate(it, nowMillis, locale) }
        ?: ZenDateFormats.shortDate(edition.fetchedAtMillis, nowMillis, locale)

/**
 * "DESCARGADA A LAS 08:12" mientras es de hoy; con el dia delante cuando no lo es. Una
 * hora suelta de una portada de anteayer se lee como de esta manana.
 */
@Composable
private fun downloadedText(
    edition: NewsEdition,
    fromToday: Boolean,
    nowMillis: Long,
    locale: Locale,
): String {
    val time = ZenDateFormats.time(edition.fetchedAtMillis)
    return if (fromToday) {
        stringResource(R.string.news_downloaded_at, time)
    } else {
        stringResource(
            R.string.news_downloaded_on,
            ZenDateFormats.shortDate(edition.fetchedAtMillis, nowMillis, locale),
            time,
        )
    }
}

/** El mismo ancho de columna que el indice de `ZenListRow`. */
private val INDEX_WIDTH = 22.dp
