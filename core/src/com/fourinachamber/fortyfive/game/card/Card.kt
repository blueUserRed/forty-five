package com.fourinachamber.fortyfive.game.card

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.BitmapFont
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.EventListener
import com.badlogic.gdx.scenes.scene2d.InputEvent
import com.badlogic.gdx.scenes.scene2d.actions.MoveByAction
import com.badlogic.gdx.scenes.scene2d.actions.ScaleToAction
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.Effect
import com.fourinachamber.fortyfive.game.GameController
import com.fourinachamber.fortyfive.game.GameController.RevolverRotation
import com.fourinachamber.fortyfive.game.GraphicsConfig
import com.fourinachamber.fortyfive.game.Trigger
import com.fourinachamber.fortyfive.onjNamespaces.OnjEffect
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.gameComponents.CardDetailActor
import com.fourinachamber.fortyfive.screen.general.CustomImageActor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.*
import onj.value.OnjArray
import onj.value.OnjNamedObject
import onj.value.OnjObject

/**
 * represents a type of card, e.g. there is one Prototype for an incendiary bullet, but there might be more than one
 * actual instances of the card. Prototypes can be used to create those instances
 * @param name the name of the card produced by this prototype
 * @param type the type of card (bullet or cover)
 * @param creator lambda that creates the instance
 */
class CardPrototype(
    val name: String,
    val type: Card.Type,
    private val creator: () -> Card
) {

    /**
     * creates an actual instance of this card
     */
    fun create(): Card = creator()
}

/**
 * represents an actual instance of a card. Can be created using [CardPrototype]
 * @param name the name of the card
 * @param title the name but formatted, so it looks good when shown on the screen
 * @param flavourText Short phrase that (should) be funny or add to the lore
 * @param shortDescription short text explaining the effects of this card; can be left blank
 * @param type the type of card (bullet or cover)
 * @param baseDamage the damage value of the card before modifiers are applied (typically 0 when this is a cover)
 * @param coverValue the cover this card provides (typically 0 when this is a bullet)
 * @param cost the cost of this card in reserves
 * @param effects the effects of this card
 */
class Card(
    val name: String,
    val title: String,
    val drawableHandle: ResourceHandle,
    val flavourText: String,
    val shortDescription: String,
    val type: Type,
    val baseDamage: Int,
    val cost: Int,
    var price: Int,
    val effects: List<Effect>,
    val rotationDirection: RevolverRotation,
    val highlightType: HighlightType,
    val tags: List<String>,
    detailFont: BitmapFont,
    detailFontColor: Color,
    detailFontScale: Float,
    detailBackgroundHandle: ResourceHandle,
    detailSpacing: Float,
    screen: OnjScreen
) {

    /**
     * used for logging
     */
    val logTag = "card-$name-${++instanceCounter}"

    /**
     * the actor for representing the card on the screen
     */
    val actor = CardActor(
        this,
        detailFont,
        detailFontColor,
        detailFontScale,
        detailBackgroundHandle,
        detailSpacing,
        screen
    )

    //TODO: isDraggable and inAnimation should be in the actor class

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

    /**
     * the current damage with all modifiers applied
     */
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

    private var isEverlasting: Boolean = false
    private var isUndead: Boolean = false
    private var isRotten: Boolean = false

    val shouldRemoveAfterShot: Boolean
        get() = !(isEverlasting || modifiers.any { it.everlasting })

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
            TemplateString(
                GraphicsConfig.rawTemplateString("rottenDetailText"),
                mapOf("damageLost" to newDamage)
            )
        ) { true }
        modifiers.add(rottenModifier)
        isDamageDirty = true
        updateText()
    }

    /**
     * checks if the modifiers of this card are still valid and removes them if they are not
     */
    fun checkModifierValidity() {
        val iterator = modifiers.iterator()
        while (iterator.hasNext()) {
            val modifier = iterator.next()
            if (!modifier.validityChecker()) {
                FortyFiveLogger.debug(logTag, "modifier no longer valid: $modifier")
                iterator.remove()
                isDamageDirty = true
            }
        }
        if (isDamageDirty) updateText()
    }

    /**
     * called by gameScreenController when the card was shot
     */
    fun afterShot() {
        if (isUndead) {
            FortyFiveLogger.debug(logTag, "undead card is respawning in hand after being shot")
            FortyFive.currentGame!!.cardHand.addCard(this)
        }
        if (shouldRemoveAfterShot) leaveGame()
    }

    fun leaveGame() {
        inGame = false
        modifiers.clear()
        isDamageDirty = true
        updateText()
    }

    /**
     * checks whether this card can currently enter the game
     */
    fun allowsEnteringGame(controller: GameController): Boolean = !effects.any { it.blocks(controller) }

    /**
     * called when this card was destroyed by the destroy effect
     */
    fun onDestroy() {
        if (isUndead) {
            FortyFiveLogger.debug(logTag, "undead card is respawning in hand after being destroyed")
            FortyFive.currentGame!!.cardHand.addCard(this)
        }
        leaveGame()
    }

    /**
     * adds a new modifier to the card
     */
    fun addModifier(modifier: CardModifier) {
        FortyFiveLogger.debug(logTag, "card got new modifier: $modifier")
        modifiers.add(modifier)
        isDamageDirty = true
        updateText()
    }

    /**
     * called when the card enters the game
     */
    fun onEnter() {
        inGame = true
    }

    /**
     * called when the revolver rotates (but not when this card was shot)
     */
    fun onRevolverTurn() {
        if (isRotten) updateRottenModifier(rottenModifier.damage - 1)
    }

    /**
     * checks if the effects of this card respond to [trigger] and returns a timeline containing the actions for the
     * effects; null if no effect was triggered
     */
    @MainThreadOnly
    fun checkEffects(trigger: Trigger): Timeline? {
        var wasEffectWithTimelineTriggered = false
        val timeline = Timeline.timeline {
            for (effect in effects) {
                val effectTimeline = effect.checkTrigger(trigger)
                if (effectTimeline != null) {
                    include(effectTimeline)
                    wasEffectWithTimelineTriggered = true
                }
            }
        }
        return if (wasEffectWithTimelineTriggered) timeline else null
    }

    private fun updateText() {
        val detail = actor.hoverDetailActor
        detail.description = shortDescription
        detail.flavourText = flavourText
        detail.statsText = if (type == Type.BULLET) "damage: $curDamage/$baseDamage" else ""

        val builder = StringBuilder()
        for (modifier in modifiers) if (modifier.description != null) {
            builder.append(modifier.description.string).append("\n")
        }
        val modifiersText = builder.toString()
        detail.statsChangedText = modifiersText
    }

    override fun toString(): String {
        return "card: $name"
    }

    companion object {

        /**
         * all textures of cards are prefixed with this string
         */
        const val cardTexturePrefix = "card%%"

        private var instanceCounter = 0

        /**
         * reads an onj array it returns the corresponding card prototypes
         * @param cards the onj array
         * @param initializer lambda that can contain additional initialization logic
         */
        fun getFrom(
            cards: OnjArray,
            onjScreen: OnjScreen,
            initializer: (Card) -> Unit
        ): List<CardPrototype> {

            val prototypes = mutableListOf<CardPrototype>()

            cards
                .value
                .forEach { onj ->
                    onj as OnjObject
                    val prototype = CardPrototype(
                        onj.get<String>("name"),
                        cardTypeOrError(onj)
                    ) { getCardFrom(onj, onjScreen, initializer) }
                    prototypes.add(prototype)
                }
            return prototypes
        }

        @MainThreadOnly
        private fun getCardFrom(
            onj: OnjObject,
            onjScreen: OnjScreen,
            initializer: (Card) -> Unit
        ): Card {
            val name = onj.get<String>("name")

            val card = Card(
                name = name,
                title = onj.get<String>("title"),
                drawableHandle = "$cardTexturePrefix$name",
                flavourText = onj.get<String>("flavourText"),
                shortDescription = onj.get<String>("description"),
                type = cardTypeOrError(onj),
                baseDamage = onj.get<Long>("baseDamage").toInt(),
                cost = onj.get<Long>("cost").toInt(),
                price = onj.get<Long>("price").toInt(),
                effects = onj.get<OnjArray>("effects")
                    .value
                    .map { (it as OnjEffect).value.copy() }, //TODO: find a better solution
                rotationDirection = RevolverRotation.fromOnj(onj.get<OnjNamedObject>("rotation")),
                highlightType = HighlightType.valueOf(onj.get<String>("highlightType").uppercase()),
                tags = onj.get<OnjArray>("tags").value.map { it.value as String },
                //TODO: CardDetailActor could call these functions itself
                detailFont = GraphicsConfig.cardDetailFont(onjScreen),
                detailFontColor = GraphicsConfig.cardDetailFontColor(),
                detailFontScale = GraphicsConfig.cardDetailFontScale(),
                detailBackgroundHandle = GraphicsConfig.cardDetailBackground(),
                detailSpacing = GraphicsConfig.cardDetailSpacing(),
                screen = onjScreen
            )

            for (effect in card.effects) effect.card = card
            applyTraitEffects(card, onj)
            initializer(card)
            return card
        }

        private fun cardTypeOrError(onj: OnjObject) = when (val type = onj.get<OnjNamedObject>("type").name) {
            "Bullet" -> Type.BULLET
            "OneShot" -> Type.ONE_SHOT
            else -> throw RuntimeException("unknown Card type: $type")
        }


        private fun applyTraitEffects(card: Card, onj: OnjObject) {
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

                else -> throw RuntimeException("unknown trait effect $effect")
            }
        }

    }

    /**
     * a type of card
     */
    enum class Type {
        BULLET, ONE_SHOT
    }

    enum class HighlightType {
        STANDARD, GLOW
    }

    /**
     * temporarily modifies a card. For example used by the buff damage effect to change the damage of a card
     * @param damage changes the damage of the card. Can be negative
     * @param description if not null this description will be displayed in the detail text of the modified card
     * @param validityChecker checks if the modifier is still valid or should be removed
     */
    data class CardModifier(
        val damage: Int,
        val description: TemplateString?,
        val everlasting: Boolean = false,
        val validityChecker: () -> Boolean
    )

}

/**
 * the actor representing a card on the screen
 */
class CardActor(
    val card: Card,
    font: BitmapFont,
    fontColor: Color,
    fontScale: Float,
    detailBackgroundHandle: ResourceHandle,
    detailSpacing: Float,
    private val screen: OnjScreen
) : CustomImageActor(card.drawableHandle, screen) {

    override var isSelected: Boolean = false

    override var partOfHierarchy: Boolean = true

    /**
     * true when the card is dragged; set by [CardDragSource][com.fourinachamber.fortyfive.game.card.CardDragSource]
     */
    var isDragged: Boolean = false

    val hoverDetailActor = CardDetailActor(
        card = card,
        initialFlavourText = "",
        initialDescription = "",
        initialStatsText = "",
        initialStatsChangedText = "",
        font = font,
        fontColor = fontColor,
        fontScale = fontScale,
        initialForcedWidth = 0f,
        backgroundHandle = detailBackgroundHandle,
        spacing = detailSpacing,
        screen = screen
    )

    private var inGlowAnim: Boolean = false
    private var inDestroyAnim: Boolean = false

    private val glowShader: BetterShader by lazy {
        ResourceManager.get(screen, "glow_shader") // TODO: fix
    }
    private val destroyShader: BetterShader by lazy {
        ResourceManager.get(screen, "dissolve_shader") // TODO: fix
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        validate()
        if (batch == null || drawable == null) {
            super.draw(batch, parentAlpha)
            return
        }
        val shader = if (inGlowAnim) {
            glowShader
        } else if (inDestroyAnim) {
            destroyShader
        } else {
            null
        }
        shader?.let {
            batch.flush()
            it.shader.bind()
            it.prepare(screen)
            batch.shader = it.shader
        }
        super.draw(batch, parentAlpha)
        batch.flush()
        shader?.let { batch.shader = null }
    }

    fun glowAnimation(): Timeline = Timeline.timeline {
        action {
            inGlowAnim = true
            glowShader.resetReferenceTime()
        }
        delay(1000)
        action { inGlowAnim = false }
    }

    fun destroyAnimation(): Timeline = Timeline.timeline {
        action {
            inDestroyAnim = true
            destroyShader.resetReferenceTime()
        }
        delay(2000)
        action { inDestroyAnim = false }
    }

    fun growAnimation(includeGlow: Boolean): Timeline = Timeline.timeline {
        // TODO: hardcoded values
        var origScaleX = 0f
        var origScaleY = 0f
        val scaleAction = ScaleToAction()
        val moveAction = MoveByAction()
        val interpolation = Interpolation.fade
        action {
            origScaleX = scaleX
            origScaleY = scaleY
            scaleAction.setScale(origScaleX * 1.3f, origScaleY * 1.3f)
            moveAction.setAmount(
                -(width * origScaleX * 1.3f - width * origScaleX) / 2,
                -(height * origScaleY * 1.3f - height * origScaleY) / 2,
            )
            moveAction.duration = 0.1f
            scaleAction.duration = 0.1f
            scaleAction.interpolation = interpolation
            moveAction.interpolation = interpolation
            addAction(scaleAction)
            addAction(moveAction)
        }
        delayUntil { scaleAction.isComplete || !card.inGame }
        if (includeGlow) {
            delay(GraphicsConfig.bufferTime)
            include(glowAnimation())
        }
        delay(GraphicsConfig.bufferTime)
        action {
            removeAction(scaleAction)
            val moveAmount = -Vector2(moveAction.amountX, moveAction.amountY)
            removeAction(moveAction)
            scaleAction.reset()
            moveAction.reset()
            scaleAction.setScale(origScaleX, origScaleY)
            moveAction.setAmount(moveAmount.x, moveAmount.y)
            scaleAction.duration = 0.2f
            moveAction.duration = 0.2f
            scaleAction.interpolation = interpolation
            moveAction.interpolation = interpolation
            addAction(scaleAction)
            addAction(moveAction)
        }
        delayUntil { scaleAction.isComplete || !card.inGame }
        action {
            removeAction(scaleAction)
            removeAction(moveAction)
        }
        delay(GraphicsConfig.bufferTime)
    }

}
