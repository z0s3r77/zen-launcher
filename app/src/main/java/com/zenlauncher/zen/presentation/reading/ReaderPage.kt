package com.zenlauncher.zen.presentation.reading

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextAlign
import com.zenlauncher.zen.domain.reading.BlockKind
import com.zenlauncher.zen.domain.reading.BookBlock
import com.zenlauncher.zen.domain.reading.Highlight
import com.zenlauncher.zen.domain.reading.HighlightSpans
import com.zenlauncher.zen.domain.reading.PageFragment
import com.zenlauncher.zen.domain.reading.PageMeasurer
import com.zenlauncher.zen.domain.reading.ReaderPage
import com.zenlauncher.zen.domain.reading.ReadingSettings
import com.zenlauncher.zen.domain.reading.TextSpan
import com.zenlauncher.zen.presentation.theme.ZenColors
import com.zenlauncher.zen.presentation.theme.ZenSpacing

/**
 * El aire entre bloques, **en un solo sitio**.
 *
 * Lo usan el que mide y el que pinta. Si cada uno tuviera el suyo, la cuenta de lo que
 * cabe en una hoja no coincidiria con lo que se dibuja: la ultima linea se saldria por
 * abajo, o quedaria un hueco, y el fallo solo se veria en el dispositivo.
 */
internal object ReaderLayout {
    val HeadingGap: Dp = ZenSpacing.XLarge
    val ParagraphGap: Dp = ZenSpacing.Medium
}

/** Lo que el usuario acaba de senalar con el dedo, todavia sin guardar. */
data class ReaderSelection(
    val blockIndex: Int,
    val span: TextSpan,
    /** El subrayado que ya habia ahi, si toco encima de uno. */
    val existing: Highlight? = null,
) {
    val editing: Boolean get() = existing != null
}

/**
 * Mide el texto con el tipo, el cuerpo y el ancho de verdad.
 *
 * Es la implementacion de [PageMeasurer] con `TextMeasurer` de Compose. Todo lo que
 * **decide** —cuanto cabe, donde se corta— vive en `Paginator`, que es puro; aqui solo se
 * contesta a lo que se le pregunta.
 */
internal class ComposePageMeasurer(
    private val blocks: List<BookBlock>,
    private val measurer: TextMeasurer,
    private val settings: ReadingSettings,
    private val widthPx: Int,
    private val density: Density,
) : PageMeasurer {

    private val bodyStyle = readingBodyStyle(settings)

    override fun spacingBefore(blockIndex: Int): Float = with(density) {
        val gap = if (blocks[blockIndex].kind == BlockKind.HEADING) {
            ReaderLayout.HeadingGap
        } else {
            ReaderLayout.ParagraphGap
        }
        gap.toPx()
    }

    override fun height(blockIndex: Int, start: Int, end: Int): Float =
        layout(blockIndex, start, end).size.height.toFloat()

    override fun cut(
        blockIndex: Int,
        start: Int,
        available: Float,
        atLeastOneLine: Boolean,
    ): Int {
        val result = layout(blockIndex, start, blocks[blockIndex].text.length)
        if (result.lineCount == 0) return start

        var line = -1
        for (index in 0 until result.lineCount) {
            if (result.getLineBottom(index) <= available) line = index else break
        }
        if (line < 0) {
            if (!atLeastOneLine) return start
            line = 0
        }

        // `visibleEnd = false` a proposito: devuelve el final de linea **con** el espacio
        // que la separa de la siguiente, asi que la pagina siguiente empieza en la
        // primera letra de verdad. Con `true`, el corte cae antes del espacio y la hoja
        // siguiente arrancaria con un espacio suelto en el margen.
        return start + result.getLineEnd(line, visibleEnd = false)
    }

    private fun layout(blockIndex: Int, start: Int, end: Int): TextLayoutResult {
        val block = blocks[blockIndex]
        val safeStart = start.coerceIn(0, block.text.length)
        val safeEnd = end.coerceIn(safeStart, block.text.length)
        return measurer.measure(
            text = block.text.substring(safeStart, safeEnd),
            style = styleFor(block),
            constraints = Constraints(maxWidth = widthPx.coerceAtLeast(1)),
        )
    }

    private fun styleFor(block: BookBlock): TextStyle =
        if (block.kind == BlockKind.HEADING) {
            readingHeadingStyle(settings, block.level)
        } else {
            bodyStyle
        }
}

/**
 * Una hoja del libro.
 *
 * Cada trozo lleva sus propios gestos porque cada uno sabe **de que bloque es y en que
 * caracter empieza**, y eso es justo lo que hace falta para saber que frase hay debajo
 * del dedo. Resolverlo desde el contenedor obligaria a guardar los limites de cada trozo
 * a mano y a cruzarlos con la posicion del toque.
 */
@Composable
fun ReaderPageView(
    page: ReaderPage,
    blocks: List<BookBlock>,
    settings: ReadingSettings,
    highlights: List<Highlight>,
    selection: ReaderSelection?,
    /** Donde se toco, de 0 (borde izquierdo) a 1 (borde derecho). */
    onTapAt: (Float) -> Unit,
    onLongPressAt: (blockIndex: Int, offset: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            // Los toques que caen en el hueco de debajo del ultimo parrafo tambien pasan
            // pagina: si no, el final de un capitulo dejaria media hoja muerta.
            .pointerInput(onTapAt) {
                detectTapGestures(onTap = { onTapAt(it.x / size.width.coerceAtLeast(1)) })
            },
    ) {
        page.fragments.forEachIndexed { index, fragment ->
            if (index > 0) {
                val gap = if (blocks[fragment.blockIndex].kind == BlockKind.HEADING) {
                    ReaderLayout.HeadingGap
                } else {
                    ReaderLayout.ParagraphGap
                }
                Spacer(Modifier.height(gap))
            }
            FragmentText(
                fragment = fragment,
                block = blocks[fragment.blockIndex],
                settings = settings,
                highlights = highlights,
                selection = selection,
                onTapAt = onTapAt,
                onLongPressAt = onLongPressAt,
            )
        }
    }
}

@Composable
private fun FragmentText(
    fragment: PageFragment,
    block: BookBlock,
    settings: ReadingSettings,
    highlights: List<Highlight>,
    selection: ReaderSelection?,
    onTapAt: (Float) -> Unit,
    onLongPressAt: (blockIndex: Int, offset: Int) -> Unit,
) {
    val heading = block.kind == BlockKind.HEADING
    val text = remember(block.text, fragment) {
        block.text.substring(
            fragment.start.coerceIn(0, block.text.length),
            fragment.end.coerceIn(0, block.text.length),
        )
    }

    val annotated = remember(text, fragment, highlights, selection) {
        annotate(text, fragment, highlights, selection)
    }

    var layout: TextLayoutResult? by remember(text) { mutableStateOf(null) }

    Text(
        text = annotated,
        style = if (heading) readingHeadingStyle(settings, block.level) else readingBodyStyle(settings),
        color = if (heading) ZenColors.Foreground else ZenColors.Reading,
        // Justificado no: en una columna estrecha de movil abre rios de espacio en blanco
        // que cansan mas de lo que arregla el borde recto.
        textAlign = TextAlign.Start,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(fragment, onLongPressAt, onTapAt) {
                detectTapGestures(
                    onTap = { onTapAt(it.x / size.width.coerceAtLeast(1)) },
                    onLongPress = { position ->
                        val result = layout ?: return@detectTapGestures
                        val local = result.multiParagraph
                            .getOffsetForPosition(position)
                            .coerceIn(0, text.length)
                        onLongPressAt(fragment.blockIndex, fragment.start + local)
                    },
                )
            },
    )
}

/**
 * Pinta encima del texto lo que el usuario ha marcado.
 *
 * Sin ambar: ese tono esta reservado a las marcas de estado de 6dp. Un subrayado es
 * **contenido**, no estado, asi que va con un fondo apagado de la misma escala de grises;
 * y los que llevan nota van ademas subrayados de verdad, para poder distinguirlos en la
 * hoja sin tener que abrir la lista.
 */
private fun annotate(
    text: String,
    fragment: PageFragment,
    highlights: List<Highlight>,
    selection: ReaderSelection?,
): AnnotatedString = buildAnnotatedString {
    append(text)

    HighlightSpans.inFragment(fragment, highlights).forEach { span ->
        addStyle(
            SpanStyle(
                background = ZenColors.Border,
                textDecoration = if (span.hasNote) TextDecoration.Underline else null,
            ),
            span.start.coerceIn(0, text.length),
            span.end.coerceIn(0, text.length),
        )
    }

    // Lo que se acaba de senalar va mas claro que lo ya guardado: hay que ver que se va a
    // marcar **antes** de decidir marcarlo.
    if (selection != null && selection.blockIndex == fragment.blockIndex) {
        val start = (selection.span.start - fragment.start).coerceIn(0, text.length)
        val end = (selection.span.end - fragment.start).coerceIn(0, text.length)
        if (start < end) addStyle(SpanStyle(background = ZenColors.Faint), start, end)
    }
}
