package com.fourinachamber.fourtyfive.map.detailMap

import com.badlogic.gdx.Gdx
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.map.MapManager
import com.fourinachamber.fourtyfive.screen.general.ScreenBuilder
import onj.builder.OnjObjectBuilderDSL
import onj.builder.buildOnjObject
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjNamedObject
import onj.value.OnjObject

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

abstract class MapEvent {

    abstract var currentlyBlocks: Boolean
        protected set
    abstract var canBeStarted: Boolean
        protected set
    abstract var isCompleted: Boolean
        protected set

    abstract val displayDescription: Boolean

    open val icon: String? = null
    open val additionalIcons: List<String> = listOf()

    open val descriptionText: String = ""
    open val displayName: String = ""

    abstract fun start()

    abstract fun asOnjObject(): OnjObject

    protected fun setStandardValuesFromConfig(config: OnjObject) {
        currentlyBlocks = config.get<Boolean>("currentlyBlocks")
        canBeStarted = config.get<Boolean>("canBeStarted")
        isCompleted = config.get<Boolean>("isCompleted")
    }

    protected fun OnjObjectBuilderDSL.includeStandardConfig() {
        "currentlyBlocks" with currentlyBlocks
        "canBeStarted" with canBeStarted
        "isCompleted" with isCompleted
    }

}

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
        FourtyFive.changeToScreen("screens/game_screen.onj", this)
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

class EnterMapMapEvent(val targetMap: String, val placeAtEnd: Boolean) : MapEvent() {

    override var currentlyBlocks: Boolean = false
    override var canBeStarted: Boolean = true
    override var isCompleted: Boolean = false
    override val displayDescription: Boolean = true

    override val displayName: String = "Enter ${MapManager.displayName(targetMap)}"
    override val descriptionText: String = "Have fun exploring ${MapManager.displayName(targetMap)}"

    override fun start() {
        MapManager.switchToMap(targetMap, placeAtEnd)
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("EnterMapMapEvent")
        "targetMap" with targetMap
        "placeAtEnd" with placeAtEnd
    }

}

class NPCMapEvent(val npc: String) : MapEvent() {

    override var currentlyBlocks: Boolean = false
    override var canBeStarted: Boolean = true
    override var isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val descriptionText: String = "talk to $npc"
    override val displayName: String = "I just want to talk"

    override fun start() {
        FourtyFive.changeToScreen("screens/dialog_test.onj", this) // TODO: ugly
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
