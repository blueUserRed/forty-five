package com.fourinachamber.fourtyfive.experimental

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.fourinachamber.fourtyfive.screen.general.CustomImageActor
import com.fourinachamber.fourtyfive.screen.general.CustomLabel
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import dev.lyze.flexbox.FlexBox
import io.github.orioncraftmc.meditate.YogaNode
import io.github.orioncraftmc.meditate.enums.YogaAlign
import io.github.orioncraftmc.meditate.enums.YogaFlexDirection
import io.github.orioncraftmc.meditate.enums.YogaJustify
import io.github.orioncraftmc.meditate.enums.YogaUnit


abstract class StyleProperty(val condition: StyleCondition?) {

    abstract fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget)

    abstract fun getWithCondition(condition: StyleCondition?): StyleProperty

}

class BackgroundProperty(
    private val backgroundName: String?,
    condition: StyleCondition?
) : StyleProperty(condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) = when (actor) {
        is CustomImageActor -> actor.drawable = backgroundName?.let {
            screen.drawableOrError(backgroundName)
        }
        is CustomLabel -> actor.background = backgroundName?.let {
            screen.drawableOrError(backgroundName)
        }
        else -> throw RuntimeException(
            "background property cannot be applied to ${actor::class.simpleName}"
        )
    }

    override fun getWithCondition(condition: StyleCondition?) = BackgroundProperty(backgroundName, condition)
}

class TextAlignProperty(
    private val align: Int,
    condition: StyleCondition?
) : StyleProperty(condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) = when (actor) {
        is CustomLabel -> actor.setAlignment(align)
        else -> throw RuntimeException(
            "textAlign property cannot be applied to ${actor::class.simpleName}"
        )
    }

    override fun getWithCondition(condition: StyleCondition?): StyleProperty = TextAlignProperty(align, condition)
}

class TextColorProperty(
    private val color: Color,
    condition: StyleCondition?
) : StyleProperty(condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) = when (actor) {
        is CustomLabel -> actor.style.fontColor = color
        else -> throw RuntimeException(
            "textColor property cannot be applied to ${actor::class.simpleName}"
        )
    }

    override fun getWithCondition(condition: StyleCondition?): StyleProperty = TextColorProperty(color, condition)
}

class FontScaleProperty(
    private val scale: Float,
    condition: StyleCondition?
) : StyleProperty(condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) = when (actor) {
        is CustomLabel -> actor.setFontScale(scale)
        else -> throw RuntimeException(
            "fontScale property cannot be applied to ${actor::class.simpleName}"
        )
    }.let {
        actor.invalidateHierarchy()
    }

    override fun getWithCondition(condition: StyleCondition?): StyleProperty = FontScaleProperty(scale, condition)
}

class FontScaleAnimationProperty(
    val duration: Int,
    val interpolation: Interpolation,
    val value: Float,
    condition: StyleCondition?
) : StyleProperty(condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) {
        if (actor !is CustomLabel) {
            throw RuntimeException("FontScaleAnimation property cannot be applied to ${actor::class.simpleName} ")
        }
        val startValue = actor.fontScaleX
        val animation = StyleAnimation(
            duration,
            -1,
            interpolation
        ) { percent, _, _ ->
            val value = startValue + (value - startValue) * percent
            actor.setFontScale(value)
            actor.invalidateHierarchy()
        }
        target.addAnimation(animation)
    }

    override fun getWithCondition(condition: StyleCondition?): StyleProperty {
        return FontScaleAnimationProperty(duration, interpolation, value, condition)
    }
}

class DimensionsProperty(
    private val width: Float?,
    private val height: Float?,
    private val widthRelative: Boolean,
    private val heightRelative: Boolean,
    private val widthAuto: Boolean,
    private val heightAuto: Boolean,
    condition: StyleCondition?
) : StyleProperty(condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) {
        if (widthAuto) node.setWidthAuto()
        if (heightAuto) node.setHeightAuto()
        if (width != null) {
            if (widthRelative) node.setWidthPercent(width)
            else node.setWidth(width)
        }
        if (height != null) {
            if (heightRelative) node.setHeightPercent(height)
            else node.setHeight(height)
        }
        if (actor is Layout) actor.invalidateHierarchy()
    }

    override fun getWithCondition(condition: StyleCondition?): StyleProperty {
        return DimensionsProperty(width, height, widthRelative, heightRelative, widthAuto, heightAuto, condition)
    }
}

class DimensionsAnimationProperty(
    private val value: Float,
    private val isWidth: Boolean,
    private val isRelative: Boolean,
    private val duration: Int,
    private val interpolation: Interpolation,
    condition: StyleCondition?
) : StyleProperty(condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) {
        val startValue = if (isWidth) node.width.value else node.height.value
        val unitToTest = if (isWidth) node.width.unit else node.height.unit

        if (unitToTest == YogaUnit.UNDEFINED || unitToTest == YogaUnit.AUTO) {
            if (isWidth) {
                if (isRelative) node.setWidthPercent(value)
                else node.setWidth(value)
            } else {
                if (isRelative) node.setHeightPercent(value)
                else node.setHeight(value)
            }
            if (actor is Layout) actor.invalidateHierarchy()
            return
        }

        if (isRelative && unitToTest != YogaUnit.PERCENT) {
            FourtyFiveLogger.medium("style", "DimensionAnimation property on actor '$actor' animates using" +
                    "a relative value, but the currently set value is not relative")
        }
        if (!isRelative && unitToTest != YogaUnit.POINT) {
            FourtyFiveLogger.medium("style", "DimensionAnimation property on actor '$actor' animates using" +
                    "an absolute value, but the currently set value is not absolute")
        }

        val animation = StyleAnimation(
            duration,
            -1,
            interpolation
        ) { percent, _, animNode ->
            val value = startValue + (value - startValue) * percent
            if (isWidth) {
                if (isRelative) animNode.setWidthPercent(value)
                else animNode.setWidth(value)
            } else {
                if (isRelative) animNode.setHeightPercent(value)
                else animNode.setHeight(value)
            }
            if (actor is Layout) actor.invalidateHierarchy()
        }
        target.addAnimation(animation)
    }

    override fun getWithCondition(condition: StyleCondition?): StyleProperty {
        return DimensionsAnimationProperty(value, isWidth, isRelative, duration, interpolation, condition)
    }
}

class FlexDirectionProperty(
    val direction: YogaFlexDirection,
    condition: StyleCondition?
) : StyleProperty(condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) {
        node.flexDirection = direction
        if (actor is Layout) actor.invalidateHierarchy()
    }

    override fun getWithCondition(condition: StyleCondition?): StyleProperty =
        FlexDirectionProperty(direction, condition)
}

class FlexAlignProperty(
    val flexAlign: YogaAlign,
    val isItems: Boolean,
    val isContent: Boolean,
    val isSelf: Boolean,
    condition: StyleCondition?
) : StyleProperty(condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) {
        if (isItems) node.alignItems = flexAlign
        if (isContent) node.alignContent = flexAlign
        if (isSelf) node.alignSelf = flexAlign
        if (actor is Layout) actor.invalidateHierarchy()
    }

    override fun getWithCondition(condition: StyleCondition?): StyleProperty =
        FlexAlignProperty(flexAlign, isItems, isContent, isSelf, condition)
}

class FlexJustifyContentProperty(
    val justify: YogaJustify,
    condition: StyleCondition?
) : StyleProperty(condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) {
        node.justifyContent = justify
        println(node.childCount)
        if (actor is FlexBox) println(actor.children.size)
        if (actor is Layout) actor.invalidateHierarchy()
    }

    override fun getWithCondition(condition: StyleCondition?): StyleProperty =
        FlexJustifyContentProperty(justify, condition)
}
