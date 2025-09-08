package com.microwavestudios.fortyfive.map

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.controller.EncounterContext
import com.microwavestudios.fortyfive.run.Encounter
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
                onjObject.get<OnjArray?>("currentCards")?.value?.map { it.value as String },
                onjObject.get<Long>("amountOfRerolls").toInt(),
                onjObject.get<Long>("rerollPriceIncrease").toInt(),
                onjObject.get<Long>("rerollBasePrice").toInt(),
            )
        },
        "ChooseCardMapEvent" to { ChooseCardMapEvent.fromOnj(it) },
        "LockNodeMapEvent" to { LockNodeMapEvent.fromOnj(it) },
        "ProgressRunMapEvent" to { ProgressRunMapEvent.fromOnj(it) }
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

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("EmptyMapEvent")
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

    override fun start() {
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

class EnterMapMapEvent(val targetMap: String, val fromEnd: Boolean) : MapEvent() {

    override var currentlyBlocks: Boolean = false
    override var startable: Boolean = true
    override var isCompleted: Boolean = false
    override val displayDescription: Boolean = true

    override val buttonText: String = "Enter"

    private val mapConfig: ConfigFileManager.MapConfig = ConfigFileManager.mapConfig
    private val targetMapDisplayName: String = mapConfig.displayNames[targetMap]!!

    // lazy so it doesn't crash when the event is instanced
    override val displayName: String by lazy {
        "Enter $targetMapDisplayName"
    }
    override val descriptionText: String by lazy {
        ""
    }

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
    private val onlyIfPlayerDoesntHaveCard: String?,
    override val dialog: String,
) : MapEvent(), DialogScreenContext {

    override var currentlyBlocks: Boolean = true
    override var startable: Boolean = true

    override var isCompleted: Boolean = !startable

    override val displayDescription: Boolean = true

    override val descriptionText: String = ""
    override val buttonText: String = "Talk"

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
        "canOnlyBeStartedOnce" with canOnlyBeStartedOnce
        onlyIfPlayerDoesntHaveCard?.let { "onlyIfPlayerDoesntHaveCard" to it }
    }

    companion object {

        fun fromOnj(onj: OnjObject): DialogMapEvent = DialogMapEvent(
            onj.get<Boolean>("canOnlyBeStartedOnce"),
            onj.getOr<String?>("onlyIfPlayerDoesntHaveCard", null),
            onj.get<String>("dialog")
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
    var currentCards: List<String>?,
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
        "currentCards" with currentCards
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

class ProgressRunMapEvent : MapEvent(), Completable {

    override var currentlyBlocks: Boolean = true
    override var startable: Boolean = true
    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override fun start() {
        val map = FortyFive.profileManager.currentProfile!!.currentMapSaver.currentMap
        if (!map.isArea) throw RuntimeException("cant start progress run when not in an area")
        val run = map.progressRun ?: throw RuntimeException("map ${map.name} doesn't define a progress run")
        FortyFive.profileManager.currentProfile!!.startRun(run)
        FortyFive.screenManager.screenFinished()
    }

    override fun onMapLoad(map: DetailMap) {
        if (isCompleted) return
        if (map.completedProgressRun) completed()
    }

    override fun completed() {
        currentlyBlocks = false
        startable = false
        isCompleted = true
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("ProgressRunMapEvent")
        includeStandardConfig()
    }

    companion object {

        fun fromOnj(onj: OnjObject): ProgressRunMapEvent =
            ProgressRunMapEvent().also { it.setStandardValuesFromConfig(onj) }
    }

}
