package com.fourinachamber.fortyfive.game.controller

import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fortyfive.game.EncounterModifier
import com.fourinachamber.fortyfive.game.GameAnimation
import com.fourinachamber.fortyfive.game.GameDirector
import com.fourinachamber.fortyfive.game.StatusEffect
import com.fourinachamber.fortyfive.game.card.Card
import com.fourinachamber.fortyfive.game.enemy.Enemy
import com.fourinachamber.fortyfive.rendering.GameRenderPipeline
import com.fourinachamber.fortyfive.screen.gameWidgets.Revolver
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.Timeline

interface GameController {

    val screen: OnjScreen
    val encounterContext: EncounterContext
    val gameRenderPipeline: GameRenderPipeline

    val playerLost: Boolean
    val curReserves: Int
    val isUIFrozen: Boolean
    val cardStack: List<Card>
    val revolverRotationCounter: Int
    val turnCounter: Int
    val playerStatusEffects: List<StatusEffect>
    val isEverlastingDisabled: Boolean
    val cardsInHand: List<Card>
    val encounterModifiers: List<EncounterModifier>
    val curPlayerLives: Int

    val activeEnemies: List<Enemy>
    val allEnemies: List<Enemy>

    val shootButton: Actor
    val revolver: Revolver

    fun cardSelectionPopupTimeline(text: String, exclude: Card? = null): Timeline

    fun destroyCardTimeline(card: Card): Timeline

    fun tryToPutCardsInHandTimeline(cardName: String, amount: Int = 1, sourceCard: Card? = null): Timeline

    fun bounceBulletTimeline(card: Card): Timeline

    fun rotateRevolverTimeline(
        rotation: RevolverRotation,
        ignoreEncounterModifiers: Boolean = false,
        sourceCard: Card? = null
    ): Timeline

    fun drawCardsTimeline(amount: Int, isSpecial: Boolean = true, fromBottom: Boolean = false, sourceCard: Card? = null): Timeline

    fun tryApplyStatusEffectToEnemyTimeline(statusEffect: StatusEffect, enemy: Enemy): Timeline

    fun damagePlayerTimeline(damage: Int, triggeredByStatusEffect: Boolean = false, isPiercing: Boolean = false): Timeline

    fun playerDeathTimeline(): Timeline

    fun tryApplyStatusEffectToPlayerTimeline(effect: StatusEffect): Timeline

    fun putCardFromStackInHandTimeline(card: Card, source: Card? = null): Timeline

    fun destroyCardInHandTimeline(card: Card): Timeline

    fun enemyAttackTimeline(damage: Int, isPiercing: Boolean = false): Timeline

    fun putBulletFromRevolverUnderTheDeckTimeline(card: Card): Timeline


    fun shoot()

    fun gainReserves(amount: Int, source: Actor? = null)

    fun tryPay(cost: Int, animTarget: Actor? = null): Boolean

    fun addTemporaryEncounterModifier(modifier: EncounterModifier, validityChecker: (GameController) -> Boolean)

    fun addEncounterModifier(modifier: EncounterModifier)

    fun addTutorialText(textParts: List<GameDirector.GameTutorialTextPart>)

    fun initEnemyArea(enemies: List<Enemy>)

    fun enemyDefeated(enemy: Enemy)

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
