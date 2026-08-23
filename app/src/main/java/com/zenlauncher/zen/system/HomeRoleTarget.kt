package com.zenlauncher.zen.system

/**
 * A que pantalla del sistema hay que enviar al usuario al tocar "pantalla de inicio".
 *
 * Se aisla de la Activity porque aqui vive un fallo facil de reintroducir: cuando Zen
 * **ya** tiene el rol, `RoleManager.createRequestRoleIntent` no sirve —el sistema no
 * ofrece un dialogo para renunciar a un rol que ya se tiene— y usarlo dejaria a Zen
 * como una via de un solo sentido, sin forma de devolver la pantalla de inicio al
 * launcher anterior desde dentro de la aplicacion.
 */
enum class HomeRoleTarget {
    /** Dialogo del sistema para pedir el rol. */
    REQUEST_ROLE,

    /** Selector de aplicacion de inicio: la unica salida cuando ya somos el launcher. */
    HOME_SETTINGS,

    ;

    companion object {
        fun of(alreadyHome: Boolean, roleAvailable: Boolean): HomeRoleTarget = when {
            // Salir siempre pasa por el selector del sistema.
            alreadyHome -> HOME_SETTINGS
            // Alguna ROM puede no exponer el rol; queda el selector como respaldo.
            !roleAvailable -> HOME_SETTINGS
            else -> REQUEST_ROLE
        }
    }
}
