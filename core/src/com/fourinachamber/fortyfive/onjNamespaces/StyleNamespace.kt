package com.fourinachamber.fortyfive.onjNamespaces

import com.fourinachamber.fortyfive.screen.general.styles.StyleCondition
import io.github.orioncraftmc.meditate.YogaValue
import io.github.orioncraftmc.meditate.enums.*
import onj.builder.buildOnjObject
import onj.customization.Namespace.*
import onj.customization.OnjFunction.*
import onj.customization.OnjFunction.RegisterOnjFunction.OnjFunctionType
import onj.value.OnjFloat
import onj.value.OnjInt
import onj.value.OnjString
import onj.value.OnjValue
import kotlin.reflect.KClass

@OnjNamespace
object StyleNamespace {

    @OnjNamespaceDatatypes
    val datatypes: Map<String, KClass<*>> = mapOf(
        "StyleCondition" to OnjStyleCondition::class
    )

    @OnjNamespaceVariables
    val variables: Map<String, OnjValue> = mapOf(
        "auto" to OnjYogaValue(YogaValue.parse("auto")),
        "flexDirection" to buildOnjObject {
            "row" with OnjFlexDirection(YogaFlexDirection.ROW)
            "column" with OnjFlexDirection(YogaFlexDirection.COLUMN)
            "rowReverse" with OnjFlexDirection(YogaFlexDirection.ROW_REVERSE)
            "columnReverse" with OnjFlexDirection(YogaFlexDirection.COLUMN_REVERSE)
        },
        "align" to buildOnjObject {
            "auto" with OnjYogaAlign(YogaAlign.AUTO)
            "stretch" with OnjYogaAlign(YogaAlign.STRETCH)
            "spaceBetween" with OnjYogaAlign(YogaAlign.SPACE_BETWEEN)
            "spaceAround" with OnjYogaAlign(YogaAlign.SPACE_AROUND)
            "flexStart" with OnjYogaAlign(YogaAlign.FLEX_START)
            "flexEnd" with OnjYogaAlign(YogaAlign.FLEX_END)
            "center" with OnjYogaAlign(YogaAlign.CENTER)
            "baseline" with OnjYogaAlign(YogaAlign.BASELINE)
        },
        "justify" to buildOnjObject {
            "flexStart" with OnjYogaJustify(YogaJustify.FLEX_START)
            "flexEnd" with OnjYogaJustify(YogaJustify.FLEX_END)
            "center" with OnjYogaJustify(YogaJustify.CENTER)
            "spaceAround" with OnjYogaJustify(YogaJustify.SPACE_AROUND)
            "spaceBetween" with OnjYogaJustify(YogaJustify.SPACE_BETWEEN)
            "spaceEvenly" with OnjYogaJustify(YogaJustify.SPACE_EVENLY)
        },
        "positionType" to buildOnjObject {
            "relative" with OnjPositionType(YogaPositionType.RELATIVE)
            "static" with OnjPositionType(YogaPositionType.STATIC)
            "absolute" with OnjPositionType(YogaPositionType.ABSOLUTE)
        },
        "wrap" to buildOnjObject {
            "noWrap" with OnjFlexWrap(YogaWrap.NO_WRAP)
            "wrap" with OnjFlexWrap(YogaWrap.WRAP)
            "wrapReverse" with OnjFlexWrap(YogaWrap.WRAP_REVERSE)
        }
    )

    @RegisterOnjFunction(schema = "params: []")
    fun always(): OnjStyleCondition = OnjStyleCondition(StyleCondition.Always)

    @RegisterOnjFunction(schema = "params: []")
    fun hover(): OnjStyleCondition = OnjStyleCondition(StyleCondition.IsHoveredOver)

    @RegisterOnjFunction(schema = "params: [string]")
    fun state(state: OnjString): OnjStyleCondition = OnjStyleCondition(StyleCondition.ScreenState(state.value))

    @RegisterOnjFunction(schema = "params: [string]")
    fun actorState(state: OnjString): OnjStyleCondition = OnjStyleCondition(StyleCondition.ActorState(state.value))

    @RegisterOnjFunction(schema = "use Style; params: [StyleCondition, StyleCondition]", type = OnjFunctionType.INFIX)
    fun or(first: OnjStyleCondition, second: OnjStyleCondition): OnjStyleCondition =
        OnjStyleCondition(StyleCondition.Or(first.value, second.value))

    @RegisterOnjFunction(schema = "use Style; params: [StyleCondition, StyleCondition]", type = OnjFunctionType.INFIX)
    fun and(first: OnjStyleCondition, second: OnjStyleCondition): OnjStyleCondition =
        OnjStyleCondition(StyleCondition.And(first.value, second.value))

    @RegisterOnjFunction(schema = "use Style; params: [StyleCondition]")
    fun not(first: OnjStyleCondition): OnjStyleCondition = OnjStyleCondition(StyleCondition.Not(first.value))

    @RegisterOnjFunction(schema = "params: [float]", type = OnjFunctionType.CONVERSION)
    fun percent(value: OnjFloat): OnjYogaValue = OnjYogaValue(YogaValue(value.value.toFloat(), YogaUnit.PERCENT))

    @RegisterOnjFunction(schema = "params: [float]", type = OnjFunctionType.CONVERSION)
    fun points(value: OnjFloat): OnjYogaValue = OnjYogaValue(YogaValue(value.value.toFloat(), YogaUnit.POINT))

    @RegisterOnjFunction(schema = "params: [int]", type = OnjFunctionType.CONVERSION)
    fun percent(value: OnjInt): OnjYogaValue = OnjYogaValue(YogaValue(value.value.toFloat(), YogaUnit.PERCENT))

    @RegisterOnjFunction(schema = "params: [int]", type = OnjFunctionType.CONVERSION)
    fun points(value: OnjInt): OnjYogaValue = OnjYogaValue(YogaValue(value.value.toFloat(), YogaUnit.POINT))

}

class OnjPositionType(
    override val value: YogaPositionType
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--yoga-position-type--'")
    }
}

class OnjYogaAlign(
    override val value: YogaAlign
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--yoga-align--'")
    }
}

class OnjYogaJustify(
    override val value: YogaJustify
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--yoga-justify--'")
    }
}

class OnjFlexDirection(
    override val value: YogaFlexDirection
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--yoga-flex-direction--'")
    }
}

class OnjYogaValue(
    override val value: YogaValue
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--yoga-value--'")
    }
}

class OnjFlexWrap(
    override val value: YogaWrap
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--yoga-wrap--'")
    }
}

class OnjStyleCondition(
    override val value: StyleCondition
) : OnjValue() {

    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--style-condition--'")
    }
}
