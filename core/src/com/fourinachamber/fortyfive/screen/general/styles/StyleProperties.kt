package com.fourinachamber.fortyfive.screen.general.styles

import com.badlogic.gdx.scenes.scene2d.Actor
import com.fourinachamber.fortyfive.screen.general.*
import io.github.orioncraftmc.meditate.YogaNode
import io.github.orioncraftmc.meditate.YogaValue
import io.github.orioncraftmc.meditate.enums.*

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Actor
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

class VisibleStyleProperty<T>(
    target: T,
    screen: OnjScreen
) : StyleProperty<T, Boolean>(
    "visible",
    target,
    true,
    Boolean::class,
    false,
    false,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: Boolean, node: YogaNode) {
        target.isVisible = data
    }

    override fun get(node: YogaNode): Boolean = target.isVisible
}

class WidthStyleProperty<T>(
    target: T,
    screen: OnjScreen
) : StyleProperty<T, YogaValue>(
    "width",
    target,
    YogaValue.parse("undefined"),
    YogaValue::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: YogaValue, node: YogaNode) {
        when (data.unit) {
            YogaUnit.AUTO -> node.setWidthAuto()
            YogaUnit.POINT -> node.setWidth(data.value)
            YogaUnit.PERCENT -> node.setWidthPercent(data.value)
            else -> {}
        }
    }

    override fun get(node: YogaNode): YogaValue = node.width
}

class MinWidthStyleProperty<T>(
    target: T,
    screen: OnjScreen
) : StyleProperty<T, YogaValue>(
    "minWidth",
    target,
    YogaValue.parse("undefined"),
    YogaValue::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: YogaValue, node: YogaNode) {
        when (data.unit) {
            YogaUnit.POINT -> node.setMinWidth(data.value)
            YogaUnit.PERCENT -> node.setMinWidthPercent(data.value)
            else -> {}
        }
    }

    override fun get(node: YogaNode): YogaValue = node.width
}

class HeightStyleProperty<T>(
    target: T,
    screen: OnjScreen
) : StyleProperty<T, YogaValue>(
    "height",
    target,
    YogaValue.parse("undefined"),
    YogaValue::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: YogaValue, node: YogaNode) {
        when (data.unit) {
            YogaUnit.AUTO -> node.setHeightAuto()
            YogaUnit.POINT -> node.setHeight(data.value)
            YogaUnit.PERCENT -> node.setHeightPercent(data.value)
            else -> {}
        }
    }

    override fun get(node: YogaNode): YogaValue = node.height
}

class MinHeightStyleProperty<T>(
    target: T,
    screen: OnjScreen
) : StyleProperty<T, YogaValue>(
    "minHeight",
    target,
    YogaValue.parse("undefined"),
    YogaValue::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: YogaValue, node: YogaNode) {
        when (data.unit) {
            YogaUnit.POINT -> node.setMinHeight(data.value)
            YogaUnit.PERCENT -> node.setMinHeightPercent(data.value)
            else -> {}
        }
    }

    override fun get(node: YogaNode): YogaValue = node.height
}

class MarginStyleProperty<T>(
    target: T,
    private val edge: YogaEdge,
    name: String,
    screen: OnjScreen
) : StyleProperty<T, YogaValue>(
    name,
    target,
    YogaValue.parse("undefined"),
    YogaValue::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: YogaValue, node: YogaNode) {
        when (data.unit) {
            YogaUnit.AUTO -> node.setMarginAuto(edge)
            YogaUnit.POINT -> node.setMargin(edge, data.value)
            YogaUnit.PERCENT -> node.setMarginPercent(edge, data.value)
            else -> {}
        }
    }

    override fun get(node: YogaNode): YogaValue = node.getMargin(edge)
}

class PositionStyleProperty<T>(
    target: T,
    private val edge: YogaEdge,
    name: String,
    screen: OnjScreen
) : StyleProperty<T, YogaValue>(
    name,
    target,
    YogaValue.parse("undefined"),
    YogaValue::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: YogaValue, node: YogaNode) {
        when (data.unit) {
            YogaUnit.AUTO -> node.setPosition(edge, 0f)
            YogaUnit.POINT -> node.setPosition(edge, data.value)
            YogaUnit.PERCENT -> node.setPositionPercent(edge, data.value)
            else -> {}
        }
    }

    override fun get(node: YogaNode): YogaValue = node.getPosition(edge)
}

class FlexGrowStyleProperty<T>(
    target: T,
    screen: OnjScreen
) : StyleProperty<T, Float>(
    "flexGrow",
    target,
    0f,
    Float::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: Float, node: YogaNode) {
        node.flexGrow = data
    }

    override fun get(node: YogaNode): Float = node.flexGrow
}

class PositionTypeStyleProperty<T>(
    target: T,
    screen: OnjScreen
) : StyleProperty<T, YogaPositionType>(
    "positionType",
    target,
    YogaPositionType.RELATIVE,
    YogaPositionType::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: YogaPositionType, node: YogaNode) {
        node.positionType = data
    }

    override fun get(node: YogaNode): YogaPositionType = node.positionType
}

class AlignSelfStyleProperty<T>(
    target: T,
    screen: OnjScreen
) : StyleProperty<T, YogaAlign>(
    "alignSelf",
    target,
    YogaAlign.AUTO,
    YogaAlign::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: YogaAlign, node: YogaNode) {
        node.alignSelf = data
    }

    override fun get(node: YogaNode): YogaAlign = node.alignSelf
}


class AspectRatioStyleProperty<T>(
    target: T,
    screen: OnjScreen
) : StyleProperty<T, Float>(
    "aspectRatio",
    target,
    0f,
    Float::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor {

    override fun set(data: Float, node: YogaNode) {
        node.aspectRatio = data
    }

    override fun get(node: YogaNode): Float = node.aspectRatio
}

fun <T> T.addActorStyles(screen: OnjScreen) where T : Actor, T : StyledActor {
    styleManager.addStyleProperty(VisibleStyleProperty(this, screen))
    styleManager.addStyleProperty(WidthStyleProperty(this, screen))
    styleManager.addStyleProperty(MinWidthStyleProperty(this, screen))
    styleManager.addStyleProperty(HeightStyleProperty(this, screen))
    styleManager.addStyleProperty(MinHeightStyleProperty(this, screen))
    styleManager.addStyleProperty(FlexGrowStyleProperty(this, screen))
    styleManager.addStyleProperty(PositionTypeStyleProperty(this, screen))
    styleManager.addStyleProperty(AlignSelfStyleProperty(this, screen))
    styleManager.addStyleProperty(AspectRatioStyleProperty(this, screen))
    styleManager.addStyleProperty(MarginStyleProperty(this, YogaEdge.TOP, "marginTop", screen))
    styleManager.addStyleProperty(MarginStyleProperty(this, YogaEdge.BOTTOM, "marginBottom", screen))
    styleManager.addStyleProperty(MarginStyleProperty(this, YogaEdge.LEFT, "marginLeft", screen))
    styleManager.addStyleProperty(MarginStyleProperty(this, YogaEdge.RIGHT, "marginRight", screen))
    styleManager.addStyleProperty(PositionStyleProperty(this, YogaEdge.TOP, "positionTop", screen))
    styleManager.addStyleProperty(PositionStyleProperty(this, YogaEdge.BOTTOM, "positionBottom", screen))
    styleManager.addStyleProperty(PositionStyleProperty(this, YogaEdge.LEFT, "positionLeft", screen))
    styleManager.addStyleProperty(PositionStyleProperty(this, YogaEdge.RIGHT, "positionRight", screen))
}


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// Label
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

class FontScaleStyleProperty(
    target: CustomLabel,
    screen: OnjScreen
) : StyleProperty<CustomLabel, Float>(
    "fontScale",
    target,
    1f,
    Float::class,
    false,
    true,
    screen
) {

    override fun set(data: Float, node: YogaNode) {
        target.setFontScale(data)
    }

    override fun get(node: YogaNode): Float = target.fontScaleX
}

fun <T> T.addLabelStyles(screen: OnjScreen) where T : CustomLabel, T : StyledActor {
    addActorStyles(screen)
    styleManager.addStyleProperty(FontScaleStyleProperty(this, screen))
}

////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// FlexBox
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

class FlexDirectionStyleProperty(
    target: CustomFlexBox,
    screen: OnjScreen
) : StyleProperty<CustomFlexBox, YogaFlexDirection>(
    "flexDirection",
    target,
    YogaFlexDirection.ROW,
    YogaFlexDirection::class,
    false,
    true,
    screen
) {

    override fun set(data: YogaFlexDirection, node: YogaNode) {
        target.root.flexDirection = data
    }

    override fun get(node: YogaNode): YogaFlexDirection = target.root.flexDirection
}

class AlignItemsStyleProperty(
    target: CustomFlexBox,
    screen: OnjScreen
) : StyleProperty<CustomFlexBox, YogaAlign>(
    "alignItems",
    target,
    YogaAlign.AUTO,
    YogaAlign::class,
    false,
    true,
    screen
) {

    override fun set(data: YogaAlign, node: YogaNode) {
        target.root.alignItems = data
    }

    override fun get(node: YogaNode): YogaAlign = target.root.alignItems
}

class JustifyContentStyleProperty(
    target: CustomFlexBox,
    screen: OnjScreen
) : StyleProperty<CustomFlexBox, YogaJustify>(
    "justifyContent",
    target,
    YogaJustify.FLEX_START,
    YogaJustify::class,
    false,
    true,
    screen
) {

    override fun set(data: YogaJustify, node: YogaNode) {
        target.root.justifyContent = data
    }

    override fun get(node: YogaNode): YogaJustify = target.root.justifyContent
}

class PaddingStyleProperty(
    target: CustomFlexBox,
    screen: OnjScreen,
    private val edge: YogaEdge,
    name: String
) : StyleProperty<CustomFlexBox, YogaValue>(
    name,
    target,
    YogaValue.parse("auto"),
    YogaValue::class,
    false,
    true,
    screen
) {

    override fun set(data: YogaValue, node: YogaNode): Unit = run { when (data.unit) {

        YogaUnit.POINT -> target.root.setPadding(edge, data.value)
        YogaUnit.PERCENT -> target.root.setPaddingPercent(edge, data.value)

        else -> { }

    } }

    override fun get(node: YogaNode): YogaValue = target.root.getPadding(edge)
}

class FlexWrapStyleProperty(
    target: CustomFlexBox,
    screen: OnjScreen,
) : StyleProperty<CustomFlexBox, YogaWrap>(
    "flexWrap",
    target,
    YogaWrap.NO_WRAP,
    YogaWrap::class,
    false,
    true,
    screen
) {

    override fun set(data: YogaWrap, node: YogaNode) {
        target.root.wrap = data
    }

    override fun get(node: YogaNode): YogaWrap = target.root.wrap
}

fun <T> T.addFlexBoxStyles(screen: OnjScreen) where T : CustomFlexBox, T : StyledActor {
    addActorStyles(screen)
    styleManager.addStyleProperty(FlexDirectionStyleProperty(this, screen))
    styleManager.addStyleProperty(AlignItemsStyleProperty(this, screen))
    styleManager.addStyleProperty(JustifyContentStyleProperty(this, screen))
    styleManager.addStyleProperty(FlexWrapStyleProperty(this, screen))
    styleManager.addStyleProperty(PaddingStyleProperty(this, screen, YogaEdge.LEFT, "paddingLeft"))
    styleManager.addStyleProperty(PaddingStyleProperty(this, screen, YogaEdge.RIGHT, "paddingRight"))
    styleManager.addStyleProperty(PaddingStyleProperty(this, screen, YogaEdge.TOP, "paddingTop"))
    styleManager.addStyleProperty(PaddingStyleProperty(this, screen, YogaEdge.BOTTOM, "paddingBottom"))
    styleManager.addStyleProperty(PaddingStyleProperty(this, screen, YogaEdge.ALL, "padding"))
}


////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
// other
////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

class BackgroundStyleProperty<T>(
    target: T,
    screen: OnjScreen
) : StyleProperty<T, String>(
    "background",
    target,
    nullHandle,
    String::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor, T : BackgroundActor {

    override fun set(data: String, node: YogaNode) {
        target.backgroundHandle = if (data == nullHandle) null else data
    }

    override fun get(node: YogaNode): String = target.backgroundHandle ?: nullHandle

    companion object {
        const val nullHandle = "%%--null%%--" // lets hope nobody names their texture this
    }

}

class DetachStyleProperty<T>(
    target: T,
    screen: OnjScreen
) : StyleProperty<T, Boolean>(
    "detached",
    target,
    false,
    Boolean::class,
    false,
    true,
    screen
) where T : Actor, T : StyledActor, T : Detachable {

    override fun set(data: Boolean, node: YogaNode) {
        if (data && target.attached) target.detach()
        else if (!data && !target.attached) target.reattach()
    }

    override fun get(node: YogaNode): Boolean = !target.attached

}

fun <T> T.addDetachableStyles(screen: OnjScreen) where T : Actor, T : StyledActor, T : Detachable {
    styleManager.addStyleProperty(DetachStyleProperty(this, screen))
}

fun <T> T.addBackgroundStyles(
    screen: OnjScreen
) where T : Actor, T : StyledActor, T : BackgroundActor {
    styleManager.addStyleProperty(BackgroundStyleProperty(this, screen))
}
