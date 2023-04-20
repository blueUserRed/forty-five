package com.fourinachamber.fourtyfive.map

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.game.SaveState
import com.fourinachamber.fourtyfive.map.detailMap.DetailMap
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjObject

object MapManager {

    // TODO: this is ugly
    const val mapScreenPath: String = "screens/map_test.onj"

    const val roadMapsPath: String = "maps/roads"
    const val areaMapsPath: String = "maps/areas"
    const val areaDefinitionsMapsPath: String = "maps/area_definitions"

    lateinit var currentDetail: DetailMap
        private set

    lateinit var currentMapFile: FileHandle
        private set

    private val mapOnjSchema: OnjSchema by lazy {
        OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/detail_map.onjschema").file())
    }

    fun init() {
        val map = lookupMapFile(SaveState.currentMap)
        currentMapFile = map
        val onj = OnjParser.parseFile(map.file())
        mapOnjSchema.assertMatches(onj)
        onj as OnjObject
        currentDetail = DetailMap.readFromOnj(onj)
    }

    fun switchToMap(newMap: String) {
        val map = lookupMapFile(newMap)
        currentMapFile = map
        val onj = OnjParser.parseFile(map.file())
        mapOnjSchema.assertMatches(onj)
        onj as OnjObject
        currentDetail = DetailMap.readFromOnj(onj)
        SaveState.currentMap = newMap
        SaveState.currentNode = 0
        switchToMapScreen()
    }

    fun write() {
        currentMapFile.file().writeText(currentDetail.asOnjObject().toString())
    }

    fun newRun() {
        Gdx.files.internal(areaDefinitionsMapsPath).file().copyRecursively(
            Gdx.files.internal(areaMapsPath).file(),
            true
        )
        MapGenerator(Gdx.files.internal("maps/map_generator_config.onj")).generate()
    }

    fun switchToMapScreen() {
        FourtyFive.changeToScreen(mapScreenPath)
    }

    fun lookupMapFile(mapName: String): FileHandle =
        lookupMapInDir(Gdx.files.internal(roadMapsPath), mapName) ?:
        lookupMapInDir(Gdx.files.internal(areaMapsPath), mapName) ?:
        throw RuntimeException("unknown map $mapName")

    private fun lookupMapInDir(dir: FileHandle, mapName: String): FileHandle? {
        val file = dir
            .file()
            .walk(FileWalkDirection.TOP_DOWN)
            .find { it.nameWithoutExtension == mapName }
        return file?.let { FileHandle(file) }
    }

}
