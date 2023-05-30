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
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
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

    const val logTag: String = "MapManager"

    lateinit var currentDetail: DetailMap
        private set

    lateinit var currentMapFile: FileHandle
        private set

    lateinit var mapImages: List<MapImageData>
        private set

    var currentMapNode: MapNode
        get() = currentDetail.uniqueNodes.find { it.index == SaveState.currentNode } ?: throw RuntimeException(
            "invalid node index ${SaveState.currentNode} in map ${currentDetail.name}"
        )
        set(value) {
            SaveState.currentNode = value.index
        }

    lateinit var displayNames: Map<String, String>
        private set

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
        displayNames = onj
            .get<OnjArray>("displayNames")
            .value
            .map { it as OnjObject }
            .associate { it.get<String>("name") to it.get<String>("display")  }
        val map = lookupMapFile(SaveState.currentMap)
        currentMapFile = map
        currentDetail = DetailMap.readFromFile(map)
    }

    fun displayName(internalName: String) = displayNames[internalName] ?: run {
        FourtyFiveLogger.warn(logTag, "no display name for $internalName")
        internalName
    }

    fun switchToMap(newMap: String, placeAtEnd: Boolean = false) {
        val map = lookupMapFile(newMap)
        currentMapFile = map
        currentDetail = DetailMap.readFromFile(map)
        SaveState.currentMap = newMap
        SaveState.currentNode = if (placeAtEnd) currentDetail.endNode.index else currentDetail.startNode.index
        switchToMapScreen()
    }

    fun write() {
        currentMapFile.file().writeText(currentDetail.asOnjObject().toString())
    }

    fun newRunSync() {
        generateMapsSync()
    }

    fun resetAllSync() {
        newRunSync()
        Gdx.files.internal(areaDefinitionsMapsPath).file().copyRecursively(
            Gdx.files.internal(areaMapsPath).file(),
            true
        )
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
        val map = generator.generate(name)
        val path = "${outputDir.toPath()}/$name.onj"
        val file = File(path)
        if (!File(file.parent).exists()) File(file.parent).mkdirs()
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
