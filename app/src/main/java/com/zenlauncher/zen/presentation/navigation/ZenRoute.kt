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
    const val SETTINGS = "settings"

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
