package com.zenlauncher.zen.domain.notes

import kotlinx.coroutines.flow.Flow

/**
 * Almacen de notas, adjuntos, conexiones y proyectos.
 *
 * Una sola interfaz para las cuatro cosas porque son un solo grafo: guardar una nota,
 * conectarla y meterla en un proyecto son escrituras que tienen que verse a la vez. Con
 * repositorios separados, la pantalla tendria que coordinar tres fuentes y decidir en
 * que orden llegan, que es justo el tipo de estado inconsistente que hace que una nota
 * aparezca sin sus adjuntos durante un fotograma.
 *
 * Igual que [com.zenlauncher.zen.domain.repository.SessionRepository], es una interfaz
 * y no una clase de Room: KSP no es compatible con el Kotlin integrado de AGP 9.
 */
interface NotesRepository {

    /** Todas las notas, de la mas reciente a la mas antigua. Reemite en cada cambio. */
    fun observeNotes(): Flow<List<Note>>

    /** Una nota concreta con sus adjuntos. Reemite mientras se la sigue mirando. */
    fun observeNote(id: String): Flow<Note?>

    suspend fun note(id: String): Note?

    /**
     * Guarda una nota nueva o pisa la que tenga ese id.
     *
     * Escribe la nota **y sus adjuntos** en una transaccion: una nota dictada con una
     * foto no puede quedar a medias si el proceso muere entre las dos escrituras.
     */
    suspend fun save(note: Note)

    suspend fun delete(id: String)

    /**
     * Notas cuyo texto contiene lo buscado, ignorando acentos y mayusculas.
     *
     * Es la busqueda literal, la que siempre esta. La semantica se monta encima en la
     * fase del indice y **no la sustituye**: quien busca una palabra que sabe que
     * escribio espera encontrarla exactamente, no algo parecido.
     */
    suspend fun search(query: String): List<Note>

    /** Notas todavia sin pasar por el asistente, de la mas antigua a la mas nueva. */
    suspend fun pendingEnrichment(limit: Int): List<Note>

    // --- Conexiones ---

    fun observeLinks(noteId: String): Flow<List<NoteLink>>

    /**
     * Guarda la conexion, o actualiza su estado si esa pareja ya existia.
     *
     * La pareja no tiene direccion (ver [NoteLink.pairKey]): proponer A-B cuando ya se
     * ignoro B-A seria repetir una sugerencia que el usuario ya descarto.
     */
    suspend fun putLink(link: NoteLink)

    /**
     * Todas las propuestas sin responder.
     *
     * La pantalla de Notas la usa para ensenar que ideas tienen algo esperando. Devuelve
     * los enlaces y no las notas porque quien llama decide que hacer con ellos: contar,
     * agrupar o abrir.
     */
    fun observePendingLinks(): Flow<List<NoteLink>>

    /** Parejas que el usuario ya descarto, para no volver a proponerlas. */
    suspend fun ignoredPairs(): Set<String>

    // --- Indice semantico ---

    /**
     * Guarda el vector de una nota, apuntando que motor lo genero.
     *
     * El id del modelo se persiste porque comparar un vector lexico con uno neuronal
     * daria un numero sin significado: al cambiar de motor, los del anterior dejan de
     * encontrarse y se reindexa.
     */
    suspend fun putEmbedding(noteId: String, model: String, vector: FloatArray)

    /** Todos los vectores de ese motor, para comparar contra ellos en memoria. */
    suspend fun embeddings(model: String): Map<String, FloatArray>

    /** Notas que ese motor todavia no ha indexado, de la mas antigua a la mas nueva. */
    suspend fun notesWithoutEmbedding(model: String, limit: Int): List<Note>

    // --- Proyectos ---

    fun observeProjects(): Flow<List<Project>>

    suspend fun saveProject(project: Project)

    suspend fun deleteProject(id: String)

    /** Mete la nota en el proyecto, o la saca si [projectId] es null. */
    suspend fun assignToProject(noteId: String, projectId: String?)

    suspend fun notesInProject(projectId: String): List<Note>
}
