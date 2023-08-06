package com.fourinachamber.fortyfive.screen.general.customActor

import com.badlogic.gdx.graphics.g2d.Batch
import com.badlogic.gdx.math.Vector2
import com.fourinachamber.fortyfive.screen.general.CustomFlexBox
import com.fourinachamber.fortyfive.screen.general.OnjScreen
import com.fourinachamber.fortyfive.screen.general.styles.*
import com.fourinachamber.fortyfive.utils.toOnjYoga
import io.github.orioncraftmc.meditate.YogaValue
import io.github.orioncraftmc.meditate.enums.YogaUnit
import onj.value.OnjString

class CustomWarningParent(screen: OnjScreen) : CustomFlexBox(screen) {

    override fun layout() {
//        addStyleProperty("width", YogaValue(28F, YogaUnit.PERCENT))
//        addStyleProperty("height", YogaValue(100 - Y_POS_PERCENTAGE * 100, YogaUnit.POINT))
        super.layout()
    }

    private fun setOffsetsCorrect() { // this is ugly, but there is no way to fix this because of marvin
//        val pos = localToStageCoordinates(Vector2())
//        offsetX = -pos.x
//        offsetY = -pos.y + stage.viewport.worldHeight * Y_POS_PERCENTAGE
    }


    override fun draw(batch: Batch?, parentAlpha: Float) {
        setOffsetsCorrect()
        super.draw(batch, parentAlpha)
    }

    private fun addStyleProperty(name: String, any: Any) {
        val styleManager = this.styleManager ?: return
        val property = styleManager.styleProperties.find { it.name == name }
        if (property == null || property.instructions.isNotEmpty()) return
        val instruction = StyleInstruction(any, 1, StyleCondition.Always, any::class)
        styleManager.addInstruction(name, instruction, any::class)
        addWarning(screen, "Test", "testing2")
    }

    enum class Severity {
        LOW {
            override fun getSymbol(): String = "i"

            override fun getBackground(): String = "forty_white_texture"
        },
        MIDDLE {
            override fun getSymbol(): String = "!"

            override fun getBackground(): String = "warning_label_background_red"
        },
        HIGH {
            override fun getSymbol(): String = "!!!"

            override fun getBackground(): String = "warning_label_background_red"
        };

        abstract fun getSymbol(): String
        abstract fun getBackground(): String
    }


    @Suppress("MemberVisibilityCanBePrivate")
    fun addWarning(
        screen: OnjScreen,
        title: String,
        body: String,
        severity: Severity = Severity.MIDDLE,
        width: YogaValue = YogaValue(100F, YogaUnit.PERCENT),
    ) {
        val data = mapOf(
            "symbol" to OnjString(severity.getSymbol()),
            "title" to OnjString(title),
            "body" to OnjString(body),
            "background" to OnjString(severity.getBackground()),
            "width" to width.value.toOnjYoga(width.unit),
        )
        val current =
            screen.screenBuilder.generateFromTemplate("warning_label_template", data, this, screen) as CustomFlexBox
        println("sucessfully added /j")
    }

    companion object {
        fun getWarningParent(screen: OnjScreen): CustomWarningParent { //name from template
            return screen.namedActorOrError("WARNING_PARENT") as CustomWarningParent
        }

        private val defaultTimeTillFadePerChar = 10

        private val defaultInitialTimeTillFade = 2000

        private val curShown: MutableList<CustomFlexBox> = mutableListOf()

        const val Y_POS_PERCENTAGE = 0.65F
    }
}