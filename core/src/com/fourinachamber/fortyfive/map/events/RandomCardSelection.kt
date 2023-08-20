package com.fourinachamber.fortyfive.map.events

import com.badlogic.gdx.Gdx
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.CardPrototype
import com.fourinachamber.fortyfive.utils.random
import onj.parser.OnjParser
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import java.lang.Exception
import kotlin.random.Random

@Suppress("MemberVisibilityCanBePrivate")
/**
 * generates with the [getRandomCards] method for [Card] and [CardPrototype] random values with certain types and restrictions
 */
object RandomCardSelection {

    private var allTypes: Map<String, List<CardChange>> = mapOf()

    /**
     * the path to the file from which the data is read from
     */
    private const val TYPES_FILE_PATH: String = "maps/events/cardSelectionTypes.onj"


    private val cardSelectionSchema: OnjSchema by lazy {
        OnjSchemaParser.parseFile(Gdx.files.internal("onjschemas/cardSelectionTypes.onjschema").file())
    }

    fun init() {
        val file = OnjParser.parseFile(Gdx.files.internal(TYPES_FILE_PATH).file())
        cardSelectionSchema.assertMatches(file)
        file as OnjObject
        val allTypes: MutableMap<String, List<CardChange>> = mutableMapOf()
        val allTypesOnj = file.get<OnjArray>("types").value.map { it as OnjObject }
        allTypesOnj.forEach {
            val name = it.get<String>("name")
            val changes = it.get<OnjArray>("cardChanges")
                .value
                .map { e -> e as OnjObject }
                .map { e -> CardChange.getFromOnj(e) }
            allTypes[name] = changes
        }
        this.allTypes = allTypes
    }

    /**
     * @param cards the cards to take as source (can have doubles)
     * @param changeNames the names of the types you want to apply to
     * @param cardsMaxOnce if true, then all chosen cards will be removed from the remaining selection and can therefore be there only once
     * @param nbrOfCards how many cards you want. However, there can be fewer cards remaining after applying the effects
     * @throws Exception if a type is not known
     */
    fun <T> getRandomCards(
        cards: List<T>,
        changeNames: List<String>,
        cardsMaxOnce: Boolean = false,
        nbrOfCards: Int,
        rnd: Random = Random(0),
    ): List<T> {
        if (nbrOfCards >= cards.size && cardsMaxOnce) return cards
        val (tempCards, tempChances) = getCardsWithChances(cards.toMutableList(), changeNames)
        return getCardsFromChances(nbrOfCards, tempCards, tempChances, rnd, cardsMaxOnce)
    }

    fun <T> getCardsFromChances(
        nbrOfCards: Int,
        tempCards: MutableList<T>,
        tempChances: MutableList<Float>,
        rnd: Random,
        cardsMaxOnce: Boolean
    ): MutableList<T> {
        val res: MutableList<T> = mutableListOf()
        for (i in 0 until nbrOfCards) {
            if (tempCards.size == 0) break
            val index = getRandomIndex(tempChances, rnd)
            res.add(tempCards[index])
            if (cardsMaxOnce) {
                tempCards.removeAt(index)
                tempChances.removeAt(index)
            }
        }
        return res
    }

    fun <T> getCardsWithChances(
        cards: List<T>,
        changeNames: List<String>
    ): Pair<MutableList<T>, MutableList<Float>> {
        val tempCards = cards.toMutableList()
        val tempChances = tempCards.map { 0F }.toMutableList()
        changeNames.forEach { name ->
            allTypes[name]!!.forEach {
                it.applyEffects(tempCards, tempChances)
            }
        }
        return Pair(tempCards, tempChances)
    }

    private fun getRandomIndex(chances: MutableList<Float>, rnd: Random): Int {
        val maxWeight = chances.sum()
        val value = (0.0F..maxWeight).random(rnd)
        var curSum = 0.0
        for (i in chances.indices) {
            curSum += chances[i]
            if (curSum > value) return i
        }
        return chances.size - 1
    }
}

interface CardChange {
    val selector: Selector

    fun <T> applyEffects(cards: MutableList<T>, chances: MutableList<Float>)

    companion object {
        fun getFromOnj(onj: OnjObject): CardChange {
            val selector = Selector.getFromOnj(onj.get<OnjNamedObject>("select"))
            val effect = onj.get<OnjNamedObject>("effect")
            return when (effect.name) {
                "Blacklist" -> {
                    BlackList(selector)
                }

                "ProbabilityAddition" -> {
                    ProbabilityAddition(selector, effect.get<Double>("weight").toFloat())
                }

                "PriceMultiplier" -> {
                    PriceMultiplier(selector, effect.get<Double>("price"))
                }

                else -> throw Exception("Unknown card change: ${effect.name}")
            }
        }
    }

    class BlackList(override val selector: Selector) : CardChange {
        override fun <T> applyEffects(cards: MutableList<T>, chances: MutableList<Float>) {
            var i = 0
            while (i < cards.size) {
                if (selector.isPartOf(cards[i])) {
                    cards.removeAt(i)
                    chances.removeAt(i)
                } else i += 1
            }
        }
    }

    class ProbabilityAddition(override val selector: Selector, private val probChange: Float) : CardChange {
        override fun <T> applyEffects(cards: MutableList<T>, chances: MutableList<Float>) {
            for (i in cards.indices) {
                if (selector.isPartOf(cards[i])) chances[i] += probChange
            }
        }
    }

    class PriceMultiplier(override val selector: Selector, private val priceMulti: Double) : CardChange {
        override fun <T> applyEffects(cards: MutableList<T>, chances: MutableList<Float>) {
            cards.filterIsInstance<Card>().filter { selector.isPartOf(it) }.forEach {
                it.price = (it.price * priceMulti).toInt()
            }
        }
    }
}

interface Selector {
    fun <T> isPartOf(card: T): Boolean

    companion object {
        fun getFromOnj(onj: OnjNamedObject): Selector {
            return when (onj.name) {
                "ByName" -> ByNameSelector(onj.get<String>("name"))
                "ByTag" -> ByTagSelector(onj.get<String>("name"))
                else -> throw Exception("Unknown card change: ${onj.name}")

            }
        }
    }

    class ByNameSelector(private val name: String) : Selector {
        override fun <T> isPartOf(card: T): Boolean =
            if (card is Card) card.name == name else if (card is CardPrototype) card.name == name else false

    }

    class ByTagSelector(private val name: String) : Selector {
        override fun <T> isPartOf(card: T): Boolean =
            if (card is Card)  name in card.tags else if (card is CardPrototype) name in card.tags else false
    }
}