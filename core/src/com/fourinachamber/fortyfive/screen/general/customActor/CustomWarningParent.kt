package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fortyfive.onjNamespaces.OnjColor
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.CustomLabel
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.styles.StyleCondition
import com.fourinachamber.fortyfive.screen.general.styles.StyleInstruction
import com.fourinachamber.fortyfive.utils.Timeline
import com.fourinachamber.fortyfive.utils.toOnjYoga
import io.github.orioncraftmc.meditate.YogaValue
import io.github.orioncraftmc.meditate.enums.YogaUnit
import onj.value.OnjString
import kotlin.math.min

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

    private val permanentsShown: MutableMap<Int, CustomFlexBox> = mutableMapOf()

    private var percentageOfNewestHeight: Float = 0F
    fun setLimit(title: String, limit: Long) {
        limits[title] = limit
    }

    init {
        timeline.startTimeline()
        deadTimeline.startTimeline()
    }


    override fun draw(batch: Batch?, parentAlpha: Float) {
        timeline.updateTimeline()
        deadTimeline.updateTimeline()
        checkDead()
        validate()
        setHeightPositions()
        super.draw(batch, parentAlpha)
    }

    private fun setHeightPositions() {
        if (curShown.isEmpty()) return
        val startY = 0F
        var curHeight = -curShown.last().height * (1 - percentageOfNewestHeight)
        for (counter in 0 until curShown.size) {
            val i = curShown.size - counter - 1
            curShown[i].y = startY + curHeight
            curHeight += curShown[i].height
        }
    }

    private fun checkDead() {
        var i = 0
        while (i < curShown.size) {
            val cur = curShown[i]
            if (cur.offsetY >= cur.height && cur !in permanentsShown.values) {
                cur.isVisible = false
                curShown.remove(cur)
                continue
            }
            i++
        }
        curDeadTimers.filter { it.value <= System.currentTimeMillis() }.forEach { addFading(it.key) }
    }

    fun removeWarningByClick(actor: Actor) {
        curDeadTimers[actor as CustomFlexBox] = System.currentTimeMillis() - 10
    }

    private fun addFading(target: CustomFlexBox) {
        curDeadTimers.remove(target)
        deadTimeline.appendAction(Timeline.timeline {
            val action = CustomMoveByAction(target, Interpolation.exp5In, relX = -target.width, duration = 200F)
            action {
                target.addAction(action)
            }
            delayUntil { action.isComplete }
            action {
                curShown.remove(target)
                val perma = permanentsShown.entries.find { it.value == target }?.key
                if (perma != null) {
                    permanentsShown.remove(perma)
                }
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
        timeline.appendAction(getAddingTimeline(data, title, body, -1).asAction())
    }

    fun addPermanentWarning(
        screen: OnjScreen,
        title: String,
        body: String,
        severity: Severity = Severity.MIDDLE,
        width: YogaValue = YogaValue(100F, YogaUnit.PERCENT),
    ): Int {
        //TODO make this return one of the displayed limits on this if
        if (!(limits[title] == null || curLimits[title] == null || curLimits[title]!!.count { it > System.currentTimeMillis() } < limits[title]!!)) return -1

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

        val index = (if (permanentsShown.isEmpty()) 0 else permanentsShown.keys.max() + 1)
        //only temporary, this will be overwritten later
        permanentsShown[index] = this

        timeline.appendAction(getAddingTimeline(data, title, body, index).asAction())
        return index
    }

    fun removePermanentWarning(id: Int) {
        permanentsShown[id]?.let {
            curDeadTimers[it] = System.currentTimeMillis()
        }
    }

    private fun getAddingTimeline(
        data: Map<String, *>,
        title: String,
        body: String,
        permanentIndex: Int,
    ) = Timeline.timeline {
        var current: CustomFlexBox? = null
        action {
            current = screen.generateFromTemplate(
                "warning_label_template",
                data,
                this@CustomWarningParent,
                screen
            ) as CustomFlexBox
        }
        val heights = FloatArray(3) //max nbr of frames till it works //TODO VERY ugly
        delayUntil {
            if (current?.height == 0F) return@delayUntil false
            val old = heights.clone()
            for (i in 1 until heights.size) heights[i] = old[i - 1]
            heights[0] = current!!.height
//            current?.invalidate()
            heights.none { it != old[old.size - 1] }
        }
        action {
            curShown.add(current!!)
            includeAction(getInitialTimeline(current!!, title.length * 2 + body.length, permanentIndex).asAction())
        }
    }

    private fun moveUpTimeline(current: CustomFlexBox): Timeline {
        return Timeline.timeline {
            var startTime = 0L
            val duration = 200F
            action {
                percentageOfNewestHeight = 0F
                startTime = System.currentTimeMillis()
            }
            delayUntil {
                percentageOfNewestHeight = min((System.currentTimeMillis() - startTime) / duration, 1F)
                percentageOfNewestHeight == 1F
            }
        }
    }

    private fun getInitialTimeline(target: CustomFlexBox, textLength: Int, permanentIndex: Int): Timeline {
        return Timeline.timeline {
            action {
                target.offsetX = -target.width
                //this might be the ugliest but best possible solution ever //TODO ugly
                val label = ((target.children[1] as CustomFlexBox).children[2] as CustomLabel)
                val lastRowHeight = label.glyphLayout.runs.first().glyphs[0].height * label.fontScaleX
                val data = YogaValue(target.height + DIST_BETWEEN_BLOCKS + lastRowHeight, YogaUnit.POINT)
                val dataClass = data::class
                val instruction = StyleInstruction(data, 10, StyleCondition.Always, dataClass)
                @Suppress("UNCHECKED_CAST")
                instruction as StyleInstruction<Any>
                target.styleManager?.addInstruction("height", instruction, dataClass)
            }
            val action = CustomMoveByAction(
                target,
                Interpolation.exp10Out,
                relX = target.width,
                duration = 200F
            )
            parallelActions(
                moveUpTimeline(target).asAction(),
                Timeline.timeline {
                    action {
                        target.addAction(action)
                        if (permanentIndex == -1) {
                            curDeadTimers[target] =
                                System.currentTimeMillis() + INITIAL_DISPLAYED_TIME + ADDITIONAL_DISPLAYED_TIME_PER_CHAR * textLength
                        } else {
                            permanentsShown[permanentIndex] = target
                        }
                    }
                    delayUntil { action.isComplete }
                }.asAction()
            )
        }
    }

    companion object {
        fun getWarning(screen: OnjScreen): CustomWarningParent { //name from template
            return screen.namedActorOrError("WARNING_PARENT") as CustomWarningParent
        }

        private const val ADDITIONAL_DISPLAYED_TIME_PER_CHAR = 60 //TODO in settings change this option

        private const val INITIAL_DISPLAYED_TIME = 500

        private const val DIST_BETWEEN_BLOCKS = 10

        private const val TIME_BETWEEN_LIMIT = 500
    }
}