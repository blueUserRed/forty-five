package com.microwavestudios.fortyfive.onjNamespaces

import com.microwavestudios.fortyfive.map.events.specialevent.SpecialEventAction
import com.microwavestudios.fortyfive.map.events.specialevent.SpecialEventActions
import onj.customization.Namespace.OnjNamespace
import onj.customization.Namespace.OnjNamespaceDatatypes
import onj.customization.OnjFunction.RegisterOnjFunction
import onj.value.OnjInt
import onj.value.OnjValue
import kotlin.reflect.KClass

@Suppress("unused") // values and functions are read via reflection
@OnjNamespace
object SpecialEventNamespace {

    @OnjNamespaceDatatypes
    val datatypes: Map<String, KClass<*>> = mapOf(
        "SpecialEventAction" to OnjSpecialEventAction::class,
    )

    @RegisterOnjFunction(schema = "params: [int]")
    fun damagePlayer(damage: OnjInt): OnjSpecialEventAction = OnjSpecialEventAction(
        SpecialEventActions.damagePlayer(damage.value.toInt())
    )

    @RegisterOnjFunction(schema = "params: [int]")
    fun healPlayer(health: OnjInt): OnjSpecialEventAction = OnjSpecialEventAction(
        SpecialEventActions.healPlayer(health.value.toInt())
    )

    @RegisterOnjFunction(schema = "params: []")
    fun getRandomCard(): OnjSpecialEventAction = OnjSpecialEventAction(
        SpecialEventActions.getRandomCard()
    )

    @RegisterOnjFunction(schema = "params: [int]")
    fun healOrDamagePlayer(amount: OnjInt): OnjSpecialEventAction = OnjSpecialEventAction(
        SpecialEventActions.healOrDamagePlayer(amount.value.toInt())
    )

}


class OnjSpecialEventAction(override val value: SpecialEventAction) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'SpecialEventAction'")
    }

}
