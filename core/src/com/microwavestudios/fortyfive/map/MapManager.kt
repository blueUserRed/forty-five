package com.microwavestudios.fortyfive.map

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.files.FileHandle
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.GameDirector
import com.microwavestudios.fortyfive.map.generation.BaseMapGenerator
import com.microwavestudios.fortyfive.resources.ResourceHandle
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import java.io.File

object MapManager {

    const val logTag: String = "MapManager"

    lateinit var roadMapsPath: String
        private set
    lateinit var areaMapsPath: String
        private set
    lateinit var areaDefinitionsMapsPath: String
        private set
    lateinit var staticRoadMapsPath: String
        private set

    lateinit var currentDetailMap: DetailMap
        private set

    lateinit var currentMapFile: FileHandle
        private set

    lateinit var mapImages: List<MapImageData>
        private set

//    var currentMapNode: MapNode
//        get() = currentDetailMap.uniqueNodes.find { it.index == SaveState.currentNode } ?: run {
//            FortyFive.logger.warn(logTag, "Player was on node ${SaveState.currentNode} in map $currentDetailMap, which doesn't exist. Reset player to node 0.")
//            SaveState.currentNode = 0
//            SaveState.lastNode = null
//            currentDetailMap.uniqueNodes[0]
//        }
//        set(value) {
//            SaveState.currentNode = value.index
//        }
//
//    var lastMapNode: MapNode?
//        get() = if (SaveState.lastNode != null) {
//            currentDetailMap.uniqueNodes.find { it.index == SaveState.lastNode }
//        } else {
//            null
//        }
//        set(value) {
//            SaveState.lastNode = value?.index
//        }

    lateinit var displayNames: Map<String, String>
        private set

    fun init() {
//        val onj = ConfigFileManager.getConfigFile("mapConfig")
//        mapImages = onj
//            .get<OnjArray>("mapImages")
//            .value
//            .map { it as OnjObject }
//            .map {
//                MapImageData(
//                    it.get<String>("name"),
//                    it.get<String>("image"),
//                    it.get<Double>("width").toFloat(),
//                    it.get<Double>("height").toFloat(),
//                    it.get<String>("type"),
//                )
//            }
//        val paths = onj.get<OnjObject>("paths")
//        areaMapsPath = paths.get<String>("areas")
//        roadMapsPath = paths.get<String>("roads")
//        areaDefinitionsMapsPath = paths.get<String>("areaDefinitions")
//        staticRoadMapsPath = paths.get<String>("staticRoadDefinitions")
//        val displayNames = onj
//            .get<OnjArray>("displayNames")
//            .value
//            .map { it as OnjObject }
//            .associate { it.get<String>("name") to it.get<String>("display") }
//            .toMutableMap()
//        val dialogs = ConfigFileManager.getConfigFile("dialogConfig")
//        dialogs
//            .get<OnjArray>("dialogs")
//            .value
//            .map { it as OnjObject }
//            .map { it.get<String>("name") to it.get<String>("eventText") }
//            .forEach { displayNames[it.first] = it.second }
//        this.displayNames = displayNames
    }

    fun read() {
//        val map = lookupMapFile(SaveState.currentMap)
//        currentMapFile = map
//        currentDetailMap = readDetailMap(map)
    }

    /**
     * @see DetailMap.invalidateCachedAssets
     */
    fun invalidateCachedAssets() {
        currentDetailMap.invalidateCachedAssets()
    }

    fun displayName(internalName: String) = displayNames[internalName] ?: run {
        FortyFive.logger.warn(logTag, "no display name for $internalName")
        internalName
    }

    fun changeToMap(newMap: String, fromArea: String = currentDetailMap.name) {
//        write()
//        val map = lookupMapFile(newMap)
//        currentMapFile = map
//        currentDetailMap = readDetailMap(map)
//        SaveState.currentMap = newMap
//        SaveState.currentNode = currentDetailMap
//            .uniqueNodes
//            .filter { it.event is EnterMapMapEvent }
//            .find { (it.event as EnterMapMapEvent).targetMap == fromArea }
//            ?.index
//            ?: 0
//        SaveState.lastNode = null
//        FortyFive.logger.debug(logTag, "changing from $fromArea to $newMap; currentNode = $currentMapNode")
    }

//    private fun readDetailMap(map: FileHandle): DetailMap = try {
//        DetailMap.readFromFile(map.file())
//    } catch (e: DetailMap.InvalidMapFileException) {
//        FortyFive.logger.warn(logTag, "Invalid map file found, reloading all maps")
//        generateMapsSync()
//        copyStaticMaps()
//        SaveState.currentNode = 0
//        SaveState.lastNode = null
//        DetailMap.readFromFile(map.file())
//    }

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
        val onj = ConfigFileManager.getConfigFile("mapConfig")
        val generatorConfig = onj.get<OnjObject>("generatorConfig")
        val outputDir = Gdx.files.local(generatorConfig.get<String>("outputDirectory")).file()
        generatorConfig
            .get<OnjArray>("maps")
            .value
            .forEach { map ->
                generateMap(map as OnjNamedObject, outputDir)
            }
    }

    private fun generateMap(onj: OnjNamedObject, outputDir: File) {
//        val name = onj.get<String>("name")
//
//        val generator = BaseMapGenerator.fromOnj(onj)
//        val map = generator.generate(name)
//
//        GameDirector.assignEncounters(map)
//        val path = "${outputDir.toPath()}/$name.onj"
//        val file = File(path)
//        if (!File(file.parent).exists()) File(file.parent).mkdirs()
//        file.createNewFile()
//        file.writeText(map.asOnjObject().toMinifiedString())
    }

    data class MapImageData(
        val name: String,
        val resourceHandle: ResourceHandle,
        val width: Float,
        val height: Float,
        val type: String, // TODO: why is this not an enum
    )

}
