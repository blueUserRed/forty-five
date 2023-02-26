package com.fourinachamber.fourtyfive.map.detailMap

import onj.builder.buildOnjObject
import onj.value.OnjNamedObject
import onj.value.OnjObject

object MapEventFactory {

    private var mapEventCreators: Map<String, (onj: OnjObject) -> MapEvent> = mapOf(
        "EmptyMapEvent" to { EmptyMapEvent() }
    )

    fun getMapEvent(onj: OnjNamedObject): MapEvent =
        mapEventCreators[onj.name]?.invoke(onj) ?: throw RuntimeException("unknown map event ${onj.name}")

}

abstract class MapEvent {

    abstract val currentlyBlocks: Boolean
    abstract val canBeStarted: Boolean

    abstract fun start()

    abstract fun asOnjObject(): OnjObject

}

class EmptyMapEvent : MapEvent() {

    override val currentlyBlocks: Boolean = false
    override val canBeStarted: Boolean = false

    override fun start() { }

    override fun asOnjObject(): OnjObject = buildOnjObject {
        name("EmptyMapEvent")
    }
}
