package com.microwavestudios.fortyfive.config

import com.microwavestudios.fortyfive.resources.ResourceHandle
import onj.value.OnjArray
import onj.value.OnjObject

data class MapConfig(
    val images: List<MapImageData>
) {
    companion object {

        fun fromOnj(onj: OnjObject) = MapConfig(
            onj
                .get<OnjArray>("mapImages")
                .value
                .map { MapImageData.fromOnj(it as OnjObject) }
        )
    }
}

data class MapImageData(
    val name: String,
    val resourceHandle: ResourceHandle,
    val width: Float,
    val height: Float,
    val type: Type
) {

    enum class Type {
        SIGN, NAME
    }

    companion object {

        fun fromOnj(onj: OnjObject): MapImageData = MapImageData(
            onj.get<String>("name"),
            onj.get<String>("image"),
            onj.get<Double>("width").toFloat(),
            onj.get<Double>("height").toFloat(),
            when (val type = onj.get<String>("type")) {
                "sign" -> Type.SIGN
                "name" -> Type.NAME
                else -> throw RuntimeException("unknown MapImageData.Type '$type'")
            }
        )
    }
}
