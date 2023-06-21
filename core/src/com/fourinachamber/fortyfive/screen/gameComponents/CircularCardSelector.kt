package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.PopupSelectionEvent
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import ktx.actors.onClick
import kotlin.math.cos
import kotlin.math.sin

class CircularCardSelector(
    val radius: Float,
    val size: Float,
    private val screen: OnjScreen
) : WidgetGroup(), StyledActor {

    override var styleManager: StyleManager? = null
    override var isHoveredOver: Boolean = false

    private val cards: Array<CustomImageActor> = Array(5) {
        val actor = CustomImageActor(null, screen, true)
        val angle = angleForIndex(it)
        val dx = cos(angle) * radius
        val dy = sin(angle) * radius
        actor.setBounds(
            y + dy.toFloat() - size / 2,
            x + dx.toFloat() - size / 2,
            size, size
        )
        actor
    }

    init {
        bindHoverStateListeners(this)
        cards.forEachIndexed { index, card ->
            addActor(card)
            card.onClick { // TODO: onButtonClick
                fire(PopupSelectionEvent(index))
            }
        }
    }

    fun setTo(revolver: Revolver) {
        revolver.slots.forEachIndexed { index, slot ->
            cards[index].backgroundHandle = slot.card?.actor?.backgroundHandle
        }
    }

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }

    private fun angleForIndex(i: Int): Double = ((2 * Math.PI) / 5) * i + (Math.PI / 2) + ((2 * Math.PI) / 5)

    override fun getPrefWidth(): Float = radius

    override fun getPrefHeight(): Float = radius
}
