package com.microwavestudios.fortyfive.game.card

import com.badlogic.gdx.graphics.Texture
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.graphics.g2d.TextureRegion
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
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
import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.keyInput.ActorWithDragFeatures
import com.microwavestudios.fortyfive.keyInput.GameInputs
import com.microwavestudios.fortyfive.keyInput.InputActor
import com.microwavestudios.fortyfive.keyInput.InputActorImpl
import com.microwavestudios.fortyfive.keyInput.InputManager
import com.microwavestudios.fortyfive.keyInput.KeyboardFocusable
import com.microwavestudios.fortyfive.onjNamespaces.OnjZone
import com.microwavestudios.fortyfive.rendering.BetterShader
import com.microwavestudios.fortyfive.screen.DropShadow
import com.microwavestudios.fortyfive.screen.SquareDropShadow
import com.microwavestudios.fortyfive.screen.DropShadowActor
import com.microwavestudios.fortyfive.resources.ResourceBorrower
import com.microwavestudios.fortyfive.screen.IScreen
import com.microwavestudios.fortyfive.screen.RenderableScreen
import com.microwavestudios.fortyfive.screen.actors.AnimatedActor
import com.microwavestudios.fortyfive.screen.actors.KotlinStyledActor
import com.microwavestudios.fortyfive.screen.actors.OffSettable
import com.microwavestudios.fortyfive.screen.commonComponents.DetailWidget
import com.microwavestudios.fortyfive.screen.actors.PositionType
import com.microwavestudios.fortyfive.screen.actors.PropertyAction
import com.microwavestudios.fortyfive.screen.actors.ZIndexActor
import com.microwavestudios.fortyfive.utils.*
import onj.builder.buildOnjObject
import onj.value.*
import kotlin.collections.map
import kotlin.math.absoluteValue

/**
 * contains all information needed to construct a specific card. Stores the namespace, name, and
 * possibly the stamp of the card. Can be serialized to/deserialized from onj. To construct an instance
 * of a card, the corresponding [CardPrototype] is needed
 */
data class CardType(
    val namespace: String?,
    val simpleName: String,
    val stamp: String?
) {
    val name = namespace?.let { "$it:$simpleName" } ?: simpleName

    fun asOnj(): OnjObject = buildOnjObject {
        namespace?.let { "namespace" with it }
        stamp?.let { "stamp" with it }
        "name" with simpleName
    }

    override fun toString(): String = stamp?.let { stamp -> "$name-$stamp" } ?: name

    companion object {

        fun fromOnj(onj: OnjObject): CardType = CardType(
            onj.getOr<String?>("namespace", null),
            onj.get<String>("name"),
            onj.getOr<String?>("stamp", null),
        )

        fun fromString(s: String): CardType {
            val parts = s.splitToSequence(':').toList()
            if (parts.size == 1) return CardType(null, s, null)
            require(parts.size == 2) { "Invalid card type string: '$s'" }
            return CardType(
                parts.first(),
                parts[1],
                null
            )
        }
    }
}

/**
 * represents a card as declared in the cards.onj file. Can be used together with [CardType] to construct
 * an actual instance of [Card]
 */
class CardPrototype(
    val namespace: String?,
    val simpleName: String,
    val title: String,
    val baseCost: Int,
    val baseDamage: Int,
    val deckMaximum: Int,
    val tags: List<String>,
) {

    val name = namespace?.let { "$it:$simpleName" } ?: simpleName

    var creator: CardCreator? = null

    private val priceModifiers: MutableList<(Int) -> Int> = mutableListOf()

    /**
     * creates an actual instance of this card. [type] should match this prototype
     */
    fun create(
        screen: IScreen,
        type: CardType,
        presentationProvider: PresentationProvider
    ): Card {
        require(type.name == name) { "CardType - Prototype mismatch $type != $this" }
        return creator!!(screen, type.stamp, presentationProvider)
    }

    fun modifyPrice(modifier: (Int) -> Int) {
        priceModifiers.add(modifier)
    }

    fun getPriceWithModifications(basePrice: Int) = priceModifiers.fold(basePrice) { acc, mod -> mod(acc) }

    fun copy(): CardPrototype = CardPrototype(namespace, name, title, baseCost, baseDamage, deckMaximum, tags).apply {
        this.priceModifiers.addAll(this@CardPrototype.priceModifiers)
        this.creator = this@CardPrototype.creator
    }

    fun cleanCopy(): CardPrototype = CardPrototype(namespace, name, title, baseCost, baseDamage, deckMaximum, tags).apply {
        this.creator = this@CardPrototype.creator
    }

    override fun equals(other: Any?): Boolean = other is CardPrototype && other.name == name
    override fun hashCode(): Int = name.hashCode()

    override fun toString(): String = "CardProto($name)"
}

typealias CardCreator = (
    screen: IScreen,
    withStamp: String?,
    presentationProvider: PresentationProvider
) -> Card

/**
 * represents an actual instance of a card. Can be created using [CardPrototype]
 */
class Card(
    val type: CardType,
    val title: String,
    val flavourText: String,
    val shortDescription: String,
    val originalBaseDamage: Int,
    val originalBaseCost: Int,
    val stamp: Stamp?,
    val rightClickCost: Int?,
    val price: Int,
    effects: List<Effect>,
    private val rotationDirection: RevolverRotation,
    val variableTexture: VariableTextureSelector?,
    val parryNumber: Int?,
    val tags: List<String>,
    val isDark: Boolean,
    val forbiddenSlots: List<Int>,
    val additionalHoverInfos: List<String>,
    screen: IScreen,
    val deckMaximum: Int,
    presentationProvider: PresentationProvider
) : Disposable {

    val name: String
        get() = type.name

    /**
     * used for logging
     */
    val logTag = "$type-${++instanceCounter}"

    val presentation: CardPresentation

    val inGame: Boolean
        get() = game != null

    private val _effects: MutableList<Effect> = effects.toMutableList()
    val effects: List<Effect>
        get() = _effects

    var isRotten: Boolean = false
        private set
    var isReinforced: Boolean = false
        private set
    var isPunk: Boolean = false
        private set
    var isPersistent: Boolean = false
        private set
    var isPiercing: Boolean = false
        private set

    private val behaviours: MutableMap<BulletBehaviour, Pair<Boolean, MutableList<() -> Boolean>>> = mutableMapOf()
    private val activeBehaviours: Set<BulletBehaviour>
        get() = behaviours.keys

    var stackPosition: StackPosition = StackPosition.NORMAL
        private set

    val baseDamage: Int = stamp?.modifyBaseDamage(this, originalBaseDamage) ?: originalBaseDamage
    val baseCost: Int = stamp?.modifyBaseCost(this, originalBaseCost) ?: originalBaseCost

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
    var fullRotationCounter: Int = 0
        private set
    var lastRotationDirection: RevolverRotation = RevolverRotation.None
        private set

    var isMarked: Boolean
        set(value) {
            presentation.isMarked = value
        }
        get() = presentation.isMarked

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
            presentation = presentationProvider(this, screen)
        }
    }

    fun setGame(game: GameController) {
        this.game = game
        game.gameEvents.watchFor<GameControllerImpl.Events.EndTurnEvent> {
            lastTurnRotationCounter = turnRotationCounter
            turnRotationCounter = 0
        }
    }

    fun canBeReplaced(controller: GameController, by: Card): Boolean =
        activeBehaviours.any { it.canBeReplaced(controller, this, by) }

    fun clearProtectingModifiers() {
        protectingModifiers.clear()
        parryOnlyProtectingModifiers.clear()
    }

    fun replaceTimeline(controller: GameController, replaceBy: Card): Timeline = Timeline.timeline {
        action { lastEffectAffectedCardsCache = listOf(replaceBy) } // Memorize replacing card
        include(controller.destroyCardTimeline(this@Card, replaceBy))
    }

    fun changeZone(newZone: Zone, controller: GameController) {
        val oldZone = zone
        zone = newZone
        if (presentation.inTriggerPosition) presentation.skipAnimateBack()
        if (newZone == Zone.REVOLVER) {
            enteredInSlot = controller.slotOfCard(this)!!
            enteredOnTurn = controller.turnCounter
            if (isRotten) addRottenModifier(controller)
        }
        if (newZone == Zone.HAND) {
            presentation.isDraggable = true
        } else {
            presentation.isDraggable = false
        }
        if (newZone == Zone.REVOLVER) {
            presentation.touchable = Touchable.disabled
        } else {
            presentation.touchable = Touchable.enabled
        }
        if (oldZone == Zone.REVOLVER) {
            rotationCounter = 0
            fullRotationCounter = 0
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

    private fun checkTemporaryBehaviours(): Boolean {
        var somethingChanged = false
        behaviours.iterateRemoving { (_, value), remover ->
            val (permanent, conditions) = value
            if (permanent) return@iterateRemoving
            conditions.removeIf { !it() }
            if (conditions.isNotEmpty()) return@iterateRemoving
            somethingChanged = true
            remover()
        }
        return somethingChanged
    }

    /**
     * checks if the modifiers of this card are still valid and removes them if they are not
     */
    private fun checkModifierValidity(controller: GameController) {
        val somethingChanged = // use 'or' to prevent short-circuiting
            checkValiditySingleModifierList(controller, costModifiers, getter = { it }) or
            checkValiditySingleModifierList(controller, damageModifiers, getter = { it.second }) or
            checkValiditySingleModifierList(controller, protectingModifiers, getter = { it }) or
            checkValiditySingleModifierList(controller, parryOnlyProtectingModifiers, getter = { it }) or
            checkTemporaryBehaviours()
        if (somethingChanged) modifiersChanged()
    }

    fun canBeShot(controller: GameController): Boolean =
        activeBehaviours.none { it.preventsShooting(controller, this) }

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

    fun addBehaviour(behaviour: BulletBehaviour) {
        if (behaviour in behaviours) return
        require(behaviour.supportsBeingAddedLater || !inGame) {
            "can't add behaviour $behaviour after initialization of card"
        }
        behaviours[behaviour] = true to mutableListOf()
        behaviour.additionalEffects()?.let { _effects.addAll(it) }
        behaviour.init(this)
    }

    fun addTemporaryBehaviour(behaviour: BulletBehaviour, condition: () -> Boolean) {
        require(behaviour.supportsBeingAddedLater) {
            "can't add temporary behaviour $behaviour after initialization of card"
        }
        if (behaviour in behaviours) {
            val (permanent, conditions) = behaviours[behaviour]!!
            if (permanent) return
            conditions.add(condition)
            return
        }
        behaviours[behaviour] = true to mutableListOf(condition)
        behaviour.additionalEffects()?.let { _effects.addAll(it) }
        behaviour.init(this)
    }

    fun curDamage(controller: GameController): Int = damageModifiers
        .filter { (_, modifier) -> modifier.data.activeChecker(controller, this, modifier.data) }
        .sortedBy { it.first }
        .fold(baseDamage) { acc, (_, modifier) -> ((acc + modifier.damage) * modifier.damageMultiplier).toInt() }
        .coerceAtLeast(0)

    fun curOnShotDamage(controller: GameController): Int {
        val damage = curDamage(controller)
        return activeBehaviours.fold(damage) { acc, cur -> cur.modifyOnShotDamage(this, controller, acc) }
    }

    fun curParryValue(controller: GameController): Int {
        val parryValue = parryNumber ?: curDamage(controller)
        return activeBehaviours.fold(parryValue) { acc, cur -> cur.modifyParryValue(this, controller, acc) }
    }

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
        parryDamage: Int,
        triggerInfo: TriggerInformation,
        putCardInTheHand: (Card, TriggerInformation) -> Timeline,
        putCardInTheStack: (Card, TriggerInformation) -> Timeline
    ): Timeline = Timeline.timeline {
        later {
            activeBehaviours
                .mapNotNull { it.afterShotTimeline(this@Card, controller, wasParry, parryDamage) }
                .collectTimeline()
                .let { include(it) }
        }
        var leaveInRevolver = false
        action {
            if (activeBehaviours.any { it.keepInRevolverAfterShot(this@Card, controller, wasParry, parryDamage) }) {
                leaveInRevolver = true
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
                leaveInRevolver = true
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
                leaveInRevolver = true
                return@action
            }
            if (isPersistent) return@action
            damageModifiers.removeIf { !it.second.data.keepActive }
            protectingModifiers.removeIf { !it.data.keepActive }
            parryOnlyProtectingModifiers.removeIf { !it.data.keepActive }
            modifiersChanged()
        }
        later {
            if (leaveInRevolver) return@later
            val putInHand =
                activeBehaviours.any { it.putInHandInsteadOfStackAfterShot(this@Card, controller, wasParry, parryDamage) }
            if (putInHand) {
                include(putCardInTheHand(this@Card, triggerInfo))
            } else {
                include(putCardInTheStack(this@Card, triggerInfo))
            }
        }
    }

    fun afterDestroyed(
        card: Card,
        controller: GameController,
        sourceCard: Card?,
        putCardInAfterlife: (card: Card, sourceCard: Card?) -> Timeline,
        putCardInHand: (card: Card, sourceCard: Card?) -> Timeline,
    ): Timeline = Timeline.timeline {
        includeLater({
            if (activeBehaviours.any { it.putInHandAfterDestroy(card, controller) }) {
                putCardInHand(card, sourceCard)
            } else {
                putCardInAfterlife(card, sourceCard)
            }
        })
    }

    fun changeStackPosition(stackPosition: StackPosition, controller: GameController) {
        this.stackPosition = stackPosition
        controller.cardStack.dirty()
    }

    fun protect(protectingModifier: ProtectingModifier) {
        if (activeBehaviours.any { it.disableProtectingModifiers() }) {
            return
        }
        protectingModifiers.add(protectingModifier.copy())
        modifiersChanged()
    }

    fun protectParryOnly(protectingModifier: ProtectingModifier) {
        if (activeBehaviours.any { it.disableProtectingModifiers() }) {
            return
        }
        parryOnlyProtectingModifiers.add(protectingModifier.copy())
        modifiersChanged()
    }

    fun targetedEnemies(controller: GameController): List<Enemy> {
        activeBehaviours.forEach { behaviour ->
            behaviour.targetedEnemies(controller, this)?.let { return it }
        }
        return listOf(controller.targetedEnemy())
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

    fun addDamageModifier(modifier: CardDamageModifier, controller: GameController) {
        FortyFive.logger.debug(logTag, "card got new modifier: $modifier")
        var newModifier = modifier.copy()
        newModifier = activeBehaviours.fold(newModifier) { acc, cur -> cur.modifyDamageModifier(this, controller, acc) }
        damageModifiers.add(++damageModifierCounter to newModifier)
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
        addDamageModifier(modifier, controller)
    }

    /**
     * called when the revolver rotates (but not when this card was shot)
     */
    fun onRevolverRotationTimeline(
        rotation: RevolverRotation,
        fullRotationTimelineCreator: (Card) -> Timeline
    ): Timeline = Timeline.timeline {
        var prevFullRotCounter = fullRotationCounter
        action {
            prevFullRotCounter = fullRotationCounter

            rotationCounter += rotation.amount
            turnRotationCounter += rotation.amount
            if (rotation.directionString == lastRotationDirection.directionString) {
                fullRotationCounter += rotation.amount
            } else {
                fullRotationCounter = rotation.amount
                lastRotationDirection = rotation
            }
        }
        later {
            val prevFullRotations = prevFullRotCounter / 5
            val currentFullRotations = fullRotationCounter / 5
            if (currentFullRotations <= prevFullRotations) return@later
            repeat(currentFullRotations - prevFullRotations) {
                include(fullRotationTimelineCreator(this@Card))
            }
        }
    }

    fun checkEffects(
        situation: GameSituation,
        triggerInformation: TriggerInformation,
        controller: GameController,
    ): Timeline = Timeline.timeline { later {

        checkModifierTransformers(situation, triggerInformation, controller)

        val prevPosition = presentation.position()
        val zoneAtStart = zone
        var isInTriggerPosition = false
        val triggeredEffects = effects.filter {
            it.checkTrigger(situation, triggerInformation, controller, this@Card)
        }
        if (situation is GameSituation.CardRightClicked && triggeredEffects.isNotEmpty()) {
            val result = controller.tryPay(rightClickCost ?: 0, presentation.animTarget())
            if (!result) FortyFive.logger.warn(logTag, "Right Click triggered but can't pay for it")
        }
        triggeredEffects.forEach { effect ->
            later {
                if (!isInTriggerPosition && !effect.data.isHidden && !inZone(Zone.STACK, Zone.LIMBO)) {
                    later {
                        isInTriggerPosition = true

                        val afterlifeShouldBeOpen = inZone(Zone.AFTERLIFE) || effect.animatesInAfterlife()
                        val afterlife = controller.afterlife
                        if (afterlife.isClosed && afterlifeShouldBeOpen) include(afterlife.openTimeline())
                        if (afterlife.isOpen && !afterlifeShouldBeOpen) include(afterlife.closeTimeline())

                        val screenShakeTimeline = Timeline.timeline {
                            delay(210)
                            FortyFive.currentRenderPipeline?.getScreenShakeTimeline()?.let { include(it) }
                        }

                        val animateLikeOnShot = triggerInformation.isOnShot && !effect.useAlternateOnShotTriggerPosition()
                        val anim = presentation.animateToTriggerPosition(controller, animateLikeOnShot, afterlifeShouldBeOpen)

                        action { controller.dispatchAnimTimeline(screenShakeTimeline) }
                        include(anim)
                    }
                }
                include(effect.trigger(this@Card, triggerInformation, controller, situation))
            }
        }

        later {
            if (!presentation.inTriggerPosition) return@later
            if (zone == zoneAtStart) {
                include(presentation.animateBack(controller, prevPosition))
            } else {
                action { presentation.skipAnimateBack() }
            }
        }
    } }

    fun getRotationDirection(controller: GameController): RevolverRotation =
        activeBehaviours.fold(rotationDirection) { acc, cur -> cur.modifyRotationDirection(this, controller, acc) }

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
        presentation.redrawPixmap(curDamage(controller), curCost(controller))

    override fun dispose() = presentation.dispose()

    override fun toString(): String {
        return logTag
    }

    fun getAllHoverTexts(): List<String> {
        val res = mutableListOf<String>()
        res.add(shortDescription)
        res.addAll(currentHoverTexts.map { it.second })
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
                    val name = onj.get<String>("name")
                    val prototype = CardPrototype(
                        from, name,
                        onj.get<String>("title"),
                        onj.get<Long>("cost").toInt(),
                        onj.get<Long>("baseDamage").toInt(),
                        onj.get<Long?>("deckMaximum")?.toInt() ?: -1,
                        onj.get<OnjArray>("tags").value.map { it.value as String },
                    )
                    prototype.creator = { screen, stamp, presentationProvider ->
                        val type = CardType(from, name, stamp)
                        getCardFrom(onj, screen, type, initializer, presentationProvider, prototype)
                    }
                    prototypes.add(prototype)
                }
            return prototypes
        }

        private fun getCardFrom(
            onj: OnjObject,
            customScreen: IScreen,
            type: CardType,
            initializer: (Card) -> Unit,
            presentationProvider: PresentationProvider,
            prototype: CardPrototype,
        ): Card {
            val stamp = type.stamp?.let { StampFactory.createStamp(it) }
            val card = Card(
                type = type,
                title = onj.get<String>("title"),
                flavourText = onj.get<String>("flavourText"),
                shortDescription = onj.get<String>("description"),
                originalBaseDamage = onj.get<Long>("baseDamage").toInt(),
                originalBaseCost = onj.get<Long>("cost").toInt(),
                rightClickCost = onj.getOr<Long?>("rightClickCost", null)?.toInt(),
                price = prototype.getPriceWithModifications(onj.get<Long>("price").toInt()),
                effects = getEffects(onj),
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
                isDark = onj.getOr<Boolean>("dark", false),
                additionalHoverInfos = onj
                    .getOr<OnjArray?>("additionalHoverInfos", null)
                    ?.value
                    ?.map { it.value as String }
                    ?: listOf(),
                screen = customScreen,
                variableTexture = onj.getOr<VariableTextureSelector?>("variableTexture", null),
                parryNumber = onj.getOr<Long?>("parryNumber", null)?.toInt(),
                stamp = stamp,
                deckMaximum = prototype.deckMaximum,
                presentationProvider = presentationProvider
            )
            applyTraitEffects(card, onj, stamp)
            stamp?.behaviours()?.let { behaviours ->
                behaviours.forEach { card.addBehaviour(it) }
            }
            initializer(card)
            return card
        }

        private fun getEffects(onj: OnjObject): List<Effect> {
            val effects = (onj.getOr<OnjArray?>("effects", null)?.value ?: listOf())
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
                }
            return effects
        }

        private fun applyTraitEffects(card: Card, onj: OnjObject, stamp: Stamp?) {
            onj
                .getOr<OnjArray?>("traitEffects", null)
                ?.value
                ?.map { it.value as String }
                ?.forEach { applyTraitEffect(it, card) }

            for(trait in stamp?.traitEffects() ?: emptyList())
            {
                applyTraitEffect(trait, card)
            }
        }

        private fun applyTraitEffect(effect: String, card: Card): Unit = when (effect) {
            "everlasting" -> card.addBehaviour(BulletBehaviour.Everlasting)
            "undead" -> card.addBehaviour(BulletBehaviour.Undead)
            "replaceable" -> card.addBehaviour(BulletBehaviour.Replaceable)
            "spray" -> card.addBehaviour(BulletBehaviour.Spray)
            "reinforced" -> card.isReinforced = true
            "shotProtected" -> card.addBehaviour(BulletBehaviour.ShotProtected)
            "rotten" -> card.isRotten = true
            "thorns" -> card.addBehaviour(BulletBehaviour.Thorns)
            "punk" -> card.isPunk = true
            "persistence" -> card.isPersistent = true
            "alwaysAtBottom" -> card.stackPosition = StackPosition.BOTTOM
            "alwaysAtTop" -> card.stackPosition = StackPosition.TOP
            "piercing" -> card.isPiercing = true
            "putInHandAfterDestroy" -> card.addBehaviour(BulletBehaviour.PutInHandAfterDestroy)

            else -> throw RuntimeException("unknown trait effect $effect")
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

typealias PresentationProvider = (card: Card, screen: IScreen) -> CardPresentation

interface CardPresentation : Disposable {

    val inTriggerPosition: Boolean
    var isMarked: Boolean

    var isDraggable: Boolean
    var touchable: Touchable

    fun setAlphaZero()
    fun setAlphaOne()
    fun position(): Vector2

    fun destroyAnimation(): Timeline
    fun spawnAnimation(reverse: Boolean = false): Timeline
    fun descendAnimation(): Timeline
    fun resetDescendAnimation()

    fun forceGetActor(): CardActor

    fun animTarget(): () -> Actor = { forceGetActor() }

    fun redrawPixmap(damageValue: Int, costValue: Int)

    fun animateToTriggerPosition(
        controller: GameController,
        isOnShot: Boolean,
        afterlifeOpen: Boolean
    ): Timeline
    fun animateBack(controller: GameController, prevCoordinates: Vector2): Timeline
    fun skipAnimateBack()

    companion object {

        val defaultProvider: PresentationProvider = { card, screen ->
            requireRenderableScreen(screen)
            val actor = CardActor(
                card,
                GraphicsConfig.cardFont(screen, screen),
                GraphicsConfig.cardFontScale(),
                card.isDark,
                screen,
                true
            )
            ActorCardPresentation(actor)
        }
    }
}

class ActorCardPresentation(private val actor: CardActor) : CardPresentation {

    override val inTriggerPosition: Boolean by actor::inTriggerPosition
    override var isMarked: Boolean by actor::isMarked

    override var isDraggable: Boolean by actor::isDraggable

    override var touchable: Touchable
        get() = actor.touchable
        set(value) { actor.touchable = value }

    override fun setAlphaZero() {
        actor.alpha = 0f
    }

    override fun setAlphaOne() {
        actor.alpha = 1f
    }

    override fun destroyAnimation(): Timeline = actor.destroyAnimation()

    override fun spawnAnimation(reverse: Boolean): Timeline = actor.spawnAnimation(reverse)

    override fun descendAnimation(): Timeline = actor.descendAnimation()

    override fun resetDescendAnimation() = actor.resetDescendAnimation()

    override fun forceGetActor(): CardActor = actor

    override fun position(): Vector2 = Vector2(actor.x, actor.y)

    override fun redrawPixmap(damageValue: Int, costValue: Int) = actor.redrawPixmap(damageValue, costValue)

    override fun animateToTriggerPosition(
        controller: GameController,
        isOnShot: Boolean,
        afterlifeOpen: Boolean
    ): Timeline = actor.animateToTriggerPosition(controller, isOnShot, afterlifeOpen)

    override fun animateBack(
        controller: GameController,
        prevCoordinates: Vector2
    ): Timeline = actor.animateBack(controller, prevCoordinates)

    override fun skipAnimateBack() = actor.skipAnimateBack()

    override fun dispose() = actor.dispose()
}

/**
 * the actor representing a card on the screen
 */
class CardActor(
    val card: Card,
    val font: Promise<PixmapFont>,
    val fontScale: Float,
    val isDark: Boolean,
    override val screen: RenderableScreen,
    val enableHoverDetails: Boolean // TODO: fix
) : Widget(), ZIndexActor, InputActor by InputActorImpl(), Selectable<CardActor>, ActorWithDragFeatures,
    OffSettable, Disposable, ResourceBorrower, KotlinStyledActor, DropShadowActor, AnimatedActor {

    override var detailWidget: DetailWidget? = DetailWidget.ComplexBigDetailActor(
        screen,
        effects = DetailDescriptionHandler.allTextEffects,
        text = {
            val list = card.currentHoverTexts.map { it.second }.toMutableList()
            list.add(card.shortDescription)
            if (card.flavourText.isNotBlank()) list.add("\$flavourText$${card.flavourText}\$flavourText$")
            list.add("allowed in deck: ${if (card.deckMaximum == -1) "unlimited" else card.deckMaximum }")
            list
        },
        topText = {
            card.stamp?.let { stamp ->
                $$"$stamp$§§$${stamp.icon}§§  $${stamp.title}$stamp$\n\n\n$${stamp.description}"
            } ?: ""
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

    override var alsoDrawOriginalInDrag: Boolean = false

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

    private var inKeyboardDrag: Boolean = false

    init {
        initInput(this, screen)
        bindDetailToInputState(GameInputs.States.focused)
        keyboardFocusable = KeyboardFocusable.LEAF

        cardTexturePromise = FortyFive.cardTextureManager.cardTextureFor(
            card,
            screen,
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
        observeInputState(
            InputManager.BaseStates.keyboardDrag,
            { inKeyboardDrag = true },
            { inKeyboardDrag = false }
        )
    }

    private fun focusEnter() {
        if (selectionPromise != null) {
            selectionAnimation.start()
            return
        }
        if (
            (!card.inGame || card.inZone(Zone.REVOLVER)) &&
            (dropShadow == null || dropShadow == defaultFocusDropShadow)
        ) {
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
        if (
            (!card.inGame || card.inZone(Zone.REVOLVER)) &&
            (dropShadow == null || dropShadow == defaultFocusDropShadow)
        ) {
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

    override fun drawInDrag(batch: Batch, oX: Float, oY: Float) {
        doDraw(batch, 1f, x, y)
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        updateAnimations()
        if (isDragged && !alsoDrawOriginalInDrag) return
        doDraw(batch, parentAlpha, x, y)
    }

    private fun doDraw(batch: Batch?, parentAlpha: Float, x: Float, y: Float) {
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
        val dragAlpha = if (inKeyboardDrag) 0.66f else 1f
        val c = batch.color.cpy()
        batch.setColor(c.r, c.g, c.b, alpha * parentAlpha * dragAlpha)
        val textureSize = width
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
        cardTexturePromise = FortyFive.cardTextureManager.cardTextureFor(
            card, screen, costValue,
            damageValue,
            card.currentVariablePostfix
        )
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
            Zone.HAND -> controller.cardHand.triggerPositionForCardActor(this@CardActor)
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
        invalidateHierarchy()
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
        val allHoverTexts = card.getAllHoverTexts().toMutableList()
        card.stamp?.description?.let { allHoverTexts.add(it) }

        val texts: MutableList<String> = mutableListOf()
        texts.addAll(DetailDescriptionHandler.extractAllExtraDescriptions(allHoverTexts))
        texts.addAll(card.getAdditionalHoverDescriptions().filter { it.isNotBlank() })
        texts
    }

    companion object {
        const val cardGroup: String = "card-group"
        const val selectableCardGroup: String = "selectable-card-group"
    }
}
