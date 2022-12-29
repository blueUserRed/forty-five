package com.fourinachamber.fourtyfive.onjNamespaces

import com.badlogic.gdx.graphics.Color
import onj.customization.Namespace.OnjNamespaceDatatypes
import onj.customization.Namespace.OnjNamespace
import onj.customization.OnjFunction.RegisterOnjFunction
import onj.value.OnjString
import onj.value.OnjValue
import kotlin.reflect.KClass

@OnjNamespace
object Common {

    @OnjNamespaceDatatypes
    val datatypes: Map<String, KClass<*>> = mapOf(
        "Color" to OnjColor::class
    )

    @RegisterOnjFunction(schema = "params: [string]")
    fun color(s: OnjString): OnjColor = OnjColor(Color.valueOf(s.value))

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
