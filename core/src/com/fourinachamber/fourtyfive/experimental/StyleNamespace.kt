package com.fourinachamber.fourtyfive.experimental

import onj.customization.Namespace
import onj.value.OnjValue
import onj.customization.Namespace.OnjNamespace
import onj.customization.Namespace.OnjNamespaceDatatypes
import onj.customization.OnjFunction.RegisterOnjFunction
import kotlin.reflect.KClass

@OnjNamespace
object StyleNamespace {

    @OnjNamespaceDatatypes
    val datatypes: Map<String, KClass<*>> = mapOf(
        "StyleProperty" to OnjStyleProperty::class
    )

    @RegisterOnjFunction(schema = "params: [string?]")
    fun background(name: OnjValue): OnjStyleProperty {
        return OnjStyleProperty(
            if (name.isString()) BackgroundProperty(name.value as String) else BackgroundProperty(null)
        )
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
