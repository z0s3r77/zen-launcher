package com.zenlauncher.zen.domain

import com.zenlauncher.zen.domain.notes.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Lo que se lee en un recuadro de la lista de notas: titulo arriba, mensaje debajo.
 *
 * Todo lo que se comprueba aqui existe por el mismo motivo: el titulo de una nota sin
 * titulo generado **sale del propio cuerpo**, asi que decidir que va debajo no es
 * recortar por la primera linea y ya.
 */
class NoteTest {

    private fun nota(body: String, title: String? = null) = Note(
        id = "a",
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
        body = body,
        title = title,
    )

    @Test
    fun `sin titulo generado, lo que ya hace de titulo no se repite debajo`() {
        val nota = nota("La gente ya no sabe aburrirse\nY eso importa")

        assertEquals("La gente ya no sabe aburrirse", nota.displayTitle)
        assertEquals("Y eso importa", nota.preview)
    }

    @Test
    fun `con titulo generado el cuerpo se lee entero`() {
        // El titulo no salio del cuerpo, asi que no repite ninguna de sus lineas.
        val nota = nota(body = "La gente ya no sabe aburrirse", title = "El aburrimiento")

        assertEquals("La gente ya no sabe aburrirse", nota.preview)
    }

    @Test
    fun `una nota de una sola linea no lleva mensaje debajo`() {
        assertEquals("", nota("Una idea suelta").preview)
    }

    @Test
    fun `un titulo truncado deja debajo el resto de su propia linea`() {
        // El titulo se corta a 80 caracteres por palabra. Si lo de debajo empezara en la
        // linea siguiente, ese resto —texto que el usuario escribio— no se veria en
        // ningun sitio hasta abrir la nota.
        val linea = "La gente ya no sabe aburrirse y eso importa mucho mas de lo que parece " +
            "cuando uno se para a pensarlo despacio"
        val nota = nota(linea)

        assertTrue(linea.startsWith(nota.displayTitle))
        assertEquals(linea.removePrefix(nota.displayTitle).trim(), nota.preview)
    }

    @Test
    fun `los huecos entre parrafos no se llevan lineas del recuadro`() {
        // Un recuadro ensena seis lineas: dos en blanco serian un tercio del mensaje.
        val nota = nota("Titulo\n\n\nPrimer parrafo\n\nSegundo parrafo")

        assertEquals("Primer parrafo\nSegundo parrafo", nota.preview)
    }

    @Test
    fun `un cuerpo enorme se corta antes de llegar a la pantalla`() {
        val nota = nota("Titulo\n" + "palabra ".repeat(500))

        assertEquals(220, nota.preview.length)
    }

    @Test
    fun `una nota sin texto no tiene ni titulo ni mensaje`() {
        val nota = nota("   \n  ")

        assertEquals("", nota.displayTitle)
        assertEquals("", nota.preview)
    }
}
