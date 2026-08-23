package com.zenlauncher.zen.domain.apps

import com.zenlauncher.zen.domain.repository.InstalledAppsRepository
import com.zenlauncher.zen.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.first

/**
 * Siembra la pantalla de inicio con las aplicaciones esenciales, **una sola vez**.
 *
 * Antes las esenciales eran solo un respaldo que se calculaba al vuelo: la home
 * ensenaba ocho aplicaciones mientras Ajustes decia `00 / 08`, y el usuario no podia
 * reordenarlas ni quitar una sin elegir las ocho a mano. Al escribirlas en preferencias,
 * la home y Ajustes cuentan lo mismo y todo se edita desde el sitio de siempre.
 *
 * Se **anaden** a lo que ya hubiera elegido el usuario en lugar de sustituirlo: quien
 * venia de una version anterior con favoritos guardados los conserva, y las esenciales
 * rellenan los huecos que queden hasta el maximo.
 *
 * La marca de sembrado es la unica guarda: si manana el usuario los borra todos, esto no
 * vuelve a entrar. Una lista vacia puede ser una decision.
 */
class SeedEssentialFavourites(
    private val preferences: PreferencesRepository,
    private val installedApps: InstalledAppsRepository,
    private val restrictions: AppRestrictionManager,
) {

    suspend operator fun invoke() {
        if (preferences.favouritesSeeded.first()) return

        val restricted = preferences.currentRestrictedPackages()
        val visible = restrictions.visibleApps(installedApps.launchableApps(), restricted)
        val current = preferences.favouritePackages.first()

        val seeded = (current + EssentialApps.resolve(visible).map { it.packageName })
            .distinct()
            .take(EssentialApps.MAX_HOME_APPS)

        // Escribir la lista solo si cambia algo: sembrar no deberia provocar una
        // reescritura de DataStore en cada arranque de una instalacion ya poblada.
        if (seeded != current) preferences.setFavourites(seeded)
        preferences.markFavouritesSeeded()
    }
}
