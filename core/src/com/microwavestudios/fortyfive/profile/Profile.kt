package com.microwavestudios.fortyfive.profile

import com.badlogic.gdx.Gdx
import com.microwavestudios.fortyfive.map.DetailMap
import com.microwavestudios.fortyfive.run.Run
import com.microwavestudios.fortyfive.run.RunGenerator
import com.microwavestudios.fortyfive.run.RunType
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

class Profile private constructor(val name: String, private var runSave: RunSave?) {

    private var data: ProfileData = ProfileData(
        mutableListOf("bullet", "bigBullet"),
        0,
        "aqua_balle",
        0,
        null,
        mutableMapOf()
    )

    val profilePath: File = File("profiles/$name")

    private var dirty: Boolean = true

    private val dataFile: File = File(profilePath.absolutePath + "/profile_data.onj")

    private var _playerMoney: Int by DataDelegate(ProfileData::playerMoney)
    val playerMoney: Int
        get() = _playerMoney

    private var _cardCollection: MutableList<String> by DataDelegate(ProfileData::cardCollection)
    val cardCollection: List<String>
        get() = _cardCollection

    private var _currentMapName: String by DataDelegate(ProfileData::currentMap)
    val currentAreaMapName: String
        get() = _currentMapName

    private var runBoards: MutableMap<String, Pair<Run, Run>> by DataDelegate(ProfileData::runBoards)

    var currentNodeIndex: Int by DataDelegate(ProfileData::currentNode)

    var lastNodeIndex: Int? by DataDelegate(ProfileData::lastNode)

    val currentMapSaver: MapSaver
        get() = runSave?.mapSaver ?: areaMapSaver

    lateinit var currentAreaMap: DetailMap
        private set

    private var currentMapFile: File? = null

    val activeRun: Run?
        get() = runSave?.run

    val isRunActive: Boolean
        get() = runSave != null

    val areaMapSaver: MapSaver = object : MapSaver {
        override var currentNodeIndex: Int by this@Profile::currentNodeIndex
        override var lastNodeIndex: Int? by this@Profile::lastNodeIndex
        override val currentMapName: String by this@Profile::currentAreaMapName
        override val currentMap: DetailMap by this@Profile::currentAreaMap
    }

    fun changeToMap(map: String, fromEnd: Boolean = false) {
        if (map == _currentMapName) return
        writeMaps()
        loadAreaMap(map)
        lastNodeIndex = null
        currentNodeIndex = if (fromEnd) {
            currentAreaMap.endNode.index
        } else {
            currentAreaMap.startNode.index
        }
    }

    fun runBoardForArea(area: DetailMap): Pair<Run, Run> {
        runBoards[area.name]?.let { return it }
        val runGenerator = RunGenerator()
        val runs = Pair(
            runGenerator.generateRun(area.majorDifficulty, area.biome, area.name, RunType.CONSTRUCTED),
            runGenerator.generateRun(area.majorDifficulty, area.biome, area.name, RunType.LIMITED),
        )
        runBoards[area.name] = runs
        dirty()
        return runs
    }

    fun startRun(run: Run) {
        if (runSave != null) throw RuntimeException("cant start new run when old run wasn't completed yet")
        runSave = RunSave.newRun(this, run)
    }

    fun loseRun() {
        val runSave = runSave ?: throw RuntimeException("cant lose run if no run is active")
        endRun(runSave)
    }

    fun winRun() {
        val runSave = runSave ?: throw RuntimeException("cant win run if no run is active")
        val run = runSave.run
        val area = run.fromArea
        val runGenerator = RunGenerator()
        val runBoard = runBoards[area] ?: throw RuntimeException("won run that doesn't exist in runBoard")
        val newRunBoard = when (run.type) {
            RunType.LIMITED -> Pair(
                runBoard.first,
                runGenerator.generateRun(run.difficulty, run.biome, area, RunType.LIMITED),
            )
            RunType.CONSTRUCTED -> Pair(
                runGenerator.generateRun(run.difficulty, run.biome, area, RunType.CONSTRUCTED),
                runBoard.second
            )
            RunType.PROGRESS -> runBoard
        }
        runBoards[area] = newRunBoard
        endRun(runSave)
    }

    private fun endRun(runSave: RunSave) {
        runSave.runMapFile.delete()
        runSave.runDataFile.delete()
        this.runSave = null
        dirty()
    }

    fun earnMoney(amount: Int) {
        _playerMoney += amount
    }

    private fun loadAreaMap(map: String) {
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
        runSave?.readFromDisc()
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
        runSave?.write()
        if (!dirty) return
        if (!dataFile.exists()) {
            dataFile.createNewFile()
            dataFile.writeText(data.asOnj().toString())
            return
        }
        dataFile.writeText(data.asOnj().toString())
    }

    fun writeMaps() {
        runSave?.writeRunMap()
        val currentFile = currentMapFile ?: return
        currentFile.writeText(currentAreaMap.asOnjObject().toMinifiedString())
    }

    data class ProfileData(
        var cardCollection: MutableList<String>,
        var playerMoney: Int,
        var currentMap: String,
        var currentNode: Int,
        var lastNode: Int?,
        var runBoards: MutableMap<String, Pair<Run, Run>>
    ) {

        fun asOnj(): OnjObject = buildOnjObject {
            "cardCollection" with cardCollection
            "playerMoney" with playerMoney
            "currentMap" with currentMap
            "currentNode" with currentNode
            "lastNode" with lastNode
            "runBoards" with runBoards.map { (area, runs) ->
                buildOnjObject {
                    "forArea" with area
                    "runs" with arrayOf(runs.first.asOnj(), runs.second.asOnj())
                }
            }
        }

        companion object {

            fun fromOnj(onj: OnjObject): ProfileData = ProfileData(
                onj.get<OnjArray>("cardCollection").value.map { it.value as String }.toMutableList(),
                onj.get<Long>("playerMoney").toInt(),
                onj.get<String>("currentMap"),
                onj.get<Long>("currentNode").toInt(),
                onj.get<Long?>("lastNode")?.toInt(),
                onj.get<OnjArray>("runBoards").value.associate { board ->
                    board as OnjObject
                    val area = board.get<String>("forArea")
                    val runs = board.get<OnjArray>("runs")
                    area to (Run.fromOnj(runs.get<OnjObject>(0)) to Run.fromOnj(runs.get<OnjObject>(1)))
                }.toMutableMap()
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
            val profile = Profile(profileName, null)
            profile.profilePath.deleteRecursively()
            profile.profilePath.toPath().createDirectories()
            Gdx.files.internal("maps/area_definitions")
                .file()
                .copyRecursively(profile.profilePath, true)
            profile.readFromDisk()
            profile.loadAreaMap(profile._currentMapName)
            return profile
        }

        fun loadProfile(profileName: String): Profile {
            val profile = Profile(profileName, null)
            profile.runSave = RunSave.load(profile)
            if (!profile.profilePath.exists()) return createNewProfile(profileName)
            profile.readFromDisk()
            profile.loadAreaMap(profile._currentMapName)
            return profile
        }
    }
}
