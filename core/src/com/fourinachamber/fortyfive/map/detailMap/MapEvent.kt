package com.fourinachamber.fortyfive.map.detailMap

import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.game.PermaSaveState
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.events.chooseCard.ChooseCardScreenContext
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.toIntRange
import onj.builder.OnjObjectBuilderDSL
import onj.builder.buildOnjObject
import onj.value.*

/**
 * used for dynamically creating events
 */
object MapEventFactory {

    private var mapEventCreators: Map<String, (onj: OnjObject) -> MapEvent> = mapOf(
        "EmptyMapEvent" to { EmptyMapEvent() },
        "EncounterMapEvent" to { EncounterMapEvent(it) },
        "EnterMapMapEvent" to { EnterMapMapEvent(it.get<String>("targetMap")) },
        "NPCMapEvent" to { NPCMapEvent(it) },
        "ShopMapEvent" to { onjObject ->
            ShopMapEvent(
                onjObject.get<OnjArray>("types").value.map { it.value as String }.toSet(),
                onjObject.get<String>("person"),
                onjObject.get<Long?>("seed") ?: (Math.random() * 1000).toLong(),
                onjObject.get<List<OnjInt>>("boughtIndices").map { it.value.toInt() }.toMutableSet(),
            )
        },
        "ChooseCardMapEvent" to { ChooseCardMapEvent(it) },
        "HealOrMaxHPEvent" to { HealOrMaxHPMapEvent(it) },
        "AddMaxHPEvent" to { AddMaxHPMapEvent(it) },
        "FinishTutorialMapEvent" to { FinishTutorialMapEvent(it) },
    )

    fun getMapEvent(onj: OnjNamedObject): MapEvent =
        mapEventCreators[onj.name]?.invoke(onj) ?: throw RuntimeException("unknown map event ${onj.name}")
}

/**
 * for those events that need to know the distance till the end of the next road
 */
interface ScaledByDistance {
    var distanceToEnd: Int

    fun setDistanceFromConfig(onj: OnjObject) {
        distanceToEnd = onj.get<Long?>("distanceToEnd")?.toInt() ?: -1
    }

    fun OnjObjectBuilderDSL.includeDistanceFromEnd() {
        "distanceToEnd" with distanceToEnd
    }
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
        protected set

    /**
     * when this is true, a start button for this event is displayed and the start function can be called
     */
    abstract var canBeStarted: Boolean
        protected set

    /**
     * when this is true, the event was already completed
     */
    abstract var isCompleted: Boolean
        protected set

    /**
     * when this is true, the sidebar with the description is displayed
     */
    abstract val displayDescription: Boolean

    open val buttonText: String = "Start"

    /**
     * currently unused
     */
    open val icon: String? = null

    /**
     * currently unused
     */
    open val additionalIcons: List<String> = listOf()

    /**
     * Short text describing the event
     */
    open val descriptionText: String = ""

    /**
     * Short text that is displayed instead of [descriptionText] when the event was completed
     */
    open val completedDescriptionText: String = ""

    /**
     * the name of the event that is displayed to the user
     */
    open val displayName: String = ""

    /**
     * called when the start button was clicked
     */
    abstract fun start()

    /**
     * returns a representation of this event (and its state) as an OnjObject
     */
    abstract fun asOnjObject(): OnjObject

    /**
     * utility function that reads and sets the [currentlyBlocks], [canBeStarted], [isCompleted] fields from an
     * OnjObject
     */
    protected fun setStandardValuesFromConfig(config: OnjObject) {
        currentlyBlocks = config.get<Boolean>("currentlyBlocks")
        canBeStarted = config.get<Boolean>("canBeStarted")
        isCompleted = config.get<Boolean>("isCompleted")
    }

    /**
     * utility function that can be called from the [asOnjObject] function and includes the [currentlyBlocks],
     * [canBeStarted], [isCompleted] fields in the onjObject.
     */
    protected fun OnjObjectBuilderDSL.includeStandardConfig() {
        "currentlyBlocks" with currentlyBlocks
        "canBeStarted" with canBeStarted
        "isCompleted" with isCompleted
    }

}

/**
 * Map Event that is not visible to the user and does nothing
 */
class EmptyMapEvent : MapEvent() {

    override var currentlyBlocks: Boolean = false
    override var canBeStarted: Boolean = false
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
class EncounterMapEvent(obj: OnjObject) : MapEvent(), GameController.EncounterContext, ScaledByDistance, Completable {

    override var currentlyBlocks: Boolean = true
    override var canBeStarted: Boolean = true
    override var isCompleted: Boolean = false
    override var distanceToEnd: Int = -1

    override val displayDescription: Boolean = true

    override var encounterIndex: Int = obj.get<Long>("encounterIndex").toInt()

    override val icon: String = "normal_bullet"
    override val descriptionText: String = "Take on enemies and come out on top!"
    override val completedDescriptionText: String = "All enemies gone already!"
    override val displayName: String = "Encounter"

    override val forwardToScreen: String = MapManager.mapScreenPath

    override val buttonText: String = "Fight!"

    init {
        setStandardValuesFromConfig(obj)
        setDistanceFromConfig(obj)
    }

    override fun start() {
        MapManager.changeToEncounterScreen(this)
    }

    override fun completed() {
        FortyFiveLogger.debug("EncounterMapEvent", "Encounter with $encounterIndex is completed")
        currentlyBlocks = false
        canBeStarted = false
        isCompleted = true
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("EncounterMapEvent")
        includeStandardConfig()
        includeDistanceFromEnd()
        "encounterIndex" with encounterIndex

    }

}

/**
 * MapEvent that opens another map when started
 * @param targetMap the name of the map to be opened
 * @param placeAtEnd if true, the player is placed at last node of the map instead of the first
 */
class EnterMapMapEvent(val targetMap: String) : MapEvent() {

    override var currentlyBlocks: Boolean = false
    override var canBeStarted: Boolean = true
    override var isCompleted: Boolean = false
    override val displayDescription: Boolean = true

    override val buttonText: String = "Enter"

    // lazy so it doesn't crash when the event is instanced
    override val displayName: String by lazy {
        "Enter ${MapManager.displayName(targetMap)}"
    }
    override val descriptionText: String by lazy {
        "Have fun exploring ${MapManager.displayName(targetMap)}"
    }

    override fun start() {
        MapManager.changeToMap(targetMap)
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("EnterMapMapEvent")
        "targetMap" with targetMap
    }

}

/**
 * event that opens a dialog box and allows talking to an NPC
 */
class NPCMapEvent(onj: OnjObject) : MapEvent() {

    override var currentlyBlocks: Boolean = true
    override var canBeStarted: Boolean = true
    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    private val canOnlyBeStartedOnce: Boolean = onj.get<Boolean>("canOnlyBeStartedOnce")

    val npc: String = onj.get<String>("npc")
    val npcDisplayName: String = MapManager.displayName(npc)

    override val descriptionText: String = ""
    override val displayName: String = "Talk with $npcDisplayName"
    override val buttonText: String = "Talk"

    init {
        setStandardValuesFromConfig(onj)
    }

    override fun start() {
        MapManager.changeToDialogScreen(this)
    }

    fun completed() {
        currentlyBlocks = false
        if (canOnlyBeStartedOnce) canBeStarted = false
        isCompleted = true
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("NPCMapEvent")
        includeStandardConfig()
        "npc" with npc
        "canOnlyBeStartedOnce" with canOnlyBeStartedOnce
    }
}

/**
 * event that opens a shop where the player can buy up to 8 cards
 * @param type which type the restrictions are
 */
class ShopMapEvent(
    val types: Set<String>,
    val person: String,
    val seed: Long,
    val boughtIndices: MutableSet<Int>
) : MapEvent() {

    override var currentlyBlocks: Boolean = false
    override var canBeStarted: Boolean = true
    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val descriptionText: String = ""
    override val displayName: String = "Shop"
    override val buttonText: String = "Enter"

    override fun start() {
        MapManager.changeToShopScreen(this)
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("ShopMapEvent")
        ("types" with types)
        ("person" with person)
        ("seed" with seed)
        ("boughtIndices" with boughtIndices)
    }
}

/**
 * event that opens a shop where the player can buy up to 8 cards
 * @param types which type the restrictions are
 */
class ChooseCardMapEvent(
    onj: OnjObject
) : MapEvent(), ChooseCardScreenContext, Completable {

    override var currentlyBlocks: Boolean = false
    override var canBeStarted: Boolean = true
    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val types: List<String> = onj.get<OnjArray>("types").value.map { (it as OnjString).value }
    override val seed: Long = onj.get<Long?>("seed") ?: (Math.random() * 1000).toLong()
    override val nbrOfCards: Int = onj.get<Long>("nbrOfCards").toInt()

    override val forwardToScreen: String = MapManager.mapScreenPath

    override val descriptionText: String =
        if (nbrOfCards > 1) "You can choose one of $nbrOfCards cards." else "You get a card."
    override val displayName: String = "Ominous person"

    init {
        setStandardValuesFromConfig(onj)
    }

    override fun start() {
        MapManager.changeToChooseCardScreen(this)
    }

    override fun completed() {
        isCompleted = true
        currentlyBlocks = false
        canBeStarted = false
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("ChooseCardMapEvent")
        includeStandardConfig()
        ("types" with types)
        ("seed" with seed)
        ("nbrOfCards" with nbrOfCards)
    }
}


class HealOrMaxHPMapEvent(
    onj: OnjObject
) : MapEvent(), ScaledByDistance, Completable {

    val seed: Long = onj.get<Long?>("seed") ?: (Math.random() * 1000).toLong()
    val healthRange: IntRange = onj.get<OnjArray>("healRange").toIntRange()
    val maxHPRange: IntRange = onj.get<OnjArray>("maxHPRange").toIntRange()

    override var currentlyBlocks: Boolean = false
    override var canBeStarted: Boolean = true
    override var isCompleted: Boolean = false
    override var distanceToEnd = -1

    override val displayDescription: Boolean = true

    override val descriptionText: String = "You can choose to either heal yourself or obtain a higher Max HP."
    override val displayName: String = "Restoration Point"

    override fun start() {
        MapManager.changeToHealOrMaxHPScreen(this)
    }

    init {
        setDistanceFromConfig(onj)
        setStandardValuesFromConfig(onj)
    }

    override fun completed() {
        isCompleted = true
        canBeStarted = false
        currentlyBlocks = false
        MapManager.changeToMapScreen()
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("HealOrMaxHPEvent")
        includeStandardConfig()
        includeDistanceFromEnd()
        "seed" with seed
        "healRange" with arrayOf(healthRange.first, healthRange.last)
        "maxHPRange" with arrayOf(maxHPRange.first, maxHPRange.last)
    }
}

class AddMaxHPMapEvent(
    onj: OnjObject
) : MapEvent(), Completable {

    val seed: Long = onj.get<Long?>("seed") ?: (Math.random() * 1000).toLong()
    val maxHPRange: IntRange = onj.get<OnjArray>("maxHPRange").toIntRange()

    override var currentlyBlocks: Boolean = false
    override var canBeStarted: Boolean = true
    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val descriptionText: String = "You obtain higher Max HP."
    override val displayName: String = "Restoration Point"

    override fun start() {
        MapManager.changeToAddMaxHPScreen(this)
    }

    init {
        setStandardValuesFromConfig(onj)
    }

    override fun completed() {
        isCompleted = true
        canBeStarted = false
        MapManager.changeToMapScreen()
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("AddMaxHPEvent")
        includeStandardConfig()
        "seed" with seed
        "maxHPRange" with arrayOf(maxHPRange.first, maxHPRange.last)
    }
}

class FinishTutorialMapEvent(
    onj: OnjObject,
) : MapEvent() {

    override var currentlyBlocks: Boolean = false
    override var canBeStarted: Boolean = true
    override var isCompleted: Boolean = false
    override val displayDescription: Boolean = true

    override val displayName: String = "Exit"
    override val descriptionText: String = "Start your Journey"

    private val goToMap: String = onj.get<String>("goToMap")

    override fun start() {
        SaveState.playerLives = SaveState.maxPlayerLives
        SaveState.write()
        PermaSaveState.playerHasCompletedTutorial = true
        PermaSaveState.write()
        MapManager.changeToMap(goToMap)
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("FinishTutorialMapEvent")
        "goToMap" with goToMap
    }

}
