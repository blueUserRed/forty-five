package com.microwavestudios.fortyfive.config

import com.microwavestudios.fortyfive.resources.ResourceHandle
import onj.value.OnjArray
import onj.value.OnjObject

class NpcConfig {

    val npcs: Map<String, Npc> by lazy {
        val onj = ConfigFileManager.getConfigFile("npcs")
        onj.get<OnjArray>("npcs").value.associate {
            val npc = Npc.fromOnj(it as OnjObject)
            npc.name to npc
        }
    }

}

data class Npc(
    val name: String,
    val texture: ResourceHandle,
    val drawWidth: Float,
    val drawHeight: Float,
    val offsetX: Float,
    val offsetY: Float,
) {

    val displayName: String = displayName(name)

    companion object {

        fun fromOnj(onj: OnjObject): Npc {
            val onjImage = onj.get<OnjObject>("image")
            return Npc(
                onj.get<String>("name"),
                onjImage.get<String>("textureName"),
                onjImage.get<Double>("width").toFloat(),
                onjImage.get<Double>("height").toFloat(),
                onjImage.getOr<Double>("offsetX", 0.0).toFloat(),
                onjImage.getOr<Double>("offsetY", 0.0).toFloat(),
            )
        }
    }
}
