package com.fourinachamber.fortyfive.onjNamespaces

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.math.Interpolation
import com.fourinachamber.fortyfive.utils.Utils
import onj.builder.buildOnjObject
import onj.customization.Namespace.*
import onj.customization.OnjFunction.RegisterOnjFunction
import onj.value.*
import kotlin.reflect.KClass

@Suppress("unused") // values and functions are read via reflection
@OnjNamespace
object CommonNamespace {

    @OnjNamespaceDatatypes
    val datatypes: Map<String, KClass<*>> = mapOf(
        "Color" to OnjColor::class,
        "Interpolation" to OnjInterpolation::class,
    )

    @OnjNamespaceVariables
    val variables: Map<String, OnjValue> = mapOf(
        "interpolation" to buildOnjObject {
            "linear" with OnjInterpolation(Interpolation.linear)
            "swing" with OnjInterpolation(Interpolation.swing)
            "swing_in" with OnjInterpolation(Interpolation.swingIn)
            "swing_out" with OnjInterpolation(Interpolation.swingOut)
            "bounce" with OnjInterpolation(Interpolation.bounce)
            "bounce_in" with OnjInterpolation(Interpolation.bounceIn)
            "bounce_out" with OnjInterpolation(Interpolation.bounceOut)
            "elastic" with OnjInterpolation(Interpolation.elastic)
            "elastic_in" with OnjInterpolation(Interpolation.elasticIn)
            "elastic_out" with OnjInterpolation(Interpolation.elasticOut)
            "circle" with OnjInterpolation(Interpolation.circle)
            "circle_in" with OnjInterpolation(Interpolation.circleIn)
            "circle_out" with OnjInterpolation(Interpolation.circleOut)
        }
    )

    @RegisterOnjFunction(schema = "params: [string]")
    fun color(s: OnjString): OnjColor = OnjColor(Color.valueOf(s.value))

    @RegisterOnjFunction(schema = "params: [string]")
    fun interpolation(s: OnjString): OnjInterpolation = OnjInterpolation(Utils.interpolationOrError(s.value))

    @RegisterOnjFunction(schema = "params: [string[]]")
    fun advancedText(arr: OnjArray): OnjArray { // TODO: continue
        val s = arr.value.joinToString { it.value as String }
        return OnjArray(listOf())
    }

    @RegisterOnjFunction("params: [string,string]", RegisterOnjFunction.OnjFunctionType.OPERATOR)
    fun plus(s: OnjString, s2: OnjString): OnjString = OnjString(s.value + s2.value)

    @RegisterOnjFunction("params: [int,*]")
    fun repeat(amount: OnjInt, value: OnjValue): OnjArray = OnjArray(List(amount.value.toInt()) { value })

}

/**
 * a color that was read from an onj file
 */
class OnjColor(
    override val value: Color
) : OnjValue() {


    override fun stringify(info: ToStringInformation) {
        info.builder.append(if (info.json) "'$value'" else "color('$value')")
    }

}

/**
 * an interpolation that was read from an onj file
 */
class OnjInterpolation(
    override val value: Interpolation
) : OnjValue() {


    override fun stringify(info: ToStringInformation) {
        info.builder.append("'--interpolation--'")
    }

}
