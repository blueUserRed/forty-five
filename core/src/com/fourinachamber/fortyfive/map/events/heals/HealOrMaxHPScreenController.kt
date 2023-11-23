package com.fourinachamber.fortyfive.map.events.heals


import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.map.detailMap.HealOrMaxHPMapEvent
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.TemplateString
import onj.value.OnjObject
import kotlin.math.min
import kotlin.random.Random

class HealOrMaxHPScreenController(onj: OnjObject) : ScreenController() {
    private var context: HealOrMaxHPMapEvent? = null

    private var tarekHealChosenGeorgWidgetName: String = onj.get<String>("addLifeActorName")

    private lateinit var amount: Pair<Int, Int>

    private lateinit var screen: OnjScreen
    override fun init(onjScreen: OnjScreen, context: Any?) {
        screen = onjScreen
        if (context !is HealOrMaxHPMapEvent) throw RuntimeException("context for ${this.javaClass.simpleName} must be a ChooseCardMapEvent")
        val rnd = Random(context.seed)
        this.context = context
        amount = context.healthRange.random(rnd) to context.maxHPRange.random(rnd)
        TemplateString.updateGlobalParam(
            "map.curEvent.heal.lives_new",
            min(SaveState.playerLives + amount.first, SaveState.maxPlayerLives)
        )
        TemplateString.updateGlobalParam("map.curEvent.maxHP.lives_new", SaveState.playerLives + amount.second)
        TemplateString.updateGlobalParam("map.curEvent.maxHP.maxLives_new", SaveState.maxPlayerLives + amount.second)
        TemplateString.updateGlobalParam("map.curEvent.maxHP.distanceToEnd", this.context?.distanceToEnd)
        TemplateString.updateGlobalParam("map.curEvent.heal.amount", amount.first)
        TemplateString.updateGlobalParam("map.curEvent.maxHP.amount", amount.second)
    }

    /**
     * gets called from the accept button, only if he is in the correct state ("valid")
     */
    fun complete() {
        if ((screen.namedActorOrError(tarekHealChosenGeorgWidgetName) as CustomFlexBox).inActorState("selected")) {
            val newLives = min(SaveState.playerLives + amount.first, SaveState.maxPlayerLives)
            FortyFiveLogger.debug(logTag, "Lives healed from ${SaveState.playerLives} to $newLives!")
            SaveState.playerLives = newLives
        } else {
            SaveState.playerLives += amount.second
            FortyFiveLogger.debug(
                logTag,
                "Max lives increased from ${SaveState.maxPlayerLives} to ${SaveState.maxPlayerLives + amount.second}!"
            )
            SaveState.maxPlayerLives += amount.second
        }
        context?.complete()
    }

    companion object {
        var logTag: String = "HealOrMaxHPScreenController"
    }
}