package com.fourinachamber.fourtyfive.screen.general.styles

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.badlogic.gdx.scenes.scene2d.Actor
import com.badlogic.gdx.scenes.scene2d.utils.Layout
import com.fourinachamber.fourtyfive.screen.ResourceManager
import com.fourinachamber.fourtyfive.screen.general.CustomFlexBox
import com.fourinachamber.fourtyfive.screen.general.CustomImageActor
import com.fourinachamber.fourtyfive.screen.general.CustomLabel
import com.fourinachamber.fourtyfive.screen.general.StyleableOnjScreen
import com.fourinachamber.fourtyfive.utils.FourtyFiveLogger
import com.fourinachamber.fourtyfive.utils.MainThreadOnly
import dev.lyze.flexbox.FlexBox
import io.github.orioncraftmc.meditate.YogaNode
import io.github.orioncraftmc.meditate.enums.YogaAlign
import io.github.orioncraftmc.meditate.enums.YogaEdge
import io.github.orioncraftmc.meditate.enums.YogaFlexDirection
import io.github.orioncraftmc.meditate.enums.YogaJustify
import io.github.orioncraftmc.meditate.enums.YogaPositionType
import io.github.orioncraftmc.meditate.enums.YogaUnit
import kotlin.reflect.KClass


abstract class StyleProperty<T>(
    private val clazz: KClass<T>,
    val condition: StyleCondition?
) where T : Actor {

    @MainThreadOnly
    fun applyToOrError(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) {
        if (!clazz.isInstance(actor)) throw RuntimeException(
            "style property ${this::class.simpleName} cannot be applied to ${actor::class.simpleName}, must be" +
            " ${clazz.simpleName}"
        )
        @Suppress("UNCHECKED_CAST") // safe because of previous check
        applyTo(node, actor as T, screen, target)
    }

    @MainThreadOnly
    abstract fun applyTo(node: YogaNode, actor: T, screen: StyleableOnjScreen, target: StyleTarget)

    abstract fun getWithCondition(condition: StyleCondition?): StyleProperty<T>

}

class BackgroundProperty(
    private val backgroundName: String?,
    condition: StyleCondition?
) : StyleProperty<Actor>(Actor::class, condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) = when (actor) {
        //TODO: put the background in some interface
        is CustomImageActor -> actor.drawable = backgroundName?.let {
            ResourceManager.get(screen, backgroundName)
        }
        is CustomLabel -> actor.background = backgroundName?.let {
            ResourceManager.get(screen, backgroundName)
        }
        is CustomFlexBox -> actor.background = backgroundName?.let {
            ResourceManager.get(screen, backgroundName)
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
) : StyleProperty<CustomLabel>(CustomLabel::class, condition) {

    override fun applyTo(node: YogaNode, actor: CustomLabel, screen: StyleableOnjScreen, target: StyleTarget) {
        actor.setAlignment(align)
    }

    override fun getWithCondition(condition: StyleCondition?) = TextAlignProperty(align, condition)
}

class TextColorProperty(
    private val color: Color,
    condition: StyleCondition?
) : StyleProperty<CustomLabel>(CustomLabel::class, condition) {

    override fun applyTo(node: YogaNode, actor: CustomLabel, screen: StyleableOnjScreen, target: StyleTarget) {
        actor.style.fontColor = color
    }

    override fun getWithCondition(condition: StyleCondition?) = TextColorProperty(color, condition)
}

class FontScaleProperty(
    private val scale: Float,
    condition: StyleCondition?
) : StyleProperty<CustomLabel>(CustomLabel::class, condition) {

    override fun applyTo(node: YogaNode, actor:CustomLabel, screen: StyleableOnjScreen, target: StyleTarget) {
        actor.setFontScale(scale)
        actor.invalidateHierarchy()
    }

    override fun getWithCondition(condition: StyleCondition?) = FontScaleProperty(scale, condition)
}

class FontScaleAnimationProperty(
    val duration: Int,
    val interpolation: Interpolation,
    val value: Float,
    condition: StyleCondition?
) : StyleProperty<CustomLabel>(CustomLabel::class, condition) {

    override fun applyTo(node: YogaNode, actor: CustomLabel, screen: StyleableOnjScreen, target: StyleTarget) {
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

    override fun getWithCondition(condition: StyleCondition?) =
        FontScaleAnimationProperty(duration, interpolation, value, condition)
}

class DimensionsProperty(
    private val width: Float?,
    private val height: Float?,
    private val widthRelative: Boolean,
    private val heightRelative: Boolean,
    private val widthAuto: Boolean,
    private val heightAuto: Boolean,
    condition: StyleCondition?
) : StyleProperty<Actor>(Actor::class, condition) {

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

    override fun getWithCondition(condition: StyleCondition?) =
        DimensionsProperty(width, height, widthRelative, heightRelative, widthAuto, heightAuto, condition)
}

class DimensionsAnimationProperty(
    private val value: Float,
    private val isWidth: Boolean,
    private val isRelative: Boolean,
    private val duration: Int,
    private val interpolation: Interpolation,
    condition: StyleCondition?
) : StyleProperty<Actor>(Actor::class, condition) {

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

    override fun getWithCondition(condition: StyleCondition?) =
        DimensionsAnimationProperty(value, isWidth, isRelative, duration, interpolation, condition)
}

class FlexDirectionProperty(
    val direction: YogaFlexDirection,
    condition: StyleCondition?
) : StyleProperty<FlexBox>(FlexBox::class, condition) {

    override fun applyTo(node: YogaNode, actor: FlexBox, screen: StyleableOnjScreen, target: StyleTarget) {
        actor.root.flexDirection = direction
        actor.invalidateHierarchy()
    }

    override fun getWithCondition(condition: StyleCondition?) = FlexDirectionProperty(direction, condition)
}

class FlexAlignProperty(
    val flexAlign: YogaAlign,
    val isItems: Boolean,
    val isContent: Boolean,
    condition: StyleCondition?
) : StyleProperty<FlexBox>(FlexBox::class, condition) {

    override fun applyTo(node: YogaNode, actor: FlexBox, screen: StyleableOnjScreen, target: StyleTarget) {
        if (isItems) actor.root.alignItems = flexAlign
        if (isContent) actor.root.alignContent = flexAlign
        actor.invalidateHierarchy()
    }

    override fun getWithCondition(condition: StyleCondition?) =
        FlexAlignProperty(flexAlign, isItems, isContent, condition)
}

class AlignSelfProperty(
    val align: YogaAlign,
    condition: StyleCondition?
) : StyleProperty<Actor>(Actor::class, condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) {
        node.alignSelf = align
        println(align)
        if (actor is Layout) actor.invalidateHierarchy()
    }

    override fun getWithCondition(condition: StyleCondition?) = AlignSelfProperty(align, condition)
}

class FlexJustifyContentProperty(
    val justify: YogaJustify,
    condition: StyleCondition?
) : StyleProperty<FlexBox>(FlexBox::class, condition) {

    override fun applyTo(node: YogaNode, actor: FlexBox, screen: StyleableOnjScreen, target: StyleTarget) {
        actor.root.justifyContent = justify
        actor.invalidateHierarchy()
    }

    override fun getWithCondition(condition: StyleCondition?) = FlexJustifyContentProperty(justify, condition)
}

class MarginProperty(
    val margins: Array<Triple<Float, Boolean, YogaEdge>>, // margin, isRelative, edge
    condition: StyleCondition?
) : StyleProperty<Actor>(Actor::class, condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) {
        for ((margin, isRelative, edge) in margins) {
            if (isRelative) node.setMarginPercent(edge, margin)
            else node.setMargin(edge, margin)
        }
        if (actor is Layout) actor.invalidateHierarchy()
    }

    override fun getWithCondition(condition: StyleCondition?) = MarginProperty(margins, condition)
}

class GrowShrinkProperty(
    val grow: Float?,
    val shrink: Float?,
    condition: StyleCondition?
) : StyleProperty<Actor>(Actor::class, condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) {
        if (grow != null) node.flexGrow = grow
        if (shrink != null) node.flexShrink = shrink
        if (actor is Layout) actor.invalidateHierarchy()
    }

    override fun getWithCondition(condition: StyleCondition?) = GrowShrinkProperty(grow, shrink, condition)
}

class PositionProperty(
    val positionType: YogaPositionType,
    condition: StyleCondition?
) : StyleProperty<Actor>(Actor::class, condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) {
        node.positionType = positionType
        if (actor is Layout) actor.invalidateHierarchy()
    }

    override fun getWithCondition(condition: StyleCondition?) = PositionProperty(positionType, condition)

}

class PositionFloatProperty(
    val left: Float?,
    val right: Float?,
    val top: Float?,
    val bottom: Float?,
    condition: StyleCondition?
) : StyleProperty<Actor>(Actor::class, condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) {
        left?.let { node.setPosition(YogaEdge.LEFT, it) }
        right?.let { node.setPosition(YogaEdge.RIGHT, it) }
        top?.let { node.setPosition(YogaEdge.TOP, it) }
        bottom?.let { node.setPosition(YogaEdge.BOTTOM, it) }
        if (actor is Layout) actor.invalidateHierarchy()
    }

    override fun getWithCondition(condition: StyleCondition?): StyleProperty<Actor> =
        PositionFloatProperty(left, right, top, bottom, condition)
}

class AspectRatioProperty(
    val ratio: Float,
    condition: StyleCondition?
) : StyleProperty<Actor>(Actor::class, condition) {

    override fun applyTo(node: YogaNode, actor: Actor, screen: StyleableOnjScreen, target: StyleTarget) {
        node.aspectRatio = ratio
        if (actor is Layout) actor.invalidateHierarchy()
    }

    override fun getWithCondition(condition: StyleCondition?) = AspectRatioProperty(ratio, condition)
}
