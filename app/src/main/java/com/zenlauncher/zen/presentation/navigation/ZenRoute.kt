package com.zenlauncher.zen.presentation.navigation

/**
 * Rutas como constantes de cadena en lugar de rutas tipadas: las tipadas exigen el
 * plugin de serializacion de Kotlin, y con ocho destinos sin argumentos no aporta
 * nada que compense anadir otro plugin al build.
 */
object ZenRoute {
    const val HOME = "home"
    const val DRAWER = "drawer"
    const val SESSION_SETUP = "session_setup"
    const val BREATHE = "breathe"
    const val RESTRICTED = "restricted"
    const val STATS = "stats"

    /** Uso del movil de hoy. Se llega desde el menu y desde el pulso de la home. */
    const val USAGE = "usage"

    /** La semana: grafica, veredicto y patron. Se llega desde la pantalla de Uso. */
    const val USAGE_WEEK = "usage_week"
    const val SETTINGS = "settings"

    /**
     * El tiempo: lo que hace y de que ciudad. Se llega desde el glifo de la franja de la
     * pantalla de inicio y desde Ajustes.
     */
    const val WEATHER = "weather"

    /**
     * La portada de noticias del dia. Se llega desde el boton NOTICIAS de la home, que
     * es la unica puerta: no hay titulares en ninguna otra pantalla.
     */
    const val NEWS = "news"

    /** Elegir las aplicaciones de la reticula. Se llega desde Ajustes y desde la home. */
    const val HOME_APPS = "home_apps"

    /**
     * Notas: la pantalla que reune capturar, buscar y recuperar. Se llega desde la fila
     * de la pantalla de inicio, que es la unica puerta.
     */
    const val NOTES = "notes"

    /** Captura. Guardar vuelve a la home, no aqui: capturar, guardar y fuera. */
    const val NOTES_QUICK = "notes_quick"

    /** Una nota concreta. El id va como segmento porque sin el no hay pantalla. */
    const val NOTE = "note"
    const val NOTE_ID_ARG = "nota"
    const val NOTE_ROUTE = "$NOTE/{$NOTE_ID_ARG}"

    fun note(id: String): String = "$NOTE/$id"

    /**
     * "Desarrollar una idea". El argumento es opcional: desde Notas se entra con una
     * idea en blanco, y desde una nota existente con su cuerpo precargado.
     */
    const val DEVELOP = "develop"
    const val DEVELOP_NOTE_ARG = "nota"
    const val DEVELOP_ROUTE = "$DEVELOP?$DEVELOP_NOTE_ARG={$DEVELOP_NOTE_ARG}"

    fun develop(noteId: String? = null): String =
        if (noteId == null) DEVELOP else "$DEVELOP?$DEVELOP_NOTE_ARG=$noteId"

    /** Lista de proyectos. Se llega desde la fila de Notas, y solo si hay alguno. */
    const val PROJECTS = "projects"

    /** Un proyecto concreto. El id va como segmento porque sin el no hay pantalla. */
    const val PROJECT = "project"
    const val PROJECT_ID_ARG = "proyecto"
    const val PROJECT_ROUTE = "$PROJECT/{$PROJECT_ID_ARG}"

    fun project(id: String): String = "$PROJECT/$id"

    /**
     * Lectura: la biblioteca de libros importados. Se llega desde la celda de la
     * reticula de la pantalla de inicio, que es la unica puerta, igual que Notas.
     */
    const val READING = "reading"

    /** Un libro concreto. El id va como segmento porque sin el no hay pantalla. */
    const val BOOK = "book"
    const val BOOK_ID_ARG = "libro"
    const val BOOK_ROUTE = "$BOOK/{$BOOK_ID_ARG}"

    fun book(id: String): String = "$BOOK/$id"

    /**
     * El escaner de documentos. Se llega **solo desde el menu** de la pantalla de inicio.
     *
     * No es una celda de la reticula ni una fila fija, y no por descuido: la home no crece,
     * las dos unicas celdas que no son aplicaciones ya son Notas y Lectura, y una tercera
     * empujaria el reloj. Escanear ademas se hace de vez en cuando —un recibo, unos
     * apuntes—, no cincuenta veces al dia, que es justo el perfil de lo que vive plegado.
     */
    const val SCANNER = "scanner"

    /**
     * Unica ruta con argumento opcional: el paquete cuya marca se toco, para abrir la lista ya
     * puesta en esa aplicacion. Es opcional —desde el menu se entra sin el— y por eso
     * va como parametro de consulta y no como segmento obligatorio.
     */
    const val NOTIFICATIONS = "notifications"
    const val NOTIFICATIONS_PACKAGE_ARG = "paquete"
    const val NOTIFICATIONS_ROUTE = "$NOTIFICATIONS?$NOTIFICATIONS_PACKAGE_ARG={$NOTIFICATIONS_PACKAGE_ARG}"

    fun notifications(packageName: String? = null): String =
        if (packageName == null) NOTIFICATIONS else "$NOTIFICATIONS?$NOTIFICATIONS_PACKAGE_ARG=$packageName"
}
