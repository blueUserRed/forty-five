package com.fourinachamber.fortyfive.game.card

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.MoveByAction
import com.badlogic.gdx.scenes.scene2d.actions.ScaleToAction
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.utils.Disposable
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.game.GameController.RevolverRotation
import com.fourinachamber.fortyfive.onjNamespaces.OnjEffect
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.utils.*
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.*

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
    val tags: List<String>,
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
    font: PixmapFont,
    fontColor: Color,
    fontScale: Float,
    screen: OnjScreen
): Disposable {

    /**
     * used for logging
     */
    val logTag = "card-$name-${++instanceCounter}"

    /**
     * the actor for representing the card on the screen
     */
    val actor: CardActor

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
    val curDamage: Int
        get() = (baseDamage + _modifiers.sumOf { it.damage }).coerceAtLeast(0)

    var isEverlasting: Boolean = false
        private set
    var isUndead: Boolean = false
        private set
    var isRotten: Boolean = false
        private set
    var isReplaceable: Boolean = false
        private set
    var isSpray: Boolean = false
        private set

    val shouldRemoveAfterShot: Boolean
        get() = !(isEverlasting || _modifiers.any { it.everlasting })

    private lateinit var rottenModifier: CardModifier

    private var lastDamageValue: Int = baseDamage

    private val _modifiers: MutableList<CardModifier> = mutableListOf()

    val modifiers: List<CardModifier>
        get() = _modifiers

    var currentHoverText: String = ""
        private set

    init {
        screen.borrowResource(cardTexturePrefix + name)
        actor = CardActor(
            this,
            font,
            fontColor,
            fontScale,
            screen
        )
    }

    /**
     * checks if the modifiers of this card are still valid and removes them if they are not
     */
    fun checkModifierValidity() {
        val iterator = _modifiers.iterator()
        while (iterator.hasNext()) {
            val modifier = iterator.next()
            if (!modifier.validityChecker()) {
                FortyFiveLogger.debug(logTag, "modifier no longer valid: $modifier")
                iterator.remove()
                modifiersChanged()
            }
        }
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
        _modifiers.clear()
        modifiersChanged()
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
        _modifiers.add(modifier)
        modifiersChanged()
    }

    fun removeModifier(modifier: CardModifier) {
        _modifiers.remove(modifier)
        modifiersChanged()
    }

    private fun addRottenModifier() {
        val rotationTransformer = { oldModifier: CardModifier, triggerInformation: TriggerInformation -> CardModifier(
            damage = oldModifier.damage - (triggerInformation.multiplier ?: 1),
            source = oldModifier.source,
            validityChecker = oldModifier.validityChecker,
            transformers = oldModifier.transformers
        )}
        val modifier = CardModifier(
            damage = 0,
            source = "rotten effect",
            validityChecker = { inGame },
            transformers = mapOf(
                Trigger.ON_REVOLVER_ROTATION to rotationTransformer
            )
        )
        addModifier(modifier)
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
    fun onRevolverRotation(rotation: RevolverRotation) {
    }

    fun inHand(controller: GameController): Boolean = this in controller.cardHand.cards

    /**
     * checks if the effects of this card respond to [trigger] and returns a timeline containing the actions for the
     * effects; null if no effect was triggered
     */
    @MainThreadOnly
    fun checkEffects(
        trigger: Trigger,
        triggerInformation: TriggerInformation,
        controller: GameController
    ): Timeline = Timeline.timeline {
        action {
            checkModifierTransformers(trigger, triggerInformation)
        }
        val inHand = inHand(controller)
        effects
            .filter { inGame || (inHand && it.triggerInHand) }
            .mapNotNull { it.checkTrigger(trigger, triggerInformation, controller) }
            .collectTimeline()
            .let { include(it) }
    }

    private fun checkModifierTransformers(trigger: Trigger, triggerInformation: TriggerInformation) {
        var modifierChanged = false
        _modifiers.replaceAll { modifier ->
            val transformer = modifier.transformers[trigger] ?: return@replaceAll modifier
            modifierChanged = true
            transformer(modifier, triggerInformation)
        }
        if (modifierChanged) modifiersChanged()
    }

    private fun modifiersChanged() {
        updateText()
        val newDamage = curDamage
        if (newDamage != lastDamageValue) {
            updateTexture()
            lastDamageValue = newDamage
        }
    }

    private fun updateText() {
        val text = StringBuilder()
        text
            .append(shortDescription)
            .append("\n")
            .append(flavourText)
            .append("\n")
            .append(if (type == Type.BULLET) "damage: $curDamage/$baseDamage" else "")

        val damageText = if (_modifiers.any { it.damage != 0 }) {
            _modifiers
                .filter { it.damage != 0 }
                .joinToString(separator =  ",", prefix = "Damage was changed by: ", transform = { it.source })
        } else {
            null
        }

        damageText?.let { text.append("\n").append(it) }

        currentHoverText = text.toString()
    }

    private fun updateTexture() = actor.redrawPixmap()

    override fun dispose() = actor.disposeTexture()

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
                        cardTypeOrError(onj),
                        onj.get<OnjArray>("tags").value.map { it.value as String },
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
                font = GraphicsConfig.cardFont(onjScreen),
                fontColor = GraphicsConfig.cardFontColor(),
                fontScale = GraphicsConfig.cardFontScale(),
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
                "replaceable" -> card.isReplaceable = true
                "spray" -> card.isSpray = true
                "rotten" -> {
                    card.isRotten = true
                    card.addRottenModifier()
                }

                else -> throw RuntimeException("unknown trait effect $effect")
            }
        }

        val cardsFileSchema: OnjSchema by lazy {
            OnjSchemaParser.parseFile("onjschemas/cards.onjschema")
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
     * @param validityChecker checks if the modifier is still valid or should be removed
     */
    data class CardModifier(
        val damage: Int,
        val everlasting: Boolean = false,
        val source: String,
        val validityChecker: () -> Boolean = { true },
        val transformers: Map<Trigger, (old: CardModifier, triggerInformation: TriggerInformation) -> CardModifier> = mapOf()
    )

}

/**
 * the actor representing a card on the screen
 */
class CardActor(
    val card: Card,
    val font: PixmapFont,
    val fontColor: Color,
    val fontScale: Float,
    private val screen: OnjScreen
) : Widget(), ZIndexActor, KeySelectableActor, DisplayDetailsOnHoverActor, HoverStateActor {

    override var actorTemplate: String = "card_hover_detail" // TODO: fix
    override var detailActor: Actor? = null

    override var fixedZIndex: Int = 0

    override var isHoveredOver: Boolean = false

    override var isSelected: Boolean = false
    override var partOfHierarchy: Boolean = true
    override var isClicked: Boolean=false

    /**
     * true when the card is dragged; set by [CardDragSource][com.fourinachamber.fortyfive.game.card.CardDragSource]
     */
    var isDragged: Boolean = false

    private var inGlowAnim: Boolean = false
    private var inDestroyAnim: Boolean = false

    private val glowShader: BetterShader by lazy {
        ResourceManager.get(screen, "glow_shader") // TODO: fix
    }
    private val destroyShader: BetterShader by lazy {
        ResourceManager.get(screen, "dissolve_shader") // TODO: fix
    }

    private val cardTexture: Texture = ResourceManager.get(screen, card.drawableHandle)

    private val pixmap: Pixmap = Pixmap(cardTexture.width, cardTexture.height, Pixmap.Format.RGBA8888)

    var pixmapTextureRegion: TextureRegion? = null
        private set

    private val cardTexturePixmap: Pixmap

    init {
        bindHoverStateListeners(this)
        registerOnHoverDetailActor(this, screen)
        if (!cardTexture.textureData.isPrepared) cardTexture.textureData.prepare()
        cardTexturePixmap = cardTexture.textureData.consumePixmap()
        redrawPixmap()
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        setBoundsOfHoverDetailActor(this)
        batch ?: return
        val texture = pixmapTextureRegion ?: return
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
        batch.draw(
            texture,
            x, y,
            width / 2, height / 2,
            width, height,
            scaleX, scaleY,
            rotation
        )
        batch.flush()
        shader?.let { batch.shader = null }
    }

    fun disposeTexture() {
        pixmapTextureRegion?.texture?.dispose()
        pixmap.dispose()
        pixmapTextureRegion = null
    }

    fun redrawPixmap() {
        pixmap.drawPixmap(cardTexturePixmap, 0, 0)
        font.write(pixmap, card.curDamage.toString(), 35, 480, fontScale, fontColor)
        font.write(pixmap, card.cost.toString(), 490, 28, fontScale, fontColor)
        pixmapTextureRegion?.texture?.dispose()
        val texture = Texture(pixmap, true)
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.MipMapLinearLinear)
        pixmapTextureRegion = TextureRegion(texture)
    }

    override fun getHighlightArea(): Rectangle {
        val (x, y) = localToScreenCoordinates(Vector2(0f, 0f))
        return Rectangle(x, y, width, height)
    }

    fun glowAnimation(): Timeline = Timeline.timeline {
//        action {
//            inGlowAnim = true
//            glowShader.resetReferenceTime()
//        }
//        delay(1000)
//        action { inGlowAnim = false }
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
//        // TODO: hardcoded values
//        var origScaleX = 0f
//        var origScaleY = 0f
//        val scaleAction = ScaleToAction()
//        val moveAction = MoveByAction()
//        val interpolation = Interpolation.fade
//        action {
//            origScaleX = scaleX
//            origScaleY = scaleY
//            scaleAction.setScale(origScaleX * 1.3f, origScaleY * 1.3f)
//            moveAction.setAmount(
//                -(width * origScaleX * 1.3f - width * origScaleX) / 2,
//                -(height * origScaleY * 1.3f - height * origScaleY) / 2,
//            )
//            moveAction.duration = 0.1f
//            scaleAction.duration = 0.1f
//            scaleAction.interpolation = interpolation
//            moveAction.interpolation = interpolation
//            addAction(scaleAction)
//            addAction(moveAction)
//        }
//        delayUntil { scaleAction.isComplete || !card.inGame }
//        if (includeGlow) {
//            delay(GraphicsConfig.bufferTime)
//            include(glowAnimation())
//        }
//        delay(GraphicsConfig.bufferTime)
//        action {
//            removeAction(scaleAction)
//            val moveAmount = -Vector2(moveAction.amountX, moveAction.amountY)
//            removeAction(moveAction)
//            scaleAction.reset()
//            moveAction.reset()
//            scaleAction.setScale(origScaleX, origScaleY)
//            moveAction.setAmount(moveAmount.x, moveAmount.y)
//            scaleAction.duration = 0.2f
//            moveAction.duration = 0.2f
//            scaleAction.interpolation = interpolation
//            moveAction.interpolation = interpolation
//            addAction(scaleAction)
//            addAction(moveAction)
//        }
//        delayUntil { scaleAction.isComplete || !card.inGame }
//        action {
//            removeAction(scaleAction)
//            removeAction(moveAction)
//        }
//        delay(GraphicsConfig.bufferTime)
    }

    override fun getHoverDetailData(): Map<String, OnjValue> = mapOf(
        "text" to OnjString(card.currentHoverText)
    )

    override fun positionChanged() {
        super.positionChanged()
        setBoundsOfHoverDetailActor(this)
    }

    override fun sizeChanged() {
        super.sizeChanged()
        setBoundsOfHoverDetailActor(this)
    }
}
