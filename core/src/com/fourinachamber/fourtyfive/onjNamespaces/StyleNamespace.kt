package com.fourinachamber.fourtyfive.onjNamespaces

import com.fourinachamber.fourtyfive.screen.general.styleTest.StyleCondition
import io.github.orioncraftmc.meditate.YogaValue
import io.github.orioncraftmc.meditate.enums.YogaAlign
import io.github.orioncraftmc.meditate.enums.YogaFlexDirection
import io.github.orioncraftmc.meditate.enums.YogaJustify
import io.github.orioncraftmc.meditate.enums.YogaUnit
import onj.builder.buildOnjObject
import onj.customization.Namespace
import onj.customization.Namespace.*
import onj.customization.OnjFunction
import onj.customization.OnjFunction.*
import onj.customization.OnjFunction.RegisterOnjFunction.OnjFunctionType
import onj.value.OnjFloat
import onj.value.OnjString
import onj.value.OnjValue

@OnjNamespace
object StyleNamespace {

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
        }
    )

    @RegisterOnjFunction(schema = "params: []")
    fun always(): OnjStyleCondition = OnjStyleCondition(StyleCondition.Always)

    @RegisterOnjFunction(schema = "params: []")
    fun hover(): OnjStyleCondition = OnjStyleCondition(StyleCondition.IsHoveredOver)

    @RegisterOnjFunction(schema = "params: [string]")
    fun state(state: OnjString): OnjStyleCondition = OnjStyleCondition(StyleCondition.ScreenState(state.value))


    @RegisterOnjFunction(schema = "params: [float]", type = OnjFunctionType.CONVERSION)
    fun percent(value: OnjFloat): OnjYogaValue = OnjYogaValue(YogaValue(value.value.toFloat(), YogaUnit.PERCENT))

    @RegisterOnjFunction(schema = "params: [float]", type = OnjFunctionType.CONVERSION)
    fun points(value: OnjFloat): OnjYogaValue = OnjYogaValue(YogaValue(value.value.toFloat(), YogaUnit.POINT))

}

class OnjYogaAlign(
    override val value: YogaAlign
) : OnjValue() {

    override fun toString(): String = "'--yoga-align--'"
    override fun toString(indentationLevel: Int): String = "'--yoga-align--'"
    override fun toJsonString(): String = "'--yoga-align--'"
    override fun toJsonString(indentationLevel: Int): String = "'--yoga-align--'"
}

class OnjYogaJustify(
    override val value: YogaJustify
) : OnjValue() {

    override fun toString(): String = "'--yoga-justify--'"
    override fun toString(indentationLevel: Int): String = "'--yoga-justify--'"
    override fun toJsonString(): String = "'--yoga-justify--'"
    override fun toJsonString(indentationLevel: Int): String = "'--yoga-justify--'"
}

class OnjFlexDirection(
    override val value: YogaFlexDirection
) : OnjValue() {

    override fun toString(): String = "'--yoga-flex-direction--'"
    override fun toString(indentationLevel: Int): String = "'--yoga-flex-direction--'"
    override fun toJsonString(): String = "'--yoga-flex-direction--'"
    override fun toJsonString(indentationLevel: Int): String = "'--yoga-flex-direction--'"
}

class OnjYogaValue(
    override val value: YogaValue
) : OnjValue() {

    override fun toString(): String = "'--yoga-value--'"
    override fun toString(indentationLevel: Int): String = "'--yoga-value--'"
    override fun toJsonString(): String = "'--yoga-value--'"
    override fun toJsonString(indentationLevel: Int): String = "'--yoga-value--'"
}

class OnjStyleCondition(
    override val value: StyleCondition
) : OnjValue() {

    override fun toString(): String = "'--style-condition--'"
    override fun toString(indentationLevel: Int): String = "'--style-condition--'"
    override fun toJsonString(): String = "'--style-condition--'"
    override fun toJsonString(indentationLevel: Int): String = "'--style-condition--'"
}
