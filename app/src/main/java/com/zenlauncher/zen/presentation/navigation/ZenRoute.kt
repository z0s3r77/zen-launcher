package com.zenlauncher.zen.presentation.navigation

/**
 * Rutas como constantes de cadena en lugar de rutas tipadas: las tipadas exigen el
 * plugin de serializacion de Kotlin, y con siete destinos sin argumentos no aporta
 * nada que compense anadir otro plugin al build.
 */
object ZenRoute {
    const val HOME = "home"
    const val DRAWER = "drawer"
    const val SESSION_SETUP = "session_setup"
    const val RESTRICTED = "restricted"
    const val STATS = "stats"
    const val SETTINGS = "settings"

    /**
     * Unica ruta con argumento: el paquete cuya marca se toco, para abrir la lista ya
     * puesta en esa aplicacion. Es opcional —desde el menu se entra sin el— y por eso
     * va como parametro de consulta y no como segmento obligatorio.
     */
    const val NOTIFICATIONS = "notifications"
    const val NOTIFICATIONS_PACKAGE_ARG = "paquete"
    const val NOTIFICATIONS_ROUTE = "$NOTIFICATIONS?$NOTIFICATIONS_PACKAGE_ARG={$NOTIFICATIONS_PACKAGE_ARG}"

    fun notifications(packageName: String? = null): String =
        if (packageName == null) NOTIFICATIONS else "$NOTIFICATIONS?$NOTIFICATIONS_PACKAGE_ARG=$packageName"
}
