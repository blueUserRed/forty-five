package com.fourinachamber.fortyfive.map.statusbar

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.CustomInputField
import com.fourinachamber.fortyfive.screen.general.customActor.CustomMoveByAction
import com.fourinachamber.fortyfive.utils.FortyFiveLogger
import com.fourinachamber.fortyfive.utils.Timeline
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
) :
    CustomFlexBox(screen),
    InOutAnimationActor {

    private val _allCards: MutableList<Card>
    private val minNameSize: Int
    private val maxNameSize: Int

    private lateinit var deckNameWidget: CustomInputField
    private lateinit var deckCardsWidget: CustomScrollableFlexBox
    private lateinit var backpackCardsWidget: CustomScrollableFlexBox
    private lateinit var deckSelectionParent: CustomFlexBox

    private var sortingSystem = BackpackSorting.Damage()


    init {
        //TODO
//         0. background stop
//         1. (done) Cards drag and drop (both direction)
//         2. automatic add to deck on double click or on press space or so
//         3. (done) automatic add to deck if deck doesn't have enough cards
//         4. (done (half (waiting for marvin))) stop cards from moving if you don't have enough cards
//         5. sorting system (ui missing)
        val backpackOnj = OnjParser.parseFile(backpackFile)
        backpackFileSchema.assertMatches(backpackOnj)
        backpackOnj as OnjObject

        _minDeckSize = backpackOnj.get<Long>("minCardsPerDeck").toInt()
        _numberOfSlots = backpackOnj.get<Long>("slotsPerDeck").toInt()
        val nameOnj = backpackOnj.get<OnjObject>("deckNameDef")
        minNameSize = nameOnj.get<Long>("minLength").toInt()
        maxNameSize = nameOnj.get<Long>("maxLength").toInt()

        val cardsOnj = OnjParser.parseFile(cardsFile)
        cardsFileSchema.assertMatches(cardsOnj)
        cardsOnj as OnjObject
        val cardPrototypes =
            (Card.getFrom(cardsOnj.get<OnjArray>("cards"), screen) {}).filter { it.name in SaveState.cards }
        _allCards = cardPrototypes.map { it.create() }.toMutableList()
    }

    override fun initAfterChildrenExist() {
        deckNameWidget = screen.namedActorOrError(deckNameWidgetName) as CustomInputField
        deckCardsWidget = screen.namedActorOrError(deckCardsWidgetName) as CustomScrollableFlexBox
        backpackCardsWidget = screen.namedActorOrError(backPackCardsWidgetName) as CustomScrollableFlexBox
        deckSelectionParent = screen.namedActorOrError(deckSelectionParentWidgetName) as CustomFlexBox
        deckNameWidget.maxLength = maxNameSize
        initDeckName()
        initDeckLayout()
        initDeckSelection()
    }

    override fun layout() {
        checkDeck()
        super.layout()
    }

    private fun checkDeck() {
        //checks for the number of visible cards
        val deckSlots = deckCardsWidget.children.filterIsInstance<CustomFlexBox>()
        val nbrOfSeenCards = deckSlots.filter { it.children[0].isVisible }.size
        if (nbrOfSeenCards != SaveState.curDeck.cards.size) {
            reloadDeck()
            return
        }

        //checks for all the cards that should be visible
        val nbrOfCorrectPlacedChildren =
            SaveState.curDeck.cardPositions.map { deckSlots[it.key].children[0] to it.value }
                .filter { it.first.isVisible }
                .filter { it.first.name != null }
                .filter { it.second == it.first.name.split(nameSeparatorStr)[0] }
                .size
        if (nbrOfCorrectPlacedChildren != SaveState.curDeck.cards.size) {
            reloadDeck()
            return
        }
    }

    private fun initDeckLayout() {
        for (i in 0 until numberOfSlots) {
            (screen.screenBuilder.generateFromTemplate(
                "backpack_slot",
                mapOf(),
                deckCardsWidget,
                screen
            ) as CustomFlexBox).backgroundHandle = "backpack_empty_deck_slot"
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
                TextureRegionDrawable(_allCards.find { card -> card.name == it.value }!!.actor.pixmapTextureRegion)
            cur.name = "${it.value}${nameSeparatorStr}deck${nameSeparatorStr}${it.key}"
            unplacedCards.remove(currentSelection)
        }
        deckCardsWidget.invalidate()


        //Reset Backpack
        backpackCardsWidget.children.filterIsInstance<CustomFlexBox>()
            .forEach { removeChildCompletely(it) }
        //Backpack
        for (i in 0 until unplacedCards.size) {
            screen.screenBuilder.generateFromTemplate(
                "backpack_slot",
                mapOf(),
                backpackCardsWidget,
                screen
            )
        }
        sortBackpack(sortingSystem.sort(_allCards, unplacedCards))
        backpackCardsWidget.invalidate()
    }

    private fun removeChildCompletely(child: CustomFlexBox) {
        (child.parent as CustomFlexBox).remove(child.styleManager!!.node)
        child.remove()
        //theoretically remove from screen with like behaviour and dragAndDrop, but idc
    }

    private fun sortBackpack(sortedCards: List<String>) {
        val allPos = backpackCardsWidget.children.filterIsInstance<CustomFlexBox>()
        for (i in sortedCards.indices) {
            val curCard = _allCards.find { it.name == sortedCards[i] }!!
            val curActor = allPos[i].children[0] as CustomFlexBox
            curActor.background = TextureRegionDrawable(curCard.actor.pixmapTextureRegion)
            curActor.name = "${curCard.name}${nameSeparatorStr}backpack${nameSeparatorStr}${i}"
        }
    }

    private fun initDeckName() {
        resetDeckNameField()
        deckNameWidget.limitListener = object : CustomInputField.CustomMaxReachedListener {
            override fun maxReached(field: CustomInputField, wrong: String) {
                println("max reached, name '$wrong' too long") //TODO this with error-block
            }
        }

        deckNameWidget.keyslistener = object : CustomInputField.CustomInputFieldListener {
            override fun keyTyped(e: InputEvent, ch: Char) {
                if (ch == '\n' || ch == '\r') saveCurrentDeckName()
            }
        }

        deckNameWidget.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                if (deckNameWidget.isDisabled && tapCount == 2) editDeckName()
            }
        })
    }

    private fun resetDeckNameField() {
        deckNameWidget.setText(SaveState.curDeck.name)
        deckNameWidget.isDisabled = true
        deckNameWidget.clearSelection()
    }

    private fun saveCurrentDeckName() {
        deckNameWidget.clearSelection()
        deckNameWidget.isDisabled = true
        SaveState.curDeck.name = deckNameWidget.text.toString()
    }

    private fun editDeckName() {
        deckNameWidget.isDisabled = false
        stage.keyboardFocus = (deckNameWidget)
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
                println("now invaildating")
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
        private var _minDeckSize: Int = 0
        private var _numberOfSlots: Int = 0

        val minDeckSize: Int
            get() = _minDeckSize

        val numberOfSlots: Int
            get() = _numberOfSlots

        const
        val nameSeparatorStr = "_-_"
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
        fun sort(cardData: List<Card>, cards: List<String>): List<String>

        class Damage(override var isReverse: Boolean = false) : BackpackSorting {
            override fun sort(cardData: List<Card>, cards: List<String>): List<String> {
                val list = cards.sortedBy { name -> -(cardData.find { name == it.name }!!.baseDamage) }
                return if (isReverse) list.reversed() else list
            }
        }
    }
}