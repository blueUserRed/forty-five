package com.microwavestudios.fortyfive.testing.mockservices

import com.microwavestudios.fortyfive.map.DetailMap
import com.microwavestudios.fortyfive.profile.MapSaver
import java.io.File

class MockMapSaver(
    val testMapFile: String,
    val initialStartNodeIndex: Int
) : MapSaver {

    override var currentNodeIndex: Int = initialStartNodeIndex

    override var lastNodeIndex: Int? = null

    override val currentMapName: String = testMapFile

    override val currentMap: DetailMap by lazy {
        DetailMap.readFromFile(File("test_maps/$testMapFile.onj"))
    }

}
