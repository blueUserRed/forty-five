package com.fourinachamber.fortyfive.keyInput.selection

import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.customActor.FocusableActor
import kotlin.math.*

typealias SelectionGroup = String

class FocusableParent(
    private val transitions: List<SelectionTransition>,
    var onLeave: () -> Unit = {},
    private val startGroup: SelectionGroup? = null,
    groups: List<SelectionGroup> = listOf(),
    var onSelection: (List<FocusableActor>) -> Unit = {},
) {
    private val groups: Set<SelectionGroup>

    init {
        val allGroups = mutableSetOf<SelectionGroup>()
        transitions.forEach { allGroups.addAll(it.groups) }
        allGroups.addAll(groups)
        this.groups = allGroups
    }

    private var focusableActors: Map<SelectionGroup, List<FocusableActor>> = mutableMapOf()

    fun updateFocusableActors(screen: OnjScreen) {
        val actors = screen.getFocusableActors()
        val focusableActors =
            mutableMapOf<SelectionGroup, MutableList<FocusableActor>>()
        actors
            .filter { it2 -> it2.group in groups }
            .forEach {
                focusableActors.putIfAbsent(it.group!!, mutableListOf())
                focusableActors[it.group]!!.add(it)
            }
        this.focusableActors = focusableActors
    }

    private fun getFocusablePrioritised(
        curActor: FocusableActor,
        screen: OnjScreen
    ): Map<SelectionTransition.TransitionType, List<FocusableActor>> {
        val res: MutableMap<SelectionTransition.TransitionType, MutableList<FocusableActor>> = mutableMapOf()
        val group = curActor.group ?: throw RuntimeException("actor $curActor should have never been selected")
        transitions.forEach {
            if (it.condition.check(screen)) {
                if (group in it.groups) {
                    it.groups.forEach { gr2 ->
                        focusableActors[gr2]?.let { it1 ->
                            run {
                                res.putIfAbsent(it.type, mutableListOf())
                                res[it.type]!!.addAll(it1)
                            }
                        }
                    }
                }
            }
        }
        return res
    }

    fun focusNext(direction: Vector2?, screen: OnjScreen): FocusableActor? {
        val oldFocusedActor = screen.focusedActor
        if (oldFocusedActor == null || oldFocusedActor !is Actor) {
            return if (startGroup == null || focusableActors[startGroup]?.isNotEmpty() != true) {
                getFirstFocused(focusableActors.values.flatten()) as FocusableActor?
            } else {
                getFirstFocused(focusableActors[startGroup]!!) as FocusableActor?
            }
        }
        if (direction == null) return focusNext(screen)
        val oldPos = oldFocusedActor.centerPos()
        val polarDir = toPolarCoords(direction)
        val prios = getFocusablePrioritised(oldFocusedActor, screen)
        val newActor= focusableActors //TODO check why this is wrong
            .values
            .flatten()
            .filter { it.isFocusable && it!=oldFocusedActor }
            .filterIsInstance<Actor>()
            .map {
                val curPos=it.centerPos()
                val curPolar = toPolarCoords(Vector2(curPos.x-oldPos.x, curPos.y - oldPos.y))
                curPolar.y = min(
                    abs(curPolar.y - polarDir.y),
                    min(abs(curPolar.y + 2 * PI.toFloat() - polarDir.y), abs(curPolar.y - 2 * PI.toFloat() - polarDir.y))
                )
                val distMulti=distanceSpreadMultiplier(curPolar, it, prios)
                curPolar to (if (distMulti == Float.MAX_VALUE) distMulti else curPolar.x * distMulti) to it
            }
        val res=newActor.filter { it.first.second < Float.MAX_VALUE }.minByOrNull { it.first.second }?.second

        return (res ?: screen.focusedActor) as FocusableActor?
    }

    private fun distanceSpreadMultiplier(
        v: Vector2,
        actor: Actor,
        prios: Map<SelectionTransition.TransitionType, List<FocusableActor>>
    ): Float {
        if (v.y > PI / 2) return Float.MAX_VALUE

        SelectionTransition.TransitionType.entries.forEach {
            if ((actor as FocusableActor) in (prios[it] ?: listOf())) {
                if (v.y < it.barrier) return 1.0F
                return ((1 + v.y).pow(it.exponent) * it.exponent)
//               return (1 + v.y*it.exponent.toDouble())
            }
        }
        return Float.MAX_VALUE
    }

    private fun toPolarCoords(v: Vector2): Vector2 {
        return Vector2(sqrt(v.x * v.x + v.y * v.y), aTan(v))
    }

    private fun aTan(v: Vector2): Float {
        if (v.x < 0) return atan(v.y / v.x) + PI.toFloat()
        if (v.x > 0 && v.y >= 0) return atan(v.y / v.x)
        if (v.x > 0 && v.y < 0) return atan(v.y / v.x) + 2 * PI.toFloat()
        if (v.y > 0) return PI.toFloat() / 2
        return 3 * PI.toFloat() / 2
    }

    private fun focusNext(screen: OnjScreen): FocusableActor? {
        val oldFocusedActor = screen.focusedActor!! as Actor
        return getNextTabFocused(focusableActors.values.flatten(), oldFocusedActor) as FocusableActor?
    }

    fun focusPrevious(screen: OnjScreen): FocusableActor? {
        val oldFocusedActor = screen.focusedActor
        if (oldFocusedActor != null && oldFocusedActor is Actor) {
            return getPreviousTabFocused(focusableActors.values.flatten(), oldFocusedActor) as FocusableActor?
        }
        return if (startGroup == null || focusableActors[startGroup]?.isNotEmpty() != true) {
            getLastFocused(focusableActors.values.flatten()) as FocusableActor?
        } else {
            getLastFocused(focusableActors[startGroup]!!) as FocusableActor?
        }
    }

    private fun getFirstFocused(actors: List<FocusableActor>): Actor? {
        var curBest: Actor? = null
        var curBestPos = Float.MAX_VALUE
        actors.filter { it.isFocusable }.filterIsInstance<Actor>().forEach {
            val newPos = getDistFromStart(it)
            if (curBestPos > newPos) {
                curBest = it
                curBestPos = newPos
            }
        }
        return curBest
    }

    private fun getLastFocused(actors: List<FocusableActor>): Actor? {
        var curBest: Actor? = null
        var curBestPos = Float.MIN_VALUE
        actors.filter { it.isFocusable }.filterIsInstance<Actor>().forEach {
            val newPos = getDistFromStart(it)
            if (curBestPos < newPos) {
                curBest = it
                curBestPos = newPos
            }
        }
        return curBest
    }

    private fun getNextTabFocused(actors: List<FocusableActor>, target: Actor): Actor? {
        val targetPos = target.centerPos()
        return actors
            .asSequence()
            .filter { it.isFocusable && it != target }
            .filterIsInstance<Actor>()
            .map { it to getRelativePositionFromTarget(it, targetPos) }
            .filter { it.second.x > 0 || (it.second.x == 0.0F && it.second.y > 0.0F) }
            .minWithOrNull(
                Comparator
                    .comparingDouble<Pair<Actor, Vector2>?> { it.second.x.toDouble() }
                    .thenComparingDouble { it.second.y.toDouble() }
            )?.first ?: return getFirstFocused(actors) //TODO make a range in which it counts as the same x-coordinate
    }

    private fun getPreviousTabFocused(actors: List<FocusableActor>, target: Actor): Actor? {
        val targetPos = target.centerPos()
        return actors
            .asSequence()
            .filter { it.isFocusable && it != target }
            .filterIsInstance<Actor>()
            .map { it to getRelativePositionFromTarget(it, targetPos) }
            .filter { it.second.x < 0 || (it.second.x == 0.0F && it.second.y < 0.0F) }
            .maxWithOrNull(
                Comparator
                    .comparingDouble<Pair<Actor, Vector2>?> { it.second.x.toDouble() }
                    .thenComparingDouble { it.second.y.toDouble() }
            )?.first ?: return getLastFocused(actors) //TODO make a range in which it counts as the same x-coordinate
    }

    private fun getDistFromStart(actor: Actor): Float {
        val pos = actor.centerPos()
        if (pos.x < 0 || pos.y < 0) return Float.MAX_VALUE
        return ((pos.x * 3) + (actor.stage.viewport.worldHeight - pos.y))
    }

    private fun Actor.centerPos(): Vector2 {
        val pos = Vector2(this.x + this.width / 2, this.y + this.height / 2)
        return localToScreenCoordinates(pos)
    }

    private fun getRelativePositionFromTarget(actor: Actor, target: Vector2): Vector2 {
        val pos = actor.centerPos()
        return Vector2(pos.x - target.x, target.y - pos.y)
    }
}

class SelectionTransition(
    val type: TransitionType,
    val condition: SelectionTransitionCondition = SelectionTransitionCondition.Always,
    val groups: List<SelectionGroup>
) {
    enum class TransitionType(val exponent: Float, val barrier: Float) {
        SEAMLESS(4.5F, (PI /6).toFloat()), //TODO check these values for all screens once implemented
        LAST_RESORT(7F, 0F);
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