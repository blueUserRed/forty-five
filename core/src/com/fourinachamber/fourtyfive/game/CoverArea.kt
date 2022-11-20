package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.HorizontalGroup
import com.badlogic.gdx.scenes.scene2d.ui.Label
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fourtyfive.card.Card
import com.fourinachamber.fourtyfive.card.CardActor
import com.fourinachamber.fourtyfive.screen.*
import com.fourinachamber.fourtyfive.utils.component1
import com.fourinachamber.fourtyfive.utils.component2
import ktx.actors.onEnter
import onj.OnjNamedObject
import java.lang.Float.max
import java.lang.Float.min


class CoverArea(
    val numStacks: Int,
    maxCards: Int,
    detailFont: BitmapFont,
    detailFontColor: Color,
    stackBackgroundTexture: TextureRegion,
    detailFontScale: Float
) : Widget(), InitialiseableActor {

    var slotDropConfig: Pair<DragAndDrop, OnjNamedObject>? = null
    private var isInitialised: Boolean = false
    private lateinit var screenDataProvider: ScreenDataProvider

    private val stacks: Array<CoverStack> = Array(numStacks) {
        CoverStack(maxCards, detailFont, detailFontColor, stackBackgroundTexture, detailFontScale, 1f, it)
    }

    override fun init(screenDataProvider: ScreenDataProvider) {
        this.screenDataProvider = screenDataProvider
    }

    /**
     * adds a new cover to the area, in a specific slot
     */
    fun addCover(card: Card, slot: Int) {
        if (slot !in stacks.indices) throw RuntimeException("slot $slot is out of bounds for coverArea")
        stacks[slot].addCard(card)
//        val stack = stacks[slot]
//        card.actor.setScale(0.045f)
//        card.actor.setPosition(stack.x, stack.y)
//        screenDataProvider.addActorToRoot(card.actor)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        super.draw(batch, parentAlpha)
        if (!isInitialised) {
            initialise()
            isInitialised = true
        }
        var (curX, curY) = localToStageCoordinates(Vector2(0f, 0f))
        curY += height
        for (stack in stacks) {
            curY -= stack.height
            stack.width = stack.prefWidth
            stack.height = stack.prefHeight
            stack.setPosition(curX, curY)
        }
    }

    private fun initialise() {
        for (stack in stacks) {
            val (dragAndDrop, dropOnj) = slotDropConfig!!
            val dropBehaviour = DragAndDropBehaviourFactory.dropBehaviourOrError(
                dropOnj.name,
                dragAndDrop,
                screenDataProvider,
                stack,
                dropOnj
            )
            println("init")
            dragAndDrop.addTarget(dropBehaviour)
            stack.init(screenDataProvider)
            screenDataProvider.addActorToRoot(stack)
            stack.debug = true
            stack.parentWidth = width
            stack.onEnter { println("hi") }
        }
    }

}

class CoverStack(
    val maxCards: Int,
    val detailFont: BitmapFont,
    val detailFontColor: Color,
    val backgroundTexture: TextureRegion,
    val detailFontScale: Float,
    val minSize: Float,
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
        space(1f)
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
        card.actor.setScale(0.05f)
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

}
