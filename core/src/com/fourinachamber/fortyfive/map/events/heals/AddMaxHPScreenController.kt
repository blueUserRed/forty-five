package com.fourinachamber.fortyfive.map.events.heals


import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.map.MapManager
import com.fourinachamber.fortyfive.map.detailMap.AddMaxHPMapEvent
import com.fourinachamber.fortyfive.map.detailMap.Completable
import com.fourinachamber.fortyfive.map.detailMap.HealOrMaxHPMapEvent
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.TemplateString
import onj.value.OnjObject
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class AddMaxHPScreenController(onj: OnjObject) : ScreenController(), Completable {
    private var context: AddMaxHPMapEvent? = null

    private var amount: Int = -1

    private lateinit var screen: OnjScreen
    override fun init(onjScreen: OnjScreen, context: Any?) {
        screen = onjScreen
        if (context !is AddMaxHPMapEvent) throw RuntimeException("context for ${this.javaClass.simpleName} must be a AddMaxHPMapEvent")
        val rnd = Random(context.seed)
        this.context = context
        amount = context.maxHPRange.random(rnd)

        TemplateString.updateGlobalParam("map.cur_event.max_hp.lives_new", SaveState.playerLives + amount)
        TemplateString.updateGlobalParam("map.cur_event.max_hp.maxLives_new", SaveState.maxPlayerLives + amount)
        TemplateString.updateGlobalParam("map.cur_event.max_hp.amount", amount)
    }

    /**
     * gets called from the accept button, only if he is in the correct state ("valid")
     */
    override fun completed() {
        SoundPlayer.situation("heal", screen)
        FortyFiveLogger.debug(
            logTag,
            "Max lives increased from ${SaveState.maxPlayerLives} to ${SaveState.maxPlayerLives + amount}!"
        )
        SaveState.maxPlayerLives += amount
        SaveState.playerLives += amount
        context?.completed()
    }

    companion object {
        var logTag: String = "AddMaxHPScreenController"
    }
}