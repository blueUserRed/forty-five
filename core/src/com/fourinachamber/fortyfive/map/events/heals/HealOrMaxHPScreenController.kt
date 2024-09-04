package com.fourinachamber.fortyfive.map.events.heals


import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.Completable
import com.fourinachamber.fortyfive.map.detailMap.HealOrMaxHPMapEvent
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.screen.general.customActor.BackgroundActor
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.TemplateString
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class HealOrMaxHPScreenController(private val screen: OnjScreen, healChosenName: String) : ScreenController(), Completable {

    private var context: HealOrMaxHPMapEvent? = null

    private var healWidgetName: String = healChosenName

    private lateinit var amount: Pair<Int, Int>

    override fun init(context: Any?) {
        if (context !is HealOrMaxHPMapEvent) throw RuntimeException("context for ${this.javaClass.simpleName} must be a ChooseCardMapEvent")
        val rnd = Random(context.seed)
        this.context = context
        amount = context.healthRange.random(rnd) to context.maxHPRange.random(rnd)
        TemplateString.updateGlobalParam(
            "map.cur_event.heal.lives_new",
            min(SaveState.playerLives + amount.first, SaveState.maxPlayerLives)
        )
        TemplateString.updateGlobalParam("map.cur_event.max_hp.lives_new", SaveState.playerLives + amount.second)
        TemplateString.updateGlobalParam("map.cur_event.max_hp.maxLives_new", SaveState.maxPlayerLives + amount.second)
        TemplateString.updateGlobalParam("map.cur_event.max_hp.distanceToEnd",
            if (MapManager.currentDetailMap.isArea){
                "You are in a safe Area"
            } else {
                "next safe Point in: ${max(context.distanceToEnd, 0)} events"
            }
        )
        TemplateString.updateGlobalParam("map.cur_event.heal.amount", amount.first)
        TemplateString.updateGlobalParam("map.cur_event.max_hp.amount", amount.second)
    }

    /**
     * gets called from the accept button, only if he is in the correct state ("valid")
     */
    override fun completed() {
        SoundPlayer.situation("heal", screen)
        if ((screen.namedActorOrError(healWidgetName) as BackgroundActor).backgroundHandle?.contains("selected") == true) {
            val newLives = min(SaveState.playerLives + amount.first, SaveState.maxPlayerLives)
            FortyFiveLogger.debug(logTag, "Lives healed from ${SaveState.playerLives} to $newLives!")
            SaveState.playerLives = newLives
        } else {
            FortyFiveLogger.debug(
                logTag,
                "Max lives increased from ${SaveState.maxPlayerLives} to ${SaveState.maxPlayerLives + amount.second}!"
            )
            SaveState.maxPlayerLives += amount.second
            SaveState.playerLives += amount.second
        }
        context?.completed()
    }

    companion object {
        var logTag: String = "HealOrMaxHPScreenController"
    }
}