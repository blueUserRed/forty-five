package com.microwavestudios.fortyfive.map

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.config.displayName
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.game.controller.EncounterContext
import com.microwavestudios.fortyfive.run.DifficultyScaling
import com.microwavestudios.fortyfive.run.Encounter
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
        "LockNodeMapEvent" to { LockNodeMapEvent.fromOnj(it) },
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
     * when this is true, the player can't progress past the node
     */
    abstract var currentlyBlocks: Boolean

    /**
     * when this is true, a start button for this event is displayed and the start function can be called
     */
    abstract var startable: Boolean

    /**
     * when this is true, the event was already completed
     */
    abstract var isCompleted: Boolean

    /**
     * when this is true, the sidebar with the description is displayed
     */
    abstract val displayDescription: Boolean

    open val buttonText: String = "Start"

    /**
     * Short text describing the event
     */
    open val descriptionText: String = ""

    open val warningText: String? = null

    /**
     * Short text that is displayed instead of [descriptionText] when the event was completed
     */
    open val completedDescriptionText: String = ""

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

    abstract val chainScreen: Pair<ScreenManager.ScreenCreatorCompanion, Any>?

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

    fun canBeStarted(map: DetailMap): Boolean =
        startable && _startConditions.all { it.check(map) }

    fun isBlocking(map: DetailMap): Boolean =
        currentlyBlocks || _blockConditions.any { it.check(map) }

    fun setStandardValuesFromConfig(config: OnjObject) {
        currentlyBlocks = config.get<Boolean>("currentlyBlocks")
        startable = config.get<Boolean>("startable")
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
    }

    /**
     * utility function that can be called from the [asOnjObject] function and includes the [currentlyBlocks],
     * [startable], [isCompleted] fields in the onjObject.
     */
    protected fun OnjObjectBuilderDSL.includeStandardConfig() {
        "currentlyBlocks" with currentlyBlocks
        "startable" with startable
        "isCompleted" with isCompleted
        "startConditions" with _startConditions.map { it.asOnj() }
        "blockConditions" with _blockConditions.map { it.asOnj() }
    }

}

/**
 * Map Event that is not visible to the user and does nothing
 */
class EmptyMapEvent : MapEvent() {

    override var currentlyBlocks: Boolean = false
    override var startable: Boolean = false
    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = false

    override fun start() {}

    override val chainScreen: Pair<ScreenManager.ScreenCreatorCompanion, Any>? = null

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("EmptyMapEvent")
    }
}

class SimpleMapEvent(
    override val displayDescription: Boolean,
    override val descriptionText: String,
    override val displayName: String
) : MapEvent() {

    override var currentlyBlocks: Boolean = false
    override var startable: Boolean = false
    override var isCompleted: Boolean = false

    override val chainScreen: Pair<ScreenManager.ScreenCreatorCompanion, Any>? = null

    constructor() : this(false, "", "")

    override fun start() {
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("SimpleMapEvent")
        "displayDescription" with displayDescription
        "descriptionText" with descriptionText
        "displayName" with displayName
        includeStandardConfig()
    }

    companion object {

        fun fromOnj(onj: OnjObject): SimpleMapEvent = SimpleMapEvent(
            onj.getOr<Boolean>("displayDescription", false),
            onj.getOr<String>("descriptionText", ""),
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

    override var currentlyBlocks: Boolean = true
    override var startable: Boolean = true
    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val descriptionText: String = "Take on enemies and come out on top!"
    override val completedDescriptionText: String = "All enemies gone already!"
    override val displayName: String = "Encounter"

    override val buttonText: String = "Fight!"

    override val warningText: String? = if (isExtraction) {
        "Last encounter:\nAfter this Encounter, all cards in your current deck will be added to your collection. All" +
                "other cards will be lost!"
    } else {
        null
    }

    override val chainScreen: Pair<ScreenManager.ScreenCreatorCompanion, Any> = EncounterScreen to this

    override fun start() {
        FortyFive.profileManager.currentProfile?.encounterStarted()
        FortyFive.screenManager.appendScreen(EncounterScreen, this)
        FortyFive.screenManager.screenFinished()
    }

    override fun completed() {
        currentlyBlocks = false
        startable = false
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
    val majorDifficulty: Int,
    val unadjustedMajorDifficulty: Int,
    val minorDifficulty: Float,
    val runModifier: List<RunModifier>,
    val amountEnemies: IntRange,
    val biome: String,
    val difficultyScaling: DifficultyScaling,
    val scaleMin: Float,
    val scaleMax: Float,
    val seed: Long,
) : MapEvent() {

    override var currentlyBlocks: Boolean = false
    override var startable: Boolean = false
    override var isCompleted: Boolean = false
    override val displayDescription: Boolean = false

    override val chainScreen: Pair<ScreenManager.ScreenCreatorCompanion, Any>? = null

    override fun start() {
        FortyFive.logger.warn("MapEvent", "EncounterPlaceholderMapEvent started")
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("EncounterPlaceholderMapEvent")
        "genExtraction" with genExtraction
        "majorDifficulty" with majorDifficulty
        "unadjustedMajorDifficulty" with unadjustedMajorDifficulty
        "minorDifficulty" with minorDifficulty
        "runModifier" with runModifier.map { it.name() }
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
            onj.get<Long>("majorDifficulty").toInt(),
            onj.get<Long>("unadjustedMajorDifficulty").toInt(),
            onj.get<Double>("minorDifficulty").toFloat(),
            onj.get<OnjArray>("runModifier").value.map { RunModifier.get(it.value as String) },
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

    override var currentlyBlocks: Boolean = false
    override var startable: Boolean = true
    override var isCompleted: Boolean = false
    override val displayDescription: Boolean = true

    override val buttonText: String = "Enter"

    private val targetMapDisplayName: String = displayName(targetMap)

    override val displayName: String = "Enter $targetMapDisplayName"
    override val descriptionText: String = ""

    override val chainScreen: Pair<ScreenManager.ScreenCreatorCompanion, Any>? = null

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
    private val canOnlyBeStartedOnce: Boolean,
    override val dialog: String,
    override val displayName: String,
    override val descriptionText: String,
    override val completedDescriptionText: String
) : MapEvent(), DialogScreenContext {

    override var currentlyBlocks: Boolean = true
    override var startable: Boolean = true

    override var isCompleted: Boolean = !startable

    override val displayDescription: Boolean = true

    override val buttonText: String = "Talk"

    override val chainScreen: Pair<ScreenManager.ScreenCreatorCompanion, Any> = DialogScreen to this

    override fun start() {
        FortyFive.screenManager.appendScreen(DialogScreen, this)
        FortyFive.screenManager.screenFinished()
    }

    override fun completed() {
        currentlyBlocks = false
        if (canOnlyBeStartedOnce) startable = false
        isCompleted = true
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("DialogMapEvent")
        includeStandardConfig()
        "dialog" with dialog
        "displayName" with displayName
        "descriptionText" with descriptionText
        "completedDescriptionText" with completedDescriptionText
        "canOnlyBeStartedOnce" with canOnlyBeStartedOnce
    }

    companion object {

        fun fromOnj(onj: OnjObject): DialogMapEvent = DialogMapEvent(
            onj.get<Boolean>("canOnlyBeStartedOnce"),
            onj.get<String>("dialog"),
            onj.get<String>("displayName"),
            onj.get<String>("descriptionText"),
            onj.get<String>("completedDescriptionText"),
        ).apply { setStandardValuesFromConfig(onj) }
    }
}

/**
 * event that opens a shop where the player can buy up to 8 cards
 * @param type which type the restrictions are
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

    override var currentlyBlocks: Boolean = false
    override var startable: Boolean = true
    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val descriptionText: String = ""
    override val displayName: String = "Shop"
    override val buttonText: String = "Enter"

    val currentRerollPrice: Int
        get() = rerollBasePrice + rerollPriceIncrease * amountOfRerolls

    override val chainScreen: Pair<ScreenManager.ScreenCreatorCompanion, Any> = ShopScreen to this

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

    override var currentlyBlocks: Boolean = false
    override var startable: Boolean = true
    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val descriptionText: String =
        if (nbrOfCards > 1) "You can choose one of $nbrOfCards cards." else "You get a card."
    override val displayName: String = "Ominous person"

    override val chainScreen: Pair<ScreenManager.ScreenCreatorCompanion, Any> = ChooseCardScreen to this

    override fun start() {
        FortyFive.screenManager.appendScreen(ChooseCardScreen, this)
        FortyFive.screenManager.screenFinished()
    }

    override fun completed() {
        isCompleted = true
        currentlyBlocks = false
        startable = false
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

class LockNodeMapEvent(
    val conditions: List<MapPredicate>,
    initialLocked: Boolean,
    val lockedDescription: String,
    val openDescription: String,
) : MapEvent() {

    override var currentlyBlocks: Boolean
        get() = locked
        set(_) {}

    override var startable: Boolean = false
    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val descriptionText: String
        get() = if (locked) lockedDescription else openDescription

    var locked: Boolean = initialLocked
        private set

    override val chainScreen: Pair<ScreenManager.ScreenCreatorCompanion, Any>? = null

    override fun start() {
    }

    override fun onMapLoad(map: DetailMap) {
        checkLocked(map)
    }

    override fun onPlayerMovedToNode(map: DetailMap) {
        checkLocked(map)
    }

    private fun checkLocked(map: DetailMap) {
        if (!locked) return
        if (conditions.all { it.check(map) }) locked = false
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("LockNodeMapEvent")
        "conditions" with conditions.map { it.asOnj() }
        "locked" with locked
        "lockedDescription" with lockedDescription
        "openDescription" with openDescription
    }

    companion object {

        fun fromOnj(onj: OnjObject): LockNodeMapEvent = LockNodeMapEvent(
            onj.get<OnjArray>("conditions").value.map { MapPredicate.fromOnj(it as OnjNamedObject) },
            onj.get<Boolean>("locked"),
            onj.get<String>("lockedDescription"),
            onj.get<String>("openDescription"),
        )
    }

}

class CompleteRunMapEvent(
    val runName: String,
    override val displayName: String,
    override val descriptionText: String,
    override val completedDescriptionText: String,
) : MapEvent(), Completable {

    override var currentlyBlocks: Boolean = true
    override var startable: Boolean = true
    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val chainScreen: Pair<ScreenManager.ScreenCreatorCompanion, Any>? = null

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
        currentlyBlocks = false
        startable = false
        isCompleted = true
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("CompleteRunMapEvent")
        "runName" with runName
        "displayName" with displayName
        "descriptionText" with descriptionText
        "completedDescriptionText" with completedDescriptionText
        includeStandardConfig()
    }

    companion object {

        fun fromOnj(onj: OnjObject): CompleteRunMapEvent = CompleteRunMapEvent(
            onj.get<String>("runName"),
            onj.get<String>("displayName"),
            onj.get<String>("descriptionText"),
            onj.get<String>("completedDescriptionText"),
        ).also { it.setStandardValuesFromConfig(onj) }
    }

}

class FinishTutorialRunMapEvent : MapEvent() {

    override var currentlyBlocks: Boolean = false
    override var startable: Boolean = true
    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val displayName: String = "Finish"
    override val descriptionText: String = "You completed the Tutorial!"

    override val chainScreen: Pair<ScreenManager.ScreenCreatorCompanion, Any>? = null

    override fun start() {
        val profile = FortyFive.profileManager.currentProfile!!
        profile.winRun()
        FortyFive.screenManager.screenFinished()
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("FinishTutorialRunMapEvent")
    }

}
