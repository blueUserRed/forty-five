package com.fourinachamber.fourtyfive.experimental

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.utils.Align
import com.fourinachamber.fourtyfive.onjNamespaces.OnjColor
import com.fourinachamber.fourtyfive.onjNamespaces.OnjInterpolation
import onj.value.OnjValue
import onj.customization.Namespace.OnjNamespace
import onj.customization.Namespace.OnjNamespaceDatatypes
import onj.customization.OnjFunction.RegisterOnjFunction
import onj.customization.OnjFunction.RegisterOnjFunction.OnjFunctionType
import onj.value.OnjFloat
import onj.value.OnjString
import kotlin.reflect.KClass

@OnjNamespace
object StyleNamespace {

    @OnjNamespaceDatatypes
    val datatypes: Map<String, KClass<*>> = mapOf(
        "StyleProperty" to OnjStyleProperty::class,
        "StyleCondition" to OnjStyleCondition::class,
        "StyleActorRef" to OnjStyleActorRef::class,
    )

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

    @RegisterOnjFunction(schema = "params: [float]")
    fun width(width: OnjFloat): OnjStyleProperty {
        return OnjStyleProperty(DimensionsProperty(
            width.value.toFloat(), null,
            widthRelative = false,
            heightRelative = false,
            widthAuto = false,
            heightAuto = false,
            condition = null
        ))
    }

    @RegisterOnjFunction(schema = "params: [float]")
    fun height(height: OnjFloat): OnjStyleProperty {
        return OnjStyleProperty(DimensionsProperty(
            null,
            height.value.toFloat(),
            widthRelative = false,
            heightRelative = false,
            widthAuto = false,
            heightAuto = false,
            condition = null
        ))
    }

    @RegisterOnjFunction(schema = "params: [float]")
    fun relWidth(width: OnjFloat): OnjStyleProperty {
        return OnjStyleProperty(DimensionsProperty(
            width.value.toFloat(), null,
            widthRelative = true,
            heightRelative = false,
            widthAuto = false, heightAuto = false,
            condition = null
        ))
    }

    @RegisterOnjFunction(schema = "params: [float]")
    fun relHeight(height: OnjFloat): OnjStyleProperty {
        return OnjStyleProperty(DimensionsProperty(
            null, height.value.toFloat(),
            widthRelative = false,
            heightRelative = true,
            widthAuto = false,
            heightAuto = false,
            condition = null
        ))
    }

    @RegisterOnjFunction(schema = "use Common; params: [float, float, Interpolation]")
    fun relWidthTo(width: OnjFloat, duration: OnjFloat, interpolation: OnjInterpolation): OnjStyleProperty {
        return OnjStyleProperty(DimensionsAnimationProperty(
            width.value.toFloat(),
            true,
            true,
            (duration.value * 1000f).toInt(),
            interpolation.value,
            null
        ))
    }

    @RegisterOnjFunction(schema = "params: []")
    fun self(): OnjStyleActorRef = OnjStyleActorRef(StyleActorReference.Self())

    @RegisterOnjFunction(schema = "use Experimental__Style; params: [StyleActorRef]")
    fun hover(actorRef: OnjStyleActorRef): OnjStyleCondition {
        return OnjStyleCondition(StyleCondition.Hover(actorRef.value))
    }

    @RegisterOnjFunction(
        schema = "use Experimental__Style; params: [StyleProperty, StyleCondition]",
        type = OnjFunctionType.INFIX
    )
    fun condition(property: OnjStyleProperty, condition: OnjStyleCondition): OnjStyleProperty {
        return OnjStyleProperty(property.value.getWithCondition(condition.value))
    }

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

}

class OnjStyleProperty(
    override val value: StyleProperty
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
