package com.fourinachamber.fourtyfive.screen.gameComponents

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fourtyfive.game.card.Card
import com.fourinachamber.fourtyfive.screen.general.*
import ktx.actors.onClick
import onj.value.OnjNamedObject
import java.lang.Float.max
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2


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
    infoFontScale: Float,
    private val areaSpacing: Float,
    private val cardScale: Float,
    private val stackHeight: Float,
    private val stackMinWidth: Float,
    private val cardInitialX: Float,
    private val cardInitialY: Float,
    private val cardDeltaX: Float,
    private val cardDeltaY: Float,
    private val stackHookDrawable: Drawable
) : WidgetGroup(), ZIndexGroup, ZIndexActor {

    /**
     * set by gameScreenController //TODO: find a better solution
     */
    var slotDropConfig: Pair<DragAndDrop, OnjNamedObject>? = null
    private var isInitialised: Boolean = false

    override var fixedZIndex: Int = 0

    private val stacks: Array<CoverStack> = Array(numStacks) {
        CoverStack(
            maxCards,
            onlyAllowAddingOnSameTurn,
            this,
            infoFont,
            infoFontColor,
            infoFontScale,
            cardScale,
            stackHeight,
            stackMinWidth,
            cardInitialX,
            cardInitialY,
            cardDeltaX,
            cardDeltaY,
            it,
            stackHookDrawable
        )
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
        if (!isInitialised) {
            initialise()
            isInitialised = true
            invalidateHierarchy()
        }
        super.draw(batch, parentAlpha)
    }

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
                    (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

    private fun initialise() {
        var isFirst = true
        for (stack in stacks) {
            val (dragAndDrop, dropOnj) = slotDropConfig!!
            val dropBehaviour = DragAndDropBehaviourFactory.dropBehaviourOrError(
                dropOnj.name,
                dragAndDrop,
                stack,
                dropOnj
            )
            if (isFirst) {
                stack.isActive = true
                isFirst = false
            }
            dragAndDrop.addTarget(dropBehaviour)
            addActor(stack)
            stack.parentWidth = width
        }
    }

    override fun layout() {
        super.layout()

        var contentHeight = 0f
        for (stack in stacks) contentHeight += stack.height
        contentHeight += areaSpacing * stacks.size - areaSpacing

        val curX = 0
        var curY = height / 2 + contentHeight / 2

//        var isCardHoveredOver = false

        for (stack in stacks) {
            curY -= stack.height
            stack.width = max(stack.prefWidth, stack.minWidth)
            stack.height = stack.prefHeight
            if (!stack.inAnimation) stack.setPosition(curX + width / 2 - stack.width / 2, curY)
            curY -= areaSpacing

//            for (card in stack.cards) if (card.actor.isHoveredOver) {
//                isCardHoveredOver = true
////                updateHoverDetailActor(card, stack)
//            }
        }
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
    detailFontScale: Float,
    private val cardScale: Float,
    private val fixedHeight: Float,
    private val minWidth: Float,
    private val cardInitialX: Float,
    private val cardInitialY: Float,
    private val cardDeltaX: Float,
    private val cardDeltaY: Float,
    val num: Int,
    private val hookDrawable: Drawable
) : WidgetGroup(), ZIndexActor, ZIndexGroup, AnimationActor {

    override var inAnimation: Boolean = false

    override var fixedZIndex: Int = 0

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
    private var detailText: CustomLabel = CustomLabel("", Label.LabelStyle(detailFont, detailFontColor))

    var parentWidth: Float = Float.MAX_VALUE

    private var currentHoverDetail: CardDetailActor? = null

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
        onClick {
            coverArea.makeActive(num)
        }
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        validate()
        val hookWidth = width * 1f
        val hookHeight = 1.5f
        if (batch != null) hookDrawable.draw(
            batch,
            x + width / 2 - hookWidth / 2,
            y + height - detailText.height - hookHeight,
            hookWidth,
            hookHeight
        )
        super.draw(batch, parentAlpha)
        var wasCardHoveredOver = false
        for (card in _cards) if (card.actor.isHoveredOver) {
            card.actor.toFront()
            wasCardHoveredOver = true
            removeActor(currentHoverDetail)
            addActor(card.actor.hoverDetailActor)
            currentHoverDetail = card.actor.hoverDetailActor
            currentHoverDetail!!.isVisible = true
            invalidate()
        }
        if (!wasCardHoveredOver && currentHoverDetail != null) {
            currentHoverDetail!!.isVisible = false
            removeActor(currentHoverDetail)
            currentHoverDetail = null
            invalidate()
        }
    }

    override fun layout() {
        super.layout()
        val cardWidth = if (_cards.isEmpty()) 0f else _cards[0].actor.prefWidth
        width = (_cards.size * cardDeltaX + cardWidth).coerceAtLeast(minWidth)
        height = fixedHeight
        var curX = width / 2 - (_cards.size * cardDeltaX + cardWidth / 1.5f) / 2 + cardInitialX
        var curY = cardInitialY
        for (card in _cards) {
            card.actor.setScale(cardScale)
            card.actor.width = card.actor.prefWidth
            card.actor.height = card.actor.prefHeight
            card.actor.setPosition(curX, curY)
            if (currentHoverDetail == card.actor.hoverDetailActor) {
                val hoverDetail = currentHoverDetail!!
                hoverDetail.forcedWidth = width
                hoverDetail.setBounds(
                    -hoverDetail.prefWidth,
                    height / 2 - hoverDetail.prefHeight / 2,
                    hoverDetail.prefWidth,
                    hoverDetail.prefHeight
                )
            }
            curX += cardDeltaX
            curY += cardDeltaY
        }
        detailText.width = detailText.prefWidth
        detailText.height = detailText.prefHeight
        detailText.setPosition(
            width / 2 - detailText.width / 2,
            height - detailText.height
        )
        resortZIndices()
    }

    /**
     * damages this stack and destroys it if the health falls below zero
     * @return the remaining damage (that wasn't blocked by the cover)
     */
    fun damage(damage: Int): Int {
        currentHealth -= damage
        updateText()
        invalidateHierarchy()
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
        invalidateHierarchy()
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
        card.actor.reportDimensionsWithScaling = true
        card.actor.ignoreScalingWhenDrawing = true
        baseHealth += card.coverValue
        currentHealth += card.coverValue
        updateText()
        addActor(card.actor)
        _cards.forEach { it.actor.forceEndHover() }
        invalidateHierarchy()
    }

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
                    (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

    private fun updateText() {
        detailText.setText("${if (isActive) "active" else "not active"} ${currentHealth}/${baseHealth}")
    }

    override fun getMinWidth(): Float {
        return minWidth
    }
}
