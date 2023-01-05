package com.fourinachamber.fourtyfive.onjNamespaces

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.fourinachamber.fourtyfive.utils.Utils
import onj.customization.Namespace.OnjNamespaceDatatypes
import onj.customization.Namespace.OnjNamespace
import onj.customization.OnjFunction.RegisterOnjFunction
import onj.value.OnjString
import onj.value.OnjValue
import kotlin.reflect.KClass

@Suppress("unused") // values and functions are read via reflection
@OnjNamespace
object CommonNamespace {

    @OnjNamespaceDatatypes
    val datatypes: Map<String, KClass<*>> = mapOf(
        "Color" to OnjColor::class,
        "Interpolation" to OnjInterpolation::class,
    )

    @RegisterOnjFunction(schema = "params: [string]")
    fun color(s: OnjString): OnjColor = OnjColor(Color.valueOf(s.value))

    @RegisterOnjFunction(schema = "params: [string]")
    fun interpolation(s: OnjString): OnjInterpolation = OnjInterpolation(Utils.interpolationOrError(s.value))

}

/**
 * a color that was read from an onj file
 */
class OnjColor(
    override val value: Color
) : OnjValue() {

    override fun toString(): String = "color($value)"
    override fun toString(indentationLevel: Int): String = toString()
    override fun toJsonString(): String = toString()
    override fun toJsonString(indentationLevel: Int): String = toString()

}

/**
 * an interpolation that was read from an onj file
 */
class OnjInterpolation(
    override val value: Interpolation
) : OnjValue() {

    override fun toString(): String = "'--interpolation--'"
    override fun toString(indentationLevel: Int): String = toString()
    override fun toJsonString(): String = toString()
    override fun toJsonString(indentationLevel: Int): String = toString()

}
