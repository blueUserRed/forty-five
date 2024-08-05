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
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.BackgroundActor
import com.fourinachamber.fortyfive.screen.general.customActor.ZIndexActor
import com.fourinachamber.fortyfive.screen.general.customActor.ZIndexGroup
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import com.fourinachamber.fortyfive.screen.general.styles.addBackgroundStyles
import com.fourinachamber.fortyfive.utils.SubscribeableObserver
import com.fourinachamber.fortyfive.utils.automaticResourceGetter
import com.fourinachamber.fortyfive.utils.random
import ktx.actors.contains
import onj.value.OnjNamedObject
import kotlin.random.Random

class PutCardsUnderDeckWidget(
    private val screen: OnjScreen,
    private val cardSize: Float,
    private val cardSpacing: Float,
) : WidgetGroup(), ZIndexActor, ZIndexGroup, StyledActor, BackgroundActor {

    override var fixedZIndex: Int = 0
    override var styleManager: StyleManager? = null
    override var isHoveredOver: Boolean = false
    override var isClicked: Boolean = false

    var targetSize: Int = 0
    private val cards: MutableList<Card> = mutableListOf()

    private val backgroundHandleObserver = SubscribeableObserver<String?>(null)
    override var backgroundHandle: ResourceHandle? by backgroundHandleObserver
    private val loadedBackground: Drawable? by automaticResourceGetter<Drawable>(backgroundHandleObserver, screen)

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
        loadedBackground?.draw(batch, x, y, width, height)
        super.draw(batch, parentAlpha)
    }

    override fun layout() {
        super.layout()
        val random = Random(237689)
        val amountCards = cards.size
        val spacePerCard = (width - cardSize) / (amountCards + 2)

        var curX = cardSize / 4
        var curZIndex = 0
        cards.forEach { card ->
            card.actor.setBounds(
                curX, height / 2 - cardSize / 2,
                cardSize, cardSize
            )
            card.actor.fixedZIndex = curZIndex
            card.actor.rotation = (-10f..10f).random(random)
            curZIndex++
            curX += spacePerCard
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

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
                    (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

}
