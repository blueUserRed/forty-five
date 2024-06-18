package com.fourinachamber.fortyfive.game.card

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Pixmap
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.graphics.glutils.FileTextureData
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.*
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.scenes.scene2d.actions.ScaleToAction
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable
import com.badlogic.gdx.utils.Disposable
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.game.GameController.RevolverRotation
import com.fourinachamber.fortyfive.onjNamespaces.OnjEffect
import com.fourinachamber.fortyfive.onjNamespaces.OnjPassiveEffect
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.ResourceHandle
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.screen.general.styles.*
import com.fourinachamber.fortyfive.utils.*
import ktx.actors.alpha
import ktx.actors.onClick
import ktx.actors.onClickEvent
import ktx.actors.onTouchEvent
import onj.parser.OnjSchemaParser
import onj.schema.OnjSchema
import onj.value.*
import kotlin.math.absoluteValue

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
) {

    var creator: ((screen: OnjScreen, isSaved: Boolean?, areHoverDetailsEnabled: Boolean) -> Card)? = null

    private val priceModifiers: MutableList<(Int) -> Int>  = mutableListOf()

    /**
     * creates an actual instance of this card
     */
    fun create(
        screen: OnjScreen,
        isSaved: Boolean? = null,
        areHoverDetailsEnabled: Boolean = true
    ): Card = creator!!(screen, isSaved, areHoverDetailsEnabled)

    fun modifyPrice(modifier: (Int) -> Int) {
        priceModifiers.add(modifier)
    }

    fun getPriceWithModifications(basePrice: Int) = priceModifiers.fold(basePrice) { acc, mod -> mod(acc) }

    fun copy(): CardPrototype = CardPrototype(name, title, type, tags, forceLoadCards).apply {
        this.priceModifiers.addAll(this@CardPrototype.priceModifiers)
        this.creator = this@CardPrototype.creator
    }

    override fun equals(other: Any?): Boolean = other is CardPrototype && other.name == name

    override fun toString(): String = "CardProto($name)"
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
    val rightClickCost: Int?,
    val price: Int,
    val effects: List<Effect>,
    val passiveEffects: List<PassiveEffect>,
    val rotationDirection: RevolverRotation,
    val tags: List<String>,
    isDark: Boolean,
    val forbiddenSlots: List<Int>,
    val additionalHoverInfos: List<String>,
    font: PixmapFont,
    fontScale: Float,
    screen: OnjScreen,
    val isSaved: Boolean?,
    val enableHoverDetails: Boolean
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
    var isReinforced: Boolean = false
        private set
    var isShotProtected: Boolean = false
        private set
    var isAlwaysAtBottom: Boolean = false
        private set
    var isAlwaysAtTop: Boolean = false
        private set

    fun shouldRemoveAfterShot(controller: GameController): Boolean = !(
        (isEverlasting && !controller.encounterModifiers.any { it.disableEverlasting() }) ||
        protectingModifiers.isNotEmpty()
    )

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

    var enteredInSlot: Int? = null
        private set

    var enteredOnTurn: Int? = null
        private set

    var rotationCounter: Int = 0
        private set

    var isMarked: Boolean
        set(value) {
            actor.isMarked = value
        }
        get() = actor.isMarked

    var lastEffectAffectedCardsCache: List<Card> = listOf()

    init {
        screen.borrowResource(cardTexturePrefix + name)
        // there is a weird race condition where the ServiceThread attempts to access card.actor for drawing the
        // card texture while the constructor is running and actor is not yet assigned
        synchronized(this) {
            actor = CardActor(
                this,
                font,
                fontScale,
                isDark,
                screen,
                enableHoverDetails
            )
        }
    }

    fun bottomCardToTopCard() {
        isAlwaysAtBottom = false
        isAlwaysAtTop = true
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

    fun canBeShot(controller: GameController): Boolean = !(isShotProtected && controller.turnCounter == enteredOnTurn)

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
            if (isRotten && newDamage == 0) controller.appendMainTimeline(controller.destroyCardTimeline(this))
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
    fun afterShot(controller: GameController) {
        if (shouldRemoveAfterShot(controller)) leaveGame()
        if (protectingModifiers.isNotEmpty()) {
            val effect = protectingModifiers.first()
            val newEffect = effect.copy(second = effect.second - 1)
            if (newEffect.second == 0) {
                protectingModifiers.removeFirst()
            } else {
                protectingModifiers[0] = newEffect
            }
            modifiersChanged()
        }
    }

    fun beforeShot() {
    }

    fun leaveGame() {
        isMarked = false
        inGame = false
        _modifiers.clear()
        rotationCounter = 0
        modifiersChanged()
    }

    fun protect(source: String, protectedFor: Int, validityChecker: () -> Boolean) {
        if (isUndead) {
            FortyFiveLogger.debug(logTag, "cant protect undead bullet")
            return
        }
        FortyFiveLogger.debug(logTag, "$source protected $this for $protectedFor shots")
        protectingModifiers.add(Triple(source, protectedFor, validityChecker))
        modifiersChanged()
    }

    /**
     * checks whether this card can currently enter the game
     */
    fun allowsEnteringGame(controller: GameController, slot: Int): Boolean =
        slot !in forbiddenSlots &&
                effects
                    .filter { it.trigger == Trigger.ON_ENTER }
                    .none { it.blocks(controller) }

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

    private fun addRottenModifier(controller: GameController) {
        val rotationTransformer = { oldModifier: CardModifier, triggerInformation: TriggerInformation ->
            val newDamage = (oldModifier.damage - (triggerInformation.multiplier ?: 1))
            CardModifier(
                damage = newDamage,
                source = oldModifier.source,
                validityChecker = oldModifier.validityChecker,
                transformers = oldModifier.transformers
            )
        }
        val modifier = CardModifier(
            damage = 0,
            source = "disintegration effect",
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
    fun onEnter(controller: GameController) {
        inGame = true
        enteredInSlot = controller.revolver.slots.find { it.card === this }!!.num
        enteredOnTurn = controller.turnCounter
        if (isRotten) addRottenModifier(controller)
    }

    /**
     * called when the revolver rotates (but not when this card was shot)
     */
    fun onRevolverRotation(rotation: RevolverRotation) {
        rotationCounter += rotation.amount
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
        controller: GameController,
    ): Timeline = Timeline.timeline {
        val isOnShot = triggerInformation.isOnShot
        action {
            checkModifierTransformers(trigger, triggerInformation)
        }
        val inHand = inHand(controller)
        val effects = effects
            .filter { inGame || (inHand && it.triggerInHand) }
            .filter { it.condition?.check(controller) ?: true }
            .zip { it.checkTrigger(trigger, triggerInformation, controller) }
            .filter { it.second != null }
        if (effects.isEmpty()) return@timeline
        val showAnimation = !effects.all { it.first.isHidden }
        action {
            actor.inAnimation = true
        }
        action {
            if (isOnShot || !showAnimation || !inGame) return@action
            controller.dispatchAnimTimeline(Timeline.timeline {
                delay(210)
                include(controller.gameRenderPipeline.getScreenShakeTimeline())
            })
        }
        val forceNonOnShotTrigger = effects.any { it.first.useAlternateOnShotTriggerPosition() }
        includeLater(
            { actor.animateToTriggerPosition(controller, isOnShot && !forceNonOnShotTrigger) },
            { inGame && showAnimation }
        )
        include(effects.mapNotNull { it.second }.collectTimeline())
        includeLater(
            { actor.animateBack(controller) },
            { inGame && showAnimation && (!isOnShot || !shouldRemoveAfterShot(controller)) }
        )
        action {
            actor.setScale(1f)
            actor.inAnimation = false
        }
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
            val allDamageEffects = activeModifiers(controller).filter { it.damage != 0 || it.damageMultiplier != 1f }
            val damageChange = curDamage(controller) - baseDamage
            val damageText = allDamageEffects
                .distinctBy { it.source }
                .joinToString(
                    separator = ", ",
                    prefix = "${if (damageChange > 0) "+" else ""}$damageChange by ",
                    transform = { it.source })
            val keyWord = if (damageChange > 0) "\$dmgBuff\$" else "\$dmgNerf\$"
            currentEffects.add("dmgBuff" to "$keyWord$damageText$keyWord")
        }

        if (protectingModifiers.isNotEmpty()) {
            val total = protectingModifiers.sumOf { it.second }
            currentEffects.add("protected" to "\$trait\$+ PROTECTED ($total)\$trait\$")
        }

        currentHoverTexts = currentEffects
        val detailActor = actor.detailActor ?: return
        detailActor as StyledActor
        actor.updateDetailStates(detailActor)
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

    fun getAdditionalHoverDescriptions(): List<String> {
        return additionalHoverInfos.map { info ->
            when (info) {
                "home" -> enteredInSlot?.let {
                    val slot = Utils.convertSlotRepresentation(it)
                    val slotIcon = GraphicsConfig.revolverSlotIcon(slot)
                    "entered in slot $slot§§$slotIcon§§"
                } ?: ""
                "rotations" -> "bullet rotated ${rotationCounter.pluralS("time")}"
                "mostExpensiveBullet" -> {
                    val mostExpensive = FortyFive.currentGame!!
                        .revolver
                        .slots
                        .mapNotNull { it.card }
                        .maxOfOrNull { it.cost }
                        ?: 0
                    "most expensive bullet costs $mostExpensive"
                }
                else -> throw RuntimeException("unknown additional hover info $info")
            }
        }
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
                    )
                    prototype.creator = { screen, isSaved, areHoverDetailsEnabled ->
                        getCardFrom(onj, screen, initializer, prototype, isSaved, areHoverDetailsEnabled)
                    }
                    prototypes.add(prototype)
                }
            return prototypes
        }

        @MainThreadOnly
        private fun getCardFrom(
            onj: OnjObject,
            onjScreen: OnjScreen,
            initializer: (Card) -> Unit,
            prototype: CardPrototype,
            isSaved: Boolean?,
            enableHoverDetails: Boolean
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
                rightClickCost = onj.getOr<Long?>("rightClickCost", null)?.toInt(),
                price = prototype.getPriceWithModifications(onj.get<Long>("price").toInt()),
                effects = onj.get<OnjArray>("effects")
                    .value
                    .map { (it as OnjEffect).value.copy() }, //TODO: find a better solution
                passiveEffects = onj.getOr<List<OnjPassiveEffect>>("passiveEffects", listOf())
                    .map { it.value }
                    .map { it.creator() },
                rotationDirection = RevolverRotation.fromOnj(onj.get<OnjNamedObject>("rotation")),
                tags = onj.get<OnjArray>("tags").value.map { it.value as String },
                forbiddenSlots = onj
                    .getOr<OnjArray?>("forbiddenSlots", null)
                    ?.value
                    ?.map { (it.value as Long).toInt() }
                    ?.map { Utils.convertSlotRepresentation(it) }
                    ?: listOf(),
                //TODO: CardDetailActor could call these functions itself
                font = GraphicsConfig.cardFont(onjScreen),
                fontScale = GraphicsConfig.cardFontScale(),
                isDark = onj.get<Boolean>("dark"),
                additionalHoverInfos = onj
                    .getOr<OnjArray?>("additionalHoverInfos", null)
                    ?.value
                    ?.map { it.value as String }
                    ?: listOf(),
                screen = onjScreen,
                isSaved = isSaved,
                enableHoverDetails = enableHoverDetails
            )

            card.effects.forEach { it.card = card }
            card.passiveEffects.forEach { it.card = card }
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
                "reinforced" -> card.isReinforced = true
                "shotProtected" -> card.isShotProtected = true
                "rotten" -> card.isRotten = true
                "alwaysAtBottom" -> card.isAlwaysAtBottom = true
                "alwaysAtTop" -> card.isAlwaysAtTop = true

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
    override val screen: OnjScreen,
    val enableHoverDetails: Boolean
) : Widget(), ZIndexActor, KeySelectableActor, DisplayDetailsOnHoverActor, HoverStateActor, HasOnjScreen, StyledActor,
    OffSettable, AnimationActor {

    override val actor: Actor = this

    override var actorTemplate: String = "card_hover_detail" // TODO: fix
    override var detailActor: Actor? = null

    override var inAnimation: Boolean = false

    override var mainHoverDetailActor: String? = "cardHoverDetailMain"

    override var fixedZIndex: Int = 0

    override var offsetX: Float = 0F
    override var offsetY: Float = 0F
    override var styleManager: StyleManager? = null

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

    private val markedSymbol: TransformDrawable by lazy {
        ResourceManager.get(screen, "card_symbol_marked")
    }

    private val cardTexture: Texture = ResourceManager.get(screen, card.drawableHandle)

    private val pixmap: Pixmap = Pixmap(cardTexture.width, cardTexture.height, Pixmap.Format.RGBA8888)

    var pixmapTextureRegion: TextureRegion? = null
        private set

    private val cardTexturePixmap: Pixmap

    override var isHoverDetailActive: Boolean
        get() = (card.shortDescription.isNotBlank() ||
                card.flavourText.isNotBlank() ||
                card.getKeyWordsForDescriptions().isNotEmpty() ||
                card.getAdditionalHoverDescriptions().isNotEmpty())
                && enableHoverDetails
        set(value) {}

    private var drawPixmapMessage: ServiceThreadMessage.DrawCardPixmap? = null

    private var prevPosition: Vector2? = null

    private var inSelectionMode: Boolean = false

    var playSoundsOnHover: Boolean = false

    var isMarked: Boolean = false

    init {
        bindHoverStateListeners(this)
        registerOnHoverDetailActor(this, screen)
        if (!cardTexture.textureData.isPrepared) cardTexture.textureData.prepare()
        cardTexturePixmap = cardTexture.textureData.consumePixmap()
        // I HATE LIBGDX
        // FileTextureData always loads a new pixmap that needs to be disposed
        // PixmapTextureData always returns the same pixmap, that doesn't need to be disposed
        // Which one is used is a race condition in the TextureResource class
        if (cardTexture.textureData is FileTextureData) screen.addDisposable(cardTexturePixmap)
        redrawPixmap(card.baseDamage)
        onClick {
            if (!inSelectionMode) return@onClick
            // UGGGGGLLLLLLYYYYY
            FortyFive.currentGame!!.selectCard(card)
        }
        onHoverEnter {
            if (!playSoundsOnHover) return@onHoverEnter
            SoundPlayer.situation("card_hover", screen)
        }
        onTouchEvent { event, _, _ ->
            if (event.button != Input.Buttons.RIGHT) return@onTouchEvent
            FortyFive.currentGame!!.cardRightClicked(card)
        }
    }

    private fun showExtraDescriptions(descriptionParent: CustomFlexBox) {
        val allKeys = card.getKeyWordsForDescriptions()
        DetailDescriptionHandler
            .descriptions
            .filter { it.key in allKeys }
            .forEach {
                addHoverItemToParent(it.value.second, descriptionParent)
            }
        if (FortyFive.currentGame == null) return
        card
            .getAdditionalHoverDescriptions()
            .filter { it.isNotBlank() }
            .forEach { addHoverItemToParent(it, descriptionParent) }
    }

    override fun setBounds(x: Float, y: Float, width: Float, height: Float) {
        // This is a fix for the ChooseCardScreen, where for some reason the CardDragAndDrop sets the position first,
        // but is then overwritten by the layout code every frame (I guess something is calling invalidate each frame,
        // but I dont know what)
        // I also dont know why the LibGDX drag and drop system is implemented like this, because it inherently relies
        // on the order in which the DragAndDrop/Layout/Draw code is executed, which breaks really easily
        // This is a really bad fix, but a good fix would probably involve completely rewriting the DragAndDrop-System
        // and this issue has been haunting me for too long

        // block the layout code from setting the position when the actor is dragged (and hope that the DragAndDrop-Code doesnt use the setBounds function)
        if (isDragged) return
        super.setBounds(x, y, width, height)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        validate()
        if (drawPixmapMessage?.isFinished ?: false) {
            finishPixmapDrawing()
        }
        setBoundsOfHoverDetailActor(screen)
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
        batch.setColor(c.r, c.g, c.b, alpha * parentAlpha)
        val width: Float
        val height: Float
        val x: Float
        val y: Float
        if (isHoveredOver && inSelectionMode) {
            width = this.width * 1.2f
            height = this.height * 1.2f
            x = this.x - (width - this.width) / 2f
            y = this.y - (height - this.height) / 2f
        } else {
            width = this.width
            height = this.height
            x = this.x
            y = this.y
        }
        batch.draw(
            texture,
            x + offsetX, y + offsetY,
            width / 2, height / 2,
            width, height,
            scaleX, scaleY,
            rotation
        )
        batch.color = c
        batch.flush()
        shader?.let { batch.shader = null }
        if (!isMarked) return
        markedSymbol.draw(
            batch,
            x + offsetX, y + offsetY,
            width / 2, height / 2,
            width, height,
            scaleX, scaleY,
            rotation
        )
    }

    fun disposeTexture() {
        pixmapTextureRegion?.texture?.dispose()
        pixmap.dispose()
        pixmapTextureRegion = null
    }

    fun redrawPixmap(damageValue: Int) {
        val savedPixmapTextureData = when (card.isSaved) {
            null -> null
            true -> GraphicsConfig.cardSavedSymbol(screen)
            false -> GraphicsConfig.cardNotSavedSymbol(screen)
        }?.textureData
        if (savedPixmapTextureData != null && !savedPixmapTextureData.isPrepared) savedPixmapTextureData.prepare()
        val savedPixmap = savedPixmapTextureData?.consumePixmap()
        if (savedPixmapTextureData != null && savedPixmapTextureData is FileTextureData) {
            screen.addDisposable(savedPixmap!!)
        }
        val message = ServiceThreadMessage.DrawCardPixmap(
            pixmap,
            cardTexturePixmap,
            card,
            damageValue,
            savedPixmap
        )
        FortyFive.serviceThread.sendMessage(message)
        drawPixmapMessage = message
    }

    private fun finishPixmapDrawing() {
        pixmapTextureRegion?.texture?.dispose()
        val texture = Texture(pixmap, true)
        texture.setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.MipMapLinearLinear)
        pixmapTextureRegion = TextureRegion(texture)
        drawPixmapMessage = null
    }

    override fun getBounds(): Rectangle {
        val (x, y) = localToStageCoordinates(Vector2(0f, 0f))
        return Rectangle(x, y, width, height)
    }

    // TODO: came up with system for animations
    fun destroyAnimation(): Timeline = Timeline.timeline {
        action {
            SoundPlayer.situation("card_destroyed", screen)
            inDestroyAnim = true
            destroyShader.resetReferenceTime()
        }
        delay(1200)
        action { inDestroyAnim = false }
    }

    fun animateToTriggerPosition(controller: GameController, isOnShotTrigger: Boolean): Timeline = Timeline.timeline {
        prevPosition = Vector2(x, y)
        val target = if (isOnShotTrigger) {
            controller.revolver.getCardOnShotTriggerPosition()
        } else {
            controller.revolver.getCardTriggerPosition()
        }
        val moveAction = MoveToAction()
        moveAction.setPosition(target.x, target.y)
        val distance = (prevPosition!! - target).len().absoluteValue
        moveAction.duration = 0.000724637f * distance
        moveAction.interpolation = Interpolation.pow2In
        val scaleAction = ScaleToAction()
        scaleAction.setScale(1.5f)
        scaleAction.duration = 0.000724637f * distance
        scaleAction.interpolation = Interpolation.pow2In
        action {
            SoundPlayer.situation("card_trigger_anim_in", screen)
            toFront()
            addAction(moveAction)
            addAction(scaleAction)
        }
        delayUntil { moveAction.isComplete }
        action {
            removeAction(moveAction)
            removeAction(scaleAction)
        }
        delay(100)
    }

    fun animateBack(controller: GameController): Timeline = Timeline.timeline {
        val target = controller
            .revolver
            .slots
            .find { it.card === card }
            ?.cardPosition()
            ?: return@timeline
        val moveAction = MoveToAction()
        moveAction.setPosition(target.x, target.y)
        val distance = (Vector2(x, y) - target).len().absoluteValue
        moveAction.duration = 0.000724637f * distance
        moveAction.interpolation = Interpolation.pow2
        val scaleAction = ScaleToAction()
        scaleAction.setScale(1f)
        scaleAction.duration = 0.000724637f * distance
        scaleAction.interpolation = Interpolation.pow2
        action {
            SoundPlayer.situation("card_trigger_anim_out", screen)
            addAction(moveAction)
            addAction(scaleAction)
        }
        delayUntil { moveAction.isComplete }
        action {
            removeAction(moveAction)
            removeAction(scaleAction)
        }
    }

    fun enterSelectionMode() {
        inSelectionMode = true
        playSoundsOnHover = true
    }

    fun exitSelectionMode() {
        inSelectionMode = false
        playSoundsOnHover = false
    }

    override fun getHoverDetailData(): Map<String, OnjValue> = mapOf(
        "description" to OnjString(
            card.shortDescription.ifBlank { card.flavourText }
        ),
        "flavorText" to OnjString(
            if (card.shortDescription.isBlank()) "" else card.flavourText
        ),
        "effects" to DetailDescriptionHandler.allTextEffects,
//        "rotation" to OnjFloat(rotation.toDouble()),
    )

    override fun positionChanged() {
        super.positionChanged()
        setBoundsOfHoverDetailActor(screen)
    }

    override fun sizeChanged() {
        super.sizeChanged()
        setBoundsOfHoverDetailActor(screen)
    }

    override fun onDetailDisplayStarted() {
        val detailActor = detailActor ?: return
        detailActor as StyledActor
        updateDetailStates(detailActor)
        val tempInfoParent = getParentsForExtras(detailActor).second
        card.currentHoverTexts.forEach { addHoverItemToParent(it.second, tempInfoParent) }
        showExtraDescriptions(getParentsForExtras(detailActor).first)
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
            } else {
                right to top
            }
        return directionsToUse
    }

    fun <T> updateDetailStates(hoverActor: T) where T : Actor, T : StyledActor {
        if (card.flavourText.isBlank() || card.shortDescription.isBlank()) {
            screen.leaveState("hoverDetailHasFlavorText")
        } else {
            screen.enterState("hoverDetailHasFlavorText")
        }

        if (card.getKeyWordsForDescriptions().isEmpty() && (FortyFive.currentGame == null || card.getAdditionalHoverDescriptions().isEmpty())) {
            screen.leaveState("hoverDetailHasMoreInfo")
        } else {
            screen.enterState("hoverDetailHasMoreInfo")
        }
    }

    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }

}
