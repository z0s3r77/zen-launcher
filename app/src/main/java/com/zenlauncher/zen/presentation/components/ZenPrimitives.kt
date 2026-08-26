package com.zenlauncher.zen.presentation.components

import androidx.compose.foundation.background
import com.zenlauncher.zen.R
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles

/** Etiqueta tecnica en mayusculas. */
@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ZenColors.Dim,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        style = ZenTextStyles.MonoLabel,
        color = color,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Dato numerico en linea. */
@Composable
fun MonoData(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = ZenColors.Secondary,
) {
    Text(
        text = text,
        style = ZenTextStyles.MonoData,
        color = color,
        modifier = modifier,
        maxLines = 1,
    )
}

/**
 * Boton compacto de rotulo tecnico dentro de un marco de 1px.
 *
 * Es el unico boton "de aparato" de Zen: ZEN y RESPIRA en la pantalla de inicio,
 * capturar y desarrollar en Notas, y las respuestas a una propuesta. Marco y no relleno,
 * para que no compita con lo que se lee —la hora, las notas—, que es lo mas visible.
 *
 * Marca el limite entre lo que se hace y lo que se lee: si algo lleva este marco, es un
 * control; si es una fila o un recuadro, es contenido. Por eso el rotulo va en
 * mayusculas y en la tipografia tecnica, nunca redactado como una frase.
 *
 * @param stretch marco al ancho disponible en lugar de al del rotulo. Sirve para
 *   apilar dos botones —ZEN y RESPIRA— y que los dos marcos midan lo mismo: con anchos
 *   distintos, uno bajo el otro, el borde izquierdo queda dentado y parece un descuido.
 */
@Composable
fun ZenTagButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    stretch: Boolean = false,
) {
    Box(
        modifier = modifier
            .clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
            // 48dp de alto minimo: el marco visible es mas pequeno, pero el area
            // tactil no puede bajar del minimo de accesibilidad.
            .heightIn(min = 48.dp)
            .widthIn(min = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .then(if (stretch) Modifier.fillMaxWidth() else Modifier)
                .border(ZenSpacing.Hairline, ZenColors.Border)
                .padding(horizontal = ZenSpacing.Medium, vertical = ZenSpacing.Small),
            contentAlignment = Alignment.Center,
        ) {
            MonoLabel(text = text, color = ZenColors.Foreground)
        }
    }
}

/** Filete de 1px: la unica division que usa el sistema Industrial. */
@Composable
fun ZenHairline(
    modifier: Modifier = Modifier,
    color: Color = ZenColors.Hairline,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(ZenSpacing.Hairline)
            .background(color),
    )
}

/**
 * Franja tecnica superior: rotulo a izquierda, dato a derecha, filete debajo.
 * Da a cada pantalla la misma cabecera de aparato.
 */
@Composable
fun ZenHeaderStrip(
    left: String,
    right: String,
    modifier: Modifier = Modifier,
    leftAccent: Boolean = false,
    onBack: (() -> Unit)? = null,
    /**
     * Que dice el dato de la derecha, en palabras.
     *
     * Existe para la cara de la pantalla de inicio: `:)` es texto, pero no es texto que
     * se pueda leer en voz alta. Sin esto, el unico resumen del dia que hay en la home
     * no existiria para quien no ve la pantalla.
     */
    rightDescription: String? = null,
    onRightClick: (() -> Unit)? = null,
    /**
     * Dato secundario, a la izquierda del principal y en la misma franja.
     *
     * Existe para el tiempo de la pantalla de inicio, al lado de la cara del dia. Es el
     * unico sitio donde cabia: la home no crece, y una fila permanente mas para tres
     * caracteres y dos cifras habria empujado una aplicacion fuera de la pantalla.
     */
    secondary: String? = null,
    secondaryDescription: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Volver tiene que ser un control visible de la aplicacion, no solo un
                // gesto: con la barra de navegacion oculta, el deslizamiento desde el
                // borde lo reconoce Zen a mano (ver `EdgeBackPolicy`) y funciona, pero
                // un gesto que no se ve no existe para quien no lo conoce. Aqui siempre
                // hay una salida a un toque.
                if (onBack != null) {
                    BackControl(onBack)
                    Spacer(Modifier.width(ZenSpacing.Small))
                }
                if (leftAccent) {
                    StatusMark(active = true)
                    Spacer(Modifier.width(ZenSpacing.Small))
                }
                MonoLabel(
                    text = left,
                    color = if (leftAccent) ZenColors.Foreground else ZenColors.Dim,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Medium),
            ) {
                if (secondary != null) {
                    RightSlot(
                        text = secondary,
                        description = secondaryDescription,
                        onClick = onSecondaryClick,
                    )
                }
                RightSlot(
                    text = right,
                    description = rightDescription,
                    onClick = onRightClick,
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(ZenSpacing.Hairline)
                .background(ZenColors.Border),
        )
    }
}

/**
 * Un dato del lado derecho de la franja.
 *
 * Se toca solo si hay a donde ir. Un resumen que ensena un problema y no lleva al
 * detalle obliga a buscarlo, y en la pantalla de inicio eso son tres toques a ciegas.
 */
@Composable
private fun RightSlot(
    text: String,
    description: String?,
    onClick: (() -> Unit)?,
) {
    val label = @Composable { modifier: Modifier ->
        MonoLabel(
            text = text,
            modifier = if (description != null) {
                modifier.semantics { contentDescription = description }
            } else {
                modifier
            },
        )
    }

    if (onClick == null) {
        label(Modifier)
        return
    }

    Box(
        modifier = Modifier
            // El glifo mide dos caracteres; el area tactil no puede bajar del minimo.
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.CenterEnd,
    ) {
        label(Modifier)
    }
}

/**
 * Flecha de volver. Dibujada, no escrita: los glifos de flecha no existen en Archivo ni
 * en DM Mono y el sistema los sacaria de una fuente de reserva.
 */
@Composable
private fun BackControl(onBack: () -> Unit) {
    val label = stringResource(R.string.action_back)
    Box(
        modifier = Modifier
            .size(BACK_TOUCH_TARGET)
            .clickable(role = Role.Button, onClick = onBack)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(modifier = Modifier.size(BACK_GLYPH)) {
            val path = Path().apply {
                moveTo(size.width, 0f)
                lineTo(0f, size.height / 2f)
                lineTo(size.width, size.height)
                close()
            }
            drawPath(path, ZenColors.Muted)
        }
    }
}

private val BACK_TOUCH_TARGET = 48.dp
private val BACK_GLYPH = 10.dp

/**
 * Cuadrado de 6dp que marca estado. Es el unico lugar donde aparece el ambar.
 *
 * Nunca es la unica senal: siempre acompana a un texto que dice lo mismo, para que no
 * dependa del color ni de la agudeza visual.
 */
@Composable
fun StatusMark(
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val base = modifier.size(ZenSpacing.StatusMark)
    if (active) {
        Box(base.background(ZenColors.Accent))
    } else {
        Box(base.border(ZenSpacing.Hairline, ZenColors.Faint))
    }
}

/**
 * Fila de lista: indice monoespaciado, rotulo y contenido opcional a la derecha.
 * Altura 64dp, por encima del minimo tactil de 48dp.
 */
@Composable
fun ZenListRow(
    label: String,
    modifier: Modifier = Modifier,
    index: String? = null,
    labelColor: Color = ZenColors.Foreground,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    /**
     * Cuantas lineas puede ocupar el rotulo.
     *
     * Una por defecto: en una lista de acciones, un rotulo que se parte en dos rompe el
     * ritmo de la retícula. Dos en las listas cuyo elemento es un **nombre largo que hay
     * que distinguir de otro** —las ciudades del buscador del tiempo—, donde lo que se
     * corta es justo la parte que diferencia "Oviedo, Asturias, España" de "Oviedo,
     * Florida, Estados Unidos" y elegir se vuelve una lotería.
     */
    labelMaxLines: Int = 1,
    trailing: @Composable (() -> Unit)? = null,
) {
    val clickable = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClickLabel = onClickLabel, onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(clickable)
            .heightIn(min = ZenSpacing.Row),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ZenSpacing.Medium),
    ) {
        if (index != null) {
            Text(
                text = index,
                style = ZenTextStyles.MonoIndex,
                color = ZenColors.Dim,
                modifier = Modifier.width(22.dp),
                maxLines = 1,
            )
        }
        Text(
            text = label,
            style = ZenTextStyles.ListItem,
            color = labelColor,
            modifier = Modifier.weight(1f),
            maxLines = labelMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
        trailing?.invoke()
    }
}
