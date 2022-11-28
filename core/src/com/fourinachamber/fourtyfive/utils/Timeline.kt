package com.fourinachamber.fourtyfive.utils

import com.badlogic.gdx.utils.TimeUtils

/**
 * tool for timing (currently only sequential) tasks. can be created directly using a list of TimelineActions or using
 * [timeline]
 */
class Timeline(private val actions: MutableList<TimelineAction>) {

    /**
     * true when the timeline has finished
     */
    var isFinished: Boolean = false
        private set

    private var hasBeenStarted: Boolean = false

    private val pushActionsBuffer: MutableList<TimelineAction> = mutableListOf()

    /**
     * starts executing the tasks in the timeline
     */
    fun start() {
        hasBeenStarted = true
        if (actions.isEmpty()) {
            isFinished = true
            return
        }
        actions.first().start(this)
    }

    /**
     * should be called every frame to keep the timeline updated
     */
    fun update() {
        if (isFinished || !hasBeenStarted) return
        while (true) {
            val first = actions.first()
            first.update()
            if (first.isFinished()) {
                first.end()
                actions.removeFirst()
                for (action in pushActionsBuffer) actions.add(0, action)
                pushActionsBuffer.clear()
                if (actions.isEmpty()) break
                actions.first().start(this)
            } else break
        }
        if (actions.isEmpty()) isFinished = true
    }

    fun pushAction(timelineAction: TimelineAction) {
        pushActionsBuffer.add(0, timelineAction)
    }

    /**
     * an action that can be put in a timeline
     */
    abstract class TimelineAction {
        open fun start(timeline: Timeline) { }
        open fun update() { }
        abstract fun isFinished(): Boolean
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
                override fun start(timeline: Timeline) = action()
            })
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
            timelineActions.addAll(timeline.actions)
        }

        fun includeLater(timelineCreator: () -> Timeline, condition: () -> Boolean) {
            timelineActions.add(object : TimelineAction() {

                override fun start(timeline: Timeline) {
                    if (condition()) {
                        val timelineToInclude = timelineCreator()
                        for (action in timelineToInclude.actions) timeline.pushAction(action)
                    }
                }

                override fun isFinished(): Boolean = true

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
