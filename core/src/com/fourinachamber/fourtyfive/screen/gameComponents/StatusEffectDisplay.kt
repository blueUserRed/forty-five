package com.fourinachamber.fourtyfive.screen.gameComponents

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.fourinachamber.fourtyfive.game.StatusEffect
import com.fourinachamber.fourtyfive.screen.general.CustomHorizontalGroup
import com.fourinachamber.fourtyfive.screen.general.CustomLabel
import com.fourinachamber.fourtyfive.screen.general.OnjScreen

/**
 * used for displaying status effects
 */
class StatusEffectDisplay(
    private val screen: OnjScreen,
    private val font: BitmapFont,
    private val fontColor: Color,
    private val fontScale: Float
) : CustomHorizontalGroup(screen) {

    private val effects: MutableList<Pair<StatusEffect, CustomLabel>> = mutableListOf()

    /**
     * adds a new status effect that will be displayed
     */
    fun displayEffect(effect: StatusEffect) {
        if (effect in effects.map { it.first }) return
        val remainingLabel = CustomLabel(screen, effect.remainingTurns.toString(), Label.LabelStyle(font, fontColor))
        remainingLabel.setFontScale(fontScale)
        addActor(effect.icon)
        addActor(remainingLabel)
        effects.add(effect to remainingLabel)
    }

    /**
     * removes a status effect
     */
    fun removeEffect(effect: StatusEffect) {
        val iterator = effects.iterator()
        while (iterator.hasNext()) {
            val (effectToTest, label) = iterator.next()
            if (effect !== effectToTest) continue
            removeActor(effect.icon)
            removeActor(label)
            break
        }
    }

    /**
     * update the indicators for how many turns the status effect will be active for
     */
    fun updateRemainingTurns() {
        for ((effect, label) in effects) {
            label.setText(effect.remainingTurns.toString())
        }
    }

}