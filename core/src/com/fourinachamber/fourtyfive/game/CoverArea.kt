package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.screen.*

class CoverStack(
    val maxCards: Int,
    val detailFont: BitmapFont,
    val detailFontColor: Color,
    val backgroundTexture: TextureRegion
) : Widget(), ZIndexActor, ZIndexGroup {

    override var fixedZIndex: Int = 0

    var baseHealth: Int = 0
        private set

    var currentHealth: Int = 0
        private set

    private val cards: MutableList<Card> = mutableListOf()

    private var hBox = CustomHorizontalGroup()
//    private var detailActor = CustomHorizontalGroup()
    private var detailText: CustomLabel = CustomLabel("", Label.LabelStyle(detailFont, detailFontColor))

    var isActive: Boolean = false
        set(value) {
            field = value
            updateText()
        }

    init {
        hBox.addActor(detailText)
        updateText()
        hBox.background = TextureRegionDrawable(backgroundTexture)
    }

    fun addCard(card: Card) {
        if (cards.size >= maxCards) {
            throw RuntimeException("cannot add another cover because max stack size is $maxCards")
        }
        cards.add(card)
        baseHealth += card.coverValue
        currentHealth += card.coverValue
        updateText()
    }

    fun updateText() {
        detailText.setText("${if (isActive) "active" else "not active"}\n${currentHealth}/${baseHealth}")
    }

    override fun resortZIndices() {
        hBox.resortZIndices()
    }

    override fun getWidth(): Float {
        return hBox.width
    }

    override fun getHeight(): Float {
        return hBox.height
    }

    override fun getPrefWidth(): Float {
        return hBox.prefWidth
    }

    override fun getPrefHeight(): Float {
        return hBox.prefWidth
    }
}
