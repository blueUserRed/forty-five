package com.fourinachamber.fortyfive.screen.gameWidgets

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.scenes.scene2d.Group
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.fourinachamber.fortyfive.game.StatusEffect
import com.fourinachamber.fortyfive.screen.general.*

// TODO: rewrite for new system

interface StatusEffectDisplay {

    val font: BitmapFont
    val fontColor: Color
    val fontScale: Float
    val iconScale: Float

    val actor: Group
    val screen: OnjScreen

    val effects: MutableList<Triple<StatusEffect, CustomHorizontalGroup, CustomLabel>>

    fun updateStatusEffects() {
        effects.forEach { (effect, _, label) -> label.setText(effect.getDisplayText()) }
    }

    /**
     * adds a new status effect that will be displayed
     */
    fun displayEffect(effect: StatusEffect) {
        val remainingLabel = CustomLabel(screen, effect.getDisplayText(), Label.LabelStyle(font, fontColor), true)
        remainingLabel.setFontScale(fontScale)
//        effect.icon.scaleX *= iconScale
//        effect.icon.scaleY *= iconScale

//        effect.icon.detailWidget = DetailWidget.SimpleBigDetailActor(
//            screen,
//            DetailDescriptionHandler.allTextEffects.value.map {
//                AdvancedTextParser.AdvancedTextEffect.getFromOnj(it as OnjNamedObject)}){
//            DetailDescriptionHandler.descriptions[effect.name]?.second ?: run {
//                FortyFiveLogger.warn("StatusEffectDisplay", "No description for effect ${effect.name}")
//                ""
//            }
//        }
//        val group = CustomHorizontalGroup(screen)
//        group.addActor(effect.icon)
//        group.addActor(remainingLabel)
//        actor.addActor(group)
//        effects.add(Triple(effect, group, remainingLabel))
    }

    /**
     * removes a status effect
     */
    fun removeEffect(effect: StatusEffect) {
        val iterator = effects.iterator()
        while (iterator.hasNext()) {
            val (effectToTest, group) = iterator.next()
            if (effect !== effectToTest) continue
            actor.removeActor(group)
            iterator.remove()
            break
        }
    }

}

class HorizontalStatusEffectDisplay(
    screen: OnjScreen,
    override val font: BitmapFont,
    override val fontColor: Color,
    override val fontScale: Float,
    override val iconScale: Float = 1f,
) : CustomHorizontalGroup(screen), StatusEffectDisplay {

    override val actor: Group = this

    override val effects: MutableList<Triple<StatusEffect, CustomHorizontalGroup, CustomLabel>> = mutableListOf()

    override fun draw(batch: Batch?, parentAlpha: Float) {
        updateStatusEffects()
        super.draw(batch, parentAlpha)
    }
}


class VerticalStatusEffectDisplay(
    screen: OnjScreen,
    override val font: BitmapFont,
    override val fontColor: Color,
    override val fontScale: Float,
    override val iconScale: Float = 1f,
) : CustomVerticalGroup(screen), StatusEffectDisplay {

    override val actor: Group = this

    override val effects: MutableList<Triple<StatusEffect, CustomHorizontalGroup, CustomLabel>> = mutableListOf()

    override fun draw(batch: Batch?, parentAlpha: Float) {
        updateStatusEffects()
        super.draw(batch, parentAlpha)
    }
}
