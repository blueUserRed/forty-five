package com.fourinachamber.fourtyfive.map.detailMap

import com.badlogic.gdx.Gdx
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.screen.general.ScreenBuilder
import onj.builder.buildOnjObject
import onj.value.OnjNamedObject
import onj.value.OnjObject

object MapEventFactory {

    private var mapEventCreators: Map<String, (onj: OnjObject) -> MapEvent> = mapOf(
        "EmptyMapEvent" to { EmptyMapEvent() },
        "EncounterMapEvent" to { EncounterMapEvent() }
    )

    fun getMapEvent(onj: OnjNamedObject): MapEvent =
        mapEventCreators[onj.name]?.invoke(onj) ?: throw RuntimeException("unknown map event ${onj.name}")

}

abstract class MapEvent {

    abstract val currentlyBlocks: Boolean
    abstract val canBeStarted: Boolean
    abstract val isCompleted: Boolean

    abstract val displayDescription: Boolean

    open val icon: String? = null
    open val additionalIcons: List<String> = listOf()

    open val descriptionText: String = ""

    abstract fun start()

    abstract fun asOnjObject(): OnjObject

}

class EmptyMapEvent : MapEvent() {

    override val currentlyBlocks: Boolean = false
    override val canBeStarted: Boolean = false
    override val isCompleted: Boolean = false

    override val displayDescription: Boolean = false

    override fun start() { }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("EmptyMapEvent")
    }
}

class EncounterMapEvent : MapEvent() {

    override var currentlyBlocks: Boolean = true
    override var canBeStarted: Boolean = true
    override val isCompleted: Boolean = false

    override val displayDescription: Boolean = true

    override val icon: String = "normal_bullet"
    override val descriptionText: String = "encounter"

    override fun start() {
        val screen = ScreenBuilder(Gdx.files.internal("screens/game_screen.onj")).build(this)
        FourtyFive.changeToScreen(screen)
    }

    fun completed() {
        currentlyBlocks = false
        canBeStarted = false
    }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("EncounterMapEvent")
    }
}
