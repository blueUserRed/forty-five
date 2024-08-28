package com.fourinachamber.fortyfive.keyInput.selection

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.FocusableActor

typealias SelectionGroup = String

class FocusableParent(
    private val transitions: List<SelectionTransition>,
    var onLeave: () -> Unit = {},
    private val startGroup: SelectionGroup? = null,
    var onSelection: (List<FocusableActor>) -> Unit,
) {


    private lateinit var focusableActors: Map<SelectionGroup, List<FocusableActor>>

    fun updateFocusableActors(screen: OnjScreen) {
        val actors = screen.getFocusableActors()
        val focusableActors =
            mutableMapOf<SelectionGroup, MutableList<FocusableActor>>().withDefault { mutableListOf() }
        actors.forEach {
            focusableActors[it.group]?.add(it)
        }
        this.focusableActors = focusableActors
    }

    fun getFocusablePrioritised(
        curActor: FocusableActor,
        screen: OnjScreen
    ): Map<SelectionTransition.TransitionType, List<FocusableActor>> {
        val res: MutableMap<SelectionTransition.TransitionType, MutableList<FocusableActor>> =
            mutableMapOf<SelectionTransition.TransitionType, MutableList<FocusableActor>>().withDefault { mutableListOf() }
        val group = curActor.group ?: throw RuntimeException("actor $curActor should have never been selected")
        transitions.forEach {
            if (it.condition.check(screen)) {
                if (group in it.groups) {
                    it.groups.forEach { gr2 -> focusableActors[gr2]?.let { it1 -> res[it.type]!!.addAll(it1) } }
                }
            }
        }
        return res
    }

    fun focusNext(direction: Vector2?, screen: OnjScreen): FocusableActor? {
        if (direction == null) {
            val oldFocusedActor = screen.focusedActor
            if (oldFocusedActor != null && oldFocusedActor is Actor) {
                return getNextTabFocused(focusableActors.values.flatten(), oldFocusedActor) as FocusableActor?
            }
            return if (startGroup == null || focusableActors[startGroup]?.isNotEmpty() != true) {
                var curBest: Actor? = null
                focusableActors.values.forEach { curBest = getFirstFocused(it, curBest) }
                curBest as FocusableActor?
            } else {
                getFirstFocused(focusableActors[startGroup]!!) as FocusableActor?
            }
        }
        return null
    }

    fun focusPrevious(screen: OnjScreen): FocusableActor? {
        val oldFocusedActor = screen.focusedActor
        val actors = focusableActors.values.flatten()
        if (oldFocusedActor == null || oldFocusedActor !is Actor) {
            return getFirstFocused(actors) as FocusableActor?
        }
        var curBest: Actor? = null
        var curBestPos = getPosForFocus(null)
        val targetPos = getPosForFocus(oldFocusedActor)
        actors
            .filter { it.isFocusable }
            .filterIsInstance<Actor>()
            .filter {
                val newPos = getPosForFocus(it)
                newPos.x < targetPos.x && newPos.y > targetPos.y
            }
            .forEach {
                val newPos = getPosForFocus(it)
                if (curBestPos.y < newPos.y || (curBestPos.y == newPos.y && curBestPos.x < newPos.x)) {
                    curBest = it
                    curBestPos = newPos
                }
            }
        return (curBest?:getLastFocused(actors)) as FocusableActor?
    }

    private fun getFirstFocused(actors: List<FocusableActor>, oldBest: Actor? = null): Actor? {
        var curBest: Actor? = oldBest
        var curBestPos = getPosForFocus(curBest)
        actors.filter { it.isFocusable }.filterIsInstance<Actor>().forEach {
            val newPos = getPosForFocus(it)
            if (curBestPos.y < newPos.y || (curBestPos.y == newPos.y && curBestPos.x > newPos.x)) {
                curBest = it
                curBestPos = newPos
            }
        }
        return curBest
    }
    private fun getLastFocused(actors: List<FocusableActor>): Actor? {
        var curBest: Actor? = null
        var curBestPos = getPosForFocus(curBest)
        actors.filter { it.isFocusable }.filterIsInstance<Actor>().forEach {
            val newPos = getPosForFocus(it)
            if (curBestPos.y > newPos.y || (curBestPos.y == newPos.y && curBestPos.x < newPos.x)) {
                curBest = it
                curBestPos = newPos
            }
        }
        return curBest
    }

    private fun getNextTabFocused(actors: List<FocusableActor>, target: Actor): Actor? {
        var curBest: Actor? = null
        var curBestPos = getPosForFocus(null)
        val targetPos = getPosForFocus(target)
        actors
            .filter { it.isFocusable }
            .filterIsInstance<Actor>()
            .filter {
                val newPos = getPosForFocus(it)
                newPos.x > targetPos.x && newPos.y < targetPos.y
            }
            .forEach {
                val newPos = getPosForFocus(it)
                if (curBestPos.y > newPos.y || (curBestPos.y == newPos.y && curBestPos.x > newPos.x)) {
                    curBest = it
                    curBestPos = newPos
                }
            }
        return (curBest ?: getFirstFocused(actors))
    }

    private fun getPosForFocus(actor: Actor?): Vector2 {
        actor ?: return Vector2(Float.MIN_VALUE, Float.MAX_VALUE)
        return Vector2(actor.x + actor.width / 2, actor.y + actor.height / 2)
    }
}

class SelectionTransition(
    val type: TransitionType,
    val condition: SelectionTransitionCondition,
    val groups: List<SelectionGroup>
) {
    enum class TransitionType {
        SEAMLESS, LAST_RESORT
    }
}

sealed class SelectionTransitionCondition {
    data object Always : SelectionTransitionCondition() {
        override fun check(screen: OnjScreen): Boolean {
            return true
        }
    }

    data class Screenstate(private val screenState: String) : SelectionTransitionCondition() {
        override fun check(screen: OnjScreen): Boolean {
            return screenState in screen.screenState
        }
    }

    abstract fun check(screen: OnjScreen): Boolean
}