package com.microwavestudios.fortyfive.game.controller

import com.badlogic.gdx.scenes.scene2d.Actor
import com.microwavestudios.fortyfive.game.EncounterModifier
import com.microwavestudios.fortyfive.game.GameAnimation
import com.microwavestudios.fortyfive.game.StatusEffect
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.rendering.GameRenderPipeline
import com.microwavestudios.fortyfive.game.widgets.Afterlife
import com.microwavestudios.fortyfive.game.widgets.Revolver
import com.microwavestudios.fortyfive.screen.OnjScreen
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.Timeline

interface GameController {

    val screen: OnjScreen
    val encounterContext: EncounterContext
    val gameRenderPipeline: GameRenderPipeline

    val playerLost: Boolean
    val hasWon: Boolean
    val curReserves: Int
    val isUIFrozen: Boolean
    val revolverRotationCounter: Int
    val turnCounter: Int
    val playerStatusEffects: List<StatusEffect>
    val isEverlastingDisabled: Boolean
    val cardsInHand: List<Card>
    val encounterModifiers: List<EncounterModifier>
    val curPlayerLives: Int
    val allCards: List<Card>

    val activeEnemies: List<Enemy>
    val allEnemies: List<Enemy>

    val shootButton: Actor
    val revolver: Revolver
    val cardStack: CardStack
    val afterlife: Afterlife

    val gameEvents: EventPipeline

    fun cardSelectionPopupTimeline(text: String, exclude: Card? = null): Timeline

    fun destroyCardTimeline(card: Card, sourceCard: Card? = null): Timeline

    fun tryToPutCardsInHandTimeline(cardName: String, amount: Int = 1, sourceCard: Card? = null): Timeline

    fun putCardsInStackTimeline(cardName: String, amount: Int, sourceCard: Card? = null): Timeline

    fun bounceBulletTimeline(card: Card): Timeline

    fun rotateRevolverTimeline(
        rotation: RevolverRotation,
        ignoreEncounterModifiers: Boolean = false,
        sourceCard: Card? = null
    ): Timeline

    fun drawCardsTimeline(amount: Int, isSpecial: Boolean = true, fromBottom: Boolean = false, sourceCard: Card? = null): Timeline

    fun tryApplyStatusEffectToEnemyTimeline(statusEffect: StatusEffect, enemy: Enemy, source: Card? = null): Timeline

    fun damagePlayerTimeline(damage: Int, triggeredByStatusEffect: Boolean = false, isPiercing: Boolean = false): Timeline

    fun playerDeathTimeline(): Timeline

    fun tryApplyStatusEffectToPlayerTimeline(effect: StatusEffect, source: Card? = null): Timeline

    fun removeAllPlayerStatusEffectsTimeline(): Timeline

    fun putCardFromStackInHandTimeline(card: Card, source: Card? = null): Timeline

    fun switchSlotOfBulletInRevolverTimeline(card: Card, newSlot: Int): Timeline

    fun destroyCardInHandTimeline(card: Card): Timeline

    fun enemyAttackTimeline(damage: Int, isPiercing: Boolean = false): Timeline

    fun putBulletFromRevolverUnderTheDeckTimeline(card: Card): Timeline


    fun shoot()

    fun gainReserves(amount: Int, source: Actor? = null)

    fun tryPay(cost: Int, animTarget: Actor? = null): Boolean

    fun addTemporaryEncounterModifier(modifier: EncounterModifier, validityChecker: (GameController) -> Boolean)

    fun addEncounterModifier(modifier: EncounterModifier)

    fun initEnemyArea(enemies: List<Enemy>)

    fun playGameAnimation(anim: GameAnimation)

    fun loadBulletFromHandInRevolver(card: Card, slot: Int)

    fun appendMainTimeline(timeline: Timeline)

    fun dispatchAnimTimeline(timeline: Timeline)

    fun cardsInRevolver(): List<Card>

    fun cardsInRevolverIndexed(): List<Pair<Int, Card>>

    fun targetedEnemy(): Enemy

    fun slotOfCard(card: Card): Int?

    fun titleOfCard(cardName: String): String

}
