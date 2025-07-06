package com.microwavestudios.fortyfive.profile

import com.badlogic.gdx.Gdx
import com.microwavestudios.fortyfive.map.DetailMap
import onj.builder.buildOnjObject
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject
import java.io.File
import kotlin.io.path.createDirectories
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty

class Profile private constructor(val name: String) {

    private var data: ProfileData = ProfileData(
        mutableListOf("bullet", "bigBullet"),
        0,
        "aqua_balle",
        0,
        null,
        null
    )

    val profilePath: File = File("profiles/$name")

    private var dirty: Boolean = true

    private val dataFile: File = File(profilePath.absolutePath + "/data.onj")

    var playerMoney: Int by DataDelegate(ProfileData::playerMoney)

    private var _cardCollection: MutableList<String> by DataDelegate(ProfileData::cardCollection)
    val cardCollection: List<String>
        get() = _cardCollection

    private var _currentMapName: String by DataDelegate(ProfileData::currentMap)
    val currentAreaMapName: String
        get() = _currentMapName

    var currentNodeIndex: Int by DataDelegate(ProfileData::currentNode)

    var lastNodeIndex: Int? by DataDelegate(ProfileData::lastNode)

    var runSave: RunSave? by DataDelegate(ProfileData::runSave)

    val currentMapSaver: MapSaver
        get() = runSave?.mapSaver ?: areaMapSaver

    lateinit var currentAreaMap: DetailMap
        private set

    private var currentMapFile: File? = null

    val areaMapSaver: MapSaver = object : MapSaver {
        override var currentNodeIndex: Int by this@Profile::currentNodeIndex
        override var lastNodeIndex: Int? by this@Profile::lastNodeIndex
        override val currentMapName: String by this@Profile::currentAreaMapName
        override val currentMap: DetailMap by this@Profile::currentAreaMap
    }

    fun changeToMap(map: String, fromEnd: Boolean = false) {
        if (map == _currentMapName) return
        writeMap()
        loadMap(map)
        lastNodeIndex = null
        currentNodeIndex = if (fromEnd) {
            currentAreaMap.endNode.index
        } else {
            currentAreaMap.startNode.index
        }
    }

    private fun loadMap(map: String) {
        val newMapFile = lookupAreaFile(map) ?: throw RuntimeException("no file for area: $map")
        this.currentMapFile = newMapFile
        currentAreaMap = DetailMap.readFromFile(newMapFile)
        _currentMapName = map
    }

    private fun lookupAreaFile(area: String): File? {
        val areaFile = File(profilePath.absolutePath + "/$area.onj")
        return if (areaFile.exists()) areaFile else null
    }

    fun dirty() {
        dirty = true
    }

    fun readFromDisk() {
        dirty = false
        if (!dataFile.exists()) {
            dataFile.createNewFile()
            dataFile.writeText(data.asOnj().toString())
            return
        }
        val onj = OnjParser.parseFile(dataFile)
        dataFileSchema.check(onj)
        onj as OnjObject
        data = ProfileData.fromOnj(onj)
    }

    fun write() {
        if (!dirty) return
        if (!dataFile.exists()) {
            dataFile.createNewFile()
            dataFile.writeText(data.asOnj().toString())
            return
        }
        dataFile.writeText(data.asOnj().toString())
    }

    fun writeMap() {
        val currentFile = currentMapFile ?: return
        currentFile.writeText(currentAreaMap.asOnjObject().toMinifiedString())
    }

    data class ProfileData(
        var cardCollection: MutableList<String>,
        var playerMoney: Int,
        var currentMap: String,
        var currentNode: Int,
        var lastNode: Int?,
        var runSave: RunSave?,
    ) {

        fun asOnj(): OnjObject = buildOnjObject {
            "cardCollection" with cardCollection
            "playerMoney" with playerMoney
            "currentMap" with currentMap
            "currentNode" with currentNode
            "lastNode" with lastNode
            "runSave" with runSave?.asOnj()
        }

        companion object {
            fun fromOnj(onj: OnjObject): ProfileData = ProfileData(
                onj.get<OnjArray>("cardCollection").value.map { it.value as String }.toMutableList(),
                onj.get<Long>("playerMoney").toInt(),
                onj.get<String>("currentMap"),
                onj.get<Long>("currentNode").toInt(),
                onj.get<Long?>("lastNode")?.toInt(),
                null
            )
        }
    }

    private inner class DataDelegate<T>(val property: KMutableProperty<T>) {

        operator fun getValue(thisRef: Any?, p: KProperty<*>): T = property.getter.call(data)

        operator fun setValue(thisRef: Any?, p: KProperty<*>, value: T) {
            val old = getValue(thisRef, p)
            if (old === value) return
            property.setter.call(data, value)
            dirty()
        }
    }

    companion object {

        val dataFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/profile_data.onjschema").file())
        }

        fun createNewProfile(profileName: String): Profile {
            val profile = Profile(profileName)
            profile.profilePath.deleteRecursively()
            profile.profilePath.toPath().createDirectories()
            Gdx.files.internal("maps/area_definitions")
                .file()
                .copyRecursively(profile.profilePath, true)
            profile.readFromDisk()
            profile.loadMap(profile._currentMapName)
            return profile
        }

        fun loadProfile(profileName: String): Profile {
            val profile = Profile(profileName)
            if (!profile.profilePath.exists()) return createNewProfile(profileName)
            profile.readFromDisk()
            profile.loadMap(profile._currentMapName)
            return profile
        }
    }
}
