package com.fourinachamber.fourtyfive.game

import com.badlogic.gdx.Gdx
import com.fourinachamber.fourtyfive.card.Card
import onj.*

object SaveState {

    const val saveFilePath: String = "saves/savefile.onj"


    private var _additionalCards: MutableMap<String, Int> = mutableMapOf()

    private var _cardsToDraw: MutableMap<String, Int> = mutableMapOf()

    val additionalCards: Map<String, Int>
        get() = _additionalCards

    val cardsToDraw: Map<String, Int>
        get() = _cardsToDraw

    private val savefileSchema: OnjSchema by lazy {
        OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/savefile.onjschema").file())
    }

    fun drawCard(card: Card) {
        if (!_cardsToDraw.containsKey(card.name)) {
            throw RuntimeException("cannot draw card $card because it dosen't exist")
        }
        _cardsToDraw[card.name] = _cardsToDraw[card.name]!! - 1
        if (_cardsToDraw[card.name]!! <= 0) _cardsToDraw.remove(card.name)

        if (_additionalCards.containsKey(card.name)) {
            _additionalCards[card.name] = _additionalCards[card.name]!! + 1
        } else {
            _additionalCards[card.name] = 1
        }
    }

    fun read() {
        val obj = OnjParser.parseFile(Gdx.files.internal(saveFilePath).file())
        savefileSchema.assertMatches(obj)
        obj as OnjObject

        _additionalCards = readCardArray(obj.get<OnjArray>("additionalCards")).toMutableMap()
        _cardsToDraw = readCardArray(obj.get<OnjArray>("cardsToDraw")).toMutableMap()
    }

    private fun readCardArray(arr: OnjArray): Map<String, Int> = arr
        .value
        .associate {
            it as OnjObject
            it.get<String>("name") to it.get<Long>("amount").toInt()
        }

    fun write() {
        val obj = buildOnjObject {
            "additionalCards" with getCardArray(_additionalCards)
            "cardsToDraw" with getCardArray(_cardsToDraw)
        }
        Gdx.files.local(saveFilePath).file().writeText(obj.toString())
    }

    private fun getCardArray(cards: Map<String, Int>) = cards.entries.map {
        buildOnjObject {
            "name" with it.key
            "amount" with it.value
        }
    }

}