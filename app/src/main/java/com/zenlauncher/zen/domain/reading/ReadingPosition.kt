package com.zenlauncher.zen.domain.reading

/**
 * Donde esta la lectura, con precision de caracter.
 *
 * Antes bastaba el bloque: con desplazamiento continuo, la pantalla se colocaba en el
 * principio de un parrafo y lo demas caia debajo. Pasando pagina, un parrafo largo se
 * parte por la mitad y "por donde ibas" es un punto **dentro** del bloque; guardar solo
 * el bloque devolveria al lector al principio del parrafo cada vez que abre el libro.
 *
 * Sigue sin ser una pagina: el texto es reflowable y la pagina depende del tamano de
 * letra de hoy. Ver [ReadingProgress].
 */
data class ReadingPosition(
    val blockIndex: Int,
    val charOffset: Int = 0,
) : Comparable<ReadingPosition> {

    override fun compareTo(other: ReadingPosition): Int =
        compareValuesBy(this, other, { it.blockIndex }, { it.charOffset })

    companion object {
        val Start = ReadingPosition(0, 0)
    }
}
