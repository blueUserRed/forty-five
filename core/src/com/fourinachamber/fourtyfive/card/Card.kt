package com.fourinachamber.fourtyfive.card

import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.fourinachamber.fourtyfive.game.Effect
import com.fourinachamber.fourtyfive.game.GameScreenController
import com.fourinachamber.fourtyfive.game.OnjExtensions
import com.fourinachamber.fourtyfive.game.Trigger
import com.fourinachamber.fourtyfive.screen.CustomImageActor
import com.fourinachamber.fourtyfive.screen.ZIndexActor
import com.fourinachamber.fourtyfive.utils.TemplateString
import com.fourinachamber.fourtyfive.utils.Timeline
import com.fourinachamber.fourtyfive.utils.toBuilder
import ktx.actors.onEnter
import ktx.actors.onExit
import onj.OnjArray
import onj.OnjNamedObject
import onj.OnjObject

/**
 * represents a card
 * @param name the name of the card
 * @param title the properly formatted name of the card, used for displaying
 * @param texture the texture for displaying the card
 * @param description the description of the card
 * @param type the CardType
 * @param baseDamage the base-damage of the card, before things like effects are applied
 */
class Card(
    val name: String,
    val title: String,
    val texture: TextureRegion,
    val shortDescription: String,
    val type: Type,
    val baseDamage: Int,
    val coverValue: Int,
    val cost: Int,
    val effects: List<Effect>
) {

    /**
     * the actor for representing the card on the screen
     */
    val actor = CardActor(this)

    /**
     * true when the card can be dragged
     */
    var isDraggable: Boolean = true

    /**
     * true when [actor] is in an animation
     */
    var inAnimation: Boolean = false

    var inGame: Boolean = false
        private set

    var curDamage: Int = baseDamage
        private set
        get() {
            if (!isDamageDirty) return field
            var cur = baseDamage
            for (modifier in modifiers) cur += modifier.damage
            field = cur
            isDamageDirty = false
            return cur
        }

    var description = ""
        private set

    private var isEverlasting: Boolean = false
    private var isUndead: Boolean = false
    private var isRotten: Boolean = false
    private var isLeftRotating: Boolean = false

    val shouldRemoveAfterShot: Boolean
        get() = !isEverlasting

    val shouldRotateLeft: Boolean
        get() = isLeftRotating

    private lateinit var rottenModifier: CardModifier

    private val modifiers: MutableList<CardModifier> = mutableListOf()

    private var isDamageDirty: Boolean = true

    init {
        updateText()
    }

    private fun initRottenModifier() {
        rottenModifier = CardModifier(0, null) { true }
        modifiers.add(rottenModifier)
        updateText()
    }

    private fun updateRottenModifier(newDamage: Int) {
        modifiers.remove(rottenModifier)
        rottenModifier = CardModifier(
            newDamage,
            TemplateString(rottenDetailTextRawString, mapOf("damageLost" to { newDamage }))
        ) { true }
        modifiers.add(rottenModifier)
        isDamageDirty = true
        updateText()
    }

    fun checkModifierValidity() {
        val iterator = modifiers.iterator()
        while (iterator.hasNext()) {
            val modifier = iterator.next()
            if (!modifier.validityChecker()) {
                iterator.remove()
                isDamageDirty = true
            }
        }
        if (isDamageDirty) updateText()
    }

    fun afterShot(gameScreenController: GameScreenController) {
        if (isUndead) {
            gameScreenController.cardHand!!.addCard(this)
        }
        inGame = false
    }

    fun addModifier(modifier: CardModifier) {
        modifiers.add(modifier)
        isDamageDirty = true
        updateText()
    }

    fun onEnter(gameScreenController: GameScreenController) {
        inGame = true
    }

    fun onRoundStart(gameScreenController: GameScreenController) {
    }

    fun onRevolverTurn(toBeShot: Boolean) {
        if (isRotten && !toBeShot) updateRottenModifier(rottenModifier.damage - 1)
    }

    fun checkEffects(trigger: Trigger, gameScreenController: GameScreenController): Timeline? {
        var wasEffectWithTimelineTriggered = false
        val timeline = Timeline.timeline {
            for (effect in effects) {
                val effectTimeline = effect.checkTrigger(trigger, gameScreenController)
                if (effectTimeline != null) {
                    include(effectTimeline)
                    wasEffectWithTimelineTriggered = true
                }
            }
        }
        return if (wasEffectWithTimelineTriggered) timeline else null
    }

    private fun updateText() {
        val builder = """
            
            $shortDescription
            
            cost: $cost
            ${
                if (type == Type.BULLET) {
                    "damage: $curDamage/$baseDamage"
                } else {
                    "cover value: $coverValue"
                }
            }
            
            
        """.trimIndent().toBuilder()

        for (modifier in modifiers) if (modifier.description != null) {
            builder.append(modifier.description.string).append("\n")
        }

        description = builder.toString()
    }

    override fun toString(): String {
        return "card: $name"
    }

    companion object {

        /**
         * all textures of cards are prefixed with this string
         */
        const val cardTexturePrefix = "card%%"

        /**
         * gets an array of cards from an OnjArray
         */
        fun getFrom(cards: OnjArray, regions: Map<String, TextureRegion>): List<Card> = cards
            .value
            .map { onj ->
                onj as OnjObject
                val name = onj.get<String>("name")

                val card = Card(
                    name,
                    onj.get<String>("title"),
                    regions["$cardTexturePrefix$name"]
                        ?: throw RuntimeException("cannot find texture for card $name"),

                    onj.get<String>("description"),

                    when (val type = onj.get<OnjNamedObject>("type").name) {
                        "Bullet" -> Type.BULLET
                        "Cover" -> Type.COVER
                        "OneShot" -> Type.ONE_SHOT
                        else -> throw RuntimeException("unknown Card type: $type")
                    },

                    onj.get<Long>("baseDamage").toInt(),
                    onj.get<Long>("coverValue").toInt(),
                    onj.get<Long>("cost").toInt(),

                    onj.get<OnjArray>("effects")
                        .value
                        .map { (it as OnjExtensions.OnjEffect).value }
                )

                for (effect in card.effects) effect.card = card
                applyTraitEffects(card, onj)
                card
            }

        fun applyTraitEffects(card: Card, onj: OnjObject) {
            val effects = onj
                .get<OnjArray>("traitEffects")
                .value
                .map { it.value as String }

            for (effect in effects) when (effect) {

                "everlasting" -> card.isEverlasting = true
                "undead" -> card.isUndead = true
                "rotten" -> {
                    card.isRotten = true
                    card.initRottenModifier()
                }
                "leftRotating" -> card.isLeftRotating = true

                else -> throw RuntimeException("unknown trait effect $effect")
            }
        }

        private lateinit var rottenDetailTextRawString: String

        fun init(config: OnjObject) {
            val tmplOnj = config.get<OnjObject>("stringTemplates")
            rottenDetailTextRawString = tmplOnj.get<String>("rottenDetailText")
        }

    }

    enum class Type {
        BULLET, COVER, ONE_SHOT
    }

    data class CardModifier(
        val damage: Int,
        val description: TemplateString?,
        val validityChecker: () -> Boolean
    )

}

/**
 * the actor representing a card
 */
class CardActor(val card: Card) : CustomImageActor(card.texture), ZIndexActor {

    override var fixedZIndex: Int = 0

    /**
     * true when the card is dragged; set by [CardDragSource][com.fourinachamber.fourtyfive.card.CardDragSource]
     */
    var isDragged: Boolean = false

    /**
     * true when the actor is hovered over
     */
    var isHoveredOver: Boolean = false
        private set

    init {
        onEnter { isHoveredOver = true }
        onExit { isHoveredOver = false }
    }

}
