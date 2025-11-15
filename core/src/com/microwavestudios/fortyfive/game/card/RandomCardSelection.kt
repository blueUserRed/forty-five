package com.microwavestudios.fortyfive.game.card

import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.utils.random
import com.microwavestudios.fortyfive.utils.zipIndexed
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject
import kotlin.random.Random


object RandomCardSelection {

    val allCardPrototypes: List<CardPrototype> by lazy {
        val onj = ConfigFileManager.getConfigFile("cards")
        Card
            .getFrom(onj.get<OnjArray>("cards"), initializer = {})
    }

    fun getRandomCards(
        typeNames: List<String>,
        nbrOfCards: Int,
        biome: String,
        difficulty: Int,
        random: Random = Random,
        unique: Boolean = true,
    ): List<CardPrototype> {
        require(nbrOfCards > 0) { "nbrOfCards must be positive" }
        val cardsWithProbabilities = cardsWithProbabilities(typeNames, biome, difficulty).toMutableList()
        if (cardsWithProbabilities.size <= nbrOfCards && unique) return cardsWithProbabilities.map { it.first }
        val chosenCards = mutableListOf<CardPrototype>()
        var weightsSum = cardsWithProbabilities.sumOf { it.second }
        repeat(nbrOfCards) {
            if (cardsWithProbabilities.isEmpty()) return@repeat
            val r = (0.0..weightsSum).random(random)
            var acc = 0.0
            var choice: Pair<CardPrototype, Double>? = null
            var i = 0
            while (choice == null) {
                require(i < cardsWithProbabilities.size)
                val element = cardsWithProbabilities[i]
                acc += element.second
                if (acc > r) choice = element
                i++
            }
            chosenCards.add(choice.first)
            if (unique) {
                cardsWithProbabilities.remove(choice)
                weightsSum -= choice.second
            }
        }
        return chosenCards
    }

    private fun cardsWithProbabilities(
        typeNames: List<String>,
        biome: String,
        difficulty: Int,
    ): List<Pair<CardPrototype, Double>> {
        val protos = allCardPrototypes.map { it.cleanCopy() }.toMutableList()
        val changes = collectCardChanges(typeNames, biome, difficulty)
        val weights = MutableList(protos.size) { 1f }
        changes.forEach { it.applyEffects(protos, weights) }
        return protos.zipIndexed { _, i -> weights[i].toDouble() }
    }

    private fun collectCardChanges(
        typeNames: List<String>,
        biome: String,
        difficulty: Int,
    ): List<CardChange> {
        val changesAcc = mutableListOf<CardChange>()

        var checkDiff = difficulty
        while (true) {
            if (checkDiff < 0) {
                throw RuntimeException("no card selection type defined for difficulty: $difficulty")
            }
            val changes = difficulties[checkDiff]
            if (changes != null) {
                changesAcc.addAll(changes)
                break
            }
            checkDiff--
        }

        biomes[biome]?.let { changesAcc.addAll(it) }
        typeNames.forEach { name ->
            val changes = types[name] ?: throw RuntimeException("unknown card selection type: $name")
            changesAcc.addAll(changes)
        }
        changesAcc.addAll(default)
        return changesAcc
    }


    private val configFile: OnjObject by lazy {
        ConfigFileManager.getConfigFile("cardSelectionTypeConfig")
    }

    private val types: Map<String, List<CardChange>> by lazy {
        configFile
            .get<OnjArray>("types")
            .value
            .associate { obj ->
                obj as OnjObject
                val changes = obj.get<OnjArray>("cardChanges").value.map { CardChange.getFromOnj(it as OnjObject) }
                obj.get<String>("name") to changes
            }
    }

    private val biomes: Map<String, List<CardChange>> by lazy {
        configFile
            .get<OnjArray>("biomes")
            .value
            .associate { obj ->
                obj as OnjObject
                val changes = obj.get<OnjArray>("changes").value.map { CardChange.getFromOnj(it as OnjObject) }
                obj.get<String>("name") to changes
            }
    }

    private val difficulties: Map<Int, List<CardChange>> by lazy {
        configFile
            .get<OnjArray>("difficulties")
            .value
            .associate { obj ->
                obj as OnjObject
                val changes = obj.get<OnjArray>("changes").value.map { CardChange.getFromOnj(it as OnjObject) }
                obj.get<Long>("difficulty").toInt() to changes
            }
    }

    private val default: List<CardChange> by lazy {
        configFile.get<OnjArray>("default").value.map { CardChange.getFromOnj(it as OnjObject) }
    }

}

interface CardChange {

    val selector: Selector

    fun applyEffects(cards: MutableList<CardPrototype>, chances: MutableList<Float>)

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

                "PriceAddition" -> {
                    PriceAddition(selector, effect.get<Long>("price").toInt())
                }

                else -> throw Exception("Unknown card change: ${effect.name}")
            }
        }
    }

    class BlackList(override val selector: Selector) : CardChange {

        override fun applyEffects(cards: MutableList<CardPrototype>, chances: MutableList<Float>) {
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

        override fun applyEffects(cards: MutableList<CardPrototype>, chances: MutableList<Float>) {
            for (i in cards.indices) {
                if (selector.isPartOf(cards[i])) chances[i] += probChange
            }
        }
    }

    class PriceMultiplier(override val selector: Selector, private val priceMulti: Double) : CardChange {

        override fun applyEffects(cards: MutableList<CardPrototype>, chances: MutableList<Float>) {
            cards
                .filter { selector.isPartOf(it) }
                .forEach { it.modifyPrice { old -> (old * priceMulti).toInt() } }
        }
    }

    class PriceAddition(override val selector: Selector, private val priceAddition: Int) : CardChange {

        override fun applyEffects(cards: MutableList<CardPrototype>, chances: MutableList<Float>) {
            cards
                .filter { selector.isPartOf(it) }
                .forEach { it.modifyPrice { old -> old + priceAddition } }
        }
    }
}

interface Selector {

    fun isPartOf(card: CardPrototype): Boolean

    companion object {

        fun getFromOnj(onj: OnjNamedObject): Selector = when (onj.name) {
            "ByName" -> ByNameSelector(onj.get<String>("name"))
            "ByTag" -> ByTagSelector(onj.get<String>("name"))
            "All" -> AllSelector
            else -> throw Exception("Unknown card change: ${onj.name}")
        }.let {
            if (onj.getOr("negate", false)) InvertingSelector(it) else it
        }

    }

    class InvertingSelector(private val selector: Selector) : Selector {

        override fun isPartOf(card: CardPrototype): Boolean = !selector.isPartOf(card)
    }

    class ByNameSelector(private val name: String) : Selector {

        override fun isPartOf(card: CardPrototype): Boolean = card.name == name

    }

    class ByTagSelector(private val name: String) : Selector {

        override fun isPartOf(card: CardPrototype): Boolean = name in card.tags
    }

    object AllSelector : Selector {

        override fun isPartOf(card: CardPrototype): Boolean = true
    }
}
