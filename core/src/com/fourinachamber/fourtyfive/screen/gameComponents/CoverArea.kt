package com.fourinachamber.fourtyfive.screen.gameComponents

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.game.card.Card
import com.fourinachamber.fourtyfive.screen.general.*
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2
import ktx.actors.onClick
import onj.value.OnjNamedObject
import java.lang.Float.max


/**
 * The area on the screen where the coverStacks are located
 * @param numStacks the number of available stacks
 * @param maxCards the maximum amount of cards allowed per stack
 * @param onlyAllowAddingOnSameTurn if true, the stacks will be looked to the turn the first card was placed in
 */
class CoverArea(
    val numStacks: Int,
    val maxCards: Int,
    val onlyAllowAddingOnSameTurn: Boolean,
    infoFont: BitmapFont,
    infoFontColor: Color,
    stackBackgroundTexture: TextureRegion,
    infoFontScale: Float,
    private val stackSpacing: Float,
    private val areaSpacing: Float,
    private val cardScale: Float,
    private val stackMinSize: Float,
    detailFont: BitmapFont,
    detailFontColor: Color,
    detailBackground: Drawable,
    detailFontScale: Float,
    val detailOffset: Vector2,
    val detailWidth: Float
) : Widget() {

    /**
     * set by gameScreenController //TODO: find a better solution
     */
    var slotDropConfig: Pair<DragAndDrop, OnjNamedObject>? = null
    private var isInitialised: Boolean = false

    private val onjScreen: OnjScreen
        get() = FourtyFive.curScreen!!

    private val stacks: Array<CoverStack> = Array(numStacks) {
        CoverStack(
            maxCards,
            onlyAllowAddingOnSameTurn,
            this,
            infoFont,
            infoFontColor,
            null,
            infoFontScale,
            stackSpacing,
            cardScale,
            stackMinSize,
            it
        )
    }

    private val hoverDetailActor: CustomLabel =
        CustomLabel("", Label.LabelStyle(detailFont, detailFontColor), detailBackground)

    init {
        hoverDetailActor.setFontScale(detailFontScale)
        hoverDetailActor.setAlignment(Align.center)
        hoverDetailActor.isVisible = false
        hoverDetailActor.fixedZIndex = Int.MAX_VALUE
        hoverDetailActor.wrap = true
    }

    /**
     * damages the active stack
     * @return the remaining damage (that wasn't blocked by the cover)
     */
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
     * returns the active stack
     */
    fun getActive(): CoverStack? {
        for (stack in stacks) if (stack.isActive) return stack
        return null
    }

    /**
     * adds a new cover to the area, in a specific slot
     */
    fun addCover(card: Card, slot: Int, turnNum: Int) {
        if (slot !in stacks.indices) throw RuntimeException("slot $slot is out of bounds for coverArea")
        if (!stacks[slot].acceptsCard(turnNum)) throw RuntimeException("slot $slot dosen't accept cards")
        stacks[slot].addCard(card, turnNum)
        card.isDraggable = false
    }

    /**
     * checks whether a card can be added to a stack
     */
    fun acceptsCover(slot: Int, turnNum: Int): Boolean = stacks[slot].acceptsCard(turnNum)

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (!isInitialised) {
            initialise()
            isInitialised = true
            invalidateHierarchy()
        }

        var contentHeight = 0f
        for (stack in stacks) contentHeight += stack.height
        contentHeight += areaSpacing * stacks.size - areaSpacing

        var (curX, curY) = localToStageCoordinates(Vector2(0f, 0f))
        curY += height / 2
        curY += contentHeight / 2

        var isCardHoveredOver = false

        for (stack in stacks) {
            curY -= stack.height
            stack.width = max(stack.prefWidth, stack.minWidth)
            stack.height = stack.prefHeight
            if (!stack.inAnimation) stack.setPosition(curX + width / 2 - stack.width / 2, curY)
            curY -= areaSpacing

            for (card in stack.cards) if (card.actor.isHoveredOver) {
                isCardHoveredOver = true
                updateHoverDetailActor(card, stack)
            }
        }

        hoverDetailActor.isVisible = isCardHoveredOver
    }

    private fun initialise() {
        onjScreen.addActorToRoot(hoverDetailActor)
        var isFirst = true
        for (stack in stacks) {
            stack.onjScreen = onjScreen
            val (dragAndDrop, dropOnj) = slotDropConfig!!
            val dropBehaviour = DragAndDropBehaviourFactory.dropBehaviourOrError(
                dropOnj.name,
                dragAndDrop,
                onjScreen,
                stack,
                dropOnj
            )
            if (isFirst) {
                stack.isActive = true
                isFirst = false
            }
            dragAndDrop.addTarget(dropBehaviour)
            onjScreen.addActorToRoot(stack)
            stack.parentWidth = width
        }
    }

    private fun updateHoverDetailActor(card: Card, stack: CoverStack) {
        hoverDetailActor.setText(card.description)

        hoverDetailActor.width = detailWidth
        hoverDetailActor.height = hoverDetailActor.prefHeight

        val (x, y) = stack.localToStageCoordinates(Vector2(card.actor.x, card.actor.y))

        val toLeft = x + card.actor.width + detailWidth > onjScreen.stage.viewport.worldWidth

        hoverDetailActor.setPosition(
            if (toLeft) x - detailWidth else x + card.actor.width + detailOffset.x,
            y + card.actor.height / 2 - hoverDetailActor.height / 2 + detailOffset.y
        )
    }

}

/**
 * stack of cover cards
 * @see CoverArea
 */
class CoverStack(
    val maxCards: Int,
    val onlyAllowAddingOnSameTurn: Boolean,
    private val coverArea: CoverArea,
    detailFont: BitmapFont,
    detailFontColor: Color,
    backgroundTexture: TextureRegion?,
    detailFontScale: Float,
    spacing: Float,
    private val cardScale: Float,
    private val minSize: Float,
    val num: Int
) : CustomHorizontalGroup(), ZIndexActor, AnimationActor {

    override var inAnimation: Boolean = false

    lateinit var onjScreen: OnjScreen

    /**
     * the theoretical maximum health of the stack
     */
    var baseHealth: Int = 0
        private set

    /**
     * the current health of the stack
     */
    var currentHealth: Int = 0
        private set

    private val _cards: MutableList<Card> = mutableListOf()
    private var lockedTurnNum: Int? = null
    var detailText: CustomLabel = CustomLabel("", Label.LabelStyle(detailFont, detailFontColor))

    var parentWidth: Float = Float.MAX_VALUE

    /**
     * true if this is the active slot
     */
    var isActive: Boolean = false
        set(value) {
            field = value
            updateText()
        }

    /**
     * the cards in this stack
     */
    val cards: List<Card>
        get() = _cards

    init {
        detailText.setFontScale(detailFontScale)
        updateText()
        addActor(detailText)
        backgroundTexture?.let { background = TextureRegionDrawable(it) }
        align(Align.left)
        space(spacing)
        onClick {
            coverArea.makeActive(num)
        }
    }

    /**
     * damages this stack and destroys it if the health falls below zero
     * @return the remaining damage (that wasn't blocked by the cover)
     */
    fun damage(damage: Int): Int {
        currentHealth -= damage
        updateText()
        if (currentHealth > 0) return 0
        val remaining = -currentHealth
        destroy()
        return remaining
    }

    /**
     * destroys the stack
     */
    fun destroy() {
        currentHealth = 0
        baseHealth = 0
        for (card in _cards) {
            card.onCoverDestroy()
            removeActor(card.actor)
        }
        _cards.clear()
        lockedTurnNum = null
        updateText()
    }

    /**
     * checks if this stack can accept a card
     */
    fun acceptsCard(turnNum: Int): Boolean {
        if (_cards.size >= maxCards) return false
        if (onlyAllowAddingOnSameTurn && lockedTurnNum != null && turnNum != lockedTurnNum) return false
        return true
    }

    /**
     * adds a new card
     */
    fun addCard(card: Card, turnNum: Int) {
        if (_cards.isEmpty()) lockedTurnNum = turnNum
        _cards.add(card)
        card.actor.setScale(cardScale)
        // this workaround is needed because HorizontalGroup doesn't consider scaling when calculating the preferred
        // dimensions, causing the layout to break
        card.actor.reportDimensionsWithScaling = true
        card.actor.ignoreScalingWhenDrawing = true
        baseHealth += card.coverValue
        currentHealth += card.coverValue
        updateText()
        onjScreen.removeActorFromRoot(card.actor)
        addActor(card.actor)
    }

    private fun updateText() {
        detailText.setText("${if (isActive) "active" else "not active"}\n${currentHealth}/${baseHealth}")
    }

    override fun getMinWidth(): Float {
        return minSize
    }
}
