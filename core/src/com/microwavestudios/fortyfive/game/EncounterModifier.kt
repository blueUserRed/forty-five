package com.microwavestudios.fortyfive.game

import com.badlogic.gdx.utils.TimeUtils
import com.microwavestudios.fortyfive.config.ConfigFileManager
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardCostModifier
import com.microwavestudios.fortyfive.game.card.CardDamageModifier
import com.microwavestudios.fortyfive.game.card.CardModifierData
import com.microwavestudios.fortyfive.game.card.GameSituation
import com.microwavestudios.fortyfive.game.card.Trigger
import com.microwavestudios.fortyfive.game.card.TriggerInformation
import com.microwavestudios.fortyfive.game.controller.GameController
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl.Zone
import com.microwavestudios.fortyfive.game.controller.RevolverRotation
import com.microwavestudios.fortyfive.resources.ResourceHandle
import com.microwavestudios.fortyfive.utils.Timeline
import onj.value.OnjArray
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.math.max
import kotlin.math.roundToInt

sealed class EncounterModifier {

    private val _types: MutableList<Type> = mutableListOf()

    val types: List<Type>
        get() = _types

    val isRtBased: Boolean
        get() = Type.RT_BASED in _types

    abstract val difficultyChange: Float

    init {
        @Suppress("LeakingThis")
        _types.addAll(getModifierTypes())
    }

    data object Rain : EncounterModifier() {
        override val displayName: String = "Rain"
        override val iconHandle: ResourceHandle = "encounter_modifier_rain"
        override val description: String = "Status effects don't work."
        override val difficultyChange: Float = 0.2f

        override fun shouldApplyStatusEffects(): Boolean = false
    }

    data object Frost : EncounterModifier() {
        override val displayName: String = "Frost"
        override val iconHandle: ResourceHandle = "encounter_modifier_frost"
        override val description: String = "The revolver doesn't turn. Everlasting doesn't work."
        override val difficultyChange: Float = 1f

        override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = RevolverRotation.None

        override fun disableEverlasting(): Boolean = true
    }

    data object BewitchedMist : EncounterModifier() {
        override val displayName: String = "Bewitched Mist"
        override val iconHandle: ResourceHandle = "encounter_modifier_bewitched_mist"
        override val description: String = "Revolver rotations are inverted."
        override val difficultyChange: Float = 0.1f

        override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = when (rotation) {
            is RevolverRotation.Right -> RevolverRotation.Left(rotation.amount)
            is RevolverRotation.Left -> RevolverRotation.Right(rotation.amount)
            else -> rotation
        }
    }

    data object Lookalike : EncounterModifier() {
        override val displayName: String = "Lookalike"
        override val iconHandle: ResourceHandle = "encounter_modifier_lookalike"
        override val description: String = "Whenever you place a bullet in the revolver, you get a copy of it in your hand."
        override val difficultyChange: Float = 0f // this modifier is so broken that correcting for it would be useless anyway

        override fun executeAfterBulletWasPlacedInRevolver(
            card: Card,
            controller: GameController
        ): Timeline = controller.tryToPutCardsInHandTimeline(card.type)
    }

    data object Moist : EncounterModifier() {
        override val displayName: String = "Moist"
        override val iconHandle: ResourceHandle = "encounter_modifier_moist"
        override val description: String = "Every bullet in the revolver loses one damage every time it turns."
        override val difficultyChange: Float = 0.5f

        override fun executeAfterBulletWasPlacedInRevolver(
            card: Card,
            controller: GameController
        ): Timeline = Timeline.timeline {
            val rotationTransformer = { old: CardDamageModifier, triggerInformation: TriggerInformation -> CardDamageModifier(
                damage = old.damage - (triggerInformation.multiplier ?: 1),
                data = CardModifierData(
                    source = old.data.source,
                    validityChecker = old.data.validityChecker,
                ),
                transformers = old.transformers
            )}
            val modifier = CardDamageModifier(
                damage = 0,
                data = CardModifierData(
                    source = "moist modifier",
                    validityChecker = { _, _, _ -> card.inZone(Zone.REVOLVER) },
                ),
                transformers = listOf(
                    Trigger.triggerForSituation<GameSituation.RevolverRotation>() to rotationTransformer
                )
            )
            card.addDamageModifier(modifier, controller)
        }


    }

    class SteelNerves : EncounterModifier() {
        override val displayName: String = "Steel Nerves"
        override val iconHandle: ResourceHandle = "encounter_modifier_steel_nerves"
        override val description: String = "The revolver shoots automatically every ten seconds."
        override val difficultyChange: Float = 0.5f

        private var baseTime: Long = -1
        private var lastDigit: Int = -1

        override fun getModifierTypes(): List<Type> = listOf(Type.RT_BASED)

        override fun onStart(controller: GameController) {
            controller.gameEvents.fire(GameControllerImpl.Events.SteelNervesCountdown(10))
        }

        override fun update(controller: GameController) {
            if (baseTime == -1L) return

            if (controller.playerLost || controller.hasWon) {
                baseTime = -1L
            }

            val now = TimeUtils.millis()
            val diff = max(10 - ((now - baseTime).toDouble() / 1000.0).roundToInt(), 0)
            if (diff != lastDigit) {
                lastDigit = diff
                controller.gameEvents.fire(GameControllerImpl.Events.SteelNervesCountdown(diff))
            }
            if (now - baseTime < 10_000) return
            if (controller.isUIFrozen) return
            baseTime = -1
            controller.shoot()
        }

        override fun executeAfterRevolverWasShot(card: Card?, controller: GameController): Timeline = Timeline.timeline {
            action {
                baseTime = TimeUtils.millis()
                lastDigit = 10
            }
        }

        override fun executeOnEndTurn(): Timeline = Timeline.timeline {
            action {
                baseTime = -1
            }
        }

        override fun executeOnPlayerTurnStart(controller: GameController): Timeline = Timeline.timeline {
            action {
                baseTime = TimeUtils.millis()
                lastDigit = 10
            }
        }
    }

    data object DrawOneMoreCard : EncounterModifier() {
        override val displayName: String = "DrawOneMoreCard"
        override val iconHandle: ResourceHandle = "encounter_modifier_rain"
        override val description: String = ""
        override val difficultyChange: Float = 0f

        override fun additionalCardsToDrawInSpecialDraw(): Int = 1
        override fun additionalCardsToDrawInNormalDraw(): Int = 1
    }

    data object Draft : EncounterModifier() {
        override val displayName: String = "Draft"
        override val iconHandle: ResourceHandle = "encounter_modifier_rain"
        override val description: String = "You draft your deck from random cards before the encounter."
        override val difficultyChange: Float = 1f

        override fun intermediateScreen(): String = "draftScreen"
    }

    data object AnOfferYouCantRefuse : EncounterModifier() {
        override val displayName: String = "An Offer you can't refuse"
        override val iconHandle: ResourceHandle = "encounter_modifier_rain"
        override val description: String = "All bullets cost 1 less, but shooting the revolver costs 1 reserve."
        override val difficultyChange: Float = -0.2f

        override fun initBullet(card: Card) {
            card.addCostModifier(
                CardCostModifier(
                    data = CardModifierData(
                        source = "An offer you cant refuse",
                    ),
                    costChange = -1,
                )
            )
        }

        override fun canShootRevolver(controller: GameController): Boolean {
            return controller.curReserves >= 1
        }

        override fun executeAfterRevolverWasShot(card: Card?, controller: GameController): Timeline = Timeline.timeline {
            controller.tryPay(1, controller.shootButton)
        }
    }

    data object BulletSkipping : EncounterModifier() {
        override val displayName: String = "Bullet Skipping"
        override val iconHandle: ResourceHandle = "encounter_modifier_rain"
        override val description: String = "When a bullet shoots, it turns twice, skipping the slot in between."
        override val difficultyChange: Float = -0.2f

        override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation =
            rotation.withAmount(rotation.amount * 2)
    }

    data object Sacrifice : EncounterModifier() {
        override val displayName: String = "Sacrifice"
        override val iconHandle: ResourceHandle = "encounter_modifier_rain"
        override val description: String = "At the beginning of every turn, destroy target bullet."
        override val difficultyChange: Float = 0.65f

        override fun executeOnPlayerTurnStart(controller: GameController): Timeline = Timeline.timeline {
            later {
                val selector = CardInRevolverSelector(controller, "Select bullet to destroy")
                val promise = selector.startSelect()
                waitForPromise(promise)
                later {
                    val result = promise.getOrNull()
                    if (result != null) include(controller.destroyCardTimeline(result))
                }
            }
        }
    }

    data object SorryNotSorry : EncounterModifier() {
        override val displayName: String = "Sorry not Sorry"
        override val iconHandle: ResourceHandle = "encounter_modifier_rain"
        override val description: String = "At the beginning of every turn, return a random Bullet back to your hand."
        override val difficultyChange: Float = 0.0f

        override fun executeOnPlayerTurnStart(controller: GameController): Timeline = Timeline.timeline {
            var card: Card? = null
            action {
                card = controller.cardsInRevolver().randomOrNull()
            }
            includeLater(
                { controller.bounceBulletTimeline(card!!) },
                { card != null }
            )
        }

    }

    data object Confused : EncounterModifier() {
        override val displayName: String = "Confused"
        override val iconHandle: ResourceHandle = "encounter_modifier_rain"
        override val description: String = "The revolver rotates when a card is placed down, not when it is shot. Everlasting doesn't work."
        override val difficultyChange: Float = 0.2f

        override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = RevolverRotation.None

        override fun executeAfterBulletWasPlacedInRevolver(
            card: Card,
            controller: GameController
        ): Timeline = Timeline.timeline {
            includeLater({
                controller.rotateRevolverTimeline(
                    card.getRotationDirection(controller),
                    ignoreEncounterModifiers = true
                )
            })
        }

        override fun disableEverlasting(): Boolean = true
    }

    abstract val displayName: String
    abstract val iconHandle: ResourceHandle
    abstract val description: String

    open fun getModifierTypes(): List<Type> = listOf()

    open fun update(controller: GameController) {}

    open fun onStart(controller: GameController) {}

    open fun executeOnEndTurn(): Timeline? = null

    open fun executeOnPlayerTurnStart(controller: GameController): Timeline? = null

    open fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = rotation

    open fun shouldApplyStatusEffects(): Boolean = true

    open fun disableEverlasting(): Boolean = false

    open fun executeAfterBulletWasPlacedInRevolver(card: Card, controller: GameController): Timeline? = null

    open fun executeAfterRevolverWasShot(card: Card?, controller: GameController): Timeline? = null

    open fun executeAfterRevolverRotated(rotation: RevolverRotation, controller: GameController): Timeline? = null

    open fun cardsInSpecialDrawMultiplier(): Float = 1f

    open fun cardsInNormalDrawMultiplier(): Float = 1f

    open fun additionalCardsToDrawInSpecialDraw(): Int = 0

    open fun additionalCardsToDrawInNormalDraw(): Int = 0

    open fun intermediateScreen(): String? = null

    open fun initBullet(card: Card) {}

    open fun canShootRevolver(controller: GameController): Boolean = true

    companion object {

        fun getFromName(name: String) = when (name.lowercase()) {
            "rain" -> Rain
            "frost" -> Frost
            "bewitchedmist" -> BewitchedMist
            "steelnerves" -> SteelNerves()
            "lookalike" -> Lookalike
            "moist" -> Moist
            "drawonemorecard" -> DrawOneMoreCard
            "draft" -> Draft
            "anofferyoucantrefuse" -> AnOfferYouCantRefuse
            "bulletskipping" -> BulletSkipping
            "sacrifice" -> Sacrifice
            "sorrynotsorry" -> SorryNotSorry
            "confused" -> Confused
            else -> throw RuntimeException("Unknown Encounter Modifier: $name")
        }

        val blacklist: List<List<String>> by lazy {
            val config = ConfigFileManager.getConfigFile("runGeneratorConfig")
            config
                .get<OnjArray>("encounterModifierBlacklist")
                .value
                .map { pool ->
                    pool as OnjArray
                    pool.value.map { (it.value as String).lowercase() }
                }
        }

        fun isValid(modifiersList: List<String>): Boolean {
            val modifiers = modifiersList.map { it.lowercase() }
            modifiers.forEach { modifier ->
                blacklist.forEach { list ->
                    if (modifier !in list) return@forEach
                    list.forEach { toCheck ->
                        if (toCheck == modifier) return@forEach
                        if (toCheck in modifiers) return false
                    }
                }
            }
            return true
        }

        fun isBlacklisted(m1: String, m2: String): Boolean {
            blacklist.forEach { pool ->
                if (m1.lowercase() in pool && m2.lowercase() in pool) return true
            }
            return false
        }
    }

    enum class Type {
        RT_BASED
    }
}
