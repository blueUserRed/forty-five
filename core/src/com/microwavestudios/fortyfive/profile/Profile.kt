package com.microwavestudios.fortyfive.profile

import com.badlogic.gdx.Gdx
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.Deck
import com.microwavestudios.fortyfive.map.DetailMap
import com.microwavestudios.fortyfive.run.Run
import com.microwavestudios.fortyfive.run.RunGenerator
import com.microwavestudios.fortyfive.run.RunType
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.FortyFiveLogger
import com.microwavestudios.fortyfive.utils.unreachable
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
        mutableListOf(
            Deck("1", 100, mutableMapOf()),
            Deck("2", 101, mutableMapOf()),
            Deck("3", 102, mutableMapOf()),
            Deck("4", 103, mutableMapOf()),
            Deck("5", 104, mutableMapOf()),
        ),
        mutableListOf(),
        100,
        0,
        "aqua_balle",
        0,
        null,
        mutableMapOf()
    )

    val events: EventPipeline = EventPipeline()

    val profilePath: File = File("profiles/$name")

    private var dirty: Boolean = true

    private val dataFile: File = File(profilePath.absolutePath + "/profile_data.onj")

    private var _playerMoney: Int by DataDelegate(
        ProfileData::playerMoney,
        onSet = { value ->
            events.fire(MoneyChangedEvent(value))
        }
    )
    val playerMoney: Int
        get() = _playerMoney

    private var _cardCollection: MutableList<String> by DataDelegate(ProfileData::cardCollection)
    val cardCollection: List<String>
        get() = _cardCollection

    private var _collectionDecks: MutableList<Deck> by DataDelegate(ProfileData::collectionDecks)
    val collectionDecks: List<Deck>
        get() = _collectionDecks

    private var _currentMapName: String by DataDelegate(ProfileData::currentMap)
    val currentAreaMapName: String
        get() = _currentMapName

    private var runBoards: MutableMap<String, RunBoard> by DataDelegate(ProfileData::runBoards)

    private var currentCollectionDeckId: Int by DataDelegate(ProfileData::currentDeckId)

    var currentCollectionDeck: Deck
        get() =
            data.collectionDecks.find { it.id == currentCollectionDeckId }!!
        set(value) {
            currentCollectionDeckId = value.id
        }

    var currentRunDeck: Deck?
        get() = runSave?.let { run ->
            run.backpackDecks[run.currentDeckId]
        }
        set(value) {
            val runSave = runSave ?: return
            runSave.currentDeckId = value?.id ?: throw RuntimeException("can't set currentRunDeck to 'null'")
        }

    val backpack: List<String>?
        get() = runSave?.backpack

    val backpackDecks: List<Deck>?
        get() = runSave?.backpackDecks

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

    var healthInRun: Int?
        get() = runSave?.playerHealth
        set(value) {
            value ?: throw RuntimeException("cant set healthInRun to null")
            runSave?.playerHealth = value
        }

    val maxHealthInRun: Int?
        get() = runSave?.run?.maxPlayerHealth

    val areaMapSaver: MapSaver = object : MapSaver {
        override var currentNodeIndex: Int by this@Profile::currentNodeIndex
        override var lastNodeIndex: Int? by this@Profile::lastNodeIndex
        override val currentMapName: String by this@Profile::currentAreaMapName
        override val currentMap: DetailMap by this@Profile::currentAreaMap
    }

    init {
        data.collectionDecks.forEach { it.checkDeck(data.cardCollection) }
    }

    fun isSpecialRunCompleted(runName: String): Boolean = runName in data.completedSpecialRuns

    fun addCardToBackpack(card: String) {
        if (!isRunActive) throw RuntimeException("not in run")
        runSave!!.addCardToBackpack(card)
    }

    fun addCardToCollection(card: String) {
        _cardCollection.add(card)
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

    fun runBoardForArea(area: DetailMap): RunBoard {
        runBoards[area.name]?.let { return it }
        val runGenerator = RunGenerator()
        val runBoard = RunBoard(
            areaName = area.name,
            limitedRun = runGenerator.generateRun(area.majorDifficulty, area.biome, area.name, RunType.LIMITED),
            constructedRun = runGenerator.generateRun(area.majorDifficulty, area.biome, area.name, RunType.CONSTRUCTED),
            progressRun = null,
            specialRuns = listOf()
        )
        runBoards[area.name] = runBoard
        dirty()
        return runBoard
    }

    fun startRun(run: Run) {
        requireNotNull(runSave) { "cant start new run when old run wasn't completed yet" }
        if (run.type == RunType.SPECIAL || run.type == RunType.PROGRESS) {
            require(run.name !in data.completedSpecialRuns) { "cant start completed special/progress run again" }
        }

        val cardsToTakeAlong = if (run.type == RunType.LIMITED) {
            listOf()
        } else {
            currentCollectionDeck.cards
        }
        runSave = RunSave.newRun(this, run, cardsToTakeAlong)
    }

    fun loseRun() {
        val runSave = runSave ?: throw RuntimeException("cant lose run if no run is active")
        endRun(runSave)
    }

    fun winRun() {
        val runSave = runSave
        requireNotNull(runSave) { "cant win run if no run is active" }
        val run = runSave.run
        val area = run.fromArea

        val runGenerator = RunGenerator()
        val runBoard = runBoards[area]
        requireNotNull(runBoard) { "won run that doesn't exist in runBoard" }
        val newRunBoard = when (run.type) {
            RunType.LIMITED -> runBoard.copy(
                limitedRun = runGenerator.generateRun(run.difficulty, run.biome, area, RunType.LIMITED)
            )
            RunType.CONSTRUCTED -> runBoard.copy(
                constructedRun = runGenerator.generateRun(run.difficulty, run.biome, area, RunType.CONSTRUCTED)
            )
            RunType.SPECIAL -> {
                data.completedSpecialRuns.add(run.name)
                runBoard.copy(specialRuns = runBoard.specialRuns.filter { it.name != run.name })
            }
            RunType.PROGRESS -> {
                data.completedSpecialRuns.add(run.name)
                runBoard.copy(progressRun = null)
            }
        }
        runBoards[area] = newRunBoard

        val cardsToExtraxt = extractableCards()
        _cardCollection.addAll(cardsToExtraxt)
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

    fun payMoney(amount: Int) {
        _playerMoney -= amount
    }

    fun getCardForRun(card: String) {
        val runSave = runSave ?: throw RuntimeException("not in a run")
        runSave.addCardToBackpack(card)
    }

    fun checkDecks() {
        collectionDecks.forEach { it.checkDeck(cardCollection) }
        runSave?.checkDecks()
    }

    fun extractableCards(): List<String> {
        val runSave = runSave
            ?: throw RuntimeException("Profile.extractableCards() can only be called when a run is active")
        if (runSave.run.type == RunType.LIMITED) return currentRunDeck!!.cards
        val cardsToExtract = currentRunDeck!!.cards.toMutableList()
        runSave.cardsTakenAlong.forEach { card ->
            cardsToExtract.remove(card)
        }
        return cardsToExtract
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
        dataFileSchema.assertMatches(onj)
        onj as OnjObject
        data = ProfileData.fromOnj(onj)
        checkDecks()
    }

    fun write() {
        checkDecks()
        runSave?.write()
        if (!dirty && !data.collectionDecks.any { it.deckDirty }) return
        dirty = false
        data.collectionDecks.forEach { it.resetDeckDirty() }
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

    class Preview(
        val name: String,
        val dataFile: File,
        val loadFailure: LoadFailure?,
        val exists: Boolean
    ) {

        val loadedSuccessfully: Boolean
            get() = loadFailure == null

        private var data: ProfileData? = null

        var runPreview: RunSave.Preview? = null
            private set

        val collection: List<String>?
            get() = data?.cardCollection

        val currentArea: String?
            get() = data?.currentMap

        val money: Int?
            get() = data?.playerMoney

        fun read() {
            if (!loadedSuccessfully || !exists) return
            val onj = OnjParser.parseFile(dataFile)
            dataFileSchema.assertMatches(onj)
            onj as OnjObject
            data = ProfileData.fromOnj(onj)
        }

        companion object {

            fun loadPreview(name: String): Preview {
                return try {
                    tryLoadPreview(name)
                } catch (e: ProfileLoadException) {
                    Preview(name, File("profiles/$name/profile_data.onj"), e.failure, true)
                }
            }

            private fun tryLoadPreview(name: String): Preview {
                val folder = File("profiles/$name")
                if (!folder.exists()) {
                    return Preview(name, File("profiles/$name/profile_data.onj"), null, false)
                }
                var markedCorrupted = false
                val version: Int = try {
                    val versionFile = File("profiles/$name/version.txt")
                    val text = versionFile.readText()
                    val lines = text.lines()
                    if (lines.getOrNull(1)?.contains("corrupted") ?: false) {
                        markedCorrupted = true
                    }
                    Integer.parseInt(lines.first())
                } catch (e: Exception) {
                    FortyFive.logger.warn(logTag, "Error loading version file for profile $name")
                    FortyFive.logger.stackTrace(e)
                    throw ProfileLoadException(LoadFailure.CORRUPTED_FILES)
                }
                if (markedCorrupted) {
                    FortyFive.logger.warn(logTag, "Profile $name marked corrupted")
                    throw ProfileLoadException(LoadFailure.MARKED_CORRUPTED)
                }
                if (version > profileVersion) {
                    FortyFive.logger.warn(
                        logTag,
                        "Version mismatch in profile $name: profile: $version, game: $profileVersion"
                    )
                    throw ProfileLoadException(LoadFailure.VERSION_TOO_NEW)
                }
                if (version < profileVersion) {
                    FortyFive.logger.warn(
                        logTag,
                        "Version mismatch in profile $name: profile: $version, game: $profileVersion"
                    )
                    throw ProfileLoadException(LoadFailure.VERSION_TOO_OLD)
                }
                try {
                    val preview = Preview(name, File("profiles/$name/profile_data.onj"), null, true)
                    preview.runPreview = RunSave.loadPreview(preview)
                    preview.read()
                    return preview
                } catch (e: Exception) {
                    FortyFive.logger.warn(logTag, "Failure loading profile $name")
                    FortyFive.logger.stackTrace(e)
                    throw ProfileLoadException(LoadFailure.CORRUPTED_FILES)
                }
            }
        }
    }

    data class ProfileData(
        var cardCollection: MutableList<String>,
        var collectionDecks: MutableList<Deck>,
        var completedSpecialRuns: MutableList<String>,
        var currentDeckId: Int,
        var playerMoney: Int,
        var currentMap: String,
        var currentNode: Int,
        var lastNode: Int?,
        var runBoards: MutableMap<String, RunBoard>
    ) {

        fun asOnj(): OnjObject = buildOnjObject {
            "cardCollection" with cardCollection
            "collectionDecks" with collectionDecks.map { it.asOnjObject() }
            "completedSpecialRuns" with completedSpecialRuns
            "currentDeckId" with currentDeckId
            "playerMoney" with playerMoney
            "currentMap" with currentMap
            "currentNode" with currentNode
            "lastNode" with lastNode
            "runBoards" with runBoards.map { it.value.asOnj() }
        }

        companion object {

            fun fromOnj(onj: OnjObject): ProfileData = ProfileData(
                onj.get<OnjArray>("cardCollection").value.map { it.value as String }.toMutableList(),
                onj.get<OnjArray>("collectionDecks").value.map { Deck.getFromOnj(it as OnjObject) }.toMutableList(),
                onj.get<OnjArray>("completedSpecialRuns").value.map { it.value as String }.toMutableList(),
                onj.get<Long>("currentDeckId").toInt(),
                onj.get<Long>("playerMoney").toInt(),
                onj.get<String>("currentMap"),
                onj.get<Long>("currentNode").toInt(),
                onj.get<Long?>("lastNode")?.toInt(),
                onj.get<OnjArray>("runBoards").value.associate { boardOnj ->
                    boardOnj as OnjObject
                    val board = RunBoard.fromOnj(boardOnj)
                    board.areaName to board
                }.toMutableMap()
            )
        }
    }

    private inner class DataDelegate<T>(
        val property: KMutableProperty<T>,
        val onGet: ((T) -> Unit)? = null,
        val onSet: ((T) -> Unit)? = null
    ) {

        operator fun getValue(thisRef: Any?, p: KProperty<*>): T {
            return property.getter.call(data).also { onGet?.invoke(it) }
        }

        operator fun setValue(thisRef: Any?, p: KProperty<*>, value: T) {
            val old = getValue(thisRef, p)
            if (old === value) return
            onSet?.invoke(value)
            property.setter.call(data, value)
            dirty()
        }
    }

    data class HealthChangedEvent(val newHealth: Int)
    data class MoneyChangedEvent(val newMoney: Int)

    companion object {

        const val profileVersion: Int = 0

        const val logTag: String = "Profile"

        val dataFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/profile_data.onjschema").file())
        }

        fun loadPreview(name: String): Preview = Preview.loadPreview(name)

        fun createNewProfile(profileName: String): Profile {
            val profile = Profile(profileName, null)
            profile.profilePath.deleteRecursively()
            profile.profilePath.toPath().createDirectories()
            Gdx.files.internal("maps/area_definitions")
                .file()
                .copyRecursively(profile.profilePath, true)
            profile.readFromDisk()
            profile.loadAreaMap(profile._currentMapName)
            val versionFile = File(profile.profilePath.absolutePath + "/version.txt")
            versionFile.createNewFile()
            versionFile.writeText(profileVersion.toString())
            return profile
        }

        fun loadProfile(profileName: String): Profile? {
            try {
                val profile = Profile(profileName, null)
                profile.runSave = RunSave.load(profile)
                if (!profile.profilePath.exists()) return createNewProfile(profileName)
                profile.readFromDisk()
                profile.loadAreaMap(profile._currentMapName)
                return profile
            } catch (e: Exception) {
                markVersionFileCorrupted(profileName)
                return null
            }
        }

        private fun markVersionFileCorrupted(name: String) {
            try {
                val versionFile = File("profiles/$name/version.txt")
                val text = versionFile.readText()
                val lines = text.lines()
                val newText = when {
                    lines.isEmpty() -> "$profileVersion\ncorrupted"
                    lines.size == 1 -> "${lines.first()}\ncorrupted"
                    else -> {
                        val mut = lines.toMutableList()
                        mut[1] = "corrupted"
                        mut.joinToString(separator = "\n")
                    }
                }
                versionFile.writeText(newText)
            } catch (_: Exception) {}
        }
    }

    enum class LoadFailure {
        VERSION_TOO_OLD, VERSION_TOO_NEW, CORRUPTED_FILES, MARKED_CORRUPTED
    }

    private class ProfileLoadException(val failure: LoadFailure) : Exception()

    data class RunBoard(
        val areaName: String,
        val limitedRun: Run?,
        val constructedRun: Run?,
        val progressRun: Run?,
        val specialRuns: List<Run>,
    ) {

        fun asOnj(): OnjObject = buildOnjObject {
            "areaName" with areaName
            "limitedRun" with limitedRun?.asOnj()
            "constructedRun" with constructedRun?.asOnj()
            "progressRun" with progressRun?.asOnj()
            "specialRuns" with specialRuns.map { it.asOnj() }
        }

        companion object {
            fun fromOnj(onj: OnjObject) = RunBoard(
                onj.get<String>("areaName"),
                onj.get<OnjObject?>("limitedRun")?.let { Run.fromOnj(it) },
                onj.get<OnjObject?>("constructedRun")?.let { Run.fromOnj(it) },
                onj.get<OnjObject?>("progressRun")?.let { Run.fromOnj(it) },
                onj.get<OnjArray>("specialRuns").value.map {
                    it as OnjObject
                    Run.fromOnj(it)
                }
            )
        }
    }

}
