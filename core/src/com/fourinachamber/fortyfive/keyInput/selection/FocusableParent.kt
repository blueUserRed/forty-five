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
    var onSelection: (List<Actor>) -> Unit = {},
) {
    val groups: Set<SelectionGroup>

    init {
        val allGroups = mutableSetOf<SelectionGroup>()
        transitions.forEach { allGroups.addAll(it.groups) }
        allGroups.addAll(groups)
        this.groups = allGroups
    }

    private var focusableActors: Map<SelectionGroup, List<FocusableActor>> = mutableMapOf()
    //TODO maybe cache focusableActors.values.flatten() as it is needed quite often

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
    ): Map<TransitionType, List<FocusableActor>> {
        val res: MutableMap<TransitionType, MutableList<FocusableActor>> = mutableMapOf()
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

        if (oldFocusedActor !is FocusableActor || !hasActor(oldFocusedActor)) {
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
        val newActor = focusableActors
            .values
            .flatten()
            .filter { it.isFocusable && it != oldFocusedActor }
            .filterIsInstance<Actor>()
            .map {
                val curPos = it.centerPos()
                val curPolar = toPolarCoords(Vector2(curPos.x - oldPos.x, curPos.y - oldPos.y))
                curPolar.y = min(
                    abs(curPolar.y - polarDir.y),
                    min(
                        abs(curPolar.y + 2 * PI.toFloat() - polarDir.y),
                        abs(curPolar.y - 2 * PI.toFloat() - polarDir.y)
                    )
                )
                val distMulti = distanceSpreadMultiplier(curPolar, it, prios)
                curPolar.x = (if (distMulti == Float.MAX_VALUE) distMulti else curPolar.x * distMulti)
                curPolar to it
            }
        val res = newActor.filter { it.first.x < Float.MAX_VALUE }
            .minWithOrNull(Comparator.comparingDouble<Pair<Vector2, Actor>?> { it.first.x.toDouble() }
                .thenComparingDouble { it.first.y.toDouble() })?.second

        return (res ?: screen.focusedActor) as FocusableActor?
    }

    private fun distanceSpreadMultiplier(
        v: Vector2,
        actor: Actor,
        prios: Map<TransitionType, List<FocusableActor>>
    ): Float {
        if (v.y > PI / 2) return Float.MAX_VALUE
        var curMin: Float = Float.MAX_VALUE
        TransitionType.entries().forEach {
            if ((actor as FocusableActor) in (prios[it] ?: listOf())) {
                if (v.y < it.barrier) curMin = min(curMin, it.multiplier)
                else curMin = min(curMin, ((1 + v.y-it.barrier).pow(it.exponent) * it.multiplier))
            }
        }
        return curMin
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
        var curBestPos:Float = Float.POSITIVE_INFINITY
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
            .filter { it.second.x > 0 || (it.second.x == 0.0F && it.second.y < 0.0F) }
            .minWithOrNull(
                Comparator
                    .comparingDouble<Pair<Actor, Vector2>?> { it.second.x.toDouble() }
                    .thenComparingDouble { -it.second.y.toDouble() }
            )?.first ?: return getFirstFocused(actors) //TODO make a range in which it counts as the same x-coordinate
    }

    private fun getPreviousTabFocused(actors: List<FocusableActor>, target: Actor): Actor? {
        val targetPos = target.centerPos()
        return actors
            .asSequence()
            .filter { it.isFocusable && it != target }
            .filterIsInstance<Actor>()
            .map { it to getRelativePositionFromTarget(it, targetPos) }
            .filter { it.second.x < 0 || (it.second.x == 0.0F && it.second.y > 0.0F) }
            .maxWithOrNull(
                Comparator
                    .comparingDouble<Pair<Actor, Vector2>?> { it.second.x.toDouble() }
                    .thenComparingDouble { -it.second.y.toDouble() }
            )?.first ?: return getLastFocused(actors) //TODO make a range in which it counts as the same x-coordinate
    }

    private fun getDistFromStart(actor: Actor): Float {
        val pos = actor.centerPos()
        if (pos.x+actor.width/2 < 0 || pos.y + actor.height/2 < 0) return Float.MAX_VALUE
        return ((pos.x * 3) + (pos.y))
    }

    private fun Actor.centerPos(): Vector2 {
        val pos = Vector2(this.width / 2, -this.height / 2)
        return localToScreenCoordinates(pos) //screen coords start on top left, that's why the Minus in front of height
    }

    private fun getRelativePositionFromTarget(actor: Actor, target: Vector2): Vector2 {
        val pos = actor.centerPos()
        return Vector2(pos.x - target.x, target.y - pos.y)
    }

    fun hasActor(actor: Actor): Boolean {
        return actor in focusableActors.values.flatten().filterIsInstance<Actor>()
    }
}

data class SelectionTransition(
    val type: TransitionType = TransitionType.Seamless,
    val condition: SelectionTransitionCondition = SelectionTransitionCondition.Always,
    val groups: List<SelectionGroup>
)


//TODO check these values for all screens once implemented everywhere
/**
 * @param exponent the exponent, the distance it exponentially grows as the angle gets bigger
 * @param barrier the minimum angle, that the exponent is used (before the exponent is just a multiplier)
 */
sealed class TransitionType(val exponent: Float, val barrier: Float, val multiplier: Float) {

    //this barrier is so big, that it is never the exponent and always just a multiplier 
    data object Prioritized : TransitionType(3F, (PI / 4).toFloat(), 1 / 2F)

    data object Seamless : TransitionType(4.5F, (PI / 4).toFloat(), 1F)

    data object LastResort : TransitionType(7F, 0F, 7F)
    companion object {
        fun entries () = listOf(Prioritized, Seamless, LastResort)
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