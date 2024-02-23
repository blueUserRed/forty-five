package com.fourinachamber.fortyfive.map.statusbar

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.fourinachamber.fortyfive.game.PermaSaveState
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardActor
import com.fourinachamber.fortyfive.game.card.CardPrototype
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.CustomInputField
import com.fourinachamber.fortyfive.screen.general.customActor.CustomMoveByAction
import com.fourinachamber.fortyfive.screen.general.customActor.CustomWarningParent
import com.fourinachamber.fortyfive.screen.general.customActor.InOutAnimationActor
import com.fourinachamber.fortyfive.screen.general.styles.StyledActor
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.Timeline
import ktx.actors.onClick
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject
import java.lang.Integer.max
import kotlin.math.min

class Backpack(
    screen: OnjScreen,
    cardsFile: String,
    backpackFile: String,
    private val deckNameWidgetName: String,
    private val deckSelectionParentWidgetName: String,
    private val deckCardsWidgetName: String,
    private val backPackCardsWidgetName: String,
    private val backpackEditIndicationWidgetName: String,
    private val sortWidgetName: String,
    private val sortReverseWidgetName: String,
) : CustomFlexBox(screen), InOutAnimationActor {

    private val cardPrototypes: List<CardPrototype>
    private val _allCards: MutableList<Card>
    private val minNameSize: Int
    private val maxNameSize: Int

    private lateinit var deckNameWidget: CustomInputField
    private lateinit var deckCardsWidget: CustomScrollableFlexBox
    private lateinit var backpackCardsWidget: CustomScrollableFlexBox
    private lateinit var deckSelectionParent: CustomFlexBox
    private lateinit var backpackEditIndication: CustomFlexBox

    private var sortingSystem: BackpackSorting = BackpackSorting.Damage()

    /**
     * it's true at the first time when called, since it needs to set the global variables in the templateString
     */
    private var sortingSystemDirty: Boolean = true

    private val quickAddRemoveListener = object : ClickListener() {
        override fun clicked(event: InputEvent, x: Float, y: Float) {
            super.clicked(event, x, y)
            val target = event.target
            if (target !is CardActor) return
            if (target.name == null) return
            val targetName = target.name.split(NAME_SEPARATOR_STRING)
            if ((tapCount and 1) == 0) {
                //if==true means that it is from backpack
                if (targetName[1] == "backpack") quickAddToDeck(targetName, screen)
                else quickAddToBackpack(targetName, screen)
            }
        }
    }

    private fun quickAddToBackpack(
        targetName: List<String>,
        screen: OnjScreen
    ) {
        if (SaveState.curDeck.canRemoveCards()) {
            val fromDeck = targetName[1] == "deck"
            if (fromDeck) {
                SaveState.curDeck.removeFromDeck(targetName[2].toInt())
                invalidate()
            }
        } else {
            CustomWarningParent.getWarning(screen).addWarning(
                screen,
                "Not enough cards",
                "The minimum deck size is ${SaveState.Deck.minDeckSize}. Since you only have ${SaveState.curDeck.cardPositions.size} cards in your Deck, you can't remove a card.",
                CustomWarningParent.Severity.MIDDLE
            )
        }
    }

    private fun quickAddToDeck(
        targetName: List<String>,
        screen: OnjScreen
    ) {
        if (SaveState.curDeck.canAddCards()) {
            val pos = SaveState.curDeck.nextFreeSlot()
            SaveState.curDeck.addToDeck(pos, targetName[0])
            invalidate()
        } else {
            CustomWarningParent.getWarning(screen).addWarning(
                screen,
                "Deck full",
                "The max deck size is ${SaveState.Deck.numberOfSlots}. Since you already have ${SaveState.Deck.numberOfSlots} cards in your Deck, you can't add a new card.",
                CustomWarningParent.Severity.MIDDLE
            )
        }
    }

    init {
        //TODO
//         0. (done(mostly)) background stop //mouse still active in background
//         1. (done) Cards drag and drop (both direction)
//         2. (done) automatic add to deck on double click or on press space or so
//         3. (done) automatic add to deck if deck doesn't have enough cards
//         4. (done) stop cards from moving if you don't have enough cards
//         5. sorting system (ui missing)
        val backpackOnj = OnjParser.parseFile(backpackFile)
        backpackFileSchema.assertMatches(backpackOnj)
        backpackOnj as OnjObject

        val nameOnj = backpackOnj.get<OnjObject>("deckNameDef")
        minNameSize = nameOnj.get<Long>("minLength").toInt()
        maxNameSize = nameOnj.get<Long>("maxLength").toInt()

        val cardsOnj = OnjParser.parseFile(cardsFile)
        cardsFileSchema.assertMatches(cardsOnj)
        cardsOnj as OnjObject
        cardPrototypes = (Card.getFrom(cardsOnj.get<OnjArray>("cards"), initializer = { screen.addDisposable(it) }))
        _allCards = cardPrototypes
            .filter { it.name in SaveState.cards }.filter { it.name in PermaSaveState.collection }
            .map { it.create(screen, true) }
            .toMutableList()
    }

    fun initAfterChildrenExist() {
        deckNameWidget = screen.namedActorOrError(deckNameWidgetName) as CustomInputField
        deckCardsWidget = screen.namedActorOrError(deckCardsWidgetName) as CustomScrollableFlexBox
        backpackCardsWidget = screen.namedActorOrError(backPackCardsWidgetName) as CustomScrollableFlexBox
        deckSelectionParent = screen.namedActorOrError(deckSelectionParentWidgetName) as CustomFlexBox
        backpackEditIndication = screen.namedActorOrError(backpackEditIndicationWidgetName) as CustomFlexBox
        deckNameWidget.maxLength = maxNameSize
        initDeckName()
        initDeckLayout()
        initDeckSelection()
        initSortBy()
    }

    private fun initSortBy() {
        (screen.namedActorOrError(sortWidgetName) as CustomFlexBox).onClick {
            sortingSystemDirty = true
            sortingSystem = sortingSystem.getNext()
            invalidateHierarchy()
        }
        val cur = screen.namedActorOrError(sortReverseWidgetName) as CustomFlexBox
        cur.onClick {
            sortingSystemDirty = true
            sortingSystem.isReverse = !sortingSystem.isReverse
            invalidateHierarchy()
            this.children[0].rotateBy(180F)
        }
    }

    override fun layout() {
        checkDeck()
        super.layout()
    }

    private fun checkDeck() {
        //checks for the number of visible cards
        val deckSlots = deckCardsWidget.children.filterIsInstance<CustomFlexBox>()
        val seenCards = deckSlots.filter { it.children.size > 0 }.map { it.children[0] }
        if (seenCards.size != SaveState.curDeck.cards.size) {
            reloadDeck()
            return
        }

        //checks for all the cards that should be visible
        val nbrOfCorrectPlacedChildren =
            SaveState.curDeck.cardPositions.filter { deckSlots[it.key].children.size > 0 }
                .map { deckSlots[it.key].children[0] to it.value }
                .filter { it.first.name != null }
                .filter { it.second == it.first.name.split(NAME_SEPARATOR_STRING)[0] }
                .size
        if (nbrOfCorrectPlacedChildren != SaveState.curDeck.cards.size) {
            reloadDeck()
            return
        }

        if (sortingSystemDirty) {
            val unplacedCards = SaveState.cards.toMutableList()
            seenCards.forEach { unplacedCards.remove(it.name.split(NAME_SEPARATOR_STRING)[0]) }
            sortBackpack(sortingSystem.sort(this, unplacedCards))
        }
    }

    private fun initDeckLayout() {
        for (i in 0 until SaveState.Deck.numberOfSlots) {
            val cur = (screen.screenBuilder.generateFromTemplate(
                "backpack_slot_parent",
                mapOf(),
                deckCardsWidget,
                screen
            ) as CustomFlexBox)
            cur.backgroundHandle = "backpack_empty_deck_slot"
            cur.addListener(quickAddRemoveListener)
        }
        deckCardsWidget.invalidate()
    }

    private fun initDeckSelection() {
        for (i in 0 until deckSelectionParent.children.size) {
            val child = deckSelectionParent.children.get(i)
            child.onButtonClick { changeDeckTo(i + 1) }
        }
    }

    private fun changeDeckTo(newDeckId: Int, firstInit: Boolean = false) {
        if (SaveState.curDeck.id != newDeckId || firstInit) {
            if (!firstInit) FortyFiveLogger.log(
                FortyFiveLogger.LogLevel.DEBUG,
                LOG_TAG,
                "Changing Deck from ${SaveState.curDeck.id} to $newDeckId"
            )
            val oldActor = deckSelectionParent.children[SaveState.curDeck.id - 1] as CustomImageActor
            oldActor.backgroundHandle = oldActor.backgroundHandle?.replace("_hover", "")
            SaveState.curDeckNbr = newDeckId
            resetDeckNameField()
            (deckSelectionParent.children[newDeckId - 1] as CustomImageActor).backgroundHandle += "_hover"
            reloadDeck()
        }
    }

    private fun reloadDeck() {
        SaveState.curDeck.checkDeck()

        //"Reset" Deck and Backpack
        val children = deckCardsWidget.children.filterIsInstance<CustomFlexBox>()

        val positions = SaveState.curDeck.cardPositions.toMutableMap()
        val unplacedCards: MutableList<String> = SaveState.cards.toMutableList()
        val removedCardInDeck = mutableListOf<String>()
        children.forEachIndexed { i, it ->
            if (it.children.size > 0) {
                val curActor = it.children[0] as CardActor
                if (positions[i] != curActor.card.name) {
                    removeChildCompletely(curActor)
                    removedCardInDeck.add(curActor.card.name)
                } else {
                    unplacedCards.remove(curActor.card.name)
                    positions.remove(i)
                }
            }
        }


        val cardsNeededToRemoveInBackpack = mutableListOf<String>()
        positions.forEach {
            if (!removedCardInDeck.remove(it.value)) cardsNeededToRemoveInBackpack.add(it.value)
        }

        backpackCardsWidget.children.filterIsInstance<CustomFlexBox>().forEach {
            if (it.children.size > 0 && it.children[0] is CardActor) {
                if (cardsNeededToRemoveInBackpack.remove((it.children[0] as CardActor).card.name)) {
                    removeChildCompletely(it)
                }
            }
        }

        //Deck
        positions.forEach {
            val cur = children[it.key]
            val curChild = getCard(it.value, true).actor
            screen.screenBuilder.addDataToWidgetFromTemplate("backpack_slot_card", mapOf(), cur, screen, curChild)
            curChild.name = "${it.value}${NAME_SEPARATOR_STRING}deck${NAME_SEPARATOR_STRING}${it.key}"
            unplacedCards.remove(it.value)
        }
        deckCardsWidget.invalidate()


        //Reset Backpack
        sortBackpack(sortingSystem.sort(this, unplacedCards))
    }

    private fun getCard(name: String, checkParent: Boolean = false): Card {
        val card = _allCards.find { card -> card.name == name && (!checkParent || card.actor.parent == null) }
        return card
            ?: throw IllegalStateException("You try to access a card that isn't loaded, this shouldn't be possible!")
    }

    private fun removeChildCompletely(actor: Actor) {
        if (actor !is StyledActor) throw Exception("This method should only be called with StyledActors")
        if (actor is CustomFlexBox) {
            actor.children.forEach { removeChildCompletely(it) }
        }
        screen.styleManagers.remove(actor.styleManager!!)
        (actor.parent as CustomFlexBox).remove(actor.styleManager!!.node)
        actor.remove()
        //theoretically remove from screen with like behaviour and dragAndDrop, but idc
    }

    private fun sortBackpack(sortedCards: List<String>) {
        var parents = backpackCardsWidget.children.filterIsInstance<CustomFlexBox>()
//        backpackCardsWidget.children.filterIsInstance<CustomFlexBox>().forEach { removeChildCompletely(it) }

        //if a card got added or removed to/from the backpack
        if (sortedCards.size != parents.size) {
            val min = min(sortedCards.size, parents.size)
            if (sortedCards.size > min) {
                for (i in min until sortedCards.size) {
                    screen.screenBuilder.generateFromTemplate(
                        "backpack_slot_parent",
                        mapOf(),
                        backpackCardsWidget,
                        screen,
                    ) as CustomFlexBox
                }
            } else if (parents.size > min)
                for (i in min until parents.size) {
                    removeChildCompletely(parents[i])
                }
        }
        parents = backpackCardsWidget.children.filterIsInstance<CustomFlexBox>()

        //leave cards that are at the right position
        for (i in sortedCards.indices) {
            val children = parents[i].children
            if (children.size >= 1 && children[0] is CardActor) {
                val c = children[0] as CardActor
                if (c.card.name != sortedCards[i]) removeChildCompletely(c)
            }
        }

        // add the cards that are at the right position
        for (i in sortedCards.indices) {
            val cardName = sortedCards[i]
            if (parents[i].children.size!=0) continue
            val curActor = getCard(cardName, true).actor
            screen.screenBuilder.addDataToWidgetFromTemplate(
                "backpack_slot_card",
                mapOf(),
                parents[i],
                screen,
                curActor
            )
            curActor.name = "${cardName}${NAME_SEPARATOR_STRING}backpack${NAME_SEPARATOR_STRING}${i}"
            curActor.addListener(quickAddRemoveListener)
        }
        backpackCardsWidget.invalidate()
        TemplateString.updateGlobalParam("overlay.backpack.sortByName", sortingSystem.getDisplayName())
        sortingSystemDirty = false
    }

    private fun initDeckName() {
        resetDeckNameField()
        deckNameWidget.limitListener = object : CustomInputField.CustomMaxReachedListener {
            override fun maxReached(field: CustomInputField, wrong: String) {
                CustomWarningParent.getWarning(screen).setLimit("Name limit reached", 3)
                CustomWarningParent.getWarning(screen).addWarning(
                    this@Backpack.screen,
                    "Name limit reached",
                    "The name \"$wrong\", which you are trying to enter is too long!",
                    CustomWarningParent.Severity.LOW
                )
            }
        }
        deckNameWidget.typedListener = object : CustomInputField.CustomInputFieldListener {
            override fun keyTyped(e: InputEvent, ch: Char) {
                if (ch == '\n' || ch == '\r') saveCurrentDeckName()
            }
        }
        deckNameWidget.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (deckNameWidget.isDisabled && tapCount == 2) {
                    startEditDeckName()
                }
            }
        })

        backpackEditIndication.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (backpackEditIndication.inActorState("inEdit")) saveCurrentDeckName()
                else startEditDeckName()
            }
        })
    }

    private fun startEditDeckName() {
        deckNameWidget.isDisabled = false
        backpackEditIndication.enterActorState("inEdit")
    }

    private fun resetDeckNameField() {
        backpackEditIndication.leaveActorState("inEdit")
        deckNameWidget.setText(SaveState.curDeck.name)
        deckNameWidget.isDisabled = true
        deckNameWidget.clearSelection()
    }

    private fun saveCurrentDeckName() {
        backpackEditIndication.leaveActorState("inEdit")
        deckNameWidget.clearSelection()
        deckNameWidget.isDisabled = true
        SaveState.curDeck.name = deckNameWidget.text.toString()
    }


    override fun display(): Timeline {
        checkCurCards()
        changeDeckTo(SaveState.curDeck.id, true)
        return Timeline.timeline {
            parallelActions(
                getInOutTimeLine(isGoingIn = true, false, deckCardsWidget.parent as CustomFlexBox).asAction(),
                getInOutTimeLine(isGoingIn = true, true, backpackCardsWidget.parent as CustomFlexBox).asAction()
            )
        }
    }

    private fun checkCurCards() {
        val savedCardsLeft = PermaSaveState.collection.toMutableList()
        val unplacedCards = SaveState.cards.toMutableList()
        val placedCards = _allCards.map { it.name }.toMutableList()
        var i = 0;
        while (i < unplacedCards.size) {
            val cur = unplacedCards[i]
            if (cur in placedCards) {
                unplacedCards.remove(cur)
                placedCards.remove(cur)
                if (cur in savedCardsLeft) savedCardsLeft.remove(cur)
            } else i++
        }
        unplacedCards.forEach { name ->
            _allCards.add(cardPrototypes.find { it.name == name }!!.create(screen, savedCardsLeft.remove(name)))
        }
    }

    private fun getInOutTimeLine(isGoingIn: Boolean, goingRight: Boolean, target: CustomFlexBox) = Timeline.timeline {
        val amount = stage.viewport.worldWidth / 2
        action {
            if (isGoingIn) {
                isVisible = true
                target.offsetX = amount * (if (goingRight) 1 else -1)
            }
        }
        val action = CustomMoveByAction(
            target,
            (if (isGoingIn) Interpolation.exp10Out else Interpolation.exp5In),
            relX = amount * (if (goingRight) -1 else 1) * (if (isGoingIn) 1 else -1),
            duration = 200F
        )
        action { target.addAction(action) }
        delayUntil { action.isComplete }
        action {
            if (!isGoingIn) isVisible = false
            else {
                deckCardsWidget.invalidate()
                backpackCardsWidget.invalidate()
            }
        }
    }

    override fun hide(): Timeline {
        return Timeline.timeline {
            parallelActions(
                getInOutTimeLine(isGoingIn = false, false, deckCardsWidget.parent as CustomFlexBox).asAction(),
                getInOutTimeLine(isGoingIn = false, true, backpackCardsWidget.parent as CustomFlexBox).asAction()
            )
        }
    }

    companion object {
        const val NAME_SEPARATOR_STRING = "_-_"

        private val backpackFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/backpack.onjschema").file())
        }
        private val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
        }
        const val LOG_TAG: String = "Backpack"
    }

    interface BackpackSorting {

        var isReverse: Boolean

        fun sort(cardData: Backpack, cards: List<String>): List<String> {
            val list = cards.map { name -> cardData.getCard(name) }.sortedWith { a, b ->
                run {
                    val comp = compare(b, a)
                    if (comp != 0) comp
                    else a.name.compareTo(b.name)
                }
            }.map { it.name }
            return if (isReverse) list.reversed() else list
        }

        fun compare(a: Card, b: Card): Int

        fun getDisplayName(): String = this.javaClass.simpleName
        fun getNext(): BackpackSorting {
            val all = arrayOf(
                "Damage" to { Damage(isReverse) },
                "Reserves" to { Reserves(isReverse) },
                "Name" to { Name(isReverse) },
            )
            return all[(all.indexOfFirst { it.first == this.getDisplayName() } + 1) % all.size].second.invoke()
        }

        class Damage(override var isReverse: Boolean = false) : BackpackSorting {
            override fun compare(a: Card, b: Card): Int {
                return a.baseDamage.compareTo(b.baseDamage)
            }
        }

        class Reserves(override var isReverse: Boolean = false) : BackpackSorting {
            override fun compare(a: Card, b: Card): Int {
                return a.cost.compareTo(b.cost)
            }
        }

        class Name(override var isReverse: Boolean = false) : BackpackSorting {
            override fun compare(a: Card, b: Card): Int {
                return 0
            }
        }
    }
}


