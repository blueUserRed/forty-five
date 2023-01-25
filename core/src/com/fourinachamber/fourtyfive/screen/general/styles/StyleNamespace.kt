package com.fourinachamber.fourtyfive.experimental

import com.badlogic.gdx.utils.Align
import com.fourinachamber.fourtyfive.onjNamespaces.OnjColor
import com.fourinachamber.fourtyfive.onjNamespaces.OnjInterpolation
import com.fourinachamber.fourtyfive.screen.general.styles.*
import io.github.orioncraftmc.meditate.enums.*
import onj.value.OnjValue
import onj.customization.Namespace.OnjNamespace
import onj.customization.Namespace.OnjNamespaceDatatypes
import onj.customization.Namespace.OnjNamespaceVariables
import onj.customization.OnjFunction.RegisterOnjFunction
import onj.customization.OnjFunction.RegisterOnjFunction.OnjFunctionType
import onj.value.OnjFloat
import onj.value.OnjString
import kotlin.reflect.KClass

@Suppress("unused") // functions and properties are red via reflection
@OnjNamespace
object StyleNamespace {

    @OnjNamespaceDatatypes
    val datatypes: Map<String, KClass<*>> = mapOf(
        "StyleProperty" to OnjStyleProperty::class,
        "StyleCondition" to OnjStyleCondition::class,
        "StyleActorRef" to OnjStyleActorRef::class,
    )

    @OnjNamespaceVariables
    val variables: Map<String, OnjValue> = mapOf(
//        "flex_direction" to buildOnjObject {
//            "row" with ""
//        }
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // dimensions
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @RegisterOnjFunction(schema = "params: [float]")
    fun width(width: OnjFloat): OnjStyleProperty {
        return OnjStyleProperty(
            DimensionsProperty(
            width.value.toFloat(), null,
            widthRelative = false,
            heightRelative = false,
            widthAuto = false,
            heightAuto = false,
            condition = null
        )
        )
    }

    @RegisterOnjFunction(schema = "params: [float]")
    fun height(height: OnjFloat): OnjStyleProperty {
        return OnjStyleProperty(
            DimensionsProperty(
            null,
            height.value.toFloat(),
            widthRelative = false,
            heightRelative = false,
            widthAuto = false,
            heightAuto = false,
            condition = null
        )
        )
    }

    @RegisterOnjFunction(schema = "params: []")
    fun widthAuto(): OnjStyleProperty {
        return OnjStyleProperty(
            DimensionsProperty(
            width = null,
            height = null,
            widthRelative = false,
            heightRelative = false,
            widthAuto = true,
            heightAuto = false,
            condition = null
        )
        )
    }

    @RegisterOnjFunction(schema = "params: []")
    fun heightAuto(): OnjStyleProperty {
        return OnjStyleProperty(
            DimensionsProperty(
            width = null,
            height = null,
            widthRelative = false,
            heightRelative = false,
            widthAuto = false,
            heightAuto = true,
            condition = null
        )
        )
    }

    @RegisterOnjFunction(schema = "params: [float]")
    fun relWidth(width: OnjFloat): OnjStyleProperty {
        return OnjStyleProperty(
            DimensionsProperty(
            width.value.toFloat(), null,
            widthRelative = true,
            heightRelative = false,
            widthAuto = false, heightAuto = false,
            condition = null
        )
        )
    }

    @RegisterOnjFunction(schema = "params: [float]")
    fun relHeight(height: OnjFloat): OnjStyleProperty {
        return OnjStyleProperty(
            DimensionsProperty(
            null, height.value.toFloat(),
            widthRelative = false,
            heightRelative = true,
            widthAuto = false,
            heightAuto = false,
            condition = null
        )
        )
    }

    @RegisterOnjFunction(schema = "use Common; params: [float, float, Interpolation]")
    fun relWidthTo(width: OnjFloat, duration: OnjFloat, interpolation: OnjInterpolation): OnjStyleProperty {
        return OnjStyleProperty(
            DimensionsAnimationProperty(
            width.value.toFloat(),
            true,
            true,
            (duration.value * 1000f).toInt(),
            interpolation.value,
            null
        )
        )
    }

    @RegisterOnjFunction(schema = "params: [float]")
    fun aspectRatio(ratio: OnjFloat): OnjStyleProperty {
        return OnjStyleProperty(AspectRatioProperty(ratio.value.toFloat(), null))
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // margins
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @RegisterOnjFunction(schema = "params: [float, float, float, float]")
    fun margin(left: OnjFloat, right: OnjFloat, top: OnjFloat, bottom: OnjFloat): OnjStyleProperty {
        return OnjStyleProperty(
            MarginProperty(
            arrayOf(
                Triple(left.value.toFloat(), false, YogaEdge.LEFT),
                Triple(right.value.toFloat(), false, YogaEdge.RIGHT),
                Triple(top.value.toFloat(), false, YogaEdge.TOP),
                Triple(bottom.value.toFloat(), false, YogaEdge.BOTTOM),
            ),
            null
        )
        )
    }

    @RegisterOnjFunction(schema = "params: [float, float, float, float]")
    fun relMargin(left: OnjFloat, right: OnjFloat, top: OnjFloat, bottom: OnjFloat): OnjStyleProperty {
        return OnjStyleProperty(
            MarginProperty(
            arrayOf(
                Triple(left.value.toFloat(), true, YogaEdge.LEFT),
                Triple(right.value.toFloat(), true, YogaEdge.RIGHT),
                Triple(top.value.toFloat(), true, YogaEdge.TOP),
                Triple(bottom.value.toFloat(), true, YogaEdge.BOTTOM),
            ),
            null
        )
        )
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // flexbox properties
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @RegisterOnjFunction(schema = "params: [string]")
    fun flexDirection(direction: OnjString): OnjStyleProperty {
        return OnjStyleProperty(
            FlexDirectionProperty(
            when (direction.value) {
                "row" -> YogaFlexDirection.ROW
                "row reverse" -> YogaFlexDirection.ROW_REVERSE
                "column" -> YogaFlexDirection.COLUMN
                "column reverse" -> YogaFlexDirection.COLUMN_REVERSE
                else -> throw RuntimeException("unknown flex direction: ${direction.value}")
            },
            null
        )
        )
    }

    @RegisterOnjFunction(schema = "params: [string]")
    fun alignItems(alignment: OnjString): OnjStyleProperty {
        return OnjStyleProperty(
            FlexAlignProperty(
            yogaAlignmentOrError(alignment.value),
            isItems = true,
            isContent = false,
            condition = null
        )
        )
    }

    @RegisterOnjFunction(schema = "params: [string]")
    fun alignContent(alignment: OnjString): OnjStyleProperty {
        return OnjStyleProperty(
            FlexAlignProperty(
            yogaAlignmentOrError(alignment.value),
            isItems = false,
            isContent = true,
            condition = null
        )
        )
    }

    @RegisterOnjFunction(schema = "params: [string]")
    fun alignSelf(alignment: OnjString): OnjStyleProperty {
        return OnjStyleProperty(
            AlignSelfProperty(
            yogaAlignmentOrError(alignment.value),
            null
        )
        )
    }

    @RegisterOnjFunction(schema = "params: [string]")
    fun justifyContent(justify: OnjString): OnjStyleProperty {
        return OnjStyleProperty(
            FlexJustifyContentProperty(
            yogaJustifyOrError(justify.value),
            null
        )
        )
    }

    @RegisterOnjFunction(schema = "params: [float]")
    fun flexGrow(grow: OnjFloat): OnjStyleProperty {
        return OnjStyleProperty(
            GrowShrinkProperty(
            grow.value.toFloat(), null, null
        )
        )
    }

    @RegisterOnjFunction(schema = "params: [float]")
    fun flexShrink(shrink: OnjFloat): OnjStyleProperty {
        return OnjStyleProperty(
            GrowShrinkProperty(
            null, shrink.value.toFloat(), null
        )
        )
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // position
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @RegisterOnjFunction(schema = "params: [string]")
    fun position(position: OnjString): OnjStyleProperty {
        return OnjStyleProperty(
            PositionProperty(
            when (position.value) {
                "absolute" -> YogaPositionType.ABSOLUTE
                "relative" -> YogaPositionType.RELATIVE
                "static" -> YogaPositionType.STATIC
                else -> throw RuntimeException("unknown position: ${position.value}")
            },
            null
        )
        )
    }

    @RegisterOnjFunction(schema = "params: [float?, float?, float?, float?]")
    fun position(left: OnjValue, right: OnjValue, top: OnjValue, bottom: OnjValue): OnjStyleProperty {
        return OnjStyleProperty(
            PositionFloatProperty(
            if (left.isNull()) null else (left as OnjFloat).value.toFloat(),
            if (right.isNull()) null else (right as OnjFloat).value.toFloat(),
            if (top.isNull()) null else (top as OnjFloat).value.toFloat(),
            if (bottom.isNull()) null else (bottom as OnjFloat).value.toFloat(),
            null
        )
        )
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // other properties
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @RegisterOnjFunction(schema = "params: [float]")
    fun fontScale(scale: OnjFloat): OnjStyleProperty {
        return OnjStyleProperty(FontScaleProperty(scale.value.toFloat(), null))
    }

    @RegisterOnjFunction(schema = "use Common; params: [ float, float, Interpolation ]")
    fun fontScaleTo(scale: OnjFloat, duration: OnjFloat, interpolation: OnjInterpolation): OnjStyleProperty {
        return OnjStyleProperty(
            FontScaleAnimationProperty(
            (duration.value * 1000).toInt(),
            interpolation.value,
            scale.value.toFloat(),
            null
        )
        )
    }

    @RegisterOnjFunction(schema = "params: [string?]")
    fun background(name: OnjValue): OnjStyleProperty {
        return OnjStyleProperty(
            if (name.isString()) {
                BackgroundProperty(name.value as String, null)
            } else {
                BackgroundProperty(null, null)
            }
        )
    }

    @RegisterOnjFunction(schema = "params: [string]")
    fun textAlign(name: OnjString): OnjStyleProperty {
        return OnjStyleProperty(TextAlignProperty(alignmentOrError(name.value), null))
    }

    @RegisterOnjFunction(schema = "use Common; params: [Color]")
    fun textColor(color: OnjColor): OnjStyleProperty {
        return OnjStyleProperty(TextColorProperty(color.value, null))
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // conditions
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    @RegisterOnjFunction(schema = "params: []")
    fun self(): OnjStyleActorRef = OnjStyleActorRef(StyleActorReference.Self())

    @RegisterOnjFunction(schema = "use Style; params: [StyleActorRef]")
    fun hover(actorRef: OnjStyleActorRef): OnjStyleCondition {
        return OnjStyleCondition(StyleCondition.Hover(actorRef.value))
    }

    @RegisterOnjFunction(
        schema = "use Style; params: [StyleProperty, StyleCondition]",
        type = OnjFunctionType.INFIX
    )
    fun condition(property: OnjStyleProperty, condition: OnjStyleCondition): OnjStyleProperty {
        return OnjStyleProperty(property.value.getWithCondition(condition.value))
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // helper functions
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private fun alignmentOrError(alignment: String): Int = when (alignment) {
        "center" -> Align.center
        "top" -> Align.top
        "bottom" -> Align.bottom
        "left" -> Align.left
        "bottom left" -> Align.bottomLeft
        "top left" -> Align.topLeft
        "right" -> Align.right
        "bottom right" -> Align.bottomRight
        "top right" -> Align.topRight
        else -> throw RuntimeException("unknown alignment: $alignment")
    }

    private fun yogaAlignmentOrError(alignment: String): YogaAlign = when (alignment) {
        "auto" -> YogaAlign.AUTO
        "baseline" -> YogaAlign.BASELINE
        "center" -> YogaAlign.CENTER
        "flex start" -> YogaAlign.FLEX_START
        "flex end" -> YogaAlign.FLEX_END
        "space around" -> YogaAlign.SPACE_AROUND
        "space between" -> YogaAlign.SPACE_BETWEEN
        "stretch" -> YogaAlign.STRETCH
        else -> throw RuntimeException("unknown yoga alignment: $alignment")
    }

    private fun yogaJustifyOrError(justify: String): YogaJustify = when (justify) {
        "center" -> YogaJustify.CENTER
        "flex start" -> YogaJustify.FLEX_START
        "flex end" -> YogaJustify.FLEX_END
        "space around" -> YogaJustify.SPACE_AROUND
        "space between" -> YogaJustify.SPACE_BETWEEN
        "space evenly" -> YogaJustify.SPACE_EVENLY
        else -> throw RuntimeException("unknown justify value: $justify")
    }

}

class OnjStyleProperty(
    override val value: StyleProperty<*>
) : OnjValue() {

    override fun toString(): String = "'--property--'"
    override fun toString(indentationLevel: Int): String = toString()
    override fun toJsonString(): String = toString()
    override fun toJsonString(indentationLevel: Int): String = toString()
}

class OnjStyleCondition(
    override val value: StyleCondition
) : OnjValue() {

    override fun toString(): String = "'--style-condition--'"
    override fun toString(indentationLevel: Int): String = toString()
    override fun toJsonString(): String = toString()
    override fun toJsonString(indentationLevel: Int): String = toString()
}

class OnjStyleActorRef(
    override val value: StyleActorReference
) : OnjValue() {

    override fun toString(): String = "'--style-actor-ref--'"
    override fun toString(indentationLevel: Int): String = toString()
    override fun toJsonString(): String = toString()
    override fun toJsonString(indentationLevel: Int): String = toString()
}
