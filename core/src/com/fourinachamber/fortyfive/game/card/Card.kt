package com.fourinachamber.fortyfive.game.card

import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.*
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable
import com.badlogic.gdx.utils.Disposable
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.game.GameController.RevolverRotation
import com.fourinachamber.fortyfive.onjNamespaces.OnjEffect
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.screen.general.styles.*
import com.fourinachamber.fortyfive.utils.*
import ktx.actors.alpha
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.*
import kotlin.math.max
import kotlin.math.min

/**
 * represents a type of card, e.g. there is one Prototype for an incendiary bullet, but there might be more than one
 * actual instances of the card. Prototypes can be used to create those instances
 * @param name the name of the card produced by this prototype
 * @param type the type of card (bullet or cover)
 * @param creator lambda that creates the instance
 */
class CardPrototype(
    val name: String,
    val title: String,
    val type: Card.Type,
    val tags: List<String>,
    val forceLoadCards: List<String>,
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
    val tags: List<String>,
    isDark: Boolean,
    val forbiddenSlots: List<Int>,
    font: PixmapFont,
    fontScale: Float,
    screen: OnjScreen
) : Disposable {

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
        get() = !(isEverlasting || protectingModifiers.isNotEmpty())

    private var lastDamageValue: Int = baseDamage

    private var modifierCounter: Int = 0
    private val _modifiers: MutableList<Pair<Int, CardModifier>> = mutableListOf()

    val modifiers: List<CardModifier>
        get() = _modifiers.map { it.second }

    /**
     * first ist the keyword, second is the actual text
     */
    var currentHoverTexts: List<Pair<String, String>> = listOf()
        private set

    private var protectingModifiers: MutableList<Triple<String, Int, () -> Boolean>> = mutableListOf()

    private var modifierValuesDirty = true

    init {
        screen.borrowResource(cardTexturePrefix + name)
        actor = CardActor(
            this,
            font,
            fontScale,
            isDark,
            screen
        )
    }

    /**
     * checks if the modifiers of this card are still valid and removes them if they are not
     */
    private fun checkModifierValidity(controller: GameController) {
        val modifierIterator = _modifiers.iterator()
        var somethingChanged = false
        while (modifierIterator.hasNext()) {
            val (_, modifier) = modifierIterator.next()
            if (!modifier.validityChecker()) {
                FortyFiveLogger.debug(logTag, "modifier no longer valid: $modifier")
                modifierIterator.remove()
                somethingChanged = true
            }
            val active = modifier.activeChecker(controller)
            if (active == modifier.wasActive) continue
            modifier.wasActive = active
            somethingChanged = true
        }
        val protectingIterator = protectingModifiers.iterator()
        while (protectingIterator.hasNext()) {
            val modifier = protectingIterator.next()
            if (!modifier.third()) {
                FortyFiveLogger.debug(logTag, "protecting modifier no longer valid: $modifier")
                protectingIterator.remove()
                somethingChanged = true
            }
        }
        if (somethingChanged) modifiersChanged()
    }

    fun update(controller: GameController) {
        checkModifierValidity(controller)
        if (modifierValuesDirty) {
            updateText(controller)
            val newDamage = curDamage(controller)
            if (newDamage != lastDamageValue) {
                updateTexture(controller)
                lastDamageValue = newDamage
            }
            modifierValuesDirty = false
        }
    }

    fun activeModifiers(controller: GameController): List<CardModifier> =
        modifiers.filter { it.activeChecker(controller) }

    fun curDamage(controller: GameController): Int = _modifiers
        .filter { (_, modifier) -> modifier.activeChecker(controller) }
        .sortedBy { it.first }
        .fold(baseDamage) { acc, (_, modifier) -> ((acc + modifier.damage) * modifier.damageMultiplier).toInt() }
        .coerceAtLeast(0)

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

    fun beforeShot() {
        if (protectingModifiers.isEmpty()) return
        val effect = protectingModifiers.first()
        val newEffect = effect.copy(second = effect.second - 1)
        if (newEffect.second == 0) {
            protectingModifiers.removeFirst()
        } else {
            protectingModifiers[0] = newEffect
        }
        modifiersChanged()
    }

    fun leaveGame() {
        inGame = false
        _modifiers.clear()
        modifiersChanged()
    }

    fun protect(source: String, protectedFor: Int, validityChecker: () -> Boolean) {
        FortyFiveLogger.debug(logTag, "$source protected $this for $protectedFor shots")
        protectingModifiers.add(Triple(source, protectedFor, validityChecker))
        modifiersChanged()
    }

    /**
     * checks whether this card can currently enter the game
     */
    fun allowsEnteringGame(controller: GameController, slot: Int): Boolean =
        slot !in forbiddenSlots && effects.none { it.blocks(controller) }

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
        _modifiers.add(++modifierCounter to modifier)
        modifiersChanged()
    }

    fun removeModifier(modifier: CardModifier) {
        _modifiers.removeIf { (_, m) -> m === modifier }
        modifiersChanged()
    }

    private fun addRottenModifier() {
        val rotationTransformer = { oldModifier: CardModifier, triggerInformation: TriggerInformation ->
            CardModifier(
                damage = oldModifier.damage - (triggerInformation.multiplier ?: 1),
                source = oldModifier.source,
                validityChecker = oldModifier.validityChecker,
                transformers = oldModifier.transformers
            )
        }
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
        _modifiers.replaceAll { (counter, modifier) ->
            val transformer = modifier.transformers[trigger] ?: return@replaceAll counter to modifier
            modifierChanged = true
            counter to transformer(modifier, triggerInformation)
        }
        if (modifierChanged) modifiersChanged()
    }

    private fun modifiersChanged() {
        modifierValuesDirty = true
    }

    private fun updateText(controller: GameController) {
        val currentEffects = mutableListOf<Pair<String, String>>()
        if (activeModifiers(controller).any { it.damage != 0 }) {
            val allDamageEffects = activeModifiers(controller).filter { it.damage != 0 }
            val damageChange = allDamageEffects.sumOf { it.damage }
            val damageText = allDamageEffects
                .joinToString(
                    separator = ", ",
                    prefix = "${if (damageChange > 0) "+" else ""}$damageChange by ",
                    transform = { it.source })
            currentEffects.add("dmgBuff" to "\$dmgBuff\$$damageText\$dmgBuff\$")
            //this is the only special keyword, since it doesn't need a description
        }

        if (protectingModifiers.isNotEmpty()) {
            val total = protectingModifiers.sumOf { it.second }
            currentEffects.add("protected" to "\$trait\$+ PROTECTED ($total)\$trait\$")
        }

        currentHoverTexts = currentEffects
        actor.updateDetailStates()
    }

    private fun updateTexture(controller: GameController) = actor.redrawPixmap(curDamage(controller))

    override fun dispose() = actor.disposeTexture()

    override fun toString(): String {
        return "card: $name"
    }

    fun getKeyWordsForDescriptions(): List<String> {
        val res = mutableListOf<String>()
        res.addAll(DetailDescriptionHandler.getKeyWordsFromDescription(shortDescription))
        res.addAll(currentHoverTexts.map { it.first })
        return res
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
                        onj.get<String>("title"),
                        cardTypeOrError(onj),
                        onj.get<OnjArray>("tags").value.map { it.value as String },
                        onj.get<OnjArray>("forceLoadCards").value.map { it.value as String },
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
                tags = onj.get<OnjArray>("tags").value.map { it.value as String },
                forbiddenSlots = onj
                    .getOr<OnjArray?>("forbiddenSlots", null)
                    ?.value
                    ?.map { (it.value as Long).toInt() }
                    ?.map { Utils.externalToInternalSlotRepresentation(it) }
                    ?: listOf(),
                //TODO: CardDetailActor could call these functions itself
                font = GraphicsConfig.cardFont(onjScreen),
                fontScale = GraphicsConfig.cardFontScale(),
                isDark = onj.get<Boolean>("dark"),
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

    /**
     * temporarily modifies a card. For example used by the buff damage effect to change the damage of a card
     * @param damage changes the damage of the card. Can be negative
     * @param validityChecker checks if the modifier is still valid or should be removed
     */
    data class CardModifier(
        val damage: Int = 0,
        val damageMultiplier: Float = 1f,
        val source: String,
        val validityChecker: () -> Boolean = { true },
        val activeChecker: (controller: GameController) -> Boolean = { true },
        var wasActive: Boolean = true,
        val transformers: Map<Trigger, (old: CardModifier, triggerInformation: TriggerInformation) -> CardModifier> = mapOf()
    )

}

/**
 * the actor representing a card on the screen
 */
class CardActor(
    val card: Card,
    val font: PixmapFont,
    val fontScale: Float,
    val isDark: Boolean,
    override val screen: OnjScreen
) : Widget(), ZIndexActor, KeySelectableActor, DisplayDetailsOnHoverActor, HoverStateActor,HasOnjScreen, StyledActor {

    override var actorTemplate: String = "card_hover_detail" // TODO: fix
    override var detailActor: Actor? = null

    override var mainHoverDetailActor: String? = "cardHoverDetailMain"

    override var fixedZIndex: Int = 0


    override var styleManager: StyleManager? = null
    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
//        addBackgroundStyles(screen) //Maybe these are needed, probably not
//        addDisableStyles(screen)
//        addOffsetableStyles(screen)
    }

    override var isHoveredOver: Boolean = false

    override var isSelected: Boolean = false
    override var partOfHierarchy: Boolean = true
    override var isClicked: Boolean = false

    /**
     * true when the card is dragged; set by [CardDragSource][com.fourinachamber.fortyfive.game.card.CardDragSource]
     */
    var isDragged: Boolean = false

    private var inDestroyAnim: Boolean = false

    private val destroyShader: BetterShader by lazy {
        ResourceManager.get(screen, "dissolve_shader") // TODO: fix
    }

    private val cardTexture: Texture = ResourceManager.get(screen, card.drawableHandle)

    private val pixmap: Pixmap = Pixmap(cardTexture.width, cardTexture.height, Pixmap.Format.RGBA8888)

    var pixmapTextureRegion: TextureRegion? = null
        private set

    private val cardTexturePixmap: Pixmap

    private val onRightClickShowAdditionalInformationListener = object : InputListener() {

        override fun touchDown(event: InputEvent, x: Float, y: Float, pointer: Int, button: Int): Boolean {
            if (button != 1) return false

            detailActor?.let {
                val descriptionParent = getParentsForExtras(it).first
                if (descriptionParent.children.isEmpty) showExtraDescriptions(descriptionParent)
                return true
            }
            return false
        }
    }

    private fun showExtraDescriptions(descriptionParent: CustomFlexBox) {
        val allKeys = card.getKeyWordsForDescriptions()
        DetailDescriptionHandler.descriptions.filter { it.key in allKeys }.forEach {
            addHoverItemToParent(it.value.second, descriptionParent)
        }
    }

    init {
        bindHoverStateListeners(this)
        registerOnHoverDetailActor(this, screen)
        if (!cardTexture.textureData.isPrepared) cardTexture.textureData.prepare()
        cardTexturePixmap = cardTexture.textureData.consumePixmap()
        redrawPixmap(card.baseDamage)
        addListener(onRightClickShowAdditionalInformationListener)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        setBoundsOfHoverDetailActor(this)
        batch ?: return
        val texture = pixmapTextureRegion ?: return
        val shader = if (inDestroyAnim) {
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
        val c = batch.color.cpy()
        batch.setColor(c.r, c.g, c.b, alpha)
        batch.draw(
            texture,
            x, y,
            width / 2, height / 2,
            width, height,
            scaleX, scaleY,
            rotation
        )
        batch.color = c
        batch.flush()
        shader?.let { batch.shader = null }
    }

    fun disposeTexture() {
        pixmapTextureRegion?.texture?.dispose()
        pixmap.dispose()
        pixmapTextureRegion = null
    }

    fun redrawPixmap(damageValue: Int) {
        pixmap.drawPixmap(cardTexturePixmap, 0, 0)
        val situation = when {
            damageValue > card.baseDamage -> "increase"
            damageValue < card.baseDamage -> "decrease"
            else -> "normal"
        }
        val damageFontColor = GraphicsConfig.cardFontColor(isDark, situation)
        val reserveFontColor = GraphicsConfig.cardFontColor(isDark, "normal")
        font.write(pixmap, damageValue.toString(), 35, 480, fontScale, damageFontColor)
        font.write(pixmap, card.cost.toString(), 490, 28, fontScale, reserveFontColor)
        pixmapTextureRegion?.texture?.dispose()
        val texture = Texture(pixmap, true)
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.MipMapLinearLinear)
        pixmapTextureRegion = TextureRegion(texture)
    }

    override fun getBounds(): Rectangle {
        val (x, y) = localToStageCoordinates(Vector2(0f, 0f))
        return Rectangle(x, y, width, height)
    }

    // TODO: came up with system for animations
    fun destroyAnimation(): Timeline = Timeline.timeline {
        action {
            inDestroyAnim = true
            destroyShader.resetReferenceTime()
        }
        delay(2000)
        action { inDestroyAnim = false }
    }

    override fun getHoverDetailData(): Map<String, OnjValue> = mapOf(
        "description" to OnjString(card.shortDescription),
        "flavorText" to OnjString(card.flavourText),
        "effects" to DetailDescriptionHandler.allTextEffects
    )

    override fun positionChanged() {
        super.positionChanged()
        setBoundsOfHoverDetailActor(this)
    }

    override fun sizeChanged() {
        super.sizeChanged()
        setBoundsOfHoverDetailActor(this)
    }

    override fun onDetailDisplayStarted() {
        detailActor?.let {
            updateDetailStates()
            val tempInfoParent = getParentsForExtras(it).second
            card.currentHoverTexts.forEach { addHoverItemToParent(it.second, tempInfoParent) }
        }
    }

    private fun addHoverItemToParent(
        desc: String,
        tempInfoParent: CustomFlexBox
    ) {
        screen.screenBuilder.generateFromTemplate( //TODO hardcoded value as name
            "card_hover_detail_extra_description",
            mapOf(
                "description" to desc,
                "effects" to DetailDescriptionHandler.allTextEffects
            ),
            tempInfoParent,
            screen
        )
    }

    /**
     * the first actor is for the explanations, the second one is for the temporary changes
     */
    private fun getParentsForExtras(it: Actor): Pair<CustomFlexBox, CustomFlexBox> { //TODO hardcoded value as name
        val left = screen.namedActorOrError("cardHoverDetailExtraParentLeft") as CustomFlexBox
        val right = screen.namedActorOrError("cardHoverDetailExtraParentRight") as CustomFlexBox
        val top = screen.namedActorOrError("cardHoverDetailExtraParentTop") as CustomFlexBox
        val directionsToUse =
            if (it.localToStageCoordinates(Vector2(it.width, 0F)).x >= stage.viewport.worldWidth) {
                left to top
                //            } else if (it.localToStageCoordinates(Vector2(0F, 0F)).x <= 0) {
                //                right to top  //maybe this changes, that's why it's still here
            } else {
                right to top
            }
        return directionsToUse
    }

    fun updateDetailStates() {
        if (card.flavourText.isBlank()) screen.leaveState("hoverDetailHasFlavorText")
        else screen.enterState("hoverDetailHasFlavorText")

        if (card.getKeyWordsForDescriptions().isEmpty()) screen.leaveState("hoverDetailHasMoreInfo")
        else screen.enterState("hoverDetailHasMoreInfo")
    }
}
