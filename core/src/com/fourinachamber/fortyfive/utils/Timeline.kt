package com.fourinachamber.fortyfive.utils

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g2d.ParticleEffect
import com.badlogic.gdx.math.Vector2
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fortyfive.FortyFive
import com.fourinachamber.fortyfive.game.GameAnimation
import com.fourinachamber.fortyfive.screen.general.CustomParticleActor
import com.fourinachamber.fortyfive.screen.general.OnjScreen

/**
 * tool for timing tasks. can be created directly using a list of TimelineActions or using
 * [timeline]
 */
class Timeline(private val _actions: MutableList<TimelineAction>) {

    /**
     * true when the timeline has finished
     */
    val isFinished: Boolean
        get() = _actions.isEmpty()

    val actions: List<TimelineAction>
        get() = _actions

    var hasBeenStarted: Boolean = false
        private set

    private val pushActionsBuffer: MutableList<TimelineAction> = mutableListOf()

    val storage: MutableMap<String, Any?> = mutableMapOf()

    /**
     * starts executing the tasks in the timeline
     */
    @AllThreadsAllowed
    fun startTimeline() {
        hasBeenStarted = true
        if (_actions.isEmpty()) return
        _actions.first().start(this)
    }

    /**
     * should be called every frame to keep the timeline updated
     */
    @AllThreadsAllowed
    fun updateTimeline() {
        if (isFinished || !hasBeenStarted) return
        while (true) {
            val first = _actions.first()
            if (!first.hasBeenStarted) first.start(this)
            first.update(this)
            if (first.isFinished(this)) {
                first.end(this)
                _actions.removeFirst()
                for (action in pushActionsBuffer) _actions.add(0, action)
                pushActionsBuffer.clear()
                if (_actions.isEmpty()) break
            } else break
        }
    }

    fun store(name: String, value: Any?) {
        storage[name] = value
    }

    inline fun <reified T> get(name: String): T {
        return storage[name] as T
    }

    /**
     * pushes an action to the beginning of timeline. the action will be temporarily stored in a buffer until the
     * current action finishes, after which this action will be started
     */
    @AllThreadsAllowed
    fun pushAction(timelineAction: TimelineAction) {
        pushActionsBuffer.add(timelineAction)
    }

    /**
     * appends an action to the end of the timeline
     */
    @AllThreadsAllowed
    fun appendAction(timelineAction: TimelineAction) {
        _actions.add(timelineAction)
    }

    @AllThreadsAllowed
    fun asAction(): TimelineAction {
        if (hasBeenStarted) {
            throw RuntimeException("Timeline cannot be made into an action if it has already started")
        }
        return TimelineAsAction(this)
    }

    /**
     * an action that can be put in a timeline
     */
    abstract class TimelineAction {

        /**
         * true if the action has already been started
         */
        var hasBeenStarted: Boolean = false
            protected set


        /**
         * called when the actions starts.
         * super.start() should be called when overriding
         */
        open fun start(timeline: Timeline) {
            hasBeenStarted = true
        }

        /**
         * called every frame as long as this action is active
         */
        open fun update(timeline: Timeline) { }

        /**
         * checks if the action has finished
         */
        abstract fun isFinished(timeline: Timeline): Boolean

        /**
         * called when the action ends
         */
        open fun end(timeline: Timeline) { }
    }

    /**
     * use via [timeline]
     */
    class TimelineBuilderDSL {

        val timelineActions: MutableList<TimelineAction> = mutableListOf()

        /**
         * adds an action that finishes instantly to the timeline
         */
        inline fun action(crossinline action: @AllThreadsAllowed Timeline.() -> Unit) {
            timelineActions.add(object : TimelineAction() {
                override fun isFinished(timeline: Timeline): Boolean = true
                override fun start(timeline: Timeline) {
                    super.start(timeline)
                    action(timeline)
                }
            })
        }

        /**
         * Adds an action that finishes instantly to the timeline.
         *
         * In contrast to [action()](Timeline.TimelineBuilderDSL.action) the action is always executed on the
         * main thread using `Gdx.app.postRunnable`
         *
         * *WARNING:* because the action is executed on the next render call, this may break the sequence of actions,
         * for example if the next action is an [action()](Timeline.TimelineBuilderDSL.action)'
         */
        inline fun mainThreadAction(crossinline action: @MainThreadOnly Timeline.() -> Unit) {
            timelineActions.add(object : TimelineAction() {
                override fun isFinished(timeline: Timeline): Boolean = true
                override fun start(timeline: Timeline) {
                    super.start(timeline)
                    Gdx.app.postRunnable { action(timeline) }
                }
            })
        }

        fun includeAction(action: TimelineAction) {
            timelineActions.add(action)
        }

        /**
         * delays the timeline until a condition is met
         */
        inline fun delayUntil(crossinline condition: @AllThreadsAllowed Timeline.() -> Boolean) {
            timelineActions.add(object : TimelineAction() {
                override fun isFinished(timeline: Timeline): Boolean = condition(timeline)
            })
        }

        /**
         * delays the timeline for a number of milliseconds
         */
        fun delay(millis: Int) {
            timelineActions.add(object : TimelineAction() {

                var finishedAt: Long = Long.MAX_VALUE

                override fun start(timeline: Timeline) {
                    super.start(timeline)
                    finishedAt = TimeUtils.millis() + millis
                }

                override fun isFinished(timeline: Timeline): Boolean = TimeUtils.millis() >= finishedAt
            })
        }

        /**
         * includes the tasks of a second timeline. The second timeline must not have started yet
         */
        fun include(timeline: Timeline) {
            if (timeline.hasBeenStarted) throw RuntimeException("cannot include a timeline which was started already")
            timelineActions.addAll(timeline._actions)
        }

        /**
         * used for conditionally including timelines. The condition is wrapped in a lambda and will only be executed
         * only once right before the decision whether to include timeline is made. This is useful when the outcome of
         * the condition is not known when the timeline is created. The timeline is also wrapped in a lambda in case
         * the creation of the timeline to include is also dependent on factors not known when the timeline is created
         */
        inline fun includeLater(
            crossinline timelineCreator: @AllThreadsAllowed Timeline.() -> Timeline,
            crossinline condition: @AllThreadsAllowed Timeline.() -> Boolean
        ) {
            timelineActions.add(object : TimelineAction() {

                override fun start(timeline: Timeline) {
                    super.start(timeline)
                    if (condition(timeline)) {
                        val timelineToInclude = timelineCreator(timeline)
                        for (action in timelineToInclude.actions.reversed()) timeline.pushAction(action)
                    }
                }

                override fun isFinished(timeline: Timeline): Boolean = true

            })
        }

        /**
         * same as [includeLater], but includes an action instead of a timeline
         */
        fun includeActionLater(action: TimelineAction, condition: @AllThreadsAllowed Timeline.() -> Boolean) {
            timelineActions.add(object : TimelineAction() {

                override fun start(timeline: Timeline) {
                    super.start(timeline)
                    if (condition(timeline)) timeline.pushAction(action)
                }

                override fun isFinished(timeline: Timeline): Boolean = true

            })
        }

        fun parallelActions(vararg actions: TimelineAction) {
            timelineActions.add(ParallelTimelineAction(actions.toList()))
        }

        /**
         * creates the timeline. Should only be used by [timeline]
         */
        fun build(): Timeline = Timeline(timelineActions)

    }

    companion object {

        /**
         * useful for quickly creating timelines
         */
        inline fun timeline(builder: TimelineBuilderDSL.() -> Unit): Timeline {
            val timelineBuilder = TimelineBuilderDSL()
            builder(timelineBuilder)
            return timelineBuilder.build()
        }

    }

}

/**
 * useful for including gameAnimations in timelines
 */
class GameAnimationTimelineAction(private val gameAnimation: GameAnimation) : Timeline.TimelineAction() {

    override fun start(timeline: Timeline) {
        super.start(timeline)
        FortyFive.currentGame!!.playGameAnimation(gameAnimation)
    }

    override fun isFinished(timeline: Timeline): Boolean = gameAnimation.isFinished()
}

/**
 * useful for including actions on actors in timelines
 */
class ActorActionTimelineAction(
    private val action: TemporalAction,
    private val actor: Actor
) : Timeline.TimelineAction() {

    override fun start(timeline: Timeline) {
        super.start(timeline)
        actor.addAction(action)
    }

    override fun isFinished(timeline: Timeline): Boolean = action.isComplete

    override fun end(timeline: Timeline) {
        actor.removeAction(action)
        action.reset()
    }
}

class TimelineAsAction(private val timeline: Timeline) : Timeline.TimelineAction() {

    override fun start(timeline: Timeline) {
        super.start(timeline)
        this.timeline.startTimeline()
    }

    override fun update(timeline: Timeline) {
        super.update(timeline)
        this.timeline.updateTimeline()
    }

    override fun isFinished(timeline: Timeline): Boolean = this.timeline.isFinished

}

class ParticleTimelineAction(
    val particle: ParticleEffect,
    val coords: Vector2,
    private val screen: OnjScreen
) : Timeline.TimelineAction() {

    override fun start(timeline: Timeline) {
        super.start(timeline)
        val particleActor = CustomParticleActor(particle)
        particleActor.isAutoRemove = true
        particleActor.fixedZIndex = Int.MAX_VALUE
        particleActor.setPosition(coords.x, coords.y)
        screen.addActorToRoot(particleActor)
        particleActor.start()
    }

    override fun isFinished(timeline: Timeline): Boolean = particle.isComplete
}

class ParallelTimelineAction(private var actions: List<Timeline.TimelineAction>) : Timeline.TimelineAction() {

    override fun start(timeline: Timeline) {
        super.start(timeline)
        for (action in actions) action.start(timeline)
    }

    override fun update(timeline: Timeline) {
        for (action in actions) action.update(timeline)
    }

    override fun end(timeline: Timeline) {
        for (action in actions) action.end(timeline)
    }

    override fun isFinished(timeline: Timeline): Boolean {
        this.actions = this.actions.filter {
            if (!it.isFinished(timeline)) {
                true
            } else {
                it.end(timeline)
                false
            }
        }
        return this.actions.isEmpty()
    }
}
