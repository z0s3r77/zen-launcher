package com.zenlauncher.zen.domain.notes

/**
 * Convierte un texto en un vector para poder compararlo con otros.
 *
 * Es la frontera que permite cambiar de motor sin tocar ni una pantalla: hoy debajo hay
 * [LexicalEmbedder], Kotlin puro y cero megabytes; manana puede haber EmbeddingGemma
 * detras de la misma interfaz.
 *
 * El **umbral vive aqui, no en quien compara**. Dos motores distintos no reparten las
 * semejanzas en la misma escala: un 0,45 lexico y un 0,45 neuronal no significan lo
 * mismo, y un numero fijo en el codigo que busca daria conexiones absurdas al cambiar
 * de modelo. Cada motor declara a partir de cuanto **el** considera que dos textos
 * hablan de lo mismo.
 */
interface EmbeddingModel {

    /**
     * Nombre con el que se guarda cada vector.
     *
     * Se persiste junto al vector porque comparar uno lexico con uno neuronal daria un
     * numero sin ningun significado en vez de un error. Al cambiar de motor, los
     * vectores del anterior simplemente dejan de encontrarse y se reindexa.
     */
    val id: String

    val dimensions: Int

    /** A partir de que semejanza (0..1) este motor considera que dos notas se tocan. */
    val relatedThreshold: Float

    /** Vector normalizado a longitud 1, para que comparar sea un producto escalar. */
    suspend fun embed(text: String): FloatArray
}
