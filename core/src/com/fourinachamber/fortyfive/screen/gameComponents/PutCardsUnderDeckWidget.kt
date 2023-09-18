package com.fourinachamber.fortyfive.screen.gameComponents

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.BackgroundActor
import com.fourinachamber.fortyfive.screen.general.DragAndDropBehaviourFactory
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.ZIndexActor
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import com.fourinachamber.fortyfive.screen.general.styles.addBackgroundStyles
import ktx.actors.contains
import onj.value.OnjNamedObject

class PutCardsUnderDeckWidget(
    private val screen: OnjScreen,
    private val cardSize: Float,
    private val cardSpacing: Float,
) : WidgetGroup(), ZIndexActor, StyledActor, BackgroundActor {

    override var fixedZIndex: Int = 0
    override var styleManager: StyleManager? = null
    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false

    var targetSize: Int = 0
    private val cards: MutableList<Card> = mutableListOf()

    private var loadedBackground: Drawable? = null
    override var backgroundHandle: ResourceHandle? = null
        set(value) {
            field = value
            loadedBackground = null
        }


    val isFinished: Boolean
        get() = cards.size >= targetSize

    init {
        bindHoverStateListeners(this)
        touchable = Touchable.enabled
    }

    fun initDragAndDrop(dragAndDrop: DragAndDrop, dropConfig: OnjNamedObject) {
        val dropBehaviour = DragAndDropBehaviourFactory.dropBehaviourOrError(
            dropConfig.name,
            dragAndDrop,
            this,
            dropConfig
        )
        dragAndDrop.addTarget(dropBehaviour)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        val backgroundHandle = backgroundHandle
        if (loadedBackground == null && backgroundHandle != null) {
            loadedBackground = ResourceManager.get(screen, backgroundHandle)
        }
        loadedBackground?.draw(batch, x, y, width, height)
        super.draw(batch, parentAlpha)
    }

    override fun layout() {
        super.layout()
        var curX = 0f
        cards.forEach { card ->
            card.actor.setBounds(
                curX,
                height / 2 - cardSize / 2,
                cardSize, cardSize
            )
            curX += cardSize + cardSpacing
        }
    }

    fun addCard(card: Card) {
        if (isFinished) return
        if (card.actor in this) return
        FortyFive.currentGame!!.cardHand.removeCard(card)
        addActor(card.actor)
        cards.add(card)
        invalidate()
    }

    fun complete(): List<Card> {
        val cards = cards.toMutableList().toList() // make copy
        this.cards.forEach { removeActor(it.actor) }
        this.cards.clear()
        return cards
    }

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
        addBackgroundStyles(screen)
    }

}
