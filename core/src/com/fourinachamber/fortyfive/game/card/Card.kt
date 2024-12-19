package com.fourinachamber.fortyfive.game.card

import com.badlogic.gdx.Input
import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Rectangle
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.*
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.scenes.scene2d.actions.ScaleToAction
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.*
import com.fourinachamber.fortyfive.game.controller.GameController
import com.fourinachamber.fortyfive.game.controller.NewGameController
import com.fourinachamber.fortyfive.game.controller.NewGameController.Zone
import com.fourinachamber.fortyfive.game.controller.RevolverRotation
import com.fourinachamber.fortyfive.keyInput.selection.SelectionGroup
import com.fourinachamber.fortyfive.onjNamespaces.OnjZone
import com.fourinachamber.fortyfive.rendering.BetterShader
import com.fourinachamber.fortyfive.screen.ResourceBorrower
import com.fourinachamber.fortyfive.screen.ResourceManager
import com.fourinachamber.fortyfive.screen.SoundPlayer
import com.fourinachamber.fortyfive.screen.general.*
import com.fourinachamber.fortyfive.screen.general.customActor.*
import com.fourinachamber.fortyfive.screen.general.styles.*
import com.fourinachamber.fortyfive.utils.*
import ktx.actors.alpha
import ktx.actors.onTouchEvent
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
    val tags: List<String>,
) {

    var creator: ((screen: OnjScreen, isSaved: Boolean?, areHoverDetailsEnabled: Boolean) -> Card)? = null

    private val priceModifiers: MutableList<(Int) -> Int> = mutableListOf()

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

    fun copy(): CardPrototype = CardPrototype(name, title, tags).apply {
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
 * @param baseCost the cost of this card in reserves
 * @param effects the effects of this card
 */
class Card(
    val name: String,
    val title: String,
    val flavourText: String,
    val shortDescription: String,
    val baseDamage: Int,
    val baseCost: Int,
    val rightClickCost: Int?,
    val price: Int,
    val effects: List<Effect>,
    val rotationDirection: RevolverRotation,
    val tags: List<String>,
    val lockedDescription: String?,
    isDark: Boolean,
    val forbiddenSlots: List<Int>,
    val additionalHoverInfos: List<String>,
    font: Promise<PixmapFont>,
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
            (isEverlasting && !controller.isEverlastingDisabled) ||
                    protectingModifiers.isNotEmpty()
            )

    private var lastDamageValue: Int = baseDamage
    private var lastCostValue: Int = baseCost

    // total amount of modifiers added, is added to [damageModifiers] alongside the modifier,
    // used for sorting them because the order is important, and they may be removed / added back
    private var damageModifierCounter: Int = 0

    private val damageModifiers: MutableList<Pair<Int, CardDamageModifier>> = mutableListOf()
    private val costModifiers: MutableList<CardCostModifier> = mutableListOf()
    private val protectingModifiers: MutableList<ProtectingModifier> = mutableListOf()

    /**
     * first ist the keyword, second is the actual text
     */
    var currentHoverTexts: List<Pair<String, String>> = listOf()
        private set

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

    var zone: Zone = Zone.STACK
        private set

    init {
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

    fun bindGameEvents(gameEvents: EventPipeline, controller: GameController) {
        var currentTargetSelection: Promise<Card>? = null
        gameEvents.watchFor<NewGameController.Events.TargetSelectionEvent> { event ->
            if (!inZone(Zone.REVOLVER)) return@watchFor
            if (this === event.exclude) return@watchFor
            actor.enterSelectionMode()
            event.promise.then { actor.exitSelectionMode() }
            currentTargetSelection = event.promise
        }
        actor.onSelect {
            if (!actor.inSelectionMode) return@onSelect
            currentTargetSelection?.resolve(this@Card)
        }
    }

    fun canBeReplaced(controller: GameController, by: Card): Boolean = false

    fun replaceTimeline(controller: NewGameController, replaceBy: Card): Timeline = Timeline()

    fun changeZone(newZone: Zone, controller: GameController) {
        val oldZone = zone
        zone = newZone
        if (newZone == Zone.REVOLVER) {
            enteredInSlot = controller.slotOfCard(this)!!
            enteredOnTurn = controller.turnCounter
            if (isRotten) addRottenModifier(controller)
        }
        if (newZone == Zone.HAND) {
            actor.isDraggable = true
        }
        if (oldZone == Zone.HAND) {
            actor.isDraggable = false
        }
    }

    fun inZone(vararg zones: Zone): Boolean {
        zones.forEach { if (it == zone) return true }
        return false
    }

    ///////////////////////////////////////////
    ///////////////////////////////////////////

    fun bottomCardToTopCard() {
        isAlwaysAtBottom = false
        isAlwaysAtTop = true
    }

    inline fun <T> checkValiditySingleModifierList(
        controller: GameController,
        modifiers: MutableList<T>,
        getter: (T) -> CardModifier
    ): Boolean {
        var somethingChanged = false
        modifiers.iterateRemoving { value, remover ->
            val modifier = getter(value)
            if (!modifier.data.validityChecker(controller, this, modifier.data)) {
                FortyFiveLogger.debug(logTag, "modifier no longer valid: $modifier")
                remover()
                somethingChanged = true
            }
            val active = modifier.data.activeChecker(controller, this, modifier.data)
            if (active == modifier.data.wasActive) return@iterateRemoving
            modifier.data.wasActive = active
            somethingChanged = true
        }
        return somethingChanged
    }

    /**
     * checks if the modifiers of this card are still valid and removes them if they are not
     */
    private fun checkModifierValidity(controller: GameController) {
        val somethingChanged =
            checkValiditySingleModifierList(controller, costModifiers, getter = { it }) ||
            checkValiditySingleModifierList(controller, damageModifiers, getter = { it.second }) ||
            checkValiditySingleModifierList(controller, protectingModifiers, getter = { it })
        if (somethingChanged) modifiersChanged()
    }

    fun canBeShot(controller: GameController): Boolean = !(isShotProtected && controller.turnCounter == enteredOnTurn)

    fun update(controller: GameController) {
        checkModifierValidity(controller)
        if (modifierValuesDirty) {
            updateText(controller)
            val newDamage = curDamage(controller)
            val newCost = curCost(controller)
            if (newDamage != lastDamageValue || newCost != lastCostValue) {
                updateTexture(controller)
                lastDamageValue = newDamage
                lastCostValue = newCost
            }
            modifierValuesDirty = false
            if (isRotten && newDamage == 0) controller.appendMainTimeline(controller.destroyCardTimeline(this))
        }
    }

    fun curDamage(controller: GameController): Int = damageModifiers
        .filter { (_, modifier) -> modifier.data.activeChecker(controller, this, modifier.data) }
        .sortedBy { it.first }
        .fold(baseDamage) { acc, (_, modifier) -> ((acc + modifier.damage) * modifier.damageMultiplier).toInt() }
        .coerceAtLeast(0)

    fun curCost(controller: GameController): Int = costModifiers
        .filter { (_, modifier) -> modifier.activeChecker(controller, this, modifier) }
        .fold(baseCost) { acc, modifier -> acc + modifier.costChange }
        .coerceAtLeast(0)

    /**
     * called by gameScreenController when the card was shot
     */
    fun afterShot(
        controller: GameController,
        putCardInTheHand: (Card) -> Timeline,
        putCardInTheStack: (Card) -> Timeline
    ): Timeline = Timeline.timeline { skipping { skip ->
        action {
            if (isEverlasting && !controller.isEverlastingDisabled) {
                skip()
                return@action
            }
            if (protectingModifiers.isNotEmpty()) {
                val effect = protectingModifiers.first()
                val newEffect = effect.copy(shots = effect.shots - 1)
                if (newEffect.shots == 0) {
                    protectingModifiers.removeFirst()
                } else {
                    protectingModifiers[0] = newEffect
                }
                modifiersChanged()
                skip()
            }
        }
        if (isUndead) include(putCardInTheHand(this@Card))
        else include(putCardInTheStack(this@Card))
    } }

    fun beforeShot() {
    }

    fun leaveGame() {
        TODO("dont use")
        isMarked = false
        inGame = false
        rotationCounter = 0
        modifiersChanged()
    }

    fun protect(protectingModifier: ProtectingModifier) {
        if (isUndead) {
            FortyFiveLogger.debug(logTag, "cant protect undead bullet")
            return
        }
        protectingModifiers.add(protectingModifier.copy())
        modifiersChanged()
    }

    /**
     * checks whether this card can currently enter the game
     */
    fun allowsEnteringGame(controller: GameController, slot: Int): Boolean {
        if (slot in forbiddenSlots) return false
        return effects
            .filter { it.data.canPreventEnteringGame }
            .none { it.blocks(this, controller) }
    }

    /**
     * called when this card was destroyed by the destroy effect
     */
    fun onDestroy() {
        TODO("dont use this function")
//        if (isUndead) {
//            FortyFiveLogger.debug(logTag, "undead card is respawning in hand after being destroyed")
//            FortyFive.currentGame!!.cardHand.addCard(this)
//        }
//        leaveGame()
    }

    fun addDamageModifier(modifier: CardDamageModifier) {
        FortyFiveLogger.debug(logTag, "card got new modifier: $modifier")
        damageModifiers.add(++damageModifierCounter to modifier.copy())
        modifiersChanged()
    }

    fun addCostModifier(modifier: CardCostModifier) {
        FortyFiveLogger.debug(logTag, "card got new modifier: $modifier")
        costModifiers.add(modifier.copy())
        modifiersChanged()
    }

    private fun addRottenModifier(controller: GameController) {
        val rotationTransformer = { oldModifier: CardDamageModifier, triggerInformation: TriggerInformation ->
            val newDamage = (oldModifier.damage - (triggerInformation.multiplier ?: 1))
            CardDamageModifier(
                damage = newDamage,
                data = oldModifier.data,
                transformers = oldModifier.transformers
            )
        }
        val modifier = CardDamageModifier(
            damage = 0,
            data = CardModifierData(
                source = "disintegration effect",
                validityChecker = { _, _, _ -> inZone(Zone.REVOLVER) },
            ),
            transformers = listOf(
                Trigger.triggerForSituation<GameSituation.RevolverRotation>() to rotationTransformer
            )
        )
        addDamageModifier(modifier)
    }

    /**
     * called when the card enters the game
     */
    fun onEnter(controller: GameController) {
        TODO("dont use this function")
//        inGame = true
//        enteredInSlot = controller.slotOfCard(this)!!
//        enteredOnTurn = controller.turnCounter
//        if (isRotten) addRottenModifier(controller)
    }

    /**
     * called when the revolver rotates (but not when this card was shot)
     */
    fun onRevolverRotation(rotation: RevolverRotation) {
        rotationCounter += rotation.amount
    }

    /**
     * checks if the effects of this card respond to [trigger] and returns a timeline containing the actions for the
     * effects; null if no effect was triggered
     */
    @MainThreadOnly
    fun checkEffects(
        situation: GameSituation,
        triggerInformation: TriggerInformation,
        controller: GameController,
    ): Timeline = Timeline.timeline { later {

        action { checkModifierTransformers(situation, triggerInformation, controller) }

        val prevPosition = Vector2(actor.x, actor.y)
        var isInTriggerPosition = false
        effects.forEach { effect ->
            later {
                val shouldTrigger = effect.checkTrigger(situation, triggerInformation, controller, this@Card)
                if (!shouldTrigger) return@later
                if (!isInTriggerPosition && !effect.data.isHidden && !inZone(Zone.STACK, Zone.LIMBO)) {
                    val animateLikeOnShot = triggerInformation.isOnShot && !effect.useAlternateOnShotTriggerPosition()
                    val anim = actor.animateToTriggerPosition(controller, animateLikeOnShot)
                    isInTriggerPosition = true
                    val screenShakeTimeline = Timeline.timeline {
                        delay(210)
                        include(controller.gameRenderPipeline.getScreenShakeTimeline())
                    }
                    action { controller.dispatchAnimTimeline(screenShakeTimeline) }
                    include(anim)
                }
                include(effect.onTrigger(this@Card, triggerInformation, controller))
            }
        }

        later {
            if (isInTriggerPosition) include(actor.animateBack(controller, prevPosition))
            action { actor.setScale(1f) }
        }
    } }

    private fun checkModifierTransformers(
        situation: GameSituation,
        triggerInformation: TriggerInformation,
        controller: GameController
    ) {
        var modifierChanged = false
        damageModifiers.replaceAll { (counter, modifier) ->
            val (_, transformer) = modifier
                .transformers
                .find { it.first.check(situation, this, triggerInformation, controller) }
                ?: return@replaceAll counter to modifier
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
        val curDamage = curDamage(controller)
        if (curDamage != baseDamage) {
            val activeDamageModifiers = damageModifiers
                .filter { it.second.data.activeChecker(controller, this, it.second.data) }
            val damageChange = curDamage(controller) - baseDamage
            val damageText = activeDamageModifiers
                .map { it.second }
                .distinctBy { it.data.source }
                .joinToString(
                    separator = ", ",
                    prefix = "${if (damageChange > 0) "+" else ""}$damageChange dmg by ",
                    transform = { it.data.source })
            val keyWord = if (damageChange > 0) "\$dmgBuff\$" else "\$dmgNerf\$"
            currentEffects.add("dmgBuff" to "$keyWord$damageText$keyWord")
        }
        val curCost = curCost(controller)
        if (curCost != baseCost) {
            val activeCostModifiers = costModifiers
                .filter { it.data.activeChecker(controller, this, it.data) }
            val costChange = curCost - baseCost
            val costText = activeCostModifiers
                .filter { it.costChange != 0 }
                .distinctBy { it.data.source }
                .joinToString(
                    separator = ", ",
                    "${if (costChange > 0) "+" else ""}$costChange cost by ",
                    transform = { it.data.source }
                )
            val keyword = if (costChange > 0) "\$costIncrease\$" else "\$costDecrease\$"
            if (costChange != 0) currentEffects.add("costChange" to "$keyword$costText$keyword")
        }

        if (protectingModifiers.isNotEmpty()) {
            val total = protectingModifiers.sumOf { it.shots }
            currentEffects.add("protected" to "\$trait\$+ PROTECTED ($total)\$trait\$")
        }

        currentHoverTexts = currentEffects
    }

    private fun updateTexture(controller: GameController) =
        actor.redrawPixmap(curDamage(controller), curCost(controller))

    override fun dispose() = actor.dispose()

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
                        .cardsInRevolver()
                        .maxOfOrNull { it.lastCostValue }
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
                        onj.get<OnjArray>("tags").value.map { it.value as String },
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
                flavourText = onj.get<String>("flavourText"),
                shortDescription = onj.get<String>("description"),
                baseDamage = onj.get<Long>("baseDamage").toInt(),
                baseCost = onj.get<Long>("cost").toInt(),
                rightClickCost = onj.getOr<Long?>("rightClickCost", null)?.toInt(),
                price = prototype.getPriceWithModifications(onj.get<Long>("price").toInt()),
                effects = (onj.getOr<OnjArray?>("effects", null)?.value ?: listOf())
                    .map {
                        it as OnjObject
                        val effect = it.get<Effect>("effect")
                        val data = EffectData(
                            trigger = it.get<Trigger>("trigger"),
                            isHidden = it.getOr("isHidden", false),
                            cacheAffectedCards = it.getOr("cacheAffectedCards", false),
                            canPreventEnteringGame = it.getOr("canPreventEnteringGame", false),
                            onlyTriggerInZones = it.ifHas<OnjArray, List<Zone>>("inZones") { arr ->
                                arr
                                    .value
                                    .map { (it as OnjZone).value }
                            }
                        )
                        effect.copy(data)
                    },
                rotationDirection = onj.getOr<OnjNamedObject?>("rotation", null)
                    ?.let { RevolverRotation.fromOnj(it) }
                    ?: RevolverRotation.Right(1),
                tags = onj.get<OnjArray>("tags").value.map { it.value as String },
                forbiddenSlots = onj
                    .getOr<OnjArray?>("forbiddenSlots", null)
                    ?.value
                    ?.map { (it.value as Long).toInt() }
                    ?.map { Utils.convertSlotRepresentation(it) }
                    ?: listOf(),
                //TODO: CardDetailActor could call these functions itself
                font = GraphicsConfig.cardFont(onjScreen, onjScreen),
                fontScale = GraphicsConfig.cardFontScale(),
                isDark = onj.getOr<Boolean>("dark", false),
                additionalHoverInfos = onj
                    .getOr<OnjArray?>("additionalHoverInfos", null)
                    ?.value
                    ?.map { it.value as String }
                    ?: listOf(),
                screen = onjScreen,
                isSaved = isSaved,
                enableHoverDetails = enableHoverDetails,
                lockedDescription = onj.get<String?>("lockedDescription")
            )
            applyTraitEffects(card, onj)
            initializer(card)
            return card
        }

        private fun applyTraitEffects(card: Card, onj: OnjObject) {
            val effects = onj
                .getOr<OnjArray?>("traitEffects", null)
                ?.value
                ?.map { it.value as String }
                ?: listOf()

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
    }

}

interface CardModifier {
    val data: CardModifierData
}

data class CardDamageModifier(
    val damage: Int = 0,
    val damageMultiplier: Float = 1f,
    val transformers: List<Pair<Trigger, (old: CardDamageModifier, triggerInformation: TriggerInformation) -> CardDamageModifier>> = listOf(),
    override val data: CardModifierData
) : CardModifier

data class CardCostModifier(
    val costChange: Int,
    override val data: CardModifierData
) : CardModifier

data class ProtectingModifier(
    val shots: Int,
    override val data: CardModifierData
) : CardModifier

data class CardModifierData(
    val source: String,
    val sourceCard: Card? = null,
    val validityChecker: CardModifierPredicate = { _, _, _ -> true },
    val activeChecker: CardModifierPredicate = { _, _, _ -> true },
    var wasActive: Boolean = true,
)

typealias CardModifierPredicate = (controller: GameController, card: Card, modifier: CardModifierData) -> Boolean

/**
 * the actor representing a card on the screen
 */
class CardActor(
    val card: Card,
    val font: Promise<PixmapFont>,
    val fontScale: Float,
    val isDark: Boolean,
    override val screen: OnjScreen,
    val enableHoverDetails: Boolean
) : Widget(), ZIndexActor, KeySelectableActor, DisplayDetailActor, HoverStateActor, HasOnjScreen, StyledActor,
    OffSettable, Lifetime, Disposable, ResourceBorrower, KotlinStyledActor, DragAndDroppableActor {

    override var detailWidget: DetailWidget? = DetailWidget.KomplexBigDetailActor(
        screen,
        effects = cardDetailEffects,
        text = { listOf(card.shortDescription, card.flavourText) },
        subtexts = getEffectTexts()
    )

    override var fixedZIndex: Int = 0

    override var drawOffsetX: Float = 0F
    override var drawOffsetY: Float = 0F
    override var logicalOffsetX: Float = 0F
    override var logicalOffsetY: Float = 0F
    override var styleManager: StyleManager? = null

    override var marginTop: Float = 0F
    override var marginBottom: Float = 0F
    override var marginLeft: Float = 0F
    override var marginRight: Float = 0F
    override var positionType: PositionType = PositionType.RELATIV
    override var group: SelectionGroup? = null
    override var isFocusable: Boolean = false
    override var isFocused: Boolean = false
    override var isSelectable: Boolean = false
    override var isSelected: Boolean = false
    override var isDraggable: Boolean = false
    override var inDragPreview: Boolean = false
    override var targetGroups: List<String> = listOf()
    override var resetCondition: ((Actor?) -> Boolean)? = null
    override val onDragAndDrop: MutableList<(Actor, Actor) -> Unit> = mutableListOf()

    override var isHoveredOver: Boolean = false

    //    override var isSelected: Boolean = false
    override var partOfHierarchy: Boolean = true
    override var isClicked: Boolean = false

    /**
     * true when the card is dragged; set by [CardDragSource][com.fourinachamber.fortyfive.game.card.CardDragSource]
     */
    var isDragged: Boolean = false

    private var inDestroyAnim: Boolean = false
    private var spawnAnimStart: Long = 0L
    private var spawnAnimDuration: Int = 0

    private val lifetime: EndableLifetime = EndableLifetime()

    private val destroyShader: Promise<BetterShader> =
        ResourceManager.request(this, this, "dissolve_shader")

    private val spawnShader: Promise<BetterShader> =
        ResourceManager.request(this, this, "card_spawn_shader")

    private val markedSymbol: Promise<TransformDrawable> =
        ResourceManager.request(this, this, "card_symbol_marked")

    private var prevPosition: Vector2? = null

    var inSelectionMode: Boolean = false

    var playSoundsOnHover: Boolean = false

    var isMarked: Boolean = false

    private var cardTexturePromise: Promise<Texture>? = null
    private var texture: Texture? = null

    init {
        bindDefaultListeners(this, screen)
        registerOnFocusDetailActor(this, screen)

        cardTexturePromise = FortyFive.cardTextureManager.cardTextureFor(card, card.baseCost, card.baseDamage)

        onHoverEnter {
            if (!playSoundsOnHover) return@onHoverEnter
            SoundPlayer.situation("card_hover", screen)
        }
        onTouchEvent { event, _, _ ->
            if (event.button != Input.Buttons.RIGHT) return@onTouchEvent
//            FortyFive.currentGame?.cardRightClicked(card)
        }
    }

    override fun onEnd(callback: () -> Unit) = lifetime.onEnd(callback)

//    private fun showExtraDescriptions(descriptionParent: CustomFlexBox) {
//        val allKeys = card.getKeyWordsForDescriptions()
//        DetailDescriptionHandler
//            .descriptions
//            .filter { it.key in allKeys }
//            .forEach {
//                addHoverItemToParent(it.value.second, descriptionParent)
//            }
//        if (FortyFive.currentGame == null) return
//        card
//            .getAdditionalHoverDescriptions()
//            .filter { it.isNotBlank() }
//            .forEach { addHoverItemToParent(it, descriptionParent) }
//    }

    override fun setX(x: Float) {
        super.setX(x)
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

    private fun setupShader(batch: Batch): Boolean {
        val shaderPromise = when {
            inDestroyAnim -> spawnShader
            spawnAnimStart != 0L -> spawnShader
            else -> return false
        }
        if (shaderPromise.isNotResolved) ResourceManager.forceResolve(shaderPromise)
        val shader = shaderPromise.getOrError()
        batch.flush()
        shader.shader.bind()
        shader.prepare(screen)
        batch.shader = shader.shader
        if (spawnAnimStart != 0L) {
            val time = TimeUtils.millis()
            val percent = (time - spawnAnimStart).toFloat() / spawnAnimDuration.toFloat()
            shader.shader.setUniformf("u_progress", percent)
        }
        return true
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        validate()
        detailWidget?.updateBounds(this)
        batch ?: return
        if (cardTexturePromise?.isResolved == true) {
            texture?.let { FortyFive.cardTextureManager.giveTextureBack(card) }
            texture = cardTexturePromise?.getOrError()
            cardTexturePromise = null
        }
        val texture = texture ?: return
        val textureRegion = TextureRegion(texture)
        val isShaderSetup = setupShader(batch)
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
            textureRegion,
            x + drawOffsetX, y + drawOffsetY,
            width / 2, height / 2,
            width, height,
            scaleX, scaleY,
            rotation
        )
        batch.color = c
        batch.flush()
        if (isShaderSetup) batch.shader = null
        if (!isMarked) return
        markedSymbol.getOrNull()?.draw(
            batch,
            x + drawOffsetX, y + drawOffsetY,
            width / 2, height / 2,
            width, height,
            scaleX, scaleY,
            rotation
        )
    }

    override fun dispose() {
        lifetime.die()
        FortyFive.cardTextureManager.giveTextureBack(card)
    }

    fun redrawPixmap(damageValue: Int, costValue: Int) {
        cardTexturePromise = FortyFive.cardTextureManager.cardTextureFor(card, costValue, damageValue)
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
            if (spawnShader.isResolved) ResourceManager.forceResolve(spawnShader)
            spawnShader.getOrError().resetReferenceTime()
        }
        delay(1200)
        action { inDestroyAnim = false }
    }

    fun spawnAnimation(): Timeline = Timeline.timeline {
        val duration = 300
        action {
            spawnAnimStart = TimeUtils.millis()
            spawnAnimDuration = duration
        }
        delay(duration)
        action {
            spawnAnimStart = 0
        }
    }

    fun animateToTriggerPosition(controller: GameController, isOnShot: Boolean): Timeline = Timeline.timeline { later {
        prevPosition = Vector2(x, y)
        val target = when (card.zone) {
            Zone.REVOLVER -> if (isOnShot) {
                controller.revolver.getCardOnShotTriggerPosition()
            } else {
                controller.revolver.getCardTriggerPosition()
            }
            Zone.HAND -> Vector2(
                x, y + 300f
            )
            Zone.AFTERLIFE -> Vector2(
                x, y + 300f
            )
            Zone.STACK, Zone.LIMBO -> return@later
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
    } }

    fun animateBack(controller: GameController, prevCoordinates: Vector2): Timeline = Timeline.timeline {
        val target = controller
            .revolver
            .slots
            .find { it.card === card }
            ?.cardPosition()
            ?: prevCoordinates
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


    override fun positionChanged() {
        super.positionChanged()
        detailWidget?.updateBounds(this)
    }

    override fun sizeChanged() {
        super.sizeChanged()
        detailWidget?.updateBounds(this)
    }

    private fun getEffectTexts(): () -> List<String> = {
        val allKeys = card.getKeyWordsForDescriptions()
        val texts: MutableList<String> = mutableListOf()

        texts.addAll(DetailDescriptionHandler
            .descriptions
            .filter { it.key in allKeys }.map { it.value.second })

        if (FortyFive.currentGame != null)
            texts.addAll(card.getAdditionalHoverDescriptions().filter { it.isNotBlank() })
        texts
    }


    override fun initStyles(screen: OnjScreen) {
        addActorStyles(screen)
    }

    companion object {
        val cardDetailEffects by lazy {
            DetailDescriptionHandler.allTextEffects.value.map {
                AdvancedTextParser.AdvancedTextEffect.getFromOnj(it as OnjNamedObject)
            }
        }
    }
}
