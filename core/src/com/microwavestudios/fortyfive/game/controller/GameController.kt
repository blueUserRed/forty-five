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
import com.microwavestudios.fortyfive.screen.CustomScreen
import com.microwavestudios.fortyfive.utils.EventPipeline
import com.microwavestudios.fortyfive.utils.Timeline

/**
 * keeps track of the encounter state and contains functions to construct timelines for e.g. effects
 *
 * **Important:** Only these functions should be used to the change the state of the encounter. Manually
 * adding and removing cards from various actors *will* cause issues, because the GameController
 * also needs to check if modifier influence a given action, if triggers need to be activated, if
 * status effects need to be executed, if events need to be fired, etc.
 *
 * For the implementation see [GameControllerImpl]
 */
interface GameController {

    val screen: CustomScreen
    val encounterContext: EncounterContext
    val gameRenderPipeline: GameRenderPipeline

    val playerLost: Boolean

    /**
     * true if the player has won, i.e. all enemies are defeated. The game will continue on until
     * the "holster" button is pressed
     */
    val hasWon: Boolean
    val curReserves: Int

    /**
     * while animations are running on the main game timeline, the ui is frozen to prevent the player
     * e.g. shooting again and causing problems
     */
    val isUIFrozen: Boolean

    /**
     * total amount of times the revolver has rotated. Rotations by multiple slots count that many
     * times
     */
    val revolverRotationCounter: Int

    /**
     * total amount of turns the game has lasted for. A turn ends when the "holster" button is pressed
     */
    val turnCounter: Int
    val playerStatusEffects: List<StatusEffect>

    /**
     * some modifiers like [EncounterModifier.Frost] disable the everlasting trait effect
     */
    val isEverlastingDisabled: Boolean
    val cardsInHand: List<Card>
    val encounterModifiers: List<EncounterModifier>

    val curPlayerLives: Int

    /**
     * all cards that where ever created in this encounter
     */
    val allCards: List<Card>

    val activeEnemies: List<Enemy>
    val allEnemies: List<Enemy>

    val shootButton: Actor
    val revolver: Revolver
    val cardStack: CardStack
    val afterlife: Afterlife

    val gameEvents: EventPipeline

    /**
     * destroys a card in the *revolver* and puts it in the afterlife
     */
    fun destroyCardTimeline(card: Card, sourceCard: Card? = null): Timeline

    /**
     * puts [amount] of cards with name [cardName] in the hand of the player
     */
    fun tryToPutCardsInHandTimeline(cardName: String, amount: Int = 1, sourceCard: Card? = null): Timeline

    /**
     * puts [amount] of cards with name [cardName] in the stack. If [onTop] is false, the cards will instead
     * be put on the bottom.
     */
    fun putCardsInStackTimeline(cardName: String, amount: Int, sourceCard: Card? = null, onTop: Boolean): Timeline

    /**
     * puts [card] from the revolver back into the hand
     */
    fun bounceBulletTimeline(card: Card): Timeline

    /**
     * takes [card] from the hand and puts it at a random position in the stack
     */
    fun shuffleCardFromHandIntoStackTimeline(card: Card, sourceCard: Card? = null): Timeline

    /**
     * rotates the revolver. If [ignoreEncounterModifiers] is true the controller will ignore modifiers
     * that try to change the revolver rotation. Should be left as false most of the time
     */
    fun rotateRevolverTimeline(
        rotation: RevolverRotation,
        ignoreEncounterModifiers: Boolean = false,
        sourceCard: Card? = null
    ): Timeline

    /**
     * draw [amount] cards from the stack. If the stack is empty, default bullets will be created.
     * @param isSpecial set to true when this was triggered e.g. by a bullet effect instead of the normal card draw at
     * the beginning of every turn
     * @param fromBottom draw the cards from the bottom of the stack instead of the top
     */
    fun drawCardsTimeline(amount: Int, isSpecial: Boolean = true, fromBottom: Boolean = false, sourceCard: Card? = null): Timeline

    /**
     * apply [statusEffect] to the enemy if status effects aren't disabled
     */
    fun tryApplyStatusEffectToEnemyTimeline(statusEffect: StatusEffect, enemy: Enemy, source: Card? = null): Timeline

    /**
     * damages the player. If [isPiercing] is true, shield will have no effect
     */
    fun damagePlayerTimeline(damage: Int, triggeredByStatusEffect: Boolean = false, isPiercing: Boolean = false): Timeline

    fun playerDeathTimeline(): Timeline

    /**
     * apply [effect] to the player if status effects aren't disabled
     */
    fun tryApplyStatusEffectToPlayerTimeline(effect: StatusEffect, source: Card? = null): Timeline

    fun removeAllPlayerStatusEffectsTimeline(): Timeline

    /**
     * puts a specific [card] from the stack into the hand
     */
    fun putCardFromStackInHandTimeline(card: Card, source: Card? = null): Timeline

    fun switchSlotOfBulletInRevolverTimeline(card: Card, newSlot: Int): Timeline

    /**
     * destroys a card in the hand and puts it in the afterlife
     */
    fun destroyCardInHandTimeline(card: Card, sourceCard: Card? = null): Timeline

    /**
     * [enemy] deals [damage] to the player. If [isPiercing] is true, shield will have no effect
     */
    fun enemyAttackTimeline(damage: Int, enemy: Enemy, isPiercing: Boolean = false): Timeline

    /**
     * take a bullet from the revolver and put it under the stack
     */
    fun putBulletFromRevolverUnderTheStackTimeline(card: Card): Timeline

    /**
     * puts [amount] of cards with name [bulletName] directly into the afterlife
     */
    fun createBulletsInAfterlifeTimeline(bulletName: String, amount: Int, sourceCard: Card? = null): Timeline

    /**
     * descends the front-most bullet in the afterlife, dealing damage and putting the card in [GameControllerImpl.Zone.LIMBO]
     */
    fun descendBulletTimeline(): Timeline

    /**
     * lets the player pick a revolver slot and puts the front-most bullet in the afterlife back into the revolver
     */
    fun resurrectTimeline(intoSlot: Int): Timeline

    fun shoot()

    /**
     * gives the player more reserves. [source] is used for animations
     */
    fun gainReserves(amount: Int, source: Actor? = null)

    /**
     * removes [cost] reserves, or returns false if the player didn't have enough reserves.
     * [animTarget] is used for animations
     */
    fun tryPay(cost: Int, animTarget: Actor? = null): Boolean

    /**
     * adds an encounter modifier and removes it again one [validityChecker] is not true anymore. Encounter
     * Modifier added like this are not displayed in the UI
     */
    fun addTemporaryEncounterModifier(modifier: EncounterModifier, validityChecker: (GameController) -> Boolean)

    fun addEncounterModifier(modifier: EncounterModifier)

    fun initEnemyArea(enemies: List<Enemy>)

    fun playGameAnimation(anim: GameAnimation)

    fun loadBulletFromHandInRevolver(card: Card, slot: Int)

    /**
     * the main timeline handles all important game logic and forces them to execute in sequence,
     * avoiding weird race conditions.
     */
    fun appendMainTimeline(timeline: Timeline)

    /**
     * adds a timeline that runs in parallel to the main timeline. Should only be used for
     * unimportant things like animations that don't influence the game state.
     */
    fun dispatchAnimTimeline(timeline: Timeline)

    fun cardsInRevolver(): List<Card>

    fun cardsInRevolverIndexed(): List<Pair<Int, Card>>

    fun targetedEnemy(): Enemy

    fun slotOfCard(card: Card): Int?

    fun titleOfCard(cardName: String): String

}
