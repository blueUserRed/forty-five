package com.microwavestudios.fortyfive.testing

import com.microwavestudios.fortyfive.FortyFive
import com.microwavestudios.fortyfive.game.StatusEffect
import com.microwavestudios.fortyfive.game.card.Card
import com.microwavestudios.fortyfive.game.card.CardType
import com.microwavestudios.fortyfive.game.controller.EncounterContext
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl
import com.microwavestudios.fortyfive.game.controller.GameControllerImpl.Events
import com.microwavestudios.fortyfive.game.enemy.Enemy
import com.microwavestudios.fortyfive.game.widgets.CardHand
import com.microwavestudios.fortyfive.game.widgets.IRevolverSlot
import com.microwavestudios.fortyfive.profile.Profile
import com.microwavestudios.fortyfive.run.Encounter
import com.microwavestudios.fortyfive.testing.mockcomponents.MockAfterlife
import com.microwavestudios.fortyfive.testing.mockcomponents.MockCardHand
import com.microwavestudios.fortyfive.testing.mockcomponents.MockCardPresentation
import com.microwavestudios.fortyfive.testing.mockcomponents.MockRevolver
import com.microwavestudios.fortyfive.testing.mockcomponents.MockScreen
import com.microwavestudios.fortyfive.testing.mockcomponents.MockWarningParent
import com.microwavestudios.fortyfive.testing.mockservices.MockProfile
import com.microwavestudios.fortyfive.testing.mockservices.MockProfileManager
import com.microwavestudios.fortyfive.utils.ANSI
import com.microwavestudios.fortyfive.utils.FortyFiveLogger
import com.microwavestudios.fortyfive.utils.Timeline
import com.microwavestudios.fortyfive.utils.Utils
import com.microwavestudios.fortyfive.utils.findInstance
import java.util.Stack

@Suppress("NOTHING_TO_INLINE")
abstract class GameControllerTest {

    abstract val name: String

    protected val screen = MockScreen()
    protected val warningParent = MockWarningParent()
    protected val afterlife = MockAfterlife()
    protected val revolver = MockRevolver()
    protected val cardHand = MockCardHand(screen.events)
    protected lateinit var controller: GameControllerImpl
        private set

    val events = screen.events

    private val eventListeners: MutableList<(event: Any) -> Unit> = mutableListOf()
    private val titleStack: Stack<String> = Stack()

    fun run(): Boolean {
        setup()
        val timeline = testTimeline()
        try {
            timeline.startTimeline()
            while (!timeline.isFinished) {
                timeline.updateTimeline()
                controller.update()
            }
        } catch (e: TestException) {
            FortyFive.logger.severe(name, "Test $name failed:")
            FortyFive.logger.dump(
                FortyFiveLogger.LogLevel.SEVERE,
                e.message!!
            )
            return false
        } catch (e: Exception) {
            FortyFive.logger.severe(name, "Test $name threw Exception:")
            FortyFive.logger.stackTrace(e)
            return false
        }
        return true
    }

    protected abstract fun testTimeline(): Timeline

    private fun setup() {
        val encounter = Encounter(
            enemiesGroups = listOf(),
            encounterModifierNames = setOf(),
            forceCards = deck(),
            forceConcreteEnemies = enemies(),
            shuffleCards = false,
            unadjustedMajorDifficulty = 1,
            majorDifficulty = 1,
            minorDifficulty = 1f,
            difficultyScalingInfo = 0f,
            special = false,
            isHard = false
        )
        val context = object : EncounterContext {
            override val encounter: Encounter = encounter
            override val isExtraction: Boolean = false
            override fun completed() { }
        }
        val profile = MockProfile(
            name = "MockProfile",
            data = Profile.ProfileData(
                cardCollection = mutableListOf(),
                collectionDecks = mutableListOf(),
                completedSpecialRuns = mutableListOf(),
                currentDeckId = 100,
                playerMoney = 0,
                currentMap = "",
                currentNode = 0,
                lastNode = null,
                wonRuns = 0,
                runBoards = mutableMapOf()
            ),
            healthInRun = 100,
            maxHealthInRun = 100,
            currentRunDeck = null,
            talismans = listOf()
        )
        (FortyFive.profileManager as MockProfileManager).currentProfile = profile
        val controller = GameControllerImpl(
            screen,
            screen.events,
            seed(),
            warningParent,
            afterlife,
            MockCardPresentation.mockProvider
        )
        this.controller = controller
        controller.injectManual("revolver", revolver)
        controller.injectManual("cardHand", cardHand)
        controller.init(context)
        screen.addScreenController(controller)

        bindDefaultEventHandler()
        prepare()
    }

    private fun bindDefaultEventHandler() {
        events.watchFor<Any> { event ->
            eventListeners.forEach { it(event) }
        }
        events.watchFor<Events.PlayBannerAnimation> { it.timeline = Timeline.emptyTimeline }
        events.watchFor<Events.PlayPlayerDamagedEffects> { it.animationTimeline = Timeline.emptyTimeline }
    }

    protected open fun prepare() {}

    protected abstract fun seed(): Long
    protected abstract fun deck(): List<CardType>
    protected abstract fun enemies(): List<String>

    ///////////////////////////////
    // helpers
    ///////////////////////////////

    protected fun cardInHand(name: String): Card = cardHand.allCards().find { it.name == name }!!

    protected fun cardInAfterlife(slot: Int): Card? = afterlife.cards.getOrNull(slot)

    protected fun revolverSlot(num: Int): IRevolverSlot = revolver.slots[Utils.convertSlotRepresentation(num) - 1]
    
    protected fun curPlayerHealth(): Int = controller.curPlayerLives
        
    protected fun curReserves(): Int = controller.curReserves

    protected fun enemy(index: Int): Enemy = controller.allEnemies[index]

    protected inline fun <reified T : StatusEffect> findPlayerStatusEffect(): T? =
        controller.playerStatusEffects.findInstance<T>()

    ///////////////////////////////
    // assertion helpers
    ///////////////////////////////

    protected fun curTestTitle(): String = titleStack.joinToString(
        separator = "->",
        transform = { "\"$it\"" },
        prefix = "$name->"
    )

    protected fun aCurReserves(): AssertionValue<Int> = object : AssertionValue<Int>() {

        override fun get(): Int = curReserves()

        override fun toString(): String = "${ANSI.cyan}aCurReservers${ANSI.reset} ${ANSI.grey}(='${get()}')${ANSI.reset}"
    }

    protected fun aCardInSlot(slot: Int): AssertionValue<Card?> = object : AssertionValue<Card?>() {

        override fun get(): Card? = revolverSlot(slot).card

        override fun toString(): String = "${ANSI.cyan}aCardInSlot($slot)${ANSI.reset} ${ANSI.grey}(='${get()}')${ANSI.reset}"
    }
    
    protected fun aCurrentPlayerHealth(): AssertionValue<Int> = object : AssertionValue<Int>() {

        override fun get(): Int = curPlayerHealth()

        override fun toString(): String = "${ANSI.cyan}aCurrentPlayerHealth${ANSI.reset} ${ANSI.grey}(='${get()}')${ANSI.reset}"
    }

    protected fun aEnemy(index: Int): AssertionValue<Enemy> = object : AssertionValue<Enemy>() {

        override fun get(): Enemy = enemy(index)

        override fun toString(): String = "${ANSI.cyan}aEnemy($index)${ANSI.reset} ${ANSI.grey}(='${get()}')${ANSI.reset}"
    }

    protected fun aCardInAfterlife(slot: Int): AssertionValue<Card?> = object : AssertionValue<Card?>() {

        override fun get(): Card? = cardInAfterlife(slot)

        override fun toString(): String = "${ANSI.cyan}aCardInAfterlife($slot)${ANSI.reset} ${ANSI.grey}(='${get()}')${ANSI.reset}"
    }

    protected fun aHasWon(): Assertion = object : Assertion() {

        override fun check(): String? {
            val hasWon = controller.hasWon
            if (!hasWon) return "controller.hasWon is false"
            return null
        }

        override fun toString(): String = "${ANSI.purple}hasWon${ANSI.reset}"

    }

    protected inline fun <reified T : StatusEffect> aFindPlayerStatusEffect() = object : AssertionValue<T?>() {

        override fun get(): T? = findPlayerStatusEffect<T>()

        override fun toString(): String =
            "${ANSI.cyan}findPlayerStatusEffect<${T::class.simpleName}> ${ANSI.grey}(='${get()}')${ANSI.reset}"
    }

    protected fun AssertionValue<Card?>.aName(): AssertionValue<String?> = object : AssertionValue<String?>() {

        override fun get(): String? = this@aName.get()?.name

        override fun toString(): String = "${ANSI.white}(${this@aName}${ANSI.white}).${ANSI.cyan}name ${ANSI.grey}(='${get()}')${ANSI.reset}"
    }

    protected fun AssertionValue<Enemy>.aCurrentHealth(): AssertionValue<Int> = object : AssertionValue<Int>() {

        override fun get(): Int = this@aCurrentHealth.get().currentHealth

        override fun toString(): String = "${ANSI.white}(${this@aCurrentHealth}${ANSI.white}).${ANSI.cyan}currentHealth ${ANSI.grey}(='${get()}')${ANSI.reset}"
    }

    protected inline fun <reified T : StatusEffect> AssertionValue<Enemy>.aFindStatusEffect(): AssertionValue<T?> = object : AssertionValue<T?>() {

        override fun get(): T? = this@aFindStatusEffect.get().statusEffects.findInstance<T>()

        override fun toString(): String =
            "${ANSI.white}(${this@aFindStatusEffect}${ANSI.white})." +
                    "${ANSI.cyan}findStatusEffect<${T::class.simpleName}> ${ANSI.grey}(='${get()}')${ANSI.reset}"
    }
    
    protected fun <T> deferred(getter: () -> T): AssertionValue<T> = AssertionValue.deferred(getter)

    ///////////////////////////////
    // Timeline helpers
    ///////////////////////////////

    internal inline fun Timeline.TimelineBuilderDSL.test(
        title: String,
        builder: Timeline.TimelineBuilderDSL.() -> Unit
    ) {
        action { titleStack.push(title) }
        builder()
        action { titleStack.pop() }
    }

    internal inline fun Timeline.TimelineBuilderDSL.assert(assertion: Assertion) {
        action {
            val message = assertion.check() ?: return@action
            val str = assertion.toString()
            throw AssertionTestException(curTestTitle(), "\nFailed assertion: $str\nReason: $message")
        }
    }

    internal inline fun Timeline.TimelineBuilderDSL.assert(
        crossinline condition: () -> Boolean,
        crossinline message: () -> String
    ) {
        action { if (!condition()) throw AssertionTestException(curTestTitle(), message()) }
    }
    
    internal inline fun Timeline.TimelineBuilderDSL.handleNextParry(
        parry: Boolean,
        crossinline callback: (event: Events.ParryStateChange) -> Unit = {}
    ) {
        var handled = false
        val listener: (Any) -> Unit = listener@{ event -> 
            if (event !is Events.ParryStateChange || !event.inParryMenu) return@listener
            handled = true
            callback(event)
            event.resolutionPromise.resolve(parry)
        }
        timelineActions.add(object : Timeline.TimelineAction() {
            
            override fun start(timeline: Timeline) {
                super.start(timeline)
                eventListeners.add(listener)    
            }

            override fun isFinished(timeline: Timeline): Boolean = handled

            override fun end(timeline: Timeline) {
                eventListeners.remove(listener)
            }
        })
    }

    internal inline fun Timeline.TimelineBuilderDSL.switchEnemy(enemy: Enemy) {
        waitForFreeUi()
        fire { Events.EnemySelected(enemy) }
    }

    internal inline fun Timeline.TimelineBuilderDSL.holster() {
        waitForFreeUi()
        fire { Events.HolsterButtonPressed }
    }

    internal inline fun Timeline.TimelineBuilderDSL.shoot() {
        waitForFreeUi()
        fire { Events.ShootButtonPressed }
        waitForFreeUi()
    }

    internal inline fun Timeline.TimelineBuilderDSL.dragCardOnSlot(card: String, slot: Int) {
        waitForFreeUi()
        fire {
            CardHand.CardDraggedOntoSlotEvent(cardInHand(card), revolverSlot(slot))
        }
        waitForFreeUi()
    }

    internal inline fun Timeline.TimelineBuilderDSL.waitForTurnBegin() {
        waitFor<Events.TurnBeginEvent>()
        waitFor<Events.ReservesChanged>()
        waitForFreeUi()
    }

    internal inline fun Timeline.TimelineBuilderDSL.waitForFreeUi() {
        delayUntil { !controller.isUIFrozen }
    }

    internal inline fun <reified T> Timeline.TimelineBuilderDSL.waitFor() {
        var caughtEvent = false
        val listener: (Any) -> Unit = { caughtEvent = true }
        timelineActions.add(object : Timeline.TimelineAction() {

            override fun start(timeline: Timeline) {
                super.start(timeline)
                eventListeners.add(listener)
            }

            override fun end(timeline: Timeline) {
                eventListeners.remove(listener)
            }

            override fun isFinished(timeline: Timeline): Boolean = caughtEvent
        })
    }

    internal inline fun Timeline.TimelineBuilderDSL.fire(crossinline creator: () -> Any) {
        timelineActions.add(object : Timeline.TimelineAction() {

            override fun start(timeline: Timeline) {
                super.start(timeline)
                events.fire(creator())
            }

            override fun isFinished(timeline: Timeline): Boolean = true
        })
    }

}
