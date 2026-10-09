package com.microwavestudios.fortyfive.onjNamespaces

import com.microwavestudios.fortyfive.map.events.specialevent.SpecialEventAction
import com.microwavestudios.fortyfive.map.events.specialevent.SpecialEventActions
import com.microwavestudios.fortyfive.map.events.specialevent.SpecialEventCondition
import com.microwavestudios.fortyfive.map.events.specialevent.SpecialEventConditions
import onj.customization.Namespace.OnjNamespace
import onj.customization.Namespace.OnjNamespaceDatatypes
import onj.customization.OnjFunction.RegisterOnjFunction
import onj.value.OnjArray
import onj.value.OnjFloat
import onj.value.OnjInt
import onj.value.OnjString
import onj.value.OnjValue
import kotlin.reflect.KClass

@Suppress("unused") // values and functions are read via reflection
@OnjNamespace
object SpecialEventNamespace {

    @OnjNamespaceDatatypes
    val datatypes: Map<String, KClass<*>> = mapOf(
        "SpecialEventAction" to OnjSpecialEventAction::class,
        "SpecialEventCondition" to OnjSpecialEventCondition::class,
    )

    @RegisterOnjFunction(schema = "params: [int]")
    fun damagePlayer(damage: OnjInt): OnjSpecialEventAction = OnjSpecialEventAction(
        SpecialEventActions.damagePlayer(damage.value.toInt())
    )

    @RegisterOnjFunction(schema = "params: [int]")
    fun healPlayer(health: OnjInt): OnjSpecialEventAction = OnjSpecialEventAction(
        SpecialEventActions.healPlayer(health.value.toInt())
    )

    @RegisterOnjFunction(schema = "params: [int]")
    fun useSteps(steps: OnjInt): OnjSpecialEventAction = OnjSpecialEventAction(
        SpecialEventActions.useSteps(steps.value.toInt())
    )

    @RegisterOnjFunction(schema = "params: []")
    fun getRandomCard(): OnjSpecialEventAction = OnjSpecialEventAction(
        SpecialEventActions.getRandomCard()
    )

    @RegisterOnjFunction(schema = "params: [string]")
    fun getTalisman(talisman: OnjString): OnjSpecialEventAction = OnjSpecialEventAction(
        SpecialEventActions.getTalisman(talisman.value)
    )

    @RegisterOnjFunction(schema = "params: [string, string[]]")
    fun fightForTalisman(talisman: OnjString, enemyGroups: OnjArray): OnjSpecialEventAction {
        val enemyGroups = enemyGroups.value.map { it.value as String }
        require(enemyGroups.size in 1..3) { "encounter can only have 1 to 3 enemies" }
        val action = SpecialEventActions.fightForTalisman(talisman.value, enemyGroups)
        return OnjSpecialEventAction(action)
    }

    @RegisterOnjFunction(schema = "params: [int]")
    fun healOrDamagePlayer(amount: OnjInt): OnjSpecialEventAction = OnjSpecialEventAction(
        SpecialEventActions.healOrDamagePlayer(amount.value.toInt())
    )

    @RegisterOnjFunction(schema = "params: [float]")
    fun boostTalismanRewardChance(amount: OnjFloat): OnjSpecialEventAction = OnjSpecialEventAction(
        SpecialEventActions.boostTalismanRewardChance(amount.value)
    )

    @RegisterOnjFunction(schema = "params: []")
    fun putPlayerOnRandomNode(): OnjSpecialEventAction = OnjSpecialEventAction(
        SpecialEventActions.putPlayerOnRandomNode()
    )

    @RegisterOnjFunction(schema = "params: [int]")
    fun putPlayerOnNodeWithDistance(distance: OnjInt): OnjSpecialEventAction = OnjSpecialEventAction(
        SpecialEventActions.putPlayerOnNodeWithDistance(distance.value.toInt())
    )

    @RegisterOnjFunction(schema = "params: [string]")
    fun playerHasTalisman(talisman: OnjString): OnjSpecialEventCondition = OnjSpecialEventCondition(
        SpecialEventConditions.playerHasTalisman(talisman.value)
    )

    @RegisterOnjFunction(schema = "params: [string]")
    fun inBiome(biome: OnjString): OnjSpecialEventCondition = OnjSpecialEventCondition(
        SpecialEventConditions.inBiome(biome.value)
    )

    @RegisterOnjFunction(schema = "use SpecialEvent; params: [SpecialEventCondition]")
    fun not(condition: OnjSpecialEventCondition): OnjSpecialEventCondition = OnjSpecialEventCondition(
        SpecialEventConditions.not(condition.value)
    )

}


class OnjSpecialEventAction(override val value: SpecialEventAction) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'SpecialEventAction'")
    }
}

class OnjSpecialEventCondition(override val value: SpecialEventCondition) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'SpecialEventAction'")
    }
}
