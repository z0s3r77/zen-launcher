package com.zenlauncher.zen.presentation.theme

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.ui.Modifier
import androidx.compose.ui.node.DelegatableNode

/**
 * Indicacion vacia: al pulsar no hay onda, ni destello, ni cambio de elevacion.
 *
 * Es una decision de producto, no un descuido. El unico feedback aceptable en Zen es
 * que la informacion cambie; cualquier animacion de recompensa al tocar empuja
 * justo al comportamiento compulsivo que la app intenta reducir.
 */
internal object NoIndication : IndicationNodeFactory {
    private class Node : Modifier.Node()

    override fun create(interactionSource: InteractionSource): DelegatableNode = Node()

    override fun hashCode(): Int = -1

    override fun equals(other: Any?): Boolean = other === this
}
