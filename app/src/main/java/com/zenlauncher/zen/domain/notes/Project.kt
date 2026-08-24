package com.zenlauncher.zen.domain.notes

/**
 * Un proyecto: varias notas que el usuario ha decidido que son lo mismo.
 *
 * Lo **decide el usuario**, siempre. El asistente puede proponer que cuatro notas
 * parecen un proyecto, pero agrupar solo convertiria la lista de ideas en carpetas que
 * nadie ha pedido, y una idea metida en el cajon equivocado se pierde igual que una
 * idea olvidada.
 */
data class Project(
    val id: String,
    val title: String,
    val createdAtMillis: Long,
    val done: Boolean = false,
)
