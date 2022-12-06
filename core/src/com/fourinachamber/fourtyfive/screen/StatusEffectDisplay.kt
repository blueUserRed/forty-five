package com.fourinachamber.fourtyfive.screen

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.fourinachamber.fourtyfive.game.StatusEffect

class StatusEffectDisplay(
    private val font: BitmapFont,
    private val fontColor: Color,
    private val fontScale: Float
) : CustomHorizontalGroup() {

    private val effects: MutableList<Pair<StatusEffect, CustomLabel>> = mutableListOf()

    fun displayEffect(effect: StatusEffect) {
        if (effect !in effects.map { it.first }) {
            val remainingLabel = CustomLabel(effect.remainingTurns.toString(), Label.LabelStyle(font, fontColor))
            remainingLabel.setFontScale(fontScale)
            addActor(effect.icon)
            addActor(remainingLabel)
            effects.add(effect to remainingLabel)
        }
    }

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

    fun updateRemainingTurns() {
        for ((effect, label) in effects) {
            label.setText(effect.remainingTurns.toString())
        }
    }

}