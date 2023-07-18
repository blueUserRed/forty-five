package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.PopupSelectionEvent
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import ktx.actors.alpha
import ktx.actors.onClick
import kotlin.math.cos
import kotlin.math.sin

class CircularCardSelector(
    val radius: Float,
    val size: Float,
    val emptySlotTextureHandle: ResourceHandle,
    val disabledAlpha: Float,
    private val screen: OnjScreen
) : WidgetGroup(), StyledActor {

    override var styleManager: StyleManager? = null
    override var isHoveredOver: Boolean = false

    private val cardActors: Array<CustomImageActor> = Array(5) {
        val actor = CustomImageActor(null, screen, true)
        val angle = angleForIndex(it)
        val dx = cos(angle) * radius
        val dy = sin(angle) * radius
        actor.setBounds(
            x + width / 2 + dx.toFloat() + size * 1.5f,
            y + height / 2 + dy.toFloat() + size,
            size, size
        )
        actor
    }

    private var excludeIndices: MutableList<Int> = mutableListOf()

    private val emptySlotTexture: Drawable by lazy {
        ResourceManager.get(screen, emptySlotTextureHandle)
    }

    init {
        bindHoverStateListeners(this)
        cardActors.forEachIndexed { index, card ->
            addActor(card)
            card.onClick {// TODO: onButtonClick
                if (index in excludeIndices) return@onClick
                fire(PopupSelectionEvent(index))
            }
        }
    }

    fun setTo(revolver: Revolver, exclude: Card? = null) {
        excludeIndices.clear()
        revolver.slots.forEachIndexed { index, slot ->
            val card = slot.card
            if (card == exclude || card == null) {
                excludeIndices.add(index)
                cardActors[index].alpha = disabledAlpha
            } else {
                cardActors[index].alpha = 1.0f
            }
            cardActors[index].drawable = if (card != null) {
                TextureRegionDrawable(TextureRegion(card.actor.pixmapTexture!!))
            } else {
                emptySlotTexture
            }
        }
    }

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }

    private fun angleForIndex(i: Int): Double = ((2 * Math.PI) / 5) * i + (Math.PI / 2) + ((2 * Math.PI) / 5)

    override fun getPrefWidth(): Float = radius * 2

    override fun getPrefHeight(): Float = radius * 2
}
