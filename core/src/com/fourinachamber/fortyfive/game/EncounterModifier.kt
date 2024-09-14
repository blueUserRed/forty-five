package com.fourinachamber.fortyfive.game

import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.card.Trigger
import com.fourinachamber.fortyfive.game.card.TriggerInformation
import com.fourinachamber.fortyfive.game.controller.GameController
import com.fourinachamber.fortyfive.game.controller.OldGameController
import com.fourinachamber.fortyfive.game.controller.RevolverRotation
import com.fourinachamber.fortyfive.utils.TemplateString
import com.fourinachamber.fortyfive.utils.Timeline
import kotlin.math.max
import kotlin.math.roundToInt

sealed class EncounterModifier {

    private val _types: MutableList<Type> = mutableListOf()

    val types: List<Type>
        get() = _types

    val isRtBased: Boolean
        get() = Type.RT_BASED in _types

    init {
        @Suppress("LeakingThis")
        _types.addAll(getModifierTypes())
    }

    object Rain : EncounterModifier() {
        override fun shouldApplyStatusEffects(): Boolean = false
    }

    object Frost : EncounterModifier() {

        override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = RevolverRotation.None

        override fun disableEverlasting(): Boolean = true
    }

    object BewitchedMist : EncounterModifier() {

        override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = when (rotation) {
            is RevolverRotation.Right -> RevolverRotation.Left(rotation.amount)
            is RevolverRotation.Left -> RevolverRotation.Right(rotation.amount)
            else -> rotation
        }
    }

    object Lookalike : EncounterModifier() {

        override fun executeAfterBulletWasPlacedInRevolver(
            card: Card,
            controller: GameController
        ): Timeline = controller.tryToPutCardsInHandTimeline(card.name)
    }

    object Moist : EncounterModifier() {

        override fun executeAfterBulletWasPlacedInRevolver(
            card: Card,
            controller: GameController
        ): Timeline = Timeline.timeline {
            val rotationTransformer = { old: Card.CardModifier, triggerInformation: TriggerInformation -> Card.CardModifier(
                damage = old.damage - (triggerInformation.multiplier ?: 1),
                source = old.source,
                validityChecker = old.validityChecker,
                transformers = old.transformers
            )}
            val modifier = Card.CardModifier(
                damage = 0,
                source = "moist modifier",
                validityChecker = { card.inGame },
                transformers = mapOf(
                    Trigger.ON_REVOLVER_ROTATION to rotationTransformer
                )
            )
            card.addModifier(modifier)
        }


    }

    class SteelNerves : EncounterModifier() {

        private var baseTime: Long = -1

        override fun getModifierTypes(): List<Type> = listOf(Type.RT_BASED)

        override fun onStart(controller: GameController) {
            controller.screen.enterState("steel_nerves")
        }

        override fun update(controller: GameController) {
            if (baseTime == -1L) return
            if (controller.playerLost || OldGameController.showWinScreen in controller.screen.screenState) {
                controller.screen.leaveState("steel_nerves")
                baseTime = -1
                return
            }
            val now = TimeUtils.millis()
            val diff = max(10 - ((now - baseTime).toDouble() / 1000.0).roundToInt(), 0)
            TemplateString.updateGlobalParam("game.steelNerves.remainingTime", diff)
            if (now - baseTime < 10_000) return
            if (controller.isUIFrozen) return
            baseTime = -1
            controller.shoot()
        }

        override fun executeAfterRevolverWasShot(card: Card?, controller: GameController): Timeline = Timeline.timeline {
            action {
                baseTime = TimeUtils.millis()
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
            }
        }
    }

    object DrawOneMoreCard : EncounterModifier() {

        override fun additionalCardsToDrawInSpecialDraw(): Int = 1
        override fun additionalCardsToDrawInNormalDraw(): Int = 1
    }

    object Draft : EncounterModifier() {

        override fun intermediateScreen(): String = "draftScreen"
    }

    object AnOfferYouCantRefuse : EncounterModifier() {

        override fun initBullet(card: Card) {
            card.addModifier(
                Card.CardModifier(
                source = "An offer you cant refuse",
                costChange = -1,
            ))
        }

        override fun canShootRevolver(controller: GameController): Boolean {
            return controller.curReserves >= 1
        }

        override fun executeAfterRevolverWasShot(card: Card?, controller: GameController): Timeline = Timeline.timeline {
            controller.cost(1, controller.shootButton)
        }
    }

    object BulletSkipping : EncounterModifier() {

        override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation =
            rotation.withAmount(rotation.amount * 2)
    }

    object Sacrifice : EncounterModifier() {

        override fun executeOnPlayerTurnStart(controller: GameController): Timeline = Timeline.timeline {
            includeLater(
                { Timeline.timeline {
                    include(controller.cardSelectionPopupTimeline("Select bullet to destroy"))
                    includeLater(
                        { controller.destroyCardTimeline(get<Card>("selectedCard")) },
                        { true }
                    )
                } },
                { controller.cardsInRevolver().isNotEmpty() }
            )
        }
    }

    object SorryNotSorry : EncounterModifier() {

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

    object Confused : EncounterModifier() {

        override fun modifyRevolverRotation(rotation: RevolverRotation): RevolverRotation = RevolverRotation.None

        override fun executeAfterBulletWasPlacedInRevolver(
            card: Card,
            controller: GameController
        ): Timeline = Timeline.timeline {
            include(controller.rotateRevolverTimeline(card.rotationDirection, ignoreEncounterModifiers = true))
        }

        override fun disableEverlasting(): Boolean = true
    }

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
    }

    enum class Type {
        RT_BASED
    }
}
