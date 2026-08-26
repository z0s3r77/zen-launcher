package com.zenlauncher.zen.domain.reading

/**
 * Un trozo de libro que el usuario ha senalado.
 *
 * Lleva de donde salio —libro, bloque, pagina— y no solo el texto, porque cualquier cosa
 * que se haga con el fragmento despues tiene que poder volver a el.
 */
data class Passage(
    val bookId: String,
    val blockIndex: Int,
    val page: Int,
    val text: String,
)

/** Que se le puede pedir sobre un fragmento. */
enum class StudyRequest { EXPLAIN, SUMMARIZE, DEFINE, RELATE, QUESTIONS }

/** Lo que devuelve. Texto plano: no hay formato que renderizar ni que inventar. */
data class StudyAnswer(val request: StudyRequest, val text: String)

/**
 * **La frontera del "modo estudio". Todavia no hay nada detras, y es a proposito.**
 *
 * Existe hoy, vacia, para que el lector no haya que rehacerlo el dia que la haya: el
 * lector ya sabe que bloque estas mirando y ya sabe convertirlo en un [Passage], que es
 * la unica pieza que un modelo necesita. Anadir el modo estudio sera implementar esta
 * interfaz y nombrarla en `ZenContainer`, exactamente igual que se hara con
 * `EmbeddingModel` al pasar a un motor de vectores de verdad.
 *
 * Dos condiciones, que son las del README y no se negocian aqui:
 *
 * - **El PDF no sale del telefono.** Lo que puede viajar es, como mucho, el fragmento
 *   que el usuario acaba de seleccionar, nunca el libro ni un indice de el.
 * - **Si algun dia hay una implementacion que use un servicio externo, es explicita y
 *   opcional**, y seria el tercer consumidor de `INTERNET` de toda la aplicacion: eso es
 *   una decision de producto y va al README antes que al codigo.
 *
 * Una implementacion local —un modelo pequeno en el dispositivo— no tendria ninguna de
 * las dos ataduras, y es la razon por la que esto es una interfaz y no una llamada
 * directa a nada.
 */
interface StudyModel {

    /** Que sabe hacer esta implementacion. Vacio significa que no hay modo estudio. */
    val supported: Set<StudyRequest>

    /** null cuando no se puede responder. Nunca lanza: ver el resto de fronteras de Zen. */
    suspend fun answer(passage: Passage, request: StudyRequest): StudyAnswer?
}
