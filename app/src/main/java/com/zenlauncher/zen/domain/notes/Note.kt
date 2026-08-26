package com.zenlauncher.zen.domain.notes

/**
 * Una nota capturada.
 *
 * Todo lo que la IA local anade despues —titulo, resumen, etiquetas— es **anulable**:
 * la nota existe y se lee entera sin nada de eso. Esa es la regla que sostiene la
 * prioridad de captura (capturar, guardar, volver): si generar un titulo pudiera
 * fallar y dejar la nota a medias, el usuario perderia la idea que acababa de tener.
 */
data class Note(
    val id: String,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val body: String,
    val attachments: List<NoteAttachment> = emptyList(),
    val title: String? = null,
    val summary: String? = null,
    val tags: List<String> = emptyList(),
    val stage: NoteStage = NoteStage.SEED,
    val projectId: String? = null,
    /** Cuando la enriquecio el asistente. Null significa "todavia en la cola". */
    val enrichedAtMillis: Long? = null,
) {

    /**
     * Lo que se lee en una lista.
     *
     * Sin titulo generado no se pone un hueco ni un "Sin titulo": se ensena la primera
     * linea de lo que el usuario escribio, que es exactamente lo que el reconoce. Un
     * marcador de ausencia solo informa de que a la aplicacion le falta algo.
     */
    val displayTitle: String
        get() = title?.takeIf { it.isNotBlank() } ?: firstLine(body)

    /**
     * Lo que se lee **debajo** del titulo en una tarjeta de la lista.
     *
     * Nunca repite lo que ya dice [displayTitle]: sin titulo generado, el titulo *es*
     * la primera linea del cuerpo, asi que ensenar el cuerpo entero debajo pintaria esa
     * linea dos veces y la tarjeta pareceria un fallo de la aplicacion. Se corta por
     * donde acabo el titulo, no por la primera linea completa, porque un titulo largo
     * se trunca: lo que sobro de esa linea es texto que el usuario escribio y tiene que
     * seguir viendose.
     *
     * Se limita a [PREVIEW_MAX_CHARS] porque una tarjeta ensena seis lineas: mandar mil
     * caracteres a un `Text` que va a descartar el 95% es trabajo de medida tirado en
     * una lista que se desplaza.
     */
    val preview: String
        get() {
            val rest = if (title?.isNotBlank() == true) body else bodyAfterTitle()
            // Los huecos entre parrafos no sobreviven al recorte a seis lineas: dejarlos
            // gastaria en blanco las lineas que tenian que llevar texto.
            return rest.trim()
                .replace(BLANK_LINES, "\n")
                .take(PREVIEW_MAX_CHARS)
        }

    private fun bodyAfterTitle(): String {
        val title = displayTitle
        if (title.isEmpty()) return ""
        val start = body.indexOf(title)
        return if (start < 0) body else body.substring(start + title.length)
    }

    /** Una nota sin texto pero con una foto o un enlace sigue siendo una nota. */
    val isEmpty: Boolean get() = body.isBlank() && attachments.isEmpty()

    val links: List<NoteAttachment>
        get() = attachments.filter { it.kind == AttachmentKind.LINK }

    val images: List<NoteAttachment>
        get() = attachments.filter { it.kind == AttachmentKind.IMAGE }

    private companion object {
        const val TITLE_MAX_CHARS = 80
        const val PREVIEW_MAX_CHARS = 220
        val BLANK_LINES = Regex("\\n\\s*\\n+")

        fun firstLine(body: String): String {
            val line = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
            // Cortar por palabra y no a mitad de una: un titulo partido en "aburri"
            // se lee como un fallo de la aplicacion, no como un texto largo.
            if (line.length <= TITLE_MAX_CHARS) return line
            val cut = line.take(TITLE_MAX_CHARS)
            val lastSpace = cut.lastIndexOf(' ')
            return if (lastSpace > TITLE_MAX_CHARS / 2) cut.take(lastSpace) else cut.trimEnd()
        }
    }
}

/**
 * Estado de maduracion de una idea.
 *
 * Se lee **como texto**, nunca como un icono ni un emoji: Zen no dibuja simbolos, y un
 * brote verde en una lista monocroma seria el unico adorno de la aplicacion. Tampoco
 * avanza solo: lo mueve el usuario cuando considera que la idea ha crecido.
 */
enum class NoteStage {
    /** Recien capturada. */
    SEED,

    /** El usuario ya la ha trabajado en "Desarrollar una idea". */
    DEVELOPED,

    /** Convertida en proyecto. */
    PROJECT,

    /** Terminada. */
    DONE,
    ;

    companion object {
        fun fromStorage(raw: String): NoteStage = entries.firstOrNull { it.name == raw } ?: SEED
    }
}

/**
 * Lo que acompana al texto.
 *
 * Las imagenes se guardan por **ruta propia** dentro de la aplicacion, no por URI del
 * sistema: una foto borrada de la galeria dejaria la nota con un hueco que no se puede
 * recuperar, y una nota que pierde su contenido solo por limpiar el carrete no es un
 * sitio donde guardar ideas.
 */
data class NoteAttachment(
    val id: String,
    val noteId: String,
    val kind: AttachmentKind,
    /** Ruta relativa dentro del almacenamiento privado (IMAGE) o URL (LINK). */
    val value: String,
    val createdAtMillis: Long,
)

enum class AttachmentKind {
    IMAGE,
    LINK,
    ;

    companion object {
        fun fromStorage(raw: String): AttachmentKind = entries.firstOrNull { it.name == raw } ?: LINK
    }
}
