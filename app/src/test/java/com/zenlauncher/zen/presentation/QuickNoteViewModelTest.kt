package com.zenlauncher.zen.presentation

import androidx.lifecycle.ViewModelStore
import app.cash.turbine.test
import com.zenlauncher.zen.domain.notes.AttachmentKind
import com.zenlauncher.zen.fakes.FakeAttachmentStore
import com.zenlauncher.zen.fakes.FakeDictation
import com.zenlauncher.zen.fakes.FakeNotesRepository
import com.zenlauncher.zen.fakes.FakeZenClock
import com.zenlauncher.zen.fakes.MainDispatcherRule
import com.zenlauncher.zen.presentation.notes.QuickNoteViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class QuickNoteViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val clock = FakeZenClock(wall = 1_700_000_000_000)
    private val repository = FakeNotesRepository()
    private val attachments = FakeAttachmentStore()
    private val dictation = FakeDictation()

    private fun model(appScope: CoroutineScope, voice: FakeDictation = dictation) =
        QuickNoteViewModel(repository, attachments, voice, clock, appScope)

    @Test
    fun `guarda el texto y marca la salida`() = runTest {
        val model = model(this)

        model.onTextChange("Hacer un video sobre el aburrimiento")
        model.save()
        runCurrent()

        assertEquals(1, repository.saved.size)
        assertEquals("Hacer un video sobre el aburrimiento", repository.saved.first().body)
        assertTrue(model.state.value.saved)
    }

    @Test
    fun `guardar sobrevive a que la pantalla se cierre en el mismo fotograma`() = runTest {
        // Regresion: guardar corria en `viewModelScope`, y al guardar la pantalla
        // navega a la home de inmediato. Al destruirse el ViewModel se cancelaba ese
        // scope y la escritura se quedaba a medias: la idea recien capturada se perdia
        // justo al guardarla. Ahora corre en un scope atado al proceso.
        val appScope = TestScope(testScheduler)
        val store = ViewModelStore()
        val model = model(appScope)
        // Guardar el ViewModel en un store real permite destruirlo como lo hace la
        // navegacion al sacar la pantalla de la pila.
        store.put("quick", model)

        model.onTextChange("Una idea que no se puede perder")
        model.save()
        store.clear()
        runCurrent()

        assertEquals(1, repository.saved.size)
        assertEquals("Una idea que no se puede perder", repository.saved.first().body)
    }

    @Test
    fun `la nota nace sin enriquecer para que la cola la recoja`() = runTest {
        // Es lo que mantiene la captura instantanea: aqui no se genera titulo, resumen
        // ni etiquetas, solo se apunta que faltan.
        val model = model(this)

        model.onTextChange("Una idea")
        model.save()
        runCurrent()

        val guardada = repository.saved.first()
        assertEquals(null, guardada.enrichedAtMillis)
        assertEquals(null, guardada.title)
        assertEquals(emptyList<String>(), guardada.tags)
        assertEquals(listOf(guardada), repository.pendingEnrichment(limit = 10))
    }

    @Test
    fun `sella la nota con la hora del reloj inyectado`() = runTest {
        val model = model(this)

        model.onTextChange("Una idea")
        model.save()
        runCurrent()

        assertEquals(1_700_000_000_000, repository.saved.first().createdAtMillis)
        assertEquals(1_700_000_000_000, repository.saved.first().updatedAtMillis)
    }

    @Test
    fun `una nota en blanco no se guarda ni cierra la pantalla`() = runTest {
        val model = model(this)

        model.onTextChange("   \n  ")
        model.save()
        runCurrent()

        assertTrue(repository.saved.isEmpty())
        assertFalse(model.state.value.saved)
    }

    @Test
    fun `recorta los espacios de los bordes al guardar`() = runTest {
        // Al dictar sobra un espacio al final casi siempre, y ese espacio acabaria
        // formando parte del titulo que se ensena en la lista.
        val model = model(this)

        model.onTextChange("  Una idea con aire alrededor  \n")
        model.save()
        runCurrent()

        assertEquals("Una idea con aire alrededor", repository.saved.first().body)
    }

    @Test
    fun `una foto sin una sola palabra ya es una nota`() = runTest {
        // Se fotografia una pizarra o una pagina y el texto es justo lo que no hace
        // falta escribir. Exigir texto obligaria a escribir algo para poder guardar.
        val model = model(this)

        model.addImage("content://media/1")
        runCurrent()
        assertTrue(model.state.value.canSave)

        model.save()
        runCurrent()

        assertEquals(1, repository.saved.size)
        assertEquals("", repository.saved.first().body)
        assertEquals(1, repository.saved.first().images.size)
    }

    @Test
    fun `una imagen que no se puede leer no impide guardar la idea`() = runTest {
        // Degradar: la foto se pierde, el texto no. Un aviso de error aqui
        // interrumpiria una captura con prisa para contar algo que no se puede arreglar.
        val model = model(this)
        attachments.failNext()

        model.onTextChange("La idea de verdad")
        model.addImage("content://media/rota")
        runCurrent()
        model.save()
        runCurrent()

        assertEquals(emptyList<Any>(), model.state.value.images)
        assertEquals("La idea de verdad", repository.saved.first().body)
    }

    @Test
    fun `los enlaces del texto se adjuntan solos al guardar`() = runTest {
        // Sin boton de adjuntar enlace: un enlace siempre llega pegado, y pedir un
        // toque para clasificar lo que se acaba de pegar es friccion.
        val model = model(this)

        model.onTextChange("Ver esto https://ejemplo.es/articulo antes del jueves")
        model.save()
        runCurrent()

        val enlaces = repository.saved.first().links
        assertEquals(1, enlaces.size)
        assertEquals("https://ejemplo.es/articulo", enlaces.first().value)
        assertEquals(AttachmentKind.LINK, enlaces.first().kind)
    }

    @Test
    fun `los enlaces se guardan en el orden en que aparecen en el texto`() = runTest {
        val model = model(this)

        model.onTextChange("primero https://uno.es/a y luego https://dos.es/b")
        model.save()
        runCurrent()

        assertEquals(
            listOf("https://uno.es/a", "https://dos.es/b"),
            repository.saved.first().links.map { it.value },
        )
    }

    @Test
    fun `una nota sin enlaces no adjunta ninguno`() = runTest {
        val model = model(this)

        model.onTextChange("Hemos perdido la capacidad de aburrirnos")
        model.save()
        runCurrent()

        assertEquals(emptyList<Any>(), repository.saved.first().attachments)
    }

    @Test
    fun `salir sin guardar se lleva las imagenes ya copiadas`() = runTest {
        // Sin esto, cada captura abandonada despues de elegir una foto dejaria una
        // carpeta que no aparece en ninguna nota: basura invisible que solo crece.
        val model = model(this)

        model.addImage("content://media/1")
        runCurrent()
        model.discard()
        runCurrent()

        assertEquals(1, attachments.deletedFor.size)
    }

    @Test
    fun `salir despues de guardar no borra las imagenes de la nota guardada`() = runTest {
        // El gesto de volver puede llegar despues de guardar; si borrara igual, la nota
        // se quedaria en la lista con las fotos ya desaparecidas del disco.
        val model = model(this)

        model.addImage("content://media/1")
        runCurrent()
        model.save()
        runCurrent()
        model.discard()
        runCurrent()

        assertEquals(emptyList<String>(), attachments.deletedFor)
    }

    @Test
    fun `sin reconocedor en el dispositivo no se puede dictar`() = runTest {
        val model = model(this, voice = FakeDictation(available = false))

        assertFalse(model.state.value.canDictate)
    }

    @Test
    fun `el texto aparece mientras se habla`() = runTest {
        // Que se vea aparecer es lo que dice que te esta oyendo. Sin parciales, dictar
        // es mirar una pantalla quieta y esperar.
        val model = model(this)

        model.toggleDictation()
        runCurrent()
        dictation.hear("hacer un video")
        runCurrent()

        assertEquals("hacer un video", model.state.value.text)
        assertTrue(model.state.value.listening)
    }

    @Test
    fun `cada resultado parcial reemplaza al anterior en vez de acumularse`() = runTest {
        // Regresion en potencia: el reconocedor manda la frase entera corregida en cada
        // parcial, no trozos nuevos. Sumarlos daria "hacer hacer un hacer un video".
        val model = model(this)

        model.toggleDictation()
        runCurrent()
        dictation.hear("hacer")
        runCurrent()
        dictation.hear("hacer un")
        runCurrent()
        dictation.hear("hacer un video")
        runCurrent()

        assertEquals("hacer un video", model.state.value.text)
    }

    @Test
    fun `lo dictado se anade detras de lo ya escrito sin pegar las palabras`() = runTest {
        val model = model(this)

        model.onTextChange("Idea:")
        model.toggleDictation()
        runCurrent()
        dictation.settle("hacer un video sobre el aburrimiento")
        runCurrent()

        assertEquals("Idea: hacer un video sobre el aburrimiento", model.state.value.text)
    }

    @Test
    fun `dictar dos veces seguidas encadena las dos frases`() = runTest {
        // El punto de partida se vuelve a fijar en cada arranque: si no, la segunda
        // tanda borraria la primera.
        val model = model(this)

        model.toggleDictation()
        runCurrent()
        dictation.settle("primera idea")
        dictation.stop()
        runCurrent()

        model.toggleDictation()
        runCurrent()
        dictation.settle("segunda idea")
        runCurrent()

        assertEquals("primera idea segunda idea", model.state.value.text)
    }

    @Test
    fun `parar suelta el microfono`() = runTest {
        // En el reconocedor real esto es lo que llama a destroy. Sin ello, el launcher
        // se quedaria el microfono cogido despues de dictar.
        val model = model(this)

        model.toggleDictation()
        runCurrent()
        model.toggleDictation()
        runCurrent()

        assertFalse(model.state.value.listening)
        assertEquals(1, dictation.released)
    }

    @Test
    fun `salir de la pantalla dictando tambien suelta el microfono`() = runTest {
        val model = model(this)

        model.toggleDictation()
        runCurrent()
        model.discard()
        runCurrent()

        assertFalse(model.state.value.listening)
        assertEquals(1, dictation.released)
    }

    @Test
    fun `si el reconocedor se cierra solo, la fila deja de decir que escucha`() = runTest {
        // Silencio o error: para el caso es lo mismo, se vuelve a tocar. Lo que no
        // puede pasar es que la fila se quede diciendo ESCUCHANDO para siempre.
        val model = model(this)

        model.toggleDictation()
        runCurrent()
        dictation.stop()
        runCurrent()

        assertFalse(model.state.value.listening)
    }

    @Test
    fun `denegar el microfono se apunta como estado y no vuelve a pedirse`() = runTest {
        val model = model(this)

        model.onMicrophoneDenied()

        assertTrue(model.state.value.micDenied)
        assertFalse(model.state.value.listening)
    }

    @Test
    fun `lo dictado se guarda como cualquier otra nota`() = runTest {
        val model = model(this)

        model.toggleDictation()
        runCurrent()
        dictation.settle("Se me acaba de ocurrir hacer un video")
        dictation.stop()
        runCurrent()
        model.save()
        runCurrent()

        assertEquals("Se me acaba de ocurrir hacer un video", repository.saved.first().body)
    }

    @Test
    fun `el boton de guardar solo existe cuando hay algo escrito`() = runTest {
        val model = model(this)

        model.state.test {
            assertFalse(awaitItem().canSave)

            model.onTextChange("a")

            assertTrue(awaitItem().canSave)
        }
    }
}
