package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.fourinachamber.fortyfive.onjNamespaces.OnjColor
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.CustomLabel
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.utils.ParallelTimelineAction
import com.fourinachamber.fortyfive.utils.Timeline
import com.fourinachamber.fortyfive.utils.toOnjYoga
import io.github.orioncraftmc.meditate.YogaValue
import io.github.orioncraftmc.meditate.enums.YogaUnit
import onj.value.OnjString

class CustomWarningParent(screen: OnjScreen) : CustomFlexBox(screen) {

    private var timeline: Timeline = Timeline(mutableListOf())

    /**
     * the timeline which removes the parts again
     */
    private var deadTimeline: Timeline = Timeline(mutableListOf())

    private val curShown: MutableList<CustomFlexBox> = mutableListOf()
    private val curDeadTimers: MutableMap<CustomFlexBox, Long> = mutableMapOf()

    private val limits: MutableMap<String, Long> = mutableMapOf()
    private val curLimits: MutableMap<String, MutableList<Long>> = mutableMapOf()

    fun setLimit(title: String, limit: Long) {
        limits[title] = limit
    }

    init {
        timeline.startTimeline()
        deadTimeline.startTimeline()
    }

    override fun layout() {
        super.layout()
        curShown.forEach { addMarginBottom(it) }
    }

    override fun draw(batch: Batch?, parentAlpha: Float) {
        timeline.updateTimeline()
        deadTimeline.updateTimeline()
        checkDead()
        super.draw(batch, parentAlpha)
    }

    private fun checkDead() {
        var i = 0
        while (i < curShown.size) {
            val cur = curShown[i]
            if (cur.offsetY >= cur.height) {
                cur.isVisible = false
                curShown.remove(cur)
                continue
            }
            i++
        }
        curDeadTimers.filter { it.value < System.currentTimeMillis() }.forEach { addFading(it.key) }
    }

    private fun addFading(target: CustomFlexBox) {
        curDeadTimers.remove(target)
        deadTimeline.appendAction(Timeline.timeline {
            val action =
                CustomMoveByAction(target, Interpolation.exp5In, relX = -target.offsetX, duration = 200F)
            action {
                target.addAction(action)
            }
            delayUntil { action.isComplete }
            action {
                curShown.remove(target)
                target.remove()
                remove(target.styleManager!!.node)
            }
        }.asAction())
    }

    enum class Severity {
        LOW {
            override fun getSymbol(): String = "i"

            override fun getBackground(): String = "forty_white_texture"

            override fun getColor(screen: OnjScreen): Color = Color.valueOf("2A2424")  // dark_brown from color.onj
        },
        MIDDLE {
            override fun getSymbol(): String = "!"

            override fun getBackground(): String = "warning_label_background_red"

            override fun getColor(screen: OnjScreen): Color = Color.valueOf("F0EADD")
            // forty_white from color.onj hardcoded, because IDK if I should access directly and read the files
        },
        HIGH {
            override fun getSymbol(): String = "!!!"

            override fun getBackground(): String = MIDDLE.getBackground()

            override fun getColor(screen: OnjScreen): Color = MIDDLE.getColor(screen)
        };

        abstract fun getSymbol(): String
        abstract fun getBackground(): String
        abstract fun getColor(screen: OnjScreen): Color
    }


    @Suppress("MemberVisibilityCanBePrivate")
    fun addWarning(
        screen: OnjScreen,
        title: String,
        body: String,
        severity: Severity = Severity.MIDDLE,
        width: YogaValue = YogaValue(100F, YogaUnit.PERCENT),
    ) {
        if (!(limits[title] == null || curLimits[title] == null || curLimits[title]!!.count { it > System.currentTimeMillis() } < limits[title]!!)) return

        if (curLimits[title] == null) curLimits[title] = mutableListOf()
        else curLimits[title]!!.removeIf { it <= System.currentTimeMillis() }
        curLimits[title]!!.add(System.currentTimeMillis() + TIME_BETWEEN_LIMIT)

        val data = mapOf(
            "symbol" to OnjString(severity.getSymbol()),
            "title" to OnjString(title),
            "body" to OnjString(body),
            "background" to OnjString(severity.getBackground()),
            "width" to width.value.toOnjYoga(width.unit),
            "color" to OnjColor(severity.getColor(screen)),
        )
        val current =
            screen.screenBuilder.generateFromTemplate("warning_label_template", data, this, screen) as CustomFlexBox
        timeline.appendAction(getAddingTimeline(current, title, body).asAction())
    }

    private fun getAddingTimeline(
        current: CustomFlexBox,
        title: String,
        body: String
    ) = Timeline.timeline {
        val heights = arrayOf(0F)
        delayUntil { //due to layout the height changes even after it is zero, that why this code waits
            val old = heights[0]
            heights[0] = current.height
            current.height != 0F && old == current.height
        }
        action {
            addMarginBottom(current)
            curShown.add(current)
            parallelActions(
                getMoveRightTimeline(current, title.length * 2 + body.length).asAction(),
                ParallelTimelineAction(curShown.map { moveUpTimeline(it, current.height).asAction() })
            )
        }
    }

    private fun addMarginBottom(current: CustomFlexBox) { //TODO ugly
        val label = ((current.children[1] as CustomFlexBox).children[2] as CustomLabel)
        current.height += label.glyphLayout.runs.first().glyphs[0].height * label.fontScaleX
    }

    private fun moveUpTimeline(target: CustomFlexBox, height: Float): Timeline {
        return Timeline.timeline {
            val action =
                CustomMoveByAction(target, Interpolation.linear, relY = height + DIST_BETWEEN_BLOCKS, duration = 200F)
            action {
                target.addAction(action)
            }
            delayUntil { action.isComplete }
        }
    }

    private fun getMoveRightTimeline(target: CustomFlexBox, textLength: Int): Timeline {
        return Timeline.timeline {
            action {
                target.offsetY = -height - DIST_BETWEEN_BLOCKS
            }
            val action = CustomMoveByAction(target, Interpolation.exp10Out, relX = target.width, duration = 200F)
            action {
                target.addAction(action)
                curDeadTimers[target] =
                    System.currentTimeMillis() + INITIAL_DISPLAYED_TIME + ADDITIONAL_DISPLAYED_TIME_PER_CHAR * textLength
            }
            delayUntil { action.isComplete }
        }
    }

    companion object {
        fun getWarning(screen: OnjScreen): CustomWarningParent { //name from template
            return screen.namedActorOrError("WARNING_PARENT") as CustomWarningParent
        }

        private const val ADDITIONAL_DISPLAYED_TIME_PER_CHAR = 60 //TODO in settings change this option

        private const val INITIAL_DISPLAYED_TIME = 1000

        private const val DIST_BETWEEN_BLOCKS = 5

        private const val TIME_BETWEEN_LIMIT = 5000
    }
}