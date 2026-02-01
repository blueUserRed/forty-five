package com.microwavestudios.fortyfive.game.card

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Touchable
import com.badlogic.gdx.scenes.scene2d.actions.MoveToAction
import com.badlogic.gdx.scenes.scene2d.actions.ScaleToAction
import com.badlogic.gdx.scenes.scene2d.ui.Widget
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable
import com.badlogic.gdx.scenes.scene2d.utils.TransformDrawable
import com.badlogic.gdx.utils.Disposable
import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.*
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl.Zone
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputActor
import com.microwavestudios.fortyfive.keyInput.InputActorImpl
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.onjNamespaces.OnjZone
import com.microwavestudios.fortyfive.rendering.BetterShader
import com.microwavestudios.fortyfive.screen.DropShadow
import com.microwavestudios.fortyfive.screen.SquareDropShadow
import com.microwavestudios.fortyfive.screen.DropShadowActor
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.screen.actors.AnimatedActor
import com.microwavestudios.fortyfive.screen.actors.KotlinStyledActor
import com.microwavestudios.fortyfive.screen.actors.OffSettable
import com.microwavestudios.fortyfive.screen.commonComponents.DetailWidget
import com.microwavestudios.fortyfive.screen.actors.PositionType
import com.microwavestudios.fortyfive.screen.actors.PropertyAction
import com.microwavestudios.fortyfive.screen.actors.ZIndexActor
import com.microwavestudios.fortyfive.utils.*
import onj.value.*
import kotlin.math.absoluteValue
import kotlin.math.log

/**
 * represents a type of card, e.g. there is one Prototype for an incendiary bullet, but there might be more than one
 * actual instances of the card. Prototypes can be used to create those instances
 * @param name the name of the card produced by this prototype
 * @param type the type of card (bullet or cover)
 * @param creator lambda that creates the instance
 */
class CardPrototype(
    val namespace: String?,
    val simpleName: String,
    val title: String,
    val baseCost: Int,
    val baseDamage: Int,
    val tags: List<String>,
) {

    val name = namespace?.let { "$it:$simpleName" } ?: simpleName

    var creator: ((screen: OnjScreen, startedInDeck: Boolean, isSaved: Boolean?, areHoverDetailsEnabled: Boolean) -> Card)? = null

    private val priceModifiers: MutableList<(Int) -> Int> = mutableListOf()

    /**
     * creates an actual instance of this card
     */
    fun create(
        screen: OnjScreen,
        startedInDeck: Boolean = false,
        isSaved: Boolean? = null,
        areHoverDetailsEnabled: Boolean = true
    ): Card = creator!!(screen, startedInDeck, isSaved, areHoverDetailsEnabled)

    fun modifyPrice(modifier: (Int) -> Int) {
        priceModifiers.add(modifier)
    }

    fun getPriceWithModifications(basePrice: Int) = priceModifiers.fold(basePrice) { acc, mod -> mod(acc) }

    fun copy(): CardPrototype = CardPrototype(namespace, name, title, baseCost, baseDamage, tags).apply {
        this.priceModifiers.addAll(this@CardPrototype.priceModifiers)
        this.creator = this@CardPrototype.creator
    }

    fun cleanCopy(): CardPrototype = CardPrototype(namespace, name, title, baseCost, baseDamage, tags).apply {
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
    val namespace: String?,
    val simpleName: String,
    val title: String,
    val flavourText: String,
    val shortDescription: String,
    val baseDamage: Int,
    val baseCost: Int,
    val rightClickCost: Int?,
    val price: Int,
    val effects: List<Effect>,
    val rotationDirection: RevolverRotation,
    val variableTexture: VariableTextureSelector?,
    val parryNumber: Int?,
    val startedInDeck: Boolean,
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

    val name: String = namespace?.let { "$it:$simpleName" } ?: simpleName

    /**
     * used for logging
     */
    val logTag = "$name-${++instanceCounter}"

    /**
     * the actor for representing the card on the screen
     */
    val actor: CardActor

    val inGame: Boolean
        get() = game != null

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
    var isThorns: Boolean = false
        private set
    var isPunk: Boolean = false
        private set
    var isPersistent: Boolean = false
        private set

    var stackPosition: StackPosition = StackPosition.NORMAL
        private set

    private var lastDamageValue: Int = baseDamage
    private var lastCostValue: Int = baseCost

    // total amount of modifiers added, is added to [damageModifiers] alongside the modifier,
    // used for sorting them because the order is important, and they may be removed / added back
    private var damageModifierCounter: Int = 0

    private val damageModifiers: MutableList<Pair<Int, CardDamageModifier>> = mutableListOf()
    private val costModifiers: MutableList<CardCostModifier> = mutableListOf()

    // first one protects in general, for shot and parry, second one protects for parry only
    private val protectingModifiers: MutableList<ProtectingModifier> = mutableListOf()
    private val parryOnlyProtectingModifiers: MutableList<ProtectingModifier> = mutableListOf()

    /**
     * first is the keyword, second is the actual text
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
    var turnRotationCounter: Int = 0
        private set
    var lastTurnRotationCounter: Int = 0
        private set
    var continuousRotationCounter: Int = 0
        private set
    var lastRotationDirection: RevolverRotation = RevolverRotation.None
        private set

    var isMarked: Boolean
        set(value) {
            actor.isMarked = value
        }
        get() = actor.isMarked

    var lastEffectAffectedCardsCache: List<Card> = listOf()

    var zone: Zone = Zone.STACK
        private set

    private var game: GameController? = null
    internal val gameEvents: EventPipeline
        get() = game!!.gameEvents

    var currentVariablePostfix: String? = null
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

    fun setGame(game: GameController) {
        this.game = game
        game.gameEvents.watchFor<GameControllerImpl.Events.EndTurnEvent> {
            lastTurnRotationCounter = turnRotationCounter
            turnRotationCounter = 0
        }
    }

    fun canBeReplaced(controller: GameController, by: Card): Boolean = isReplaceable

    fun replaceTimeline(controller: GameController, replaceBy: Card): Timeline = Timeline.timeline {
        action { lastEffectAffectedCardsCache = listOf(replaceBy) } // Memorize replacing card
        include(controller.destroyCardTimeline(this@Card, replaceBy))
    }

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
        } else {
            actor.isDraggable = false
        }
        if (newZone == Zone.REVOLVER) {
            actor.touchable = Touchable.disabled
        } else {
            actor.touchable = Touchable.enabled
        }
        if (oldZone == Zone.REVOLVER) {
            rotationCounter = 0
            continuousRotationCounter = 0
        }
    }

    fun inZone(vararg zones: Zone): Boolean = zones.contains(zone)

    inline fun <T> checkValiditySingleModifierList(
        controller: GameController,
        modifiers: MutableList<T>,
        getter: (T) -> CardModifier
    ): Boolean {
        var somethingChanged = false
        modifiers.iterateRemoving { value, remover ->
            val modifier = getter(value)
            if (!modifier.data.validityChecker(controller, this, modifier.data)) {
                FortyFive.logger.debug(logTag, "modifier no longer valid: $modifier")
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
            checkValiditySingleModifierList(controller, protectingModifiers, getter = { it }) ||
            checkValiditySingleModifierList(controller, parryOnlyProtectingModifiers, getter = { it })
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
        variableTexture?.let { selector ->
            val new = selector.selector(controller, this)
            if (new == currentVariablePostfix) return@let
            currentVariablePostfix = new
            updateTexture(controller)
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
        wasParry: Boolean,
        putCardInTheHand: (Card) -> Timeline,
        putCardInTheStack: (Card) -> Timeline
    ): Timeline = Timeline.timeline { skipping { skip ->
        action {
            if (isEverlasting && !controller.isEverlastingDisabled) {
                skip()
                return@action
            }
            if (wasParry && parryOnlyProtectingModifiers.isNotEmpty()) {
                val effect = parryOnlyProtectingModifiers.first()
                val newEffect = effect.copy(shots = effect.shots - 1)
                if (newEffect.shots == 0) {
                    parryOnlyProtectingModifiers.removeFirst()
                } else {
                    parryOnlyProtectingModifiers[0] = newEffect
                }
                modifiersChanged()
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
        action {
            if (isPersistent) return@action
            damageModifiers.removeIf { !it.second.data.keepActive }
            protectingModifiers.removeIf { !it.data.keepActive }
            parryOnlyProtectingModifiers.removeIf { !it.data.keepActive }
            modifiersChanged()
        }
        if (isUndead) include(putCardInTheHand(this@Card))
        else include(putCardInTheStack(this@Card))
    } }

    fun changeStackPosition(stackPosition: StackPosition, controller: GameController) {
        this.stackPosition = stackPosition
        controller.cardStack.dirty()
    }

    fun protect(protectingModifier: ProtectingModifier) {
        if (isUndead) {
            FortyFive.logger.debug(logTag, "cant protect undead bullet")
            return
        }
        protectingModifiers.add(protectingModifier.copy())
        modifiersChanged()
    }

    fun protectParryOnly(protectingModifier: ProtectingModifier) {
        if (isUndead) {
            FortyFive.logger.debug(logTag, "cant protect undead bullet")
            return
        }
        parryOnlyProtectingModifiers.add(protectingModifier.copy())
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

    fun addDamageModifier(modifier: CardDamageModifier) {
        FortyFive.logger.debug(logTag, "card got new modifier: $modifier")
        damageModifiers.add(++damageModifierCounter to modifier.copy())
        modifiersChanged()
    }

    fun addCostModifier(modifier: CardCostModifier) {
        FortyFive.logger.debug(logTag, "card got new modifier: $modifier")
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
     * called when the revolver rotates (but not when this card was shot)
     */
    fun onRevolverRotation(rotation: RevolverRotation) {
        rotationCounter += rotation.amount
        turnRotationCounter += rotation.amount
        if (rotation.directionString == lastRotationDirection.directionString) {
            continuousRotationCounter += rotation.amount
        } else {
            continuousRotationCounter = 0
            lastRotationDirection = rotation
        }
    }

    fun checkEffects(
        situation: GameSituation,
        triggerInformation: TriggerInformation,
        controller: GameController,
    ): Timeline = Timeline.timeline { later {

        action { checkModifierTransformers(situation, triggerInformation, controller) }

        val prevPosition = Vector2(actor.x, actor.y)
        val zoneAtStart = zone
        var isInTriggerPosition = false
        effects.forEach { effect ->
            later {
                val shouldTrigger = effect.checkTrigger(situation, triggerInformation, controller, this@Card)
                if (!shouldTrigger) return@later
                if (!isInTriggerPosition && !effect.data.isHidden && !inZone(Zone.STACK, Zone.LIMBO)) {
                    later {
                        isInTriggerPosition = true

                        val afterlifeShouldBeOpen = inZone(Zone.AFTERLIFE) || effect.animatesInAfterlife()
                        val afterlife = controller.afterlife
                        if (afterlife.isClosed && afterlifeShouldBeOpen) include(afterlife.openTimeline())
                        if (afterlife.isOpen && !afterlifeShouldBeOpen) include(afterlife.closeTimeline())

                        val screenShakeTimeline = Timeline.timeline {
                            delay(210)
                            include(controller.gameRenderPipeline.getScreenShakeTimeline())
                        }

                        val animateLikeOnShot = triggerInformation.isOnShot && !effect.useAlternateOnShotTriggerPosition()
                        val anim = actor.animateToTriggerPosition(controller, animateLikeOnShot, afterlifeShouldBeOpen)

                        action { controller.dispatchAnimTimeline(screenShakeTimeline) }
                        include(anim)
                    }
                }
                include(effect.trigger(this@Card, triggerInformation, controller))
                later {
                    if (!isInTriggerPosition) return@later
                    if (zone == zoneAtStart) return@later
                    actor.skipAnimateBack()
                    isInTriggerPosition = false
                }
            }
        }

        later {
            if (!isInTriggerPosition) return@later
            if (zone == zoneAtStart) {
                include(actor.animateBack(controller, prevPosition))
            } else {
                action { actor.skipAnimateBack() }
            }
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

        if (parryOnlyProtectingModifiers.isNotEmpty()) {
            val total = parryOnlyProtectingModifiers.sumOf { it.shots }
            currentEffects.add("hardened" to "\$trait\$+ HARDENED ($total)\$trait\$")
        }

        currentHoverTexts = currentEffects
    }

    private fun updateTexture(controller: GameController) =
        actor.redrawPixmap(curDamage(controller), curCost(controller))

    override fun dispose() = actor.dispose()

    override fun toString(): String {
        return logTag
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
                    val mostExpensive = game
                        ?.cardsInRevolver()
                        ?.maxOfOrNull { it.lastCostValue }
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
            from: String?,
            initializer: (Card) -> Unit
        ): List<CardPrototype> {

            val prototypes = mutableListOf<CardPrototype>()

            cards
                .value
                .forEach { onj ->
                    onj as OnjObject
                    val prototype = CardPrototype(
                        from,
                        onj.get<String>("name"),
                        onj.get<String>("title"),
                        onj.get<Long>("cost").toInt(),
                        onj.get<Long>("baseDamage").toInt(),
                        onj.get<OnjArray>("tags").value.map { it.value as String },
                    )
                    prototype.creator = { screen, startedInDeck, isSaved, areHoverDetailsEnabled ->
                        getCardFrom(onj, screen, initializer, prototype, startedInDeck, isSaved, areHoverDetailsEnabled)
                    }
                    prototypes.add(prototype)
                }
            return prototypes
        }

        private fun getCardFrom(
            onj: OnjObject,
            onjScreen: OnjScreen,
            initializer: (Card) -> Unit,
            prototype: CardPrototype,
            startedInDeck: Boolean,
            isSaved: Boolean?,
            enableHoverDetails: Boolean
        ): Card {
            val name = onj.get<String>("name")
            val card = Card(
                namespace = prototype.namespace,
                simpleName = name,
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
                            maxExecutions = it.getOr("maxExecutions", -1L).toInt(),
                            onlyTriggerInZones = it.ifHas<OnjArray, List<Zone>>("inZones") { arr ->
                                arr
                                    .value
                                    .map { (it as OnjZone).value }
                            },
                            condition = it.getOr<OnjNamedObject?>("condition", null)?.let { GamePredicate.fromOnj(it) }
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
                variableTexture = onj.getOr<VariableTextureSelector?>("variableTexture", null),
                parryNumber = onj.getOr<Long?>("parryNumber", null)?.toInt(),
                startedInDeck = startedInDeck,
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
                "thorns" -> card.isThorns = true
                "punk" -> card.isPunk = true
                "persistence" -> card.isPersistent = true
                "alwaysAtBottom" -> card.stackPosition = StackPosition.BOTTOM
                "alwaysAtTop" -> card.stackPosition = StackPosition.TOP

                else -> throw RuntimeException("unknown trait effect $effect")
            }
        }
    }

    enum class StackPosition {
        NORMAL, BOTTOM, TOP
    }

}

interface CardModifier {
    val data: CardModifierData
}

data class VariableTextureSelector(
    val selector: (controller: GameController, card: Card) -> String,
    val base: String
)

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
    val keepActive: Boolean = false,
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
    val enableHoverDetails: Boolean // TODO: fix
) : Widget(), ZIndexActor, InputActor by InputActorImpl(), Selectable<CardActor>,
    OffSettable, Disposable, ResourceBorrower, KotlinStyledActor, DropShadowActor, AnimatedActor {

    override var detailWidget: DetailWidget? = DetailWidget.ComplexBigDetailActor(
        screen,
        effects = cardDetailEffects,
        text = {
            val list = card.currentHoverTexts.map { it.second }.toMutableList()
            list.add(card.shortDescription)
            list.add(card.flavourText)
            list
        },
        subtexts = getEffectTexts()
    )

    override val animationsNeedingUpdate: MutableList<AnimatedActor.NeedsUpdate> = mutableListOf()

    override var fixedZIndex: Int = 0

    override var drawOffsetX: Float = 0F
    override var drawOffsetY: Float = 0F
    override var logicalOffsetX: Float = 0F
    override var logicalOffsetY: Float = 0F

    override var marginTop: Float = 0F
    override var marginBottom: Float = 0F
    override var marginLeft: Float = 0F
    override var marginRight: Float = 0F
    override var positionType: PositionType = PositionType.RELATIVE

    override val reusableInputActor: Boolean = true

    private var inDestroyAnim: Boolean = false
    private var spawnAnimStart: Long = 0L
    private var spawnAnimDuration: Int = 0
    private var reverseSpawnAnim: Boolean = false

    override var dropShadow: DropShadow? = null

    private val _lifetime: EndableLifetime = EndableLifetime()
    val lifetime: Lifetime
        get() = _lifetime

    private val destroyShader: Promise<BetterShader> =
        FortyFive.resourceManager.request(this, lifetime, "dissolve_shader")

    private val spawnShader: Promise<BetterShader> =
        FortyFive.resourceManager.request(this, lifetime, "card_spawn_shader")

    private val grayScaleShader: Promise<BetterShader> =
        FortyFive.resourceManager.request(this, lifetime, "grayscale_shader")

    private val markedSymbol: Promise<TransformDrawable> =
        FortyFive.resourceManager.request(this, lifetime, "card_symbol_marked")

    private var prevPosition: Vector2? = null

    private var selectionPromise: Promise<CardActor>? = null

    private var rotationOnSelectionEnter: Float = 0f
    private val selectionAnimation: AnimatedActor.AnimationController =
        animateRotationSinus(amplitude = Math.PI.toFloat() * 0.5f, frequency = 30f, phase = 0f)
            .also { it.stop() }

    private val selectionDropShadow = SquareDropShadow(
        color = Color.GOLDENROD,
        scale = 1.2f,
        offX = 0f,
        offY = 0f
    )

    private val defaultFocusDropShadow = SquareDropShadow(
        color = Color.Black,
        scale = 1.1f,
        offX = 3f,
        offY = -3f
    )

    var playSoundsOnHover: Boolean = false

    var isMarked: Boolean = false

    var isGrayScale: Boolean = false

    private var cardTexturePromise: Promise<Texture>? = null
    private var texture: Texture? = null

    var inTriggerPosition: Boolean = false
        private set

    init {
        initInput(this, screen)
        bindDetailToInputState(GameInputs.States.focused)
        keyboardFocusable = KeyboardFocusable.LEAF

        cardTexturePromise = FortyFive.cardTextureManager.cardTextureFor(
            card,
            card.baseCost,
            card.baseDamage,
            card.variableTexture?.base
        )

        joinGroup(cardGroup)
        startDragAndDropOn(GameInputs.initDragAndDrop)
        onEnterInputState(GameInputs.States.focused) {
            if (!playSoundsOnHover) return@onEnterInputState
            FortyFive.soundPlayer.situation("card_hover", screen)
        }

        onInput(GameInputs.interact) { clicked() }
        onInput(GameInputs.triggerCard) { rightClicked() }

        observeInputState(
            GameInputs.States.focused,
            ::focusEnter, ::focusLoss
        )
        observeInputState(
            GameInputs.States.inDrag,
            { FortyFive.soundPlayer.situation("card_drag_started", screen) },
            { FortyFive.soundPlayer.situation("card_drag_finished", screen) }
        )
    }

    private fun focusEnter() {
        if (selectionPromise != null) {
            selectionAnimation.start()
            return
        }
        if (card.inZone(Zone.REVOLVER) && (dropShadow == null || dropShadow == defaultFocusDropShadow)) {
            dropShadow = defaultFocusDropShadow
            defaultFocusDropShadow.showDropShadow = true
        }
    }

    private fun focusLoss() {
        if (selectionPromise != null) {
            selectionAnimation.stop()
            selectionAnimation.reset()
            rotation = rotationOnSelectionEnter
            return
        }
        if (card.inZone(Zone.REVOLVER) && (dropShadow == null || dropShadow == defaultFocusDropShadow)) {
            dropShadow = defaultFocusDropShadow
            defaultFocusDropShadow.showDropShadow = false
        }
    }

    fun clickedViaSlot(rightClick: Boolean) {
        if (rightClick) rightClicked() else clicked()
    }

    private fun clicked() {
        val selectionPromise = selectionPromise ?: return
        selectionPromise.resolve(this)
    }

    private fun rightClicked() {
        if (!card.inGame) return
        card.gameEvents.fire(GameControllerImpl.Events.CardRightClickEvent(card))
    }

    override fun enterSelectionMode(promise: Promise<CardActor>) {
        selectionPromise = promise
        dropShadow = selectionDropShadow
        joinGroup(selectableCardGroup)
        rotationOnSelectionEnter = rotation
    }

    override fun exitSelectionMode() {
        selectionPromise = null
        dropShadow = null
        selectionAnimation.stop()
        selectionAnimation.reset()
        rotation = rotationOnSelectionEnter
        leaveGroup(selectableCardGroup)
    }

    private fun setupShader(batch: Batch): Boolean {
        val shaderPromise = when {
            inDestroyAnim -> destroyShader
            spawnAnimStart != 0L -> spawnShader
            isGrayScale -> grayScaleShader
            else -> return false
        }
        if (shaderPromise.isNotResolved) FortyFive.resourceManager.forceResolve(shaderPromise)
        val shader = shaderPromise.getOrError()
        batch.flush()
        shader.shader.bind()
        shader.prepare(screen)
        batch.shader = shader.shader
        if (spawnAnimStart != 0L) {
            val time = TimeUtils.millis()
            var percent = (time - spawnAnimStart).toFloat() / spawnAnimDuration.toFloat()
            if (reverseSpawnAnim) percent = 1f - percent
            shader.shader.setUniformf("u_progress", percent)
        }
        return true
    }

    override fun drawInDrag(batch: Batch) {
        doDraw(batch, 1f)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        updateAnimations()
        if (isDragged) return
        doDraw(batch, parentAlpha)
    }

    private fun doDraw(batch: Batch?, parentAlpha: Float) {
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
        val textureSize = width
        val x = x
        val y = y
        dropShadow?.doDropShadow(batch, screen, TextureRegionDrawable(textureRegion), this, scaleX, scaleY, rotation)
        batch.draw(
            textureRegion,
            x + drawOffsetX, y + drawOffsetY,
            textureSize / 2, textureSize / 2,
            textureSize, textureSize,
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
            textureSize / 2, textureSize / 2,
            textureSize, textureSize,
            scaleX, scaleY,
            rotation
        )
    }

    override fun dispose() {
        _lifetime.die()
        FortyFive.cardTextureManager.giveTextureBack(card)
    }

    fun redrawPixmap(damageValue: Int, costValue: Int) {
        cardTexturePromise = FortyFive.cardTextureManager.cardTextureFor(card, costValue, damageValue, card.currentVariablePostfix)
    }

    // TODO: came up with system for animations
    fun destroyAnimation(): Timeline = Timeline.timeline {
        action {
            FortyFive.soundPlayer.situation("card_destroyed", screen)
            inDestroyAnim = true
            if (destroyShader.isResolved) FortyFive.resourceManager.forceResolve(destroyShader)
            destroyShader.getOrError().resetReferenceTime()
        }
        delay(1200)
        action { inDestroyAnim = false }
    }

    fun spawnAnimation(reverse: Boolean = false): Timeline = Timeline.timeline {
        val duration = 300
        action {
            spawnAnimStart = TimeUtils.millis()
            spawnAnimDuration = duration
            reverseSpawnAnim = reverse
        }
        delay(duration)
        action {
            spawnAnimStart = 0
        }
    }

    fun animateToTriggerPosition(
        controller: GameController,
        isOnShot: Boolean,
        afterlifeOpen: Boolean
    ): Timeline = Timeline.timeline { later {
        prevPosition = Vector2(x, y)
        val target = when (card.zone) {
            Zone.REVOLVER -> if (isOnShot) {
                controller.revolver.getCardOnShotTriggerPosition()
            } else {
                if (afterlifeOpen) {
                    controller.revolver.getMirroredCardTriggerPosition()
                } else {
                    controller.revolver.getCardTriggerPosition()
                }
            }
            Zone.HAND -> Vector2(
                x, y + 300f
            )
            Zone.AFTERLIFE -> Vector2(
                x, y + 200f
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
            inTriggerPosition = true
            if (card.inZone(Zone.REVOLVER)) touchable = Touchable.enabled
            FortyFive.soundPlayer.situation("card_trigger_anim_in", screen)
            toFront()
            addAction(moveAction)
            addAction(scaleAction)
        }
        delayUntil { moveAction.isComplete }
        action {
            removeAction(moveAction)
            removeAction(scaleAction)
            (parent as? Layout)?.invalidate()
        }
        delay(100)
    } }

    override fun setBounds(x: Float, y: Float, width: Float, height: Float) {
        // baaaaaaaaaad
        if (!inTriggerPosition) super.setBounds(x, y, width, height)
    }

    fun skipAnimateBack() {
        inTriggerPosition = false
        setScale(1f)
    }

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
            if (card.inZone(Zone.REVOLVER)) touchable = Touchable.disabled
            FortyFive.soundPlayer.situation("card_trigger_anim_out", screen)
            addAction(moveAction)
            addAction(scaleAction)
        }
        delayUntil { moveAction.isComplete }
        action {
            removeAction(moveAction)
            removeAction(scaleAction)
            inTriggerPosition = false
        }
    }

    fun descendAnimation(): Timeline = Timeline.timeline {
        val action = PropertyAction(
            this@CardActor,
            ::drawOffsetY,
            y - 1000f
        )
        action.duration = 0.4f
        action.interpolation = Interpolation.exp10
        action { addAction(action) }
        delayUntil { action.isComplete }
    }

    fun resetDescendAnimation() {
        drawOffsetY = 0f
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
            texts.addAll(card.getAdditionalHoverDescriptions().filter { it.isNotBlank() })
        texts
    }

    companion object {

        val cardDetailEffects by lazy {
            DetailDescriptionHandler.allTextEffects.value.map {
                AdvancedTextParser.AdvancedTextEffect.getFromOnj(it as OnjNamedObject)
            }
        }

        const val cardGroup: String = "card-group"
        const val selectableCardGroup: String = "selectable-card-group"
    }
}
