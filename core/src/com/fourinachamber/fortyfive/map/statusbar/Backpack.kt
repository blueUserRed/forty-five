package com.fourinachamber.fortyfive.map.statusbar

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.scenes.scene2d.Event
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.InputListener
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.keyInput.Keycode
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.CustomInputField
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.Timeline
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject
import java.awt.event.KeyEvent


class Backpack(
    private val screen: OnjScreen,
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
    private val cardImgs: MutableList<CustomImageActor> = mutableListOf()
    private val minDeckSize: Int
    private val numberOfSlots: Int
    private val minNameSize: Int
    private val maxNameSize: Int

    private lateinit var deckNameWidget: CustomInputField
    private lateinit var deckCardsWidget: CustomScrollableFlexBox
    private lateinit var backPackCardsWidget: CustomScrollableFlexBox
    private lateinit var deckSelectionParentWidget: CustomFlexBox

    init {
        val cardsOnj = OnjParser.parseFile(cardsFile)
        cardsFileSchema.assertMatches(cardsOnj)
        cardsOnj as OnjObject
        val cardPrototypes =
            (Card.getFrom(cardsOnj.get<OnjArray>("cards"), screen) {}).filter { it.name in SaveState.cards }
        _allCards = cardPrototypes.map { it.create() }.toMutableList()
        val onj = OnjParser.parseFile(backpackFile)
        backpackFileSchema.assertMatches(onj)
        onj as OnjObject
        minDeckSize = onj.get<Long>("minCardsPerDeck").toInt()
        numberOfSlots = onj.get<Long>("slotsPerDeck").toInt()
        val nameOnj = onj.get<OnjObject>("deckNameDef")
        minNameSize = nameOnj.get<Long>("minLength").toInt()
        maxNameSize = nameOnj.get<Long>("maxLength").toInt()
    }

    override fun initAfterChildrenExist() {
        deckNameWidget = screen.namedActorOrError(deckNameWidgetName) as CustomInputField
        deckCardsWidget = screen.namedActorOrError(deckCardsWidgetName) as CustomScrollableFlexBox
        backPackCardsWidget = screen.namedActorOrError(backPackCardsWidgetName) as CustomScrollableFlexBox
        deckSelectionParentWidget = screen.namedActorOrError(deckSelectionParentWidgetName) as CustomFlexBox
        deckNameWidget.maxLength = maxNameSize
        initDeckName()
    }

    private fun initDeckName() {
        deckNameWidget.setText(SaveState.curDeck.name)
        deckNameWidget.isDisabled = true
        deckNameWidget.limitListener = object : CustomInputField.CustomMaxReachedListener {
            override fun maxReached(field: CustomInputField, wrong: String) {
                println("max reached, string $wrong too long")
            }
        }

        deckNameWidget.keyslistener = object : CustomInputField.CustomInputFieldListener {
            override fun keyTyped(e: InputEvent, ch: Char) {
                if (ch == '\n' || ch == '\r') {
                    saveCurrentDeckName()
                }
            }
        }

        deckNameWidget.addListener(object : ClickListener() {
            override fun clicked(event: InputEvent?, x: Float, y: Float) {
                super.clicked(event, x, y)
                if (deckNameWidget.isDisabled) {
                    val count = tapCount
                    if (count == 2) {
                        enterDeckName()
                    }
                }
            }
        })
    }

    private fun saveCurrentDeckName() {
        deckNameWidget.clearSelection()
        deckNameWidget.isDisabled = true
        SaveState.curDeck.name = deckNameWidget.text.toString()
    }

    private fun enterDeckName() {
        deckNameWidget.isDisabled = false
        stage.keyboardFocus = (deckNameWidget)
    }

    override fun display(): Timeline {
        TemplateString.updateGlobalParam("deck.name", SaveState.curDeck.name)
        return Timeline.timeline {
            action {
                println("now opening")
            }
            delay(200)
            action {
                println("now open")
            }
        }
    }

    override fun hide(): Timeline {
        return Timeline.timeline {
            action {
                println("now closing")
            }
            delay(200)
            action {
                println("now closed")
            }
        }
    }

    companion object {
        private val backpackFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/backpack.onjschema").file())
        }
        private val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
        }
        const val logTag: String = "Backpack"
    }
}