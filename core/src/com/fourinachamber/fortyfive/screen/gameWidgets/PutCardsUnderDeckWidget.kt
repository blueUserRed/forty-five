package com.fourinachamber.fortyfive.screen.gameWidgets

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup
import com.badlogic.gdx.scenes.scene2d.utils.DragAndDrop
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardActor
import com.fourinachamber.fortyfive.game.controller.NewGameController
import com.fourinachamber.fortyfive.keyInput.selection.SelectionGroup
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.BackgroundActor
import com.fourinachamber.fortyfive.screen.general.customActor.DragAndDroppableActor
import com.fourinachamber.fortyfive.screen.general.customActor.ZIndexActor
import com.fourinachamber.fortyfive.screen.general.customActor.ZIndexGroup
import com.fourinachamber.fortyfive.screen.general.styles.StyleManager
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.screen.general.styles.addActorStyles
import com.fourinachamber.fortyfive.screen.general.styles.addBackgroundStyles
import com.fourinachamber.fortyfive.utils.EventPipeline
import com.fourinachamber.fortyfive.utils.Promise
import com.fourinachamber.fortyfive.utils.SubscribeableObserver
import com.fourinachamber.fortyfive.utils.automaticResourceGetter
import com.fourinachamber.fortyfive.utils.random
import com.fourinachamber.fortyfive.utils.templateParam
import ktx.actors.contains
import onj.value.OnjNamedObject
import kotlin.random.Random

class PutCardsUnderDeckWidget(
    screen: OnjScreen,
    private val cardSize: Float,
    private val cardSpacing: Float,
    private val gameEvents: EventPipeline
) : CustomGroup(screen), DragAndDroppableActor {

    var targetSize: Int = 0
    private val cards: MutableList<Card> = mutableListOf()

    val isFinished: Boolean
        get() = cards.size >= targetSize


    override var isDraggable: Boolean = false
    override var inDragPreview: Boolean = false
    override var targetGroups: List<String> = listOf()
    override var resetCondition: ((Actor?) -> Boolean)? = null
    override val onDragAndDrop: MutableList<(Actor, Actor) -> Unit> = mutableListOf()

    private var currentPromise: Promise<List<Card>>? = null

    private var remainingCardsForTemplate: Int by templateParam("game.remainingCardsToPutUnderStack", 0)

    init {
        isFocusable = true
        makeDraggable(this)
        group = focusGroupName
        bindDroppable(this, screen, listOf(NewCardHand.cardFocusGroupName))
        onDragAndDrop.add { source, _ ->
            if (source !is CardActor) return@add
            addCard(source.card)
        }
        gameEvents.watchFor<NewGameController.Events.PutCardsUnderStack> { event ->
            targetSize = event.amount
            remainingCardsForTemplate = targetSize
            currentPromise = event.selectedCards
        }
    }

    fun initDragAndDrop(dragAndDrop: DragAndDrop, dropConfig: OnjNamedObject) {
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
        remainingCardsForTemplate--
        addActor(card.actor)
        cards.add(card)
        invalidate()
        if (isFinished) complete()
    }

    fun complete() {
        val cards = cards.toMutableList().toList() // make copy
        this.cards.forEach { removeActor(it.actor) }
        this.cards.clear()
        currentPromise?.resolve(cards)
    }

    override fun resortZIndices() {
        children.sort { el1, el2 ->
            (if (el1 is ZIndexActor) el1.fixedZIndex else -1) -
                    (if (el2 is ZIndexActor) el2.fixedZIndex else -1)
        }
    }

    companion object {
        const val focusGroupName = "putCardsUnderDeckFocusGroup"
    }

}
