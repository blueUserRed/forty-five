package com.fourinachamber.fourtyfive.map

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.game.SaveState
import com.fourinachamber.fourtyfive.map.detailMap.DetailMap
import com.fourinachamber.fourtyfive.map.detailMap.MapNode
import com.fourinachamber.fourtyfive.map.detailMap.MapRestriction
import com.fourinachamber.fourtyfive.map.detailMap.SeededMapGenerator
import com.fourinachamber.fourtyfive.screen.ResourceHandle
import kotlinx.coroutines.*
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject
import java.io.File

object MapManager {

    // TODO: this is ugly
    const val mapScreenPath: String = "screens/map_test.onj"

    const val mapConfigFilePath: String = "maps/map_config.onj"
    const val roadMapsPath: String = "maps/roads"
    const val areaMapsPath: String = "maps/areas"
    const val areaDefinitionsMapsPath: String = "maps/area_definitions"

    lateinit var currentDetail: DetailMap
        private set

    lateinit var currentMapFile: FileHandle
        private set

    lateinit var mapImages: List<MapImageData>
        private set

    private val mapOnjSchema: OnjSchema by lazy {
        OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/detail_map.onjschema").file())
    }

    fun init() {

        val onj = OnjParser.parseFile(Gdx.files.internal(mapConfigFilePath).file())
        mapConfigSchema.assertMatches(onj)
        onj as OnjObject
        mapImages = onj
            .get<OnjArray>("mapImages")
            .value
            .map { it as OnjObject }
            .map { MapImageData(
                it.get<String>("name"),
                it.get<String>("image"),
                it.get<Double>("width").toFloat(),
                it.get<Double>("width").toFloat()
            )}

        val map = lookupMapFile(SaveState.currentMap)
        currentMapFile = map
        val mapOnj = OnjParser.parseFile(map.file())
        mapOnjSchema.assertMatches(mapOnj)
        mapOnj as OnjObject
        currentDetail = DetailMap.readFromOnj(mapOnj)
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

//    fun newRun() {
//        generateMaps()
//    }

//    fun resetAll() {
//        newRun()
//        Gdx.files.internal(areaDefinitionsMapsPath).file().copyRecursively(
//            Gdx.files.internal(areaMapsPath).file(),
//            true
//        )
//    }

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

    suspend fun generateMaps(coroutineScope: CoroutineScope) = with(coroutineScope) {
        val onj = OnjParser.parseFile(Gdx.files.internal(mapConfigFilePath).file())
        mapConfigSchema.assertMatches(onj)
        onj as OnjObject
        val generatorConfig = onj.get<OnjObject>("generatorConfig")
        val outputDir = Gdx.files.local(generatorConfig.get<String>("outputDirectory")).file()
        val jobs = generatorConfig
            .get<OnjArray>("maps")
            .value
            .map { map ->
                launch {
                    generateMap(map as OnjObject, outputDir)
                }
            }
        jobs.joinAll()
    }

    fun generateMapsSync() {
        val onj = OnjParser.parseFile(Gdx.files.internal(mapConfigFilePath).file())
        mapConfigSchema.assertMatches(onj)
        onj as OnjObject
        val generatorConfig = onj.get<OnjObject>("generatorConfig")
        val outputDir = Gdx.files.local(generatorConfig.get<String>("outputDirectory")).file()
        generatorConfig
            .get<OnjArray>("maps")
            .value
            .forEach { map ->
                generateMap(map as OnjObject, outputDir)
            }
    }

    private fun generateMap(onj: OnjObject, outputDir: File) {
        val name = onj.get<String>("name")
        val mapRestriction = MapRestriction.fromOnj(onj.get<OnjObject>("restrictions"))
        val generator = SeededMapGenerator(onj.get<Long>("seed"), mapRestriction)
        val map = generator.generate()
        val path = "${outputDir.toPath()}/$name.onj"
        val file = File(path)
        file.createNewFile()
        file.writeText(map.asOnjObject().toString())
    }

    private val mapConfigSchema: OnjSchema by lazy {
        OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/map_config.onjschema").file())
    }

    data class MapImageData(
        val name: String,
        val resourceHandle: ResourceHandle,
        val width: Float,
        val height: Float
    )

}
