package com.fourinachamber.fortyfive.map.statusbar

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardPrototype
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.CustomInputField
import com.fourinachamber.fortyfive.screen.general.customActor.CustomMoveByAction
import com.fourinachamber.fortyfive.screen.general.customActor.CustomWarningParent
import com.fourinachamber.fortyfive.screen.general.customActor.InOutAnimationActor
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.Timeline
import ktx.actors.onClick
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject

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

    private val quickAddRemoveListener = object : ClickListener() {
        override fun clicked(event: InputEvent?, x: Float, y: Float) {
            super.clicked(event, x, y)
            if ((event!!.target as CustomFlexBox).name == null) return
            val targetName = (event.target as CustomFlexBox).name.split(NAME_SEPARATOR_STRING)
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
        cardPrototypes = (Card.getFrom(cardsOnj.get<OnjArray>("cards"), screen) {})
        _allCards = cardPrototypes.filter { it.name in SaveState.cards }.map { it.create() }.toMutableList()
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
            sortingSystem = sortingSystem.getNext()
            invalidateHierarchy()
        }
        val cur = screen.namedActorOrError(sortReverseWidgetName) as CustomFlexBox
        cur.onClick {
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
        val seenCards = deckSlots.map { it.children[0] }.filter { it.isVisible }
        if (seenCards.size != SaveState.curDeck.cards.size) {
            reloadDeck()
            return
        }

        //checks for all the cards that should be visible
        val nbrOfCorrectPlacedChildren =
            SaveState.curDeck.cardPositions.map { deckSlots[it.key].children[0] to it.value }
                .filter { it.first.isVisible }
                .filter { it.first.name != null }
                .filter { it.second == it.first.name.split(NAME_SEPARATOR_STRING)[0] }
                .size
        if (nbrOfCorrectPlacedChildren != SaveState.curDeck.cards.size) {
            reloadDeck()
            return
        }

        //maybe check before actually sorting if needed, but I think it doesn't matter
        val unplacedCards = SaveState.cards.toMutableList()
        seenCards.forEach { unplacedCards.remove(it.name.split(NAME_SEPARATOR_STRING)[0]) }
        TemplateString.updateGlobalParam("overlay.backpack.sortByName", sortingSystem.getDisplayName())
        sortBackpack(sortingSystem.sort(this, unplacedCards))
    }

    private fun initDeckLayout() {
        for (i in 0 until SaveState.Deck.numberOfSlots) {
            val cur = (screen.screenBuilder.generateFromTemplate(
                "backpack_slot",
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
        SaveState.curDeck.checkMinimum()

        //"Reset" Deck
        val children = deckCardsWidget.children.filterIsInstance<CustomFlexBox>()
        children.forEach { (it.children[0] as CustomFlexBox).isVisible = false }

        //Deck
        val unplacedCards: MutableList<String> = SaveState.cards.toMutableList()
        SaveState.curDeck.cardPositions.forEach {
            val currentSelection = unplacedCards.find { card -> it.value == card }!!
            val cur = children[it.key].children[0] as CustomFlexBox
            cur.isVisible = true
            cur.background =
                TextureRegionDrawable(getCard(it.value).actor.pixmapTextureRegion)
            cur.name = "${it.value}${NAME_SEPARATOR_STRING}deck${NAME_SEPARATOR_STRING}${it.key}"
            unplacedCards.remove(currentSelection)
        }
        deckCardsWidget.invalidate()


        //Reset Backpack
        backpackCardsWidget.children.filterIsInstance<CustomFlexBox>()
            .forEach { removeChildCompletely(it) }
        //Backpack
        for (i in 0 until unplacedCards.size) {
            val cur = screen.screenBuilder.generateFromTemplate(
                "backpack_slot",
                mapOf(),
                backpackCardsWidget,
                screen
            ) as CustomFlexBox
            cur.addListener(quickAddRemoveListener)
        }
        sortBackpack(sortingSystem.sort(this, unplacedCards))
    }

    private fun getCard(name: String): Card {
        val card = _allCards.find { card -> card.name == name } ?: cardPrototypes.find { it.name == name }!!.create()
        if (card !in _allCards) _allCards.add(card)
        return card
    }

    private fun removeChildCompletely(child: CustomFlexBox) {
        (child.parent as CustomFlexBox).remove(child.styleManager!!.node)
        child.remove()
        //theoretically remove from screen with like behaviour and dragAndDrop, but idc
    }

    private fun sortBackpack(sortedCards: List<String>) {
        val allPos = backpackCardsWidget.children.filterIsInstance<CustomFlexBox>()
        for (i in sortedCards.indices) {
            val curCard = getCard(sortedCards[i])
            val curActor = allPos[i].children[0] as CustomFlexBox
            curActor.background = TextureRegionDrawable(curCard.actor.pixmapTextureRegion)
            curActor.name = "${curCard.name}${NAME_SEPARATOR_STRING}backpack${NAME_SEPARATOR_STRING}${i}"
        }
        backpackCardsWidget.invalidate()
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
        changeDeckTo(SaveState.curDeck.id, true)
        return Timeline.timeline {
            parallelActions(
                getInOutTimeLine(isGoingIn = true, false, deckCardsWidget.parent as CustomFlexBox).asAction(),
                getInOutTimeLine(isGoingIn = true, true, backpackCardsWidget.parent as CustomFlexBox).asAction()
            )
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


