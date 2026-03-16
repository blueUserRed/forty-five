package com.microwavestudios.fortyfive.profile

import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.Deck
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.map.DetailMap
import com.microwavestudios.fortyfive.run.Run
import onj.builder.buildOnjObject
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject
import java.io.File
import kotlin.reflect.KMutableProperty
import kotlin.reflect.KProperty

class RunSave private constructor(val profile: Profile) {

    private lateinit var data: RunSaveData

    private var dirty: Boolean = true

    var currentNodeIndex: Int by DataDelegate(RunSaveData::currentNode)
    var lastNodeIndex: Int? by DataDelegate(RunSaveData::lastNode)
    var backpackDecks: MutableList<Deck> by DataDelegate(RunSaveData::backpackDecks)
    var currentDeckId: Int by DataDelegate(RunSaveData::currentDeckId)
    var cardsTakenAlong: List<CardType> by DataDelegate(RunSaveData::cardsTakenAlong)

    var playerHealth: Int by DataDelegate(
        RunSaveData::playerHealth,
        onSet = { value ->
            FortyFive.currentScreen?.events?.fire(Profile.HealthChangedEvent(value))
        }
    )

    var run: Run by DataDelegate(RunSaveData::run)
        private set

    val mapSaver: MapSaver = object : MapSaver {
        override var currentNodeIndex: Int by this@RunSave::currentNodeIndex
        override var lastNodeIndex: Int? by this@RunSave::lastNodeIndex
        override val currentMapName: String = "run_map"
        override val currentMap: DetailMap by this@RunSave::map
    }

    val runMapFile: File = File(profile.profilePath.path + "/runMap.onj")
    val runDataFile: File = File(profile.profilePath.path + "/run_data.onj")

    private var _backpack: MutableList<CardType> by DataDelegate(RunSaveData::backpack)
    val backpack: List<CardType>
        get() = _backpack

    private lateinit var map: DetailMap

    fun dirty() {
        dirty = true
    }

    private fun loadMap() {
        map = DetailMap.readFromFile(runMapFile)
    }

    fun addCardToBackpack(card: CardType) {
        _backpack.add(card)
        backpackDecks.forEach { it.checkDeck(_backpack) }
        dirty()
    }

    fun swapCardInBackpack(old: CardType, new: CardType) {
        val result = _backpack.remove(old)
        require(result) { "card $old not in backpack" }
        _backpack.add(new)
        dirty()
        checkDecks()
    }

    fun readFromDisc() {
        dirty = false
        if (!runDataFile.exists()) {
            runDataFile.createNewFile()
            runDataFile.writeText(data.asOnj().toString())
            return
        }
        val onj = OnjParser.parseFile(runDataFile)
        dataFileSchema.assertMatches(onj)
        onj as OnjObject
        data = RunSaveData.fromOnj(onj)
        data.backpackDecks.forEach { it.checkDeck(data.backpack) }
    }

    fun write() {
        if (!dirty && !data.backpackDecks.any { it.deckDirty }) return
        data.backpackDecks.forEach { it.resetDeckDirty() }
        dirty = false
        if (!runDataFile.exists()) {
            runDataFile.createNewFile()
        }
        runDataFile.writeText(data.asOnj().toString())
    }

    fun writeRunMap() {
        runMapFile.writeText(map.asOnjObject().toMinifiedString())
    }

    fun checkDecks() {
        backpackDecks.forEach { it.checkDeck(backpack) }
    }

    class Preview(val dataFile: File) {

        private lateinit var data: RunSaveData

        val playerHealth: Int
            get() = data.playerHealth

        val run: Run
            get() = data.run

        val backpack: List<CardType>
            get() = data.backpack

        fun read() {
            val onj = OnjParser.parseFile(dataFile)
            dataFileSchema.assertMatches(onj)
            onj as OnjObject
            data = RunSaveData.fromOnj(onj)
        }

        companion object {

            fun loadPreview(profilePreview: Profile.Preview): Preview? {
                val dataFile = File("profiles/${profilePreview.name}/run_data.onj")
                if (!dataFile.exists()) return null
                val preview = Preview(dataFile)
                preview.read()
                return preview
            }
        }

    }

    data class RunSaveData(
        var currentNode: Int,
        var lastNode: Int?,
        var playerHealth: Int,
        var backpack: MutableList<CardType>,
        var backpackDecks: MutableList<Deck>,
        var currentDeckId: Int,
        var cardsTakenAlong: List<CardType>,
        var run: Run
    ) {

        fun asOnj(): OnjObject = buildOnjObject {
            "currentNode" with currentNode
            "lastNode" with lastNode
            "playerHealth" with playerHealth
            "backpack" with backpack.map { it.asOnj() }
            "backpackDecks" with backpackDecks.map { it.asOnjObject() }
            "currentDeckId" with currentDeckId
            "cardsTakenAlong" with cardsTakenAlong.map { it.asOnj() }
            "run" with run.asOnj()
        }

        companion object {

            fun fromOnj(onj: OnjObject): RunSaveData = RunSaveData(
                onj.get<Long>("currentNode").toInt(),
                onj.get<Long?>("lastNode")?.toInt(),
                onj.get<Long>("playerHealth").toInt(),
                onj
                    .get<OnjArray>("backpack")
                    .value
                    .map { CardType.fromOnj(it as OnjObject) }
                    .toMutableList(),
                onj
                    .get<OnjArray>("backpackDecks")
                    .value
                    .map { Deck.getFromOnj(it as OnjObject) }
                    .toMutableList(),
                onj.get<Long>("currentDeckId").toInt(),
                onj
                    .get<OnjArray>("cardsTakenAlong")
                    .value
                    .map { CardType.fromOnj(it as OnjObject) }
                    .toMutableList(),
                Run.fromOnj(onj.get<OnjObject>("run"))
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

    companion object {

        val dataFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/run_data.onjschema")
        }

        fun load(profile: Profile): RunSave? {
            val dataFile = File(profile.profilePath.path + "/run_data.onj")
            if (!dataFile.exists()) return null
            val save = RunSave(profile)
            save.readFromDisc()
            save.loadMap()
            return save
        }

        fun loadPreview(profilePreview: Profile.Preview): Preview? = Preview.loadPreview(profilePreview)

        fun newRun(profile: Profile, run: Run, cardsToTakeAlong: List<CardType>): RunSave {
            val mapGenerator = run.mapGenerator
            val map = mapGenerator.generate("run_map", TimeUtils.millis())
            val save = RunSave(profile)
            save.data = RunSaveData(
                0,
                null,
                run.initialPlayerHealth,
                cardsToTakeAlong.toMutableList(),
                mutableListOf(
                    Deck("1", 0, mutableMapOf()),
                    Deck("2", 1, mutableMapOf()),
                    Deck("3", 2, mutableMapOf()),
                    Deck("4", 3, mutableMapOf()),
                    Deck("5", 4, mutableMapOf()),
                ),
                0,
                cardsToTakeAlong,
                run
            )
            save.write()
            val runMapFile = save.runMapFile
            if (runMapFile.exists()) runMapFile.delete()
            runMapFile.createNewFile()
            runMapFile.writeText(map.asOnjObject().toMinifiedString())
            save.map = map
            return save
        }
    }

}
