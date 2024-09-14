package com.fourinachamber.fortyfive.game.controller

import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.rendering.GameRenderPipeline
import com.fourinachamber.fortyfive.screen.gameWidgets.CardHand
import com.fourinachamber.fortyfive.screen.gameWidgets.Revolver
import com.fourinachamber.fortyfive.screen.general.Inject
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ScreenController

class NewGameController(val screen: OnjScreen) : ScreenController() {

    @Inject private lateinit var revolver: Revolver
    @Inject private lateinit var cardHand: CardHand

    private lateinit var context: EncounterContext

    override fun init(context: Any?) {
        if (context !is EncounterContext) {
            throw RuntimeException("GameScreen needs a context of type encounterMapEvent")
        }
        this.context = context
//        FortyFive.currentGame = this
    }

    override fun onShow() {
        FortyFive.useRenderPipeline(GameRenderPipeline(screen))
    }

    override fun update() {
        super.update()
    }

    override fun end() {
        super.end()
    }
}
