package com.fourinachamber.fourtyfive.screen.general.styleTest

import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fourtyfive.screen.general.CustomFlexBox
import com.fourinachamber.fourtyfive.screen.general.CustomLabel
import com.fourinachamber.fourtyfive.screen.general.OnjScreen
import io.github.orioncraftmc.meditate.YogaNode
import io.github.orioncraftmc.meditate.YogaValue
import io.github.orioncraftmc.meditate.enums.YogaAlign
import io.github.orioncraftmc.meditate.enums.YogaEdge
import io.github.orioncraftmc.meditate.enums.YogaFlexDirection
import io.github.orioncraftmc.meditate.enums.YogaJustify
import io.github.orioncraftmc.meditate.enums.YogaPositionType
import io.github.orioncraftmc.meditate.enums.YogaUnit

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Actor
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

class VisibleStyleProperty<T>(
    target: T,
    node: YogaNode,
    screen: OnjScreen
) : StyleProperty<T, Boolean>(
    "visible",
    target,
    node,
    true,
    Boolean::class,
    false,
    false,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: Boolean) {
        target.isVisible = data
    }

    override fun get(): Boolean = target.isVisible
}

class WidthStyleProperty<T>(
    target: T,
    node: YogaNode,
    screen: OnjScreen
) : StyleProperty<T, YogaValue>(
    "width",
    target,
    node,
    YogaValue.parse("undefined"),
    YogaValue::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: YogaValue) {
        when (data.unit) {
            YogaUnit.AUTO -> node.setWidthAuto()
            YogaUnit.POINT -> node.setWidth(data.value)
            YogaUnit.PERCENT -> node.setWidthPercent(data.value)
            else -> {}
        }
    }

    override fun get(): YogaValue = node.width
}

class HeightStyleProperty<T>(
    target: T,
    node: YogaNode,
    screen: OnjScreen
) : StyleProperty<T, YogaValue>(
    "height",
    target,
    node,
    YogaValue.parse("undefined"),
    YogaValue::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: YogaValue) {
        when (data.unit) {
            YogaUnit.AUTO -> node.setHeightAuto()
            YogaUnit.POINT -> node.setHeight(data.value)
            YogaUnit.PERCENT -> node.setHeightPercent(data.value)
            else -> {}
        }
    }

    override fun get(): YogaValue = node.height
}

class MarginStyleProperty<T>(
    target: T,
    node: YogaNode,
    private val edge: YogaEdge,
    name: String,
    screen: OnjScreen
) : StyleProperty<T, YogaValue>(
    name,
    target,
    node,
    YogaValue.parse("undefined"),
    YogaValue::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: YogaValue) {
        when (data.unit) {
            YogaUnit.AUTO -> node.setMarginAuto(edge)
            YogaUnit.POINT -> node.setMargin(edge, data.value)
            YogaUnit.PERCENT -> node.setMarginPercent(edge, data.value)
            else -> {}
        }
    }

    override fun get(): YogaValue = node.getMargin(edge)
}

class PositionStyleProperty<T>(
    target: T,
    node: YogaNode,
    private val edge: YogaEdge,
    name: String,
    screen: OnjScreen
) : StyleProperty<T, YogaValue>(
    name,
    target,
    node,
    YogaValue.parse("undefined"),
    YogaValue::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: YogaValue) {
        when (data.unit) {
            YogaUnit.AUTO -> node.setPosition(edge, 0f)
            YogaUnit.POINT -> node.setPosition(edge, data.value)
            YogaUnit.PERCENT -> node.setPositionPercent(edge, data.value)
            else -> {}
        }
    }

    override fun get(): YogaValue = node.getPosition(edge)
}

class FlexGrowStyleProperty<T>(
    target: T,
    node: YogaNode,
    screen: OnjScreen
) : StyleProperty<T, Float>(
    "flexGrow",
    target,
    node,
    0f,
    Float::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: Float) {
        node.flexGrow = data
    }

    override fun get(): Float = node.flexGrow
}

class PositionTypeStyleProperty<T>(
    target: T,
    node: YogaNode,
    screen: OnjScreen
) : StyleProperty<T, YogaPositionType>(
    "positionType",
    target,
    node,
    YogaPositionType.RELATIVE,
    YogaPositionType::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: YogaPositionType) {
        node.positionType = data
    }

    override fun get(): YogaPositionType = node.positionType
}

class AlignSelfStyleProperty<T>(
    target: T,
    node: YogaNode,
    screen: OnjScreen
) : StyleProperty<T, YogaAlign>(
    "alignSelf",
    target,
    node,
    YogaAlign.AUTO,
    YogaAlign::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: YogaAlign) {
        node.alignSelf = data
    }

    override fun get(): YogaAlign = node.alignSelf
}


class AspectRatioStyleProperty<T>(
    target: T,
    node: YogaNode,
    screen: OnjScreen
) : StyleProperty<T, Float>(
    "aspectRatio",
    target,
    node,
    0f,
    Float::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: Float) {
        node.aspectRatio = data
    }

    override fun get(): Float = node.aspectRatio
}

fun <T> T.addActorStyles(node: YogaNode, screen: OnjScreen) where T : Actor, T : StyledActor {
    styleManager.addStyleProperty(VisibleStyleProperty(this, node, screen))
    styleManager.addStyleProperty(WidthStyleProperty(this, node, screen))
    styleManager.addStyleProperty(HeightStyleProperty(this, node, screen))
    styleManager.addStyleProperty(FlexGrowStyleProperty(this, node, screen))
    styleManager.addStyleProperty(PositionTypeStyleProperty(this, node, screen))
    styleManager.addStyleProperty(AlignSelfStyleProperty(this, node, screen))
    styleManager.addStyleProperty(AspectRatioStyleProperty(this, node, screen))
    styleManager.addStyleProperty(MarginStyleProperty(this, node, YogaEdge.TOP, "marginTop", screen))
    styleManager.addStyleProperty(MarginStyleProperty(this, node, YogaEdge.BOTTOM, "marginBottom", screen))
    styleManager.addStyleProperty(MarginStyleProperty(this, node, YogaEdge.LEFT, "marginLeft", screen))
    styleManager.addStyleProperty(MarginStyleProperty(this, node, YogaEdge.RIGHT, "marginRight", screen))
    styleManager.addStyleProperty(PositionStyleProperty(this, node, YogaEdge.TOP, "positionTop", screen))
    styleManager.addStyleProperty(PositionStyleProperty(this, node, YogaEdge.BOTTOM, "positionBottom", screen))
    styleManager.addStyleProperty(PositionStyleProperty(this, node, YogaEdge.LEFT, "positionLeft", screen))
    styleManager.addStyleProperty(PositionStyleProperty(this, node, YogaEdge.RIGHT, "positionRight", screen))
}


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Label
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

class FontScaleStyleProperty(
    target: CustomLabel,
    node: YogaNode,
    screen: OnjScreen
) : StyleProperty<CustomLabel, Float>(
    "fontScale",
    target,
    node,
    1f,
    Float::class,
    false,
    true,
    screen
) {

    override fun set(data: Float) {
        target.setFontScale(data)
    }

    override fun get(): Float = target.fontScaleX
}

fun <T> T.addLabelStyles(node: YogaNode, screen: OnjScreen) where T : CustomLabel, T : StyledActor {
    addActorStyles(node, screen)
    styleManager.addStyleProperty(FontScaleStyleProperty(this, node, screen))
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// FlexBox
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

class FlexDirectionStyleProperty(
    target: CustomFlexBox,
    node: YogaNode,
    screen: OnjScreen
) : StyleProperty<CustomFlexBox, YogaFlexDirection>(
    "flexDirection",
    target,
    node,
    YogaFlexDirection.ROW,
    YogaFlexDirection::class,
    false,
    true,
    screen
) {

    override fun set(data: YogaFlexDirection) {
        target.root.flexDirection = data
    }

    override fun get(): YogaFlexDirection = target.root.flexDirection
}

class AlignItemsStyleProperty(
    target: CustomFlexBox,
    node: YogaNode,
    screen: OnjScreen
) : StyleProperty<CustomFlexBox, YogaAlign>(
    "alignItems",
    target,
    node,
    YogaAlign.AUTO,
    YogaAlign::class,
    false,
    true,
    screen
) {

    override fun set(data: YogaAlign) {
        target.root.alignItems = data
    }

    override fun get(): YogaAlign = target.root.alignItems
}

class JustifyContentStyleProperty(
    target: CustomFlexBox,
    node: YogaNode,
    screen: OnjScreen
) : StyleProperty<CustomFlexBox, YogaJustify>(
    "justifyContent",
    target,
    node,
    YogaJustify.FLEX_START,
    YogaJustify::class,
    false,
    true,
    screen
) {

    override fun set(data: YogaJustify) {
        target.root.justifyContent = data
    }

    override fun get(): YogaJustify = target.root.justifyContent
}

fun <T> T.addFlexBoxStyles(node: YogaNode, screen: OnjScreen) where T : CustomFlexBox, T : StyledActor {
    addActorStyles(node, screen)
    styleManager.addStyleProperty(FlexDirectionStyleProperty(this, node, screen))
    styleManager.addStyleProperty(AlignItemsStyleProperty(this, node, screen))
    styleManager.addStyleProperty(JustifyContentStyleProperty(this, node, screen))
}
