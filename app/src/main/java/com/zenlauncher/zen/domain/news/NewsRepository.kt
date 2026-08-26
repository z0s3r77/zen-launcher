package com.zenlauncher.zen.domain.news

/**
 * De donde sale la portada. La segunda —y ultima— frontera de Zen que sale a la red.
 *
 * **No lanza.** Devuelve null cuando no hay red, cuando el sitio contesta mal, cuando
 * tarda demasiado o cuando la pagina ya no se parece a lo que este analizador entiende.
 * Es la misma regla que el tiempo y por el mismo motivo: una excepcion sin capturar en
 * una pantalla de Zen deja el telefono sin pantalla de inicio, y unas noticias son lo
 * menos importante que hay aqui.
 */
interface NewsRepository {

    /** La portada de ahora mismo, o null si no se pudo traer o entender. */
    suspend fun frontPage(): NewsEdition?
}
