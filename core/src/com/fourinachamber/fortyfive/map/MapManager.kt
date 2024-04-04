package com.fourinachamber.fortyfive.map

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.game.GameDirector
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.map.detailMap.*
import com.fourinachamber.fortyfive.map.events.chooseCard.ChooseCardScreenContext
import com.fourinachamber.fortyfive.map.events.dialog.DialogScreenController
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject
import java.io.File
import kotlin.math.log

object MapManager {

    const val logTag: String = "MapManager"
    const val mapConfigFilePath: String = "maps/map_config.onj"

    lateinit var roadMapsPath: String
        private set
    lateinit var areaMapsPath: String
        private set
    lateinit var areaDefinitionsMapsPath: String
        private set
    lateinit var staticRoadMapsPath: String
        private set
    lateinit var mapScreenPath: String
        private set

    lateinit var currentDetailMap: DetailMap
        private set

    lateinit var currentMapFile: FileHandle
        private set

    lateinit var mapImages: List<MapImageData>
        private set

    var currentMapNode: MapNode
        get() = currentDetailMap.uniqueNodes.find { it.index == SaveState.currentNode } ?: run {
            FortyFiveLogger.warn(logTag, "Player was on node ${SaveState.currentNode} in map $currentDetailMap, which doesn't exist. Reset player to node 0.")
            SaveState.currentNode = 0
            SaveState.lastNode = null
            currentDetailMap.uniqueNodes[0]
        }
        set(value) {
            SaveState.currentNode = value.index
        }

    var lastMapNode: MapNode?
        get() = if (SaveState.lastNode != null) {
            currentDetailMap.uniqueNodes.find { it.index == SaveState.lastNode }
        } else {
            null
        }
        set(value) {
            SaveState.lastNode = value?.index
        }

    lateinit var displayNames: Map<String, String>
        private set

    private lateinit var screenPaths: Map<String, String>

    lateinit var npcsOnj: OnjObject
        private set

    fun init() {
        val onj = OnjParser.parseFile(Gdx.files.internal(mapConfigFilePath).file())
        mapConfigSchema.assertMatches(onj)
        onj as OnjObject
        mapImages = onj
            .get<OnjArray>("mapImages")
            .value
            .map { it as OnjObject }
            .map {
                MapImageData(
                    it.get<String>("name"),
                    it.get<String>("image"),
                    it.get<Double>("width").toFloat(),
                    it.get<Double>("height").toFloat(),
                    it.get<String>("type"),
                )
            }
        val paths = onj.get<OnjObject>("paths")
        areaMapsPath = paths.get<String>("areas")
        roadMapsPath = paths.get<String>("roads")
        areaDefinitionsMapsPath = paths.get<String>("areaDefinitions")
        mapScreenPath = paths.get<String>("mapScreen")
        staticRoadMapsPath = paths.get<String>("staticRoadDefinitions")
        val displayNames = onj
            .get<OnjArray>("displayNames")
            .value
            .map { it as OnjObject }
            .associate { it.get<String>("name") to it.get<String>("display") }
            .toMutableMap()
        screenPaths = onj
            .get<OnjObject>("screens")
            .value
            .mapValues { (_, value) -> value.value as String }
        val npcs = OnjParser.parseFile(Gdx.files.internal("maps/events/npcs.onj").file())
        val npcsSchema =  OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/npcs.onjschema").file())
        npcsSchema.assertMatches(npcs)
        npcs as OnjObject
        npcsOnj = npcs
        npcs
            .get<OnjArray>("npcs")
            .value
            .map { it as OnjObject }
            .map { it.get<String>("name") to it.get<String>("displayName") }
            .forEach {displayNames[it.first] = it.second }
        this.displayNames = displayNames
    }

    fun read() {
        val map = lookupMapFile(SaveState.currentMap)
        currentMapFile = map
        currentDetailMap = readDetailMap(map)
    }

    fun changeToEncounterScreen(context: GameController.EncounterContext) {
        FortyFive.changeToScreen(screenPaths["encounterScreen"]!!, context)
    }

    fun changeToDialogScreen(event: MapEvent) {
        FortyFive.changeToScreen(screenPaths["dialogScreen"]!!, event)
    }

    fun changeToShopScreen(event: MapEvent) {
        FortyFive.changeToScreen(screenPaths["shopScreen"]!!, event)
    }

    fun changeToChooseCardScreen(context: ChooseCardScreenContext) {
        FortyFive.changeToScreen(screenPaths["chooseCardScreen"]!!, context)
    }

    fun changeToHealOrMaxHPScreen(event: MapEvent) {
        FortyFive.changeToScreen(screenPaths["changeToHealOrMaxHPScreen"]!!, event)
    }

    fun changeToAddMaxHPScreen(event: MapEvent) {
        FortyFive.changeToScreen(screenPaths["changeToAddMaxHPScreen"]!!, event)
    }

    fun changeToTitleScreen() {
        FortyFive.changeToScreen(screenPaths["titleScreen"]!!)
    }

    fun changeToCreditsScreen() {
        FortyFive.changeToScreen(screenPaths["creditsScreen"]!!)
    }

    /**
     * @see DetailMap.invalidateCachedAssets
     */
    fun invalidateCachedAssets() {
        currentDetailMap.invalidateCachedAssets()
    }

    fun displayName(internalName: String) = displayNames[internalName] ?: run {
        FortyFiveLogger.warn(logTag, "no display name for $internalName")
        internalName
    }

    fun changeToMap(newMap: String, fromArea: String = currentDetailMap.name) {
        write()
        val map = lookupMapFile(newMap)
        currentMapFile = map
        currentDetailMap = readDetailMap(map)
        SaveState.currentMap = newMap
        SaveState.currentNode = currentDetailMap
            .uniqueNodes
            .filter { it.event is EnterMapMapEvent }
            .find { (it.event as EnterMapMapEvent).targetMap == fromArea }
            ?.index
            ?: 0
        SaveState.lastNode = null
        FortyFiveLogger.debug(logTag, "changing from $fromArea to $newMap; currentNode = $currentMapNode")
        changeToMapScreen()
    }

    private fun readDetailMap(map: FileHandle): DetailMap = try {
        DetailMap.readFromFile(map)
    } catch (e: DetailMap.InvalidMapFileException) {
        FortyFiveLogger.warn(logTag, "Invalid map file found, reloading all maps")
        generateMapsSync()
        copyStaticMaps()
        SaveState.currentNode = 0
        SaveState.lastNode = null
        DetailMap.readFromFile(map)
    }

    fun write() {
        currentMapFile.file().writeText(currentDetailMap.asOnjObject().toMinifiedString())
    }

    fun newRunSync() {
        generateMapsSync()
        copyStaticMaps()
        read()
    }

    private fun copyStaticMaps() {
        Gdx.files.internal(staticRoadMapsPath).file().copyRecursively(
            Gdx.files.internal(roadMapsPath).file(),
            true
        )
        Gdx.files.internal(areaDefinitionsMapsPath).file().copyRecursively(
            Gdx.files.internal(areaMapsPath).file(),
            true
        )
    }

    fun resetAllSync() {
        newRunSync()
//        read()
    }

    fun changeToMapScreen() {
        FortyFive.changeToScreen(mapScreenPath)
    }

    fun lookupMapFile(mapName: String): FileHandle =
        lookupMapInDir(Gdx.files.internal(roadMapsPath), mapName) ?: lookupMapInDir(
            Gdx.files.internal(areaMapsPath),
            mapName
        ) ?: throw RuntimeException("unknown map $mapName")

    private fun lookupMapInDir(dir: FileHandle, mapName: String): FileHandle? {
        val file = dir
            .file()
            .walk(FileWalkDirection.TOP_DOWN)
            .find { it.nameWithoutExtension == mapName }
        return file?.let { FileHandle(file) }
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

//        val biome = onj.get<String>("biome")
//        val mapRestriction = MapRestriction.fromOnj(onj.get<OnjObject>("restrictions"))
//        val generator = SeededMapGenerator(onj.get<Long>("seed"), mapRestriction)
//        val map = generator.generate(name, biome)

        val data = NewMapGenerator.MapGeneratorData.fromOnj(onj)
        val map = NewMapGenerator().generate(name, data)

        GameDirector.assignEncounters(map)
        val path = "${outputDir.toPath()}/$name.onj"
        val file = File(path)
        if (!File(file.parent).exists()) File(file.parent).mkdirs()
        file.createNewFile()
        file.writeText(map.asOnjObject().toMinifiedString())
    }

    private val mapConfigSchema: OnjSchema by lazy {
        OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/map_config.onjschema").file())
    }

    data class MapImageData(
        val name: String,
        val resourceHandle: ResourceHandle,
        val width: Float,
        val height: Float,
        val type: String,
    )

}
