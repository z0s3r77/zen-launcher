package com.zenlauncher.zen.domain.reading

/** Titulo y autor, con la certeza que se pueda. El autor puede faltar. */
data class BookInfo(val title: String, val author: String?)

/**
 * Deduce el titulo y el autor. Puro y sin Android.
 *
 * **Android no da metadatos de un PDF**: `PdfRenderer` abre paginas y extrae texto, y no
 * hay ninguna API publica para leer el diccionario `/Info` con el titulo y el autor. Asi
 * que se leen de la portada, que es donde estan escritos para un lector humano.
 *
 * El autor puede quedarse en null y no pasa nada: la biblioteca no pinta la linea si no
 * hay autor, igual que la home no pinta el mando del reproductor sin musica. Un "Autor
 * desconocido" es texto que ocupa sitio para no decir nada.
 */
object BookMetadata {

    /**
     * @param coverText el texto de la primera pagina con contenido.
     * @param fileName el nombre del fichero elegido, como ultimo recurso.
     */
    fun detect(coverText: String, fileName: String): BookInfo {
        val lines = coverText.split('\n')
            .map { it.trim() }
            .filter { it.length in MIN_LINE..MAX_LINE && it.any(Char::isLetter) }
            .filterNot { NOISE.containsMatchIn(it) }

        val title = lines.firstOrNull()?.let(::tidy)
        val author = lines.drop(1).firstOrNull { plausibleAuthor(it) }?.let(::tidy)

        return BookInfo(
            title = title ?: fromFileName(fileName),
            author = author,
        )
    }

    /**
     * Un nombre de persona: pocas palabras, empieza en mayuscula y sin puntuacion de
     * frase. "Jean-Paul Sartre" pasa; "uno de los problemas fundamentales de la" no.
     */
    internal fun plausibleAuthor(line: String): Boolean {
        val words = line.split(' ').filter { it.isNotBlank() }
        if (words.size !in MIN_AUTHOR_WORDS..MAX_AUTHOR_WORDS) return false
        if (line.any { it in SENTENCE }) return false
        val named = words.count { word -> word.firstOrNull()?.isUpperCase() == true }
        // La mitad en mayuscula deja pasar las particulas ("de", "van", "y").
        return named * 2 >= words.size
    }

    /**
     * El nombre del fichero, presentable.
     *
     * Se le quita la extension y se cambian guiones y subrayados por espacios: casi
     * todos los apuntes que circulan por la universidad se llaman
     * "sartre_el-ser-y-la-nada.pdf", y ensenar eso tal cual en la biblioteca es ensenar
     * un nombre de fichero, no un libro.
     */
    internal fun fromFileName(fileName: String): String {
        val bare = fileName.substringBeforeLast('.', fileName)
        val spaced = bare.replace('_', ' ').replace('-', ' ').replace(Regex("\\s+"), " ").trim()
        return spaced.ifEmpty { fileName }.take(MAX_LINE)
    }

    private fun tidy(text: String): String = text.replace(Regex("\\s+"), " ").trim()

    private const val MIN_LINE = 2
    private const val MAX_LINE = 120
    private const val MIN_AUTHOR_WORDS = 2
    private const val MAX_AUTHOR_WORDS = 6

    private val SENTENCE = charArrayOf('.', ',', ';', ':', '?', '!', '(', ')')

    /**
     * Lo que aparece en una portada y no es ni el titulo ni el autor.
     *
     * Cortito a proposito: cada linea que se descarta es una linea que ya no puede ser
     * el titulo, y equivocarse aqui deja el libro llamandose como su fichero.
     */
    private val NOISE = Regex(
        "^(www\\.|http|isbn|edici[óo]n|editorial|traducci[óo]n|copyright|©|\\d{4}$)",
        RegexOption.IGNORE_CASE,
    )
}
