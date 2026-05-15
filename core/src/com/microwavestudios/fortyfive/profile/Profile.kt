package com.microwavestudios.fortyfive.profile

import com.badlogic.gdx.Gdx
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.Deck
import com.microwavestudios.fortyfive.game.Talisman
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.map.DetailMap
import com.microwavestudios.fortyfive.profile.Profile.Companion.dataFileSchema
import com.microwavestudios.fortyfive.profile.Profile.Companion.limitedTakeAlong
import com.microwavestudios.fortyfive.profile.Profile.ProfileData
import com.microwavestudios.fortyfive.profile.Profile.RunBoard
import com.microwavestudios.fortyfive.run.Run
import com.microwavestudios.fortyfive.run.RunGenerator
import com.microwavestudios.fortyfive.run.RunType
import com.microwavestudios.fortyfive.utils.*
import onj.builder.buildOnjObject
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject
import java.io.File
import kotlin.collections.set
import kotlin.getValue
import kotlin.io.path.createDirectories
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty
import kotlin.setValue

interface IProfile {

    val name: String
    val playerMoney: Int
    val cardCollection: List<CardType>
    val collectionDecks: List<Deck>
    val currentAreaMapName: String
    val wonRuns: Int
    var currentCollectionDeck: Deck
    var currentRunDeck: Deck?
    val backpack: List<CardType>?
    val backpackDecks: List<Deck>?
    val encountersStartedInRun: Int?
    val talismans: List<Talisman>
    var currentNodeIndex: Int
    var lastNodeIndex: Int?
    val currentMapSaver: MapSaver
    val currentAreaMap: DetailMap
    val activeRun: Run?
    val usedSteps: Int?
    val isRunActive: Boolean
    var healthInRun: Int?
    val maxHealthInRun: Int?
    val areaMapSaver: MapSaver

    fun isSpecialRunCompleted(runName: String): Boolean
    fun addCardToBackpack(card: CardType)
    fun swapCardInBackpack(old: CardType, new: CardType)
    fun addCardToCollection(card: CardType)
    fun swapCardInCollection(old: CardType, new: CardType)
    fun changeToMap(map: String, fromEnd: Boolean = false)
    fun runBoardForArea(area: DetailMap): RunBoard
    fun addRunToRunBoard(areaName: String, run: Run)
    fun startRun(run: Run)
    fun encounterStarted()
    fun loseRun()
    fun winRun()
    fun stepTaken()
    fun earnMoney(amount: Int)
    fun payMoney(amount: Int)
    fun getCardForRun(card: CardType)
    fun checkDecks()
    fun extractableCards(): List<CardType>
    fun readFromDisk()
    fun dirty()
    fun write()

}

class Profile private constructor(
    override val name: String,
    private var runSave: RunSave?
) : IProfile {

    private var data: ProfileData = ProfileData(
        mutableListOf(
            CardType(null, "bullet", null),
            CardType(null, "bigBullet", null)
        ),
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
        0,
        mutableMapOf()
    )

    val profilePath: File = File("profiles/$name")

    private var dirty: Boolean = true

    private val dataFile: File = File(profilePath.absolutePath + "/profile_data.onj")

    private var _playerMoney: Int by DataDelegate(
        ProfileData::playerMoney,
        onSet = { value ->
            FortyFive.currentScreen?.events?.fire(MoneyChangedEvent(value))
        }
    )
    override val playerMoney: Int
        get() = _playerMoney

    private var _cardCollection: MutableList<CardType> by DataDelegate(ProfileData::cardCollection)
    override val cardCollection: List<CardType>
        get() = _cardCollection

    private var _collectionDecks: MutableList<Deck> by DataDelegate(ProfileData::collectionDecks)
    override val collectionDecks: List<Deck>
        get() = _collectionDecks

    private var _currentMapName: String by DataDelegate(ProfileData::currentMap)
    override val currentAreaMapName: String
        get() = _currentMapName

    private var _wonRuns: Int by DataDelegate(ProfileData::wonRuns)
    override val wonRuns: Int
        get() = _wonRuns

    private var runBoards: MutableMap<String, RunBoard> by DataDelegate(ProfileData::runBoards)

    private var currentCollectionDeckId: Int by DataDelegate(ProfileData::currentDeckId)

    override var currentCollectionDeck: Deck
        get() =
            data.collectionDecks.find { it.id == currentCollectionDeckId }!!
        set(value) {
            currentCollectionDeckId = value.id
        }

    override var currentRunDeck: Deck?
        get() = runSave?.let { run ->
            run.backpackDecks[run.currentDeckId]
        }
        set(value) {
            val runSave = runSave ?: return
            runSave.currentDeckId = value?.id ?: throw RuntimeException("can't set currentRunDeck to 'null'")
        }

    override val backpack: List<CardType>?
        get() = runSave?.backpack

    override val backpackDecks: List<Deck>?
        get() = runSave?.backpackDecks

    override val encountersStartedInRun: Int?
        get() = runSave?.encountersStarted

    override val talismans: List<Talisman>
        get() = runSave?.talismans ?: listOf()

    override var currentNodeIndex: Int by DataDelegate(ProfileData::currentNode)

    override val usedSteps: Int?
        get() = runSave?.usedSteps

    override var lastNodeIndex: Int? by DataDelegate(ProfileData::lastNode)

    override val currentMapSaver: MapSaver
        get() = runSave?.mapSaver ?: areaMapSaver

    override lateinit var currentAreaMap: DetailMap
        private set

    private var currentMapFile: File? = null

    override val activeRun: Run?
        get() = runSave?.run

    override val isRunActive: Boolean
        get() = runSave != null

    override var healthInRun: Int?
        get() = runSave?.playerHealth
        set(value) {
            value ?: throw RuntimeException("cant set healthInRun to null")
            runSave?.playerHealth = value
        }

    override val maxHealthInRun: Int?
        get() = runSave?.run?.maxPlayerHealth

    override val areaMapSaver: MapSaver = object : MapSaver {
        override var currentNodeIndex: Int by this@Profile::currentNodeIndex
        override var lastNodeIndex: Int? by this@Profile::lastNodeIndex
        override val currentMapName: String by this@Profile::currentAreaMapName
        override val currentMap: DetailMap by this@Profile::currentAreaMap
    }

    init {
        data.collectionDecks.forEach { it.checkDeck(data.cardCollection) }
    }

    override fun isSpecialRunCompleted(runName: String): Boolean = runName in data.completedSpecialRuns

    override fun addCardToBackpack(card: CardType) {
        require(isRunActive) { "not in run" }
        runSave!!.addCardToBackpack(card)
        checkDecks()
    }

    override fun swapCardInBackpack(old: CardType, new: CardType) {
        require(isRunActive) { "not in run" }
        runSave!!.swapCardInBackpack(old, new)
    }

    override fun addCardToCollection(card: CardType) {
        _cardCollection.add(card)
        checkDecks()
        dirty()
    }

    override fun swapCardInCollection(old: CardType, new: CardType) {
        val result = _cardCollection.remove(old)
        require(result) { "card $old not in collection" }
        _cardCollection.add(new)
        checkDecks()
        dirty()
    }

    override fun stepTaken() {
        val runSave = runSave ?: return
        runSave.usedSteps++
    }

    override fun changeToMap(map: String, fromEnd: Boolean) {
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

    override fun runBoardForArea(area: DetailMap): RunBoard {
        var runBoard = runBoards[area.name] ?: RunBoard(
            areaName = area.name,
            null, null, null,
            listOf()
        )
        val runGenerator by lazy { RunGenerator() }
        var somethingChanged = false
        if (runBoard.limitedRun == null) {
            runBoard = runBoard.copy(
                limitedRun = runGenerator.generateRun(area.majorDifficulty, area.biome, area.name, RunType.LIMITED),
            )
            somethingChanged = true
        }
        if (runBoard.constructedRun == null) {
            runBoard = runBoard.copy(
                constructedRun = runGenerator.generateRun(area.majorDifficulty, area.biome, area.name, RunType.CONSTRUCTED),
            )
            somethingChanged = true
        }
        if (somethingChanged) {
            runBoards[area.name] = runBoard
            dirty()
        }
        return runBoard
    }

    override fun addRunToRunBoard(areaName: String, run: Run) {
        requireNot(run.type == RunType.LIMITED || run.type == RunType.CONSTRUCTED) {
            "limited or constructed runs cant be manually added to the run board"
        }
        requireNot(run.type == RunType.SPECIAL_NOT_IN_BOARD) {
            "cant add run with type 'SPECIAL_NOT_IN_BOARD' to run board"
        }
        var runBoard = runBoards[areaName] ?: run {
            val board = RunBoard(areaName, null, null, null, listOf())
            runBoards[areaName] = board
            board
        }
        when (run.type) {
            RunType.PROGRESS -> {
                requireNull(runBoard.progressRun) { "cant add a progress run when one already exists" }
                runBoard = runBoard.copy(progressRun = run)
            }
            RunType.SPECIAL -> runBoard = runBoard.copy(specialRuns = runBoard.specialRuns.with(run))
            else -> unreachable()
        }
        runBoards[areaName] = runBoard
        dirty()
    }

    override fun startRun(run: Run) {
        requireNull(runSave) { "cant start new run when old run wasn't completed yet" }
        if (
            run.type == RunType.SPECIAL ||
            run.type == RunType.PROGRESS ||
            run.type == RunType.SPECIAL_NOT_IN_BOARD
        ) {
            require(run.name !in data.completedSpecialRuns) { "cant start completed special/progress run again" }
        }

        val cardsToTakeAlong = if (run.type == RunType.LIMITED) {
            limitedTakeAlong.toList()
        } else {
            currentCollectionDeck.cards
        }
        runSave = RunSave.newRun(this, run, cardsToTakeAlong)
    }

    override fun encounterStarted() {
        val runSave = runSave ?: return
        runSave.encountersStarted++
    }

    override fun loseRun() {
        val runSave = runSave ?: throw RuntimeException("cant lose run if no run is active")
        endRun(runSave)
    }

    override fun winRun() {
        val runSave = runSave
        requireNotNull(runSave) { "cant win run if no run is active" }
        _wonRuns++
        val run = runSave.run
        val area = run.fromArea

        if (run.type == RunType.SPECIAL_NOT_IN_BOARD) {
            data.completedSpecialRuns.add(run.name)
            val cardsToExtraxt = extractableCards()
            _cardCollection.addAll(cardsToExtraxt)
            endRun(runSave)
            return
        }

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
            else -> unreachable()
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

    override fun earnMoney(amount: Int) {
        _playerMoney += amount
    }

    override fun payMoney(amount: Int) {
        _playerMoney -= amount
    }

    override fun getCardForRun(card: CardType) {
        val runSave = runSave ?: throw RuntimeException("not in a run")
        runSave.addCardToBackpack(card)
        checkDecks()
    }

    override fun checkDecks() {
        collectionDecks.forEach { it.checkDeck(cardCollection) }
        runSave?.checkDecks()
    }

    override fun extractableCards(): List<CardType> {
        val runSave = runSave
            ?: throw RuntimeException("Profile.extractableCards() can only be called when a run is active")
        val cardsToExtract = currentRunDeck!!.cards.toMutableList()
        runSave.cardsTakenAlong.forEach { card ->
            cardsToExtract.remove(card)
        }
        return cardsToExtract
    }

    private fun loadAreaMap(map: String) {
        val newMapFile = lookupAreaFile(map)
            ?: throw RuntimeException("no file for area: $map")
        this.currentMapFile = newMapFile
        currentAreaMap = DetailMap.readFromFile(newMapFile)
        _currentMapName = map
    }

    private fun lookupAreaFile(area: String): File? {
        val areaFile = File(profilePath.absolutePath + "/$area.onj")
        return if (areaFile.exists()) areaFile else null
    }

    override fun dirty() {
        dirty = true
    }

    override fun readFromDisk() {
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

    override fun write() {
        writeMaps()
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

    private fun writeMaps() {
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

        val collection: List<CardType>?
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
        var cardCollection: MutableList<CardType>,
        var collectionDecks: MutableList<Deck>,
        var completedSpecialRuns: MutableList<String>,
        var currentDeckId: Int,
        var playerMoney: Int,
        var currentMap: String,
        var currentNode: Int,
        var lastNode: Int?,
        var wonRuns: Int,
        var runBoards: MutableMap<String, RunBoard>
    ) {

        fun asOnj(): OnjObject = buildOnjObject {
            "cardCollection" with cardCollection.map { it.asOnj() }
            "collectionDecks" with collectionDecks.map { it.asOnjObject() }
            "completedSpecialRuns" with completedSpecialRuns
            "currentDeckId" with currentDeckId
            "playerMoney" with playerMoney
            "currentMap" with currentMap
            "currentNode" with currentNode
            "lastNode" with lastNode
            "wonRuns" with wonRuns
            "runBoards" with runBoards.map { it.value.asOnj() }
        }

        companion object {

            fun fromOnj(onj: OnjObject): ProfileData = ProfileData(
                onj.get<OnjArray>("cardCollection")
                    .value
                    .map { CardType.fromOnj(it as OnjObject) }.
                    toMutableList(),
                onj.get<OnjArray>("collectionDecks").value.map { Deck.getFromOnj(it as OnjObject) }.toMutableList(),
                onj.get<OnjArray>("completedSpecialRuns").value.map { it.value as String }.toMutableList(),
                onj.get<Long>("currentDeckId").toInt(),
                onj.get<Long>("playerMoney").toInt(),
                onj.get<String>("currentMap"),
                onj.get<Long>("currentNode").toInt(),
                onj.get<Long?>("lastNode")?.toInt(),
                onj.get<Long>("wonRuns").toInt(),
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

        val limitedTakeAlong: Array<CardType> = arrayOf(
            CardType.fromString("bigBullet"),
            CardType.fromString("silverBullet"),
            CardType.fromString("workerBullet"),
            CardType.fromString("incendiaryBullet")
        )

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
                FortyFive.logger.severe(logTag, "Error loading profile: $profileName")
                FortyFive.logger.stackTrace(e)
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
