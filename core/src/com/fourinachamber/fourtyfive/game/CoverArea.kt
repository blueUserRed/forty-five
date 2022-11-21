package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.screen.*
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2
import ktx.actors.onClick
import onj.OnjNamedObject
import java.lang.Float.max


class CoverArea(
    val numStacks: Int,
    val maxCards: Int,
    detailFont: BitmapFont,
    detailFontColor: Color,
    stackBackgroundTexture: TextureRegion,
    detailFontScale: Float,
    private val stackSpacing: Float,
    private val areaSpacing: Float,
    private val cardScale: Float,
    private val stackMinSize: Float
) : Widget(), InitialiseableActor {

    var slotDropConfig: Pair<DragAndDrop, OnjNamedObject>? = null
    private var isInitialised: Boolean = false
    private lateinit var screenDataProvider: ScreenDataProvider

    private val stacks: Array<CoverStack> = Array(numStacks) {
        CoverStack(
            maxCards,
            this,
            detailFont,
            detailFontColor,
            stackBackgroundTexture,
            detailFontScale,
            stackSpacing,
            cardScale,
            stackMinSize,
            it
        )
    }

    override fun init(screenDataProvider: ScreenDataProvider) {
        this.screenDataProvider = screenDataProvider
    }

    fun damage(damage: Int): Int {
        for (stack in stacks) {
            if (stack.isActive) return stack.damage(damage)
        }
        return damage
    }

    /**
     * activates a stack and deactivates all others
     */
    fun makeActive(stackNum: Int) {
        for (stack in stacks) {
            if (stack.num == stackNum) {
                if (stack.isActive) break // stack was already active
                stack.isActive = true
            } else {
                if (stack.isActive) stack.isActive = false
            }
        }
    }

    /**
     * adds a new cover to the area, in a specific slot
     */
    fun addCover(card: Card, slot: Int): Boolean {
        if (slot !in stacks.indices) throw RuntimeException("slot $slot is out of bounds for coverArea")
        if (stacks[slot].numCards >= maxCards) return false
        stacks[slot].addCard(card)
        card.isDraggable = false
        return true
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (!isInitialised) {
            initialise()
            isInitialised = true
        }

        var contentHeight = 0f
        for (stack in stacks) contentHeight += stack.height
        contentHeight += areaSpacing * stacks.size - areaSpacing

        var (curX, curY) = localToStageCoordinates(Vector2(0f, 0f))
        curY += height / 2
        curY += contentHeight / 2

        for (stack in stacks) {
            curY -= stack.height
            stack.width = max(stack.prefWidth, stack.minWidth)
            stack.height = stack.prefHeight
            stack.setPosition(curX + width / 2 - stack.width / 2, curY)
            curY -= areaSpacing
        }
    }

    private fun initialise() {
        var isFirst = true
        for (stack in stacks) {
            val (dragAndDrop, dropOnj) = slotDropConfig!!
            val dropBehaviour = DragAndDropBehaviourFactory.dropBehaviourOrError(
                dropOnj.name,
                dragAndDrop,
                screenDataProvider,
                stack,
                dropOnj
            )
            if (isFirst) {
                stack.isActive = true
                isFirst = false
            }
            dragAndDrop.addTarget(dropBehaviour)
            stack.init(screenDataProvider)
            screenDataProvider.addActorToRoot(stack)
            stack.parentWidth = width
        }
    }

}

class CoverStack(
    val maxCards: Int,
    private val coverArea: CoverArea,
    private val detailFont: BitmapFont,
    private val detailFontColor: Color,
    private val backgroundTexture: TextureRegion,
    private val detailFontScale: Float,
    private val spacing: Float,
    private val cardScale: Float,
    private val minSize: Float,
    val num: Int
) : CustomHorizontalGroup(), ZIndexActor, InitialiseableActor {

    var baseHealth: Int = 0
        private set

    var currentHealth: Int = 0
        private set

    private lateinit var screenDataProvider: ScreenDataProvider
    private val cards: MutableList<Card> = mutableListOf()
    var detailText: CustomLabel = CustomLabel("", Label.LabelStyle(detailFont, detailFontColor))

    var parentWidth: Float = Float.MAX_VALUE

    val numCards: Int
        get() = cards.size

    var isActive: Boolean = false
        set(value) {
            field = value
            updateText()
        }

    init {
        detailText.setFontScale(detailFontScale)
        updateText()
        addActor(detailText)
        background = TextureRegionDrawable(backgroundTexture)
        align(Align.left)
        space(spacing)
        onClick {
            coverArea.makeActive(num)
        }
    }

    fun damage(damage: Int): Int {
        currentHealth -= damage
        if (currentHealth > 0) return 0
        val remaining = -currentHealth
        destroy()
        return remaining
    }

    fun destroy() {
        currentHealth = 0
        for (card in cards) removeActor(card.actor)
        cards.clear()
        updateText()
    }

    override fun init(screenDataProvider: ScreenDataProvider) {
        this.screenDataProvider = screenDataProvider
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
    }

    fun addCard(card: Card) {
        if (cards.size >= maxCards) {
            throw RuntimeException("cannot add another cover because max stack size is $maxCards")
        }
        cards.add(card)
        card.actor.setScale(cardScale)
        // this workaround is needed because HorizontalGroup doesn't consider scaling when calculating the preferred
        // dimensions, causing the layout to break
        card.actor.reportDimensionsWithScaling = true
        card.actor.ignoreScaling = true
        baseHealth += card.coverValue
        currentHealth += card.coverValue
        updateText()
        screenDataProvider.removeActorFromRoot(card.actor)
        addActor(card.actor)
    }

    fun updateText() {
        detailText.setText("${if (isActive) "active" else "not active"}\n${currentHealth}/${baseHealth}")
    }

    override fun getMinWidth(): Float {
        return minSize
    }
}
