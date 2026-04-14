package com.microwavestudios.fortyfive.map

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.config.displayName
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.game.controller.EncounterContext
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.run.DifficultyScaling
import com.microwavestudios.fortyfive.run.Encounter
import com.microwavestudios.fortyfive.run.RunBehaviour
import com.microwavestudios.fortyfive.run.RunModifier
import com.microwavestudios.fortyfive.screen.ScreenManager
import com.microwavestudios.fortyfive.screen.screenController.DialogScreenContext
import com.microwavestudios.fortyfive.screen.screens.*
import com.microwavestudios.fortyfive.utils.toIntRange
import onj.builder.OnjObjectBuilderDSL
import onj.builder.buildOnjObject
import onj.value.*

/**
 * used for dynamically creating events
 */
object MapEventFactory {

    private var mapEventCreators: Map<String, (onj: OnjObject) -> MapEvent> = mapOf(
        "EmptyMapEvent" to { EmptyMapEvent() },
        "SimpleMapEvent" to { SimpleMapEvent.fromOnj(it) },
        "EncounterMapEvent" to { EncounterMapEvent.fromOnj(it) },
        "EnterMapMapEvent" to {
            EnterMapMapEvent(it.get<String>("targetMap"), it.get<Boolean>("fromEnd"))
        },
        "DialogMapEvent" to { DialogMapEvent.fromOnj(it) },
        "ShopMapEvent" to { onjObject ->
            ShopMapEvent(
                onjObject.get<OnjArray>("types").value.map { it.value as String }.toSet(),
                onjObject.get<String>("person"),
                onjObject.get<List<OnjInt>>("boughtIndices").map { it.value.toInt() }.toMutableSet(),
                onjObject.get<OnjArray>("amountCards").toIntRange(),
                onjObject.get<OnjArray?>("currentCards")
                    ?.value
                    ?.map { CardType.fromOnj(it as OnjObject) },
                onjObject.get<Long>("amountOfRerolls").toInt(),
                onjObject.get<Long>("rerollPriceIncrease").toInt(),
                onjObject.get<Long>("rerollBasePrice").toInt(),
            )
        },
        "EncounterPlaceholderMapEvent" to { EncounterPlaceholderMapEvent.fromOnj(it) },
        "ChooseCardMapEvent" to { ChooseCardMapEvent.fromOnj(it) },
        "CompleteRunMapEvent" to { CompleteRunMapEvent.fromOnj(it) },
        "FinishTutorialRunMapEvent" to { FinishTutorialRunMapEvent() }
    )

    fun getMapEvent(onj: OnjNamedObject): MapEvent =
        mapEventCreators[onj.name]?.invoke(onj) ?: throw RuntimeException("unknown map event ${onj.name}")
}

interface Completable {
    fun completed()
}

/**
 * an event that can be placed on a [MapNode]
 */
abstract class MapEvent {

    /**
     * when this is true, the event was already completed
     */
    abstract var isCompleted: Boolean

    /**
     * when this is true, the sidebar with the description is displayed
     */
    abstract val displayDescription: Boolean

    open val buttonText: String = "Start"

    var descriptionText: List<Pair<MapPredicate, String>> = listOf()
        private set

    open val warningText: String? = null

    /**
     * the name of the event that is displayed to the user
     */
    open val displayName: String = ""

    private val _startConditions: MutableList<MapPredicate> = mutableListOf()
    val startConditions: List<MapPredicate>
        get() = _startConditions

    private val _blockConditions: MutableList<MapPredicate> = mutableListOf()
    val blockConditions: List<MapPredicate>
        get() = _blockConditions

    abstract val nodeTexture: ResourceHandle
    open val secondaryNodeTexture: ResourceHandle? = null

    /**
     * called when the start button was clicked
     */
    abstract fun start()

    /**
     * returns a representation of this event (and its state) as an OnjObject
     */
    abstract fun asOnjObject(): OnjObject

    open fun onMapLoad(map: DetailMap) {}
    open fun onPlayerMovedToNode(map: DetailMap) {}

    fun canBeStarted(map: DetailMap): Boolean = _startConditions.all { it.check(map, this) }

    fun isBlocking(map: DetailMap): Boolean = _blockConditions.any { it.check(map, this) }

    fun setDescriptionText(texts: List<Pair<MapPredicate, String>>) {
        descriptionText = texts
    }

    fun currentDescription(map: DetailMap): String =
        descriptionText.firstOrNull { it.first.check(map, this) }?.second ?: ""

    fun addStartCondition(predicate: MapPredicate) {
        _startConditions.add(predicate)
    }

    fun addBlockCondition(predicate: MapPredicate) {
        _blockConditions.add(predicate)
    }

    fun setStandardValuesFromConfig(config: OnjObject) {
        isCompleted = config.get<Boolean>("isCompleted")
        val startConditions = config
            .getOr<OnjArray?>("startConditions", null)
            ?.value
            ?.map { MapPredicate.fromOnj(it as OnjNamedObject) }
        _startConditions.clear()
        startConditions?.let { _startConditions.addAll(it) }
        val blockConditions = config
            .getOr<OnjArray?>("blockConditions", null)
            ?.value
            ?.map { MapPredicate.fromOnj(it as OnjNamedObject) }
        _blockConditions.clear()
        blockConditions?.let { _blockConditions.addAll(it) }
        descriptionText = config
            .get<OnjArray>("descriptionText")
            .value
            .map { obj ->
                obj as OnjObject
                MapPredicate.fromOnj(obj.get<OnjNamedObject>("predicate")) to obj.get<String>("text")
            }
    }

    /**
     * utility function that can be called from the [asOnjObject] function and includes standard config
     */
    protected fun OnjObjectBuilderDSL.includeStandardConfig() {
        "isCompleted" with isCompleted
        "startConditions" with _startConditions.map { it.asOnj() }
        "blockConditions" with _blockConditions.map { it.asOnj() }
        "descriptionText" with descriptionText.map {
            buildOnjObject {
                "predicate" with it.first.asOnj()
                "text" with it.second
            }
        }
    }

}

/**
 * Map Event that is not visible to the user and does nothing
 */
class EmptyMapEvent : MapEvent() {

    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = false

    override fun start() {}

    override val nodeTexture: ResourceHandle = "map_node_default"

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("EmptyMapEvent")
    }
}

class SimpleMapEvent(
    override val displayDescription: Boolean,
    override val displayName: String
) : MapEvent() {

    override var isCompleted: Boolean = false

    override val nodeTexture: ResourceHandle = "map_node_default"

    constructor() : this(false, "")

    override fun start() {
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("SimpleMapEvent")
        "displayDescription" with displayDescription
        "displayName" with displayName
        includeStandardConfig()
    }

    companion object {

        fun fromOnj(onj: OnjObject): SimpleMapEvent = SimpleMapEvent(
            onj.getOr<Boolean>("displayDescription", false),
            onj.getOr<String>("displayName", ""),
        ).apply { setStandardValuesFromConfig(onj) }
    }

}

/**
 * Map Event that represents an encounter with an enemy
 */
class EncounterMapEvent(
    override var encounter: Encounter,
    override val isExtraction: Boolean
) : MapEvent(), EncounterContext, Completable {

    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val displayName: String = if (encounter.isHard) "Hard Encounter" else "Encounter"

    override val buttonText: String = "Fight!"

    override val warningText: String? = if (isExtraction) {
        "Last encounter:\nAfter this Encounter, all cards in your current deck will be added to your collection. All" +
                "other cards will be lost!"
    } else {
        null
    }

    override val nodeTexture: ResourceHandle = "map_node_fight"

    override val secondaryNodeTexture: ResourceHandle? = if (isExtraction) {
        "map_node_exit"
    } else {
        null
    }

    override fun start() {
        FortyFive.profileManager.currentProfile?.encounterStarted()
        FortyFive.screenManager.appendScreen(EncounterScreen, this)
        FortyFive.screenManager.screenFinished()
    }

    override fun completed() {
        isCompleted = true
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("EncounterMapEvent")
        includeStandardConfig()
        "encounter" with encounter.asOnj()
        "isExtraction" with isExtraction
    }

    companion object {

        fun fromOnj(onj: OnjObject): EncounterMapEvent = EncounterMapEvent(
            Encounter.fromOnj(onj.get<OnjObject>("encounter")),
            onj.get<Boolean>("isExtraction")
        ).apply { setStandardValuesFromConfig(onj) }
    }
}

class EncounterPlaceholderMapEvent(
    val genExtraction: Boolean,
    val genHard: Boolean,
    val majorDifficulty: Int,
    val unadjustedMajorDifficulty: Int,
    val minorDifficulty: Float,
    val amountEnemies: IntRange,
    val biome: String,
    val difficultyScaling: DifficultyScaling,
    val scaleMin: Float,
    val scaleMax: Float,
    val seed: Long,
) : MapEvent() {

    override var isCompleted: Boolean = false
    override val displayDescription: Boolean = false

    override val nodeTexture: ResourceHandle = "map_node_default"

    override fun start() {
        FortyFive.logger.warn("MapEvent", "EncounterPlaceholderMapEvent started")
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("EncounterPlaceholderMapEvent")
        "genExtraction" with genExtraction
        "genHard" with genHard
        "majorDifficulty" with majorDifficulty
        "unadjustedMajorDifficulty" with unadjustedMajorDifficulty
        "minorDifficulty" with minorDifficulty
        "amountEnemies" with arrayOf(amountEnemies.first, amountEnemies.last)
        "biome" with biome
        "seed" with seed
        "difficultyScaling" with difficultyScaling.toOnj()
        "scaleMin" with scaleMin
        "scaleMax" with scaleMax
    }

    companion object {

        fun fromOnj(onj: OnjObject): EncounterPlaceholderMapEvent = EncounterPlaceholderMapEvent(
            onj.get<Boolean>("genExtraction"),
            onj.get<Boolean>("genHard"),
            onj.get<Long>("majorDifficulty").toInt(),
            onj.get<Long>("unadjustedMajorDifficulty").toInt(),
            onj.get<Double>("minorDifficulty").toFloat(),
            onj.get<OnjArray>("amountEnemies").toIntRange(),
            onj.get<String>("biome"),
            DifficultyScaling.fromOnj(onj.get<OnjNamedObject>("difficultyScaling")),
            onj.get<Double>("scaleMin").toFloat(),
            onj.get<Double>("scaleMax").toFloat(),
            onj.get<Long>("seed"),
        )
    }

}

class EnterMapMapEvent(val targetMap: String, val fromEnd: Boolean) : MapEvent() {

    override var isCompleted: Boolean = false
    override val displayDescription: Boolean = true

    override val buttonText: String = "Enter"

    private val targetMapDisplayName: String = displayName(targetMap)

    override val displayName: String = "Enter $targetMapDisplayName"
    override val nodeTexture: ResourceHandle = "map_node_exit"

    override fun start() {
        FortyFive.profileManager.currentProfile!!.changeToMap(targetMap, fromEnd)
        FortyFive.screenManager.appendScreen(MapScreen, this)
        FortyFive.screenManager.screenFinished()
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("EnterMapMapEvent")
        "targetMap" with targetMap
        "fromEnd" with fromEnd
    }
}

/**
 * event that opens a dialog box and allows talking to an NPC
 */
class DialogMapEvent(
    override val dialog: String,
    override val displayName: String,
) : MapEvent(), DialogScreenContext {

    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val buttonText: String = "Talk"

    override val nodeTexture: ResourceHandle = "map_node_dialog"

    override fun start() {
        FortyFive.screenManager.appendScreen(DialogScreen, this)
        FortyFive.screenManager.screenFinished()
    }

    override fun completed() {
        isCompleted = true
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("DialogMapEvent")
        includeStandardConfig()
        "dialog" with dialog
        "displayName" with displayName
    }

    companion object {

        fun fromOnj(onj: OnjObject): DialogMapEvent = DialogMapEvent(
            onj.get<String>("dialog"),
            onj.get<String>("displayName"),
        ).apply { setStandardValuesFromConfig(onj) }
    }
}

/**
 * event that opens a shop where the player can buy cards
 */
class ShopMapEvent(
    val types: Set<String>,
    val person: String,
    val boughtIndices: MutableSet<Int>,
    val amountCards: IntRange,
    var currentCards: List<CardType>?,
    var amountOfRerolls: Int,
    val rerollPriceIncrease: Int,
    val rerollBasePrice: Int,
) : MapEvent() {

    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val displayName: String = "Shop"
    override val buttonText: String = "Enter"

    val currentRerollPrice: Int
        get() = rerollBasePrice + rerollPriceIncrease * amountOfRerolls

    override val nodeTexture: ResourceHandle = "map_node_shop"

    override fun start() {
        FortyFive.screenManager.appendScreen(ShopScreen, this)
        FortyFive.screenManager.screenFinished()
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("ShopMapEvent")
        "types" with types
        "person" with person
        "amountCards" with arrayOf(amountCards.first, amountCards.last)
        "boughtIndices" with boughtIndices
        "currentCards" with currentCards?.map { it.asOnj() }
        "amountOfRerolls" with amountOfRerolls
        "rerollPriceIncrease" with rerollPriceIncrease
        "rerollBasePrice" with rerollBasePrice
    }
}

/**
 * event that opens a shop where the player can buy up to 8 cards
 * @param types which type the restrictions are
 */
class ChooseCardMapEvent(
    override val types: List<String>,
    override val enableRerolls: Boolean,
    override var amountOfRerolls: Int,
    override val rerollPriceIncrease: Int,
    override val rerollBasePrice: Int,
    override var seed: Long,
    override val nbrOfCards: Int,
) : MapEvent(), ChooseCardScreenContext, Completable {

    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val displayName: String = "Ominous person"

    override val nodeTexture: ResourceHandle = "map_node_choose_card"

    override fun start() {
        FortyFive.screenManager.appendScreen(ChooseCardScreen, this)
        FortyFive.screenManager.screenFinished()
    }

    override fun completed() {
        isCompleted = true
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("ChooseCardMapEvent")
        includeStandardConfig()
        ("types" with types)
        ("seed" with seed)
        ("nbrOfCards" with nbrOfCards)
        "enableRerolls" with enableRerolls
        "amountOfRerolls" with amountOfRerolls
        "rerollPriceIncrease" with rerollPriceIncrease
        "rerollBasePrice" with rerollBasePrice
    }

    companion object {

        fun fromOnj(onj: OnjObject): ChooseCardMapEvent = ChooseCardMapEvent(
            onj.get<OnjArray>("types").value.map { (it as OnjString).value },
            onj.get<Boolean>("enableRerolls"),
            onj.get<Long>("amountOfRerolls").toInt(),
            onj.get<Long>("rerollPriceIncrease").toInt(),
            onj.get<Long>("rerollBasePrice").toInt(),
            onj.get<Long?>("seed") ?: (Math.random() * 1000).toLong(),
            onj.get<Long>("nbrOfCards").toInt(),
        ).apply { setStandardValuesFromConfig(onj) }
    }
}

class CompleteRunMapEvent(
    val runName: String,
    override val displayName: String,
) : MapEvent(), Completable {

    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val nodeTexture: ResourceHandle = "map_node_exit"

    override fun start() {
        val map = FortyFive.profileManager.currentProfile!!.currentMapSaver.currentMap
        if (!map.isArea) throw RuntimeException("cant start progress run when not in an area")
        val run = ConfigFileManager.runConfig.loadRun(runName)
        FortyFive.profileManager.currentProfile!!.startRun(run)
        FortyFive.screenManager.screenFinished()
    }

    override fun onMapLoad(map: DetailMap) {
        val profile = FortyFive.profileManager.currentProfile!!
        if (profile.isSpecialRunCompleted(runName)) {
            completed()
        }
    }

    override fun completed() {
        isCompleted = true
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("CompleteRunMapEvent")
        "runName" with runName
        "displayName" with displayName
        includeStandardConfig()
    }

    companion object {

        fun fromOnj(onj: OnjObject): CompleteRunMapEvent = CompleteRunMapEvent(
            onj.get<String>("runName"),
            onj.get<String>("displayName"),
        ).also { it.setStandardValuesFromConfig(onj) }
    }

}

class FinishTutorialRunMapEvent : MapEvent() {

    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val displayName: String = "Finish"

    override val nodeTexture: ResourceHandle = "map_node_exit"

    override fun start() {
        val profile = FortyFive.profileManager.currentProfile!!
        profile.winRun()
        FortyFive.screenManager.screenFinished()
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("FinishTutorialRunMapEvent")
    }

}
