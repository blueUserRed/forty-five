package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.screen.*
import java.lang.Float.max
import javax.swing.GroupLayout.Alignment


class CoverArea(
    val numStacks: Int,
    maxCards: Int,
    detailFont: BitmapFont,
    detailFontColor: Color,
    stackBackgroundTexture: TextureRegion,
    detailFontScale: Float
) : CustomVerticalGroup() {

    private val stacks: Array<CoverStack> = Array(numStacks) {
        CoverStack(maxCards, detailFont, detailFontColor, stackBackgroundTexture, detailFontScale, 1f)
    }

    init {
        for (stack in stacks) addActor(stack)
        align(Align.center)
        space(5f)
    }

}

class CoverStack(
    val maxCards: Int,
    val detailFont: BitmapFont,
    val detailFontColor: Color,
    val backgroundTexture: TextureRegion,
    val detailFontScale: Float,
    val minSize: Float
) : CustomHorizontalGroup() {

    var baseHealth: Int = 0
        private set

    var currentHealth: Int = 0
        private set

    private val cards: MutableList<Card> = mutableListOf()
    private var detailText: CustomLabel = CustomLabel("", Label.LabelStyle(detailFont, detailFontColor))

    var isActive: Boolean = false
        set(value) {
            field = value
            updateText()
        }

    init {
        addActor(detailText)
        debug = true
        updateText()
        background = TextureRegionDrawable(backgroundTexture)
        detailText.setFontScale(detailFontScale)
    }

    fun addCard(card: Card) {
        if (cards.size >= maxCards) {
            throw RuntimeException("cannot add another cover because max stack size is $maxCards")
        }
        cards.add(card)
        baseHealth += card.coverValue
        currentHealth += card.coverValue
        updateText()
        addActor(card.actor)
    }

    fun updateText() {
        detailText.setText("${if (isActive) "active" else "not active"}\n${currentHealth}/${baseHealth}")
    }

}
