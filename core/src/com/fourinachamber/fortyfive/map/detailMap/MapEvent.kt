package com.fourinachamber.fortyfive.map.detailMap

import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.map.MapManager
import onj.builder.OnjObjectBuilderDSL
import onj.builder.buildOnjObject
import onj.value.OnjNamedObject
import onj.value.OnjObject

/**
 * used for dynamically creating events
 */
object MapEventFactory {

    private var mapEventCreators: Map<String, (onj: OnjObject) -> MapEvent> = mapOf(
        "EmptyMapEvent" to { EmptyMapEvent() },
        "EncounterMapEvent" to { EncounterMapEvent(it) },
        "EnterMapMapEvent" to { EnterMapMapEvent(it.get<String>("targetMap"), it.get<Boolean>("placeAtEnd")) },
        "NPCMapEvent" to { NPCMapEvent(it.get<String>("npc")) }
    )

    fun getMapEvent(onj: OnjNamedObject): MapEvent =
        mapEventCreators[onj.name]?.invoke(onj) ?: throw RuntimeException("unknown map event ${onj.name}")

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

    override fun start() { }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("EmptyMapEvent")
    }
}

/**
 * Map Event that represents an encounter with an enemy
 */
class EncounterMapEvent(obj: OnjObject) : MapEvent() {

    override var currentlyBlocks: Boolean = true
    override var canBeStarted: Boolean = true
    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val icon: String = "normal_bullet"
    override val descriptionText: String = "Take on enemies and come out on top!"
    override val displayName: String = "Encounter"

    init {
        setStandardValuesFromConfig(obj)
    }

    override fun start() {
        // TODO: ugly
        FortyFive.changeToScreen("screens/game_screen.onj", this)
    }

    fun completed() {
        currentlyBlocks = false
        canBeStarted = false
        isCompleted = true
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("EncounterMapEvent")
        includeStandardConfig()
    }
}

/**
 * MapEvent that opens another map when started
 * @param targetMap the name of the map to be opened
 * @param placeAtEnd if true, the player is placed at last node of the map instead of the first
 */
class EnterMapMapEvent(val targetMap: String, val placeAtEnd: Boolean) : MapEvent() {

    override var currentlyBlocks: Boolean = false
    override var canBeStarted: Boolean = true
    override var isCompleted: Boolean = false
    override val displayDescription: Boolean = true

    // lazy so it doesn't crash when the event is instanced
    override val displayName: String by lazy {
        "Enter ${MapManager.displayName(targetMap)}"
    }
    override val descriptionText: String by lazy {
        "Have fun exploring ${MapManager.displayName(targetMap)}"
    }

    override fun start() {
        MapManager.switchToMap(targetMap, placeAtEnd)
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("EnterMapMapEvent")
        "targetMap" with targetMap
        "placeAtEnd" with placeAtEnd
    }

}

/**
 * event that opens a dialog box and allows talking to an NPC
 * @param npc the name of the npc
 */
class NPCMapEvent(val npc: String) : MapEvent() {

    override var currentlyBlocks: Boolean = false
    override var canBeStarted: Boolean = true
    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val descriptionText: String = "talk to $npc"
    override val displayName: String = "I just want to talk"

    override fun start() {
        FortyFive.changeToScreen("screens/dialog.onj", this) // TODO: ugly
    }

    fun complete() {
        canBeStarted = false
        isCompleted = true
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("NPCMapEvent")
        includeStandardConfig()
        "npc" with npc
    }


}
