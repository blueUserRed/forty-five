package com.fourinachamber.fortyfive.map.statusbar

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.game.SaveState
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.map.shop.ShopCardsHandler
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.Timeline
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjObject

class Backpack(screen: OnjScreen, dataFile: String) : CustomFlexBox(screen), InOutAnimationActor {

    private val _allCards: MutableList<Card>
    private val cardImgs: MutableList<CustomImageActor> = mutableListOf()

    init {
        val onj = OnjParser.parseFile(dataFile)
        cardsFileSchema.assertMatches(onj)
        onj as OnjObject
        val cardPrototypes = (Card.getFrom(onj.get<OnjArray>("cards"), screen) {}).filter { it.name in SaveState.cards }
        _allCards = cardPrototypes.map { it.create() }.toMutableList()
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