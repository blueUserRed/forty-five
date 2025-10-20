package com.microwavestudios.fortyfive.profile

import com.microwavestudios.fortyfive.map.DetailMap
import com.microwavestudios.fortyfive.map.MapNode

interface MapSaver {

    var currentNodeIndex: Int
    var lastNodeIndex: Int?
    val currentMapName: String
    val currentMap: DetailMap

    var currentNode: MapNode
        get() {
            val index = currentNodeIndex
            return currentMap.uniqueNodes.find { it.index == index }
                ?: throw RuntimeException("no mapNode with index $index")
        }
        set(value) {
            currentNodeIndex = value.index
        }

    var lastNode: MapNode?
        get() {
            val index = lastNodeIndex ?: return null
            return currentMap.uniqueNodes.find { it.index == index }
                ?: throw RuntimeException("no mapNode with index $index")
        }
        set(value) {
            lastNodeIndex = value?.index
        }

}
