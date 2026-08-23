package com.zenlauncher.zen.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zenlauncher.zen.R
import com.zenlauncher.zen.domain.media.NowPlaying
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing
import com.zenlauncher.zen.presentation.theme.ZenTextStyles

/**
 * Mando del reproductor: tres ordenes y nada mas.
 *
 * No hay caratula, ni titulo de cancion, ni barra de progreso arrastrable. Un launcher
 * que ensena que estas escuchando te invita a cambiarlo; este solo permite hacer a
 * ciegas lo que ya ibas a hacer —pausar o saltar— sin abrir Spotify.
 *
 * Los simbolos se dibujan con [Canvas] en lugar de escribirse como texto: los glifos
 * de reproduccion no existen en Archivo ni en DM Mono, asi que el sistema los sacaria
 * de una fuente de reserva y romperia la unica regla tipografica de la aplicacion.
 */
@Composable
fun MediaTransportBar(
    playing: Boolean,
    onPrevious: () -> Unit,
    onTogglePlayback: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    nowPlaying: NowPlaying? = null,
    onOpenPlayer: (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        ZenHairline(color = ZenColors.Border)

        // La ficha de la cancion solo aparece si el usuario concedio el acceso a los
        // metadatos; sin el, la barra es exactamente la de antes.
        if (nowPlaying != null && nowPlaying.hasText) {
            NowPlayingCard(nowPlaying, onOpenPlayer)
            ZenHairline()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TRANSPORT_ROW_HEIGHT),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TransportButton(
                glyph = Glyph.PREVIOUS,
                description = stringResource(R.string.media_previous),
                onClick = onPrevious,
                modifier = Modifier.weight(1f),
            )
            TransportButton(
                glyph = if (playing) Glyph.PAUSE else Glyph.PLAY,
                description = stringResource(
                    if (playing) R.string.media_pause else R.string.media_play,
                ),
                onClick = onTogglePlayback,
                modifier = Modifier.weight(1f),
            )
            TransportButton(
                glyph = Glyph.NEXT,
                description = stringResource(R.string.media_next),
                onClick = onNext,
                modifier = Modifier.weight(1f),
            )
        }

        ZenHairline()
        Spacer(Modifier.height(ZenSpacing.Small))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlaybackEqualizer(playing = playing)
                Spacer(Modifier.width(ZenSpacing.Small))
                MonoLabel(text = stringResource(R.string.media_title))
            }
            // El estado se lee, no se deduce de un color ni de la forma del icono.
            MonoLabel(
                text = stringResource(
                    if (playing) R.string.media_state_playing else R.string.media_state_paused,
                ),
            )
        }
    }
}

/**
 * Caratula, titulo y artista. Sin barra de progreso arrastrable y sin botones de "me
 * gusta": esto informa de que suena, no invita a gestionar la biblioteca.
 */
@Composable
private fun NowPlayingCard(
    nowPlaying: NowPlaying,
    onOpenPlayer: (() -> Unit)?,
) {
    // Solo se puede tocar si se sabe a quien abrir: una ficha que no reacciona es peor
    // que una que no invita a tocarla.
    val openable = onOpenPlayer != null && nowPlaying.packageName != null
    val openLabel = stringResource(R.string.media_open_player)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (openable) {
                    Modifier.clickable(
                        role = Role.Button,
                        onClickLabel = openLabel,
                        onClick = { onOpenPlayer?.invoke() },
                    )
                } else {
                    Modifier
                },
            )
            .padding(vertical = ZenSpacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val artwork = nowPlaying.artwork
        if (artwork != null) {
            Image(
                bitmap = artwork.asImageBitmap(),
                // Decorativa: el titulo y el artista, que van al lado, ya lo dicen todo.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(ARTWORK_SIZE),
            )
        } else {
            // Hueco marcado en lugar de texto desplazado: hay reproductores y podcasts
            // que publican titulo pero no imagen.
            Box(
                Modifier
                    .size(ARTWORK_SIZE)
                    .border(ZenSpacing.Hairline, ZenColors.Border),
            )
        }

        Spacer(Modifier.width(ZenSpacing.Medium))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = nowPlaying.title,
                style = ZenTextStyles.Tile,
                color = ZenColors.Foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (nowPlaying.artist.isNotBlank()) {
                Spacer(Modifier.height(ZenSpacing.XSmall))
                MonoData(text = nowPlaying.artist, color = ZenColors.Muted)
            }
        }
    }
}

private enum class Glyph { PREVIOUS, PLAY, PAUSE, NEXT }

@Composable
private fun TransportButton(
    glyph: Glyph,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .heightIn(min = TRANSPORT_ROW_HEIGHT)
            .clickable(role = Role.Button, onClick = onClick)
            // El simbolo esta dibujado, no escrito: sin esto el lector de pantalla
            // anunciaria un boton sin nombre.
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(GLYPH_SIZE)) {
            when (glyph) {
                Glyph.PREVIOUS -> drawSkip(pointsRight = false, color = ZenColors.Foreground)
                Glyph.NEXT -> drawSkip(pointsRight = true, color = ZenColors.Foreground)
                Glyph.PLAY -> drawTriangle(
                    left = 0f,
                    width = size.width,
                    pointsRight = true,
                    color = ZenColors.Foreground,
                )

                Glyph.PAUSE -> drawPause(ZenColors.Foreground)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSkip(
    pointsRight: Boolean,
    color: Color,
) {
    val half = size.width / 2f
    drawTriangle(left = 0f, width = half, pointsRight = pointsRight, color = color)
    drawTriangle(left = half, width = half, pointsRight = pointsRight, color = color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTriangle(
    left: Float,
    width: Float,
    pointsRight: Boolean,
    color: Color,
) {
    val path = Path().apply {
        if (pointsRight) {
            moveTo(left, 0f)
            lineTo(left + width, size.height / 2f)
            lineTo(left, size.height)
        } else {
            moveTo(left + width, 0f)
            lineTo(left, size.height / 2f)
            lineTo(left + width, size.height)
        }
        close()
    }
    drawPath(path, color)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPause(color: Color) {
    val barWidth = size.width * 0.3f
    drawRect(color = color, topLeft = Offset.Zero, size = Size(barWidth, size.height))
    drawRect(
        color = color,
        topLeft = Offset(size.width - barWidth, 0f),
        size = Size(barWidth, size.height),
    )
}

private val TRANSPORT_ROW_HEIGHT = 56.dp
private val ARTWORK_SIZE = 56.dp
private val GLYPH_SIZE = 16.dp
