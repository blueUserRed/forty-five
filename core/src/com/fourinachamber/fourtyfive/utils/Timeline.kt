package com.fourinachamber.fourtyfive.utils

import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.actions.TemporalAction
import com.badlogic.gdx.utils.TimeUtils
import com.fourinachamber.fourtyfive.FourtyFive
import com.fourinachamber.fourtyfive.game.GameAnimation

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

    private var hasBeenStarted: Boolean = false

    private val pushActionsBuffer: MutableList<TimelineAction> = mutableListOf()

    /**
     * starts executing the tasks in the timeline
     */
    fun start() {
        hasBeenStarted = true
        if (_actions.isEmpty()) return
        _actions.first().start(this)
    }

    /**
     * should be called every frame to keep the timeline updated
     */
    fun update() {
        if (isFinished || !hasBeenStarted) return
        while (true) {
            val first = _actions.first()
            if (!first.hasBeenStarted) first.start(this)
            first.update()
            if (first.isFinished()) {
                first.end()
                _actions.removeFirst()
                for (action in pushActionsBuffer) _actions.add(0, action)
                pushActionsBuffer.clear()
                if (_actions.isEmpty()) break
            } else break
        }
    }

    /**
     * pushes an action to the beginning of timeline. the action will be temporarily stored in a buffer until the
     * current action finishes, after which this action will be started
     */
    fun pushAction(timelineAction: TimelineAction) {
        pushActionsBuffer.add(timelineAction)
    }

    /**
     * appends an action to the end of the timeline
     */
    fun appendAction(timelineAction: TimelineAction) {
        _actions.add(timelineAction)
    }

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
        open fun update() { }

        /**
         * checks if the action has finished
         */
        abstract fun isFinished(): Boolean

        /**
         * called when the action ends
         */
        open fun end() { }
    }

    /**
     * use via [timeline]
     */
    class TimelineBuilderDSL {

        private val timelineActions: MutableList<TimelineAction> = mutableListOf()

        /**
         * adds an action that finishes instantly to the timeline
         */
        fun action(action: () -> Unit) {
            timelineActions.add(object : TimelineAction() {
                override fun isFinished(): Boolean = true
                override fun start(timeline: Timeline) {
                    super.start(timeline)
                    action()
                }
            })
        }

        fun includeAction(action: TimelineAction) {
            timelineActions.add(action)
        }

        /**
         * delays the timeline until a condition is met
         */
        fun delayUntil(condition: () -> Boolean) {
            timelineActions.add(object : TimelineAction() {
                override fun isFinished(): Boolean = condition()
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

                override fun isFinished(): Boolean = TimeUtils.millis() >= finishedAt
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
        fun includeLater(timelineCreator: () -> Timeline, condition: () -> Boolean) {
            timelineActions.add(object : TimelineAction() {

                override fun start(timeline: Timeline) {
                    super.start(timeline)
                    if (condition()) {
                        val timelineToInclude = timelineCreator()
                        for (action in timelineToInclude._actions.reversed()) timeline.pushAction(action)
                    }
                }

                override fun isFinished(): Boolean = true

            })
        }

        /**
         * same as [includeLater], but includes an action instead of a timeline
         */
        fun includeActionLater(action: TimelineAction, condition: () -> Boolean) {
            timelineActions.add(object : TimelineAction() {

                override fun start(timeline: Timeline) {
                    super.start(timeline)
                    if (condition()) timeline.pushAction(action)
                }

                override fun isFinished(): Boolean = true

            })
        }

        fun parallelActions(vararg actions: TimelineAction) {
            timelineActions.add(object : TimelineAction() {

                private var actions: List<TimelineAction> = actions.toMutableList()

                override fun start(timeline: Timeline) {
                    super.start(timeline)
                    for (action in actions) action.start(timeline)
                }

                override fun update() {
                    for (action in actions) action.update()
                }

                override fun end() {
                    for (action in actions) action.end()
                }

                override fun isFinished(): Boolean {
                    this.actions = this.actions.filter {
                        if (!it.isFinished()) {
                            true
                        } else {
                            it.end()
                            false
                        }
                    }
                    return this.actions.isEmpty()
                }
            })
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
        fun timeline(builder: TimelineBuilderDSL.() -> Unit): Timeline {
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
        FourtyFive.currentGame!!.playGameAnimation(gameAnimation)
    }

    override fun isFinished(): Boolean = gameAnimation.isFinished()
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

    override fun isFinished(): Boolean = action.isComplete

    override fun end() {
        actor.removeAction(action)
        action.reset()
    }
}

class TimelineAsAction(private val timeline: Timeline) : Timeline.TimelineAction() {

    override fun start(timeline: Timeline) {
        super.start(timeline)
        this.timeline.start()
    }

    override fun update() {
        super.update()
        this.timeline.update()
    }

    override fun isFinished(): Boolean = timeline.isFinished

}
